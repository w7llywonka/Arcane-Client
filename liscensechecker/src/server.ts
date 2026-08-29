import { randomUUID } from "node:crypto";
import Fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import helmet from "@fastify/helmet";
import rateLimit from "@fastify/rate-limit";
import { z } from "zod";
import type { AppConfig } from "./config.js";
import type { DatabaseClient, DatabasePool } from "./db.js";
import {
  canonical,
  constantTimeEqual,
  generateLicenseKey,
  issueEntitlement,
  keyedHash,
  normalizeLicenseKey,
  publicKeyFingerprint,
  randomOpaqueToken,
  verifyDeviceSignature
} from "./crypto.js";

const noncePattern = /^[A-Za-z0-9_-]{16,96}$/;
const sessionPattern = /^arc_sess_[A-Za-z0-9_-]{32,96}$/;

const signedBase = z.object({
  devicePublicKey: z.string().min(80).max(1_000),
  timestamp: z.number().int().positive(),
  nonce: z.string().regex(noncePattern),
  signature: z.string().min(40).max(256)
});

const activationSchema = signedBase.extend({
  licenseKey: z.string().min(16).max(120),
  deviceName: z.string().trim().min(1).max(100)
});

const sessionSchema = signedBase.extend({
  sessionToken: z.string().regex(sessionPattern)
});

const releaseRequestSchema = sessionSchema.extend({
  minecraftVersion: z.string().min(1).max(32)
});

const createLicenseSchema = z.object({
  tier: z.enum(["base", "premium"]),
  expiresAt: z.iso.datetime().nullable().optional()
});

const createReleaseSchema = z.object({
  tier: z.enum(["base", "premium"]),
  version: z.string().min(1).max(64),
  minecraftVersion: z.string().min(1).max(32),
  fabricLoaderVersion: z.string().min(1).max(32),
  downloadUrl: z.url(),
  sha256: z.string().regex(/^[0-9a-fA-F]{64}$/),
  signature: z.string().min(40).max(512)
});

type SessionContext = {
  licenseId: string;
  tier: "base" | "premium";
  licenseExpiresAt: Date | null;
  sessionExpiresAt: Date;
  fingerprint: string;
};

function assertFresh(timestamp: number, skewSeconds: number): void {
  const delta = Math.abs(Date.now() - timestamp);
  if (delta > skewSeconds * 1_000) {
    const error = new Error("Request timestamp is outside the allowed window");
    (error as Error & { statusCode: number }).statusCode = 401;
    throw error;
  }
}

async function consumeNonce(client: DatabaseClient, fingerprint: string, nonce: string, skewSeconds: number): Promise<void> {
  await client.query("DELETE FROM request_nonces WHERE expires_at < NOW()");
  try {
    await client.query(
      "INSERT INTO request_nonces(device_fingerprint, nonce, expires_at) VALUES ($1, $2, NOW() + ($3 * INTERVAL '1 second'))",
      [fingerprint, nonce, skewSeconds]
    );
  } catch (error) {
    if ((error as { code?: string }).code === "23505") {
      const replay = new Error("Request nonce has already been used");
      (replay as Error & { statusCode: number }).statusCode = 409;
      throw replay;
    }
    throw error;
  }
}

function requireAdmin(request: FastifyRequest, adminToken: string): void {
  const authorization = request.headers.authorization ?? "";
  const supplied = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!supplied || !constantTimeEqual(supplied, adminToken)) {
    const error = new Error("Unauthorized");
    (error as Error & { statusCode: number }).statusCode = 401;
    throw error;
  }
}

async function validateSession(
  pool: DatabasePool,
  config: AppConfig,
  input: z.infer<typeof sessionSchema>,
  operation: string,
  extraCanonicalParts: string[] = []
): Promise<SessionContext> {
  assertFresh(input.timestamp, config.CLOCK_SKEW_SECONDS);
  const fingerprint = publicKeyFingerprint(input.devicePublicKey);
  const payload = canonical(operation, input.timestamp, input.nonce, input.sessionToken, ...extraCanonicalParts);
  if (!verifyDeviceSignature(input.devicePublicKey, payload, input.signature)) {
    const error = new Error("Invalid device signature");
    (error as Error & { statusCode: number }).statusCode = 401;
    throw error;
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await consumeNonce(client, fingerprint, input.nonce, config.CLOCK_SKEW_SECONDS);
    const result = await client.query<{
      license_id: string;
      tier: "base" | "premium";
      license_status: string;
      license_expires_at: Date | null;
      session_expires_at: Date;
      device_fingerprint: string;
      revoked_at: Date | null;
    }>(
      `SELECT s.license_id, l.tier, l.status AS license_status,
              l.expires_at AS license_expires_at, s.expires_at AS session_expires_at,
              s.device_fingerprint, s.revoked_at
         FROM sessions s
         JOIN licenses l ON l.id = s.license_id
        WHERE s.token_hash = $1
        FOR UPDATE OF s, l`,
      [keyedHash(input.sessionToken, config.LICENSE_PEPPER)]
    );
    const row = result.rows[0];
    const now = new Date();
    if (!row
      || row.device_fingerprint !== fingerprint
      || row.revoked_at
      || row.license_status !== "active"
      || row.session_expires_at <= now
      || (row.license_expires_at && row.license_expires_at <= now)) {
      const error = new Error("License session is not active");
      (error as Error & { statusCode: number }).statusCode = 401;
      throw error;
    }
    await client.query("UPDATE sessions SET last_seen_at = NOW() WHERE token_hash = $1", [
      keyedHash(input.sessionToken, config.LICENSE_PEPPER)
    ]);
    await client.query("UPDATE licenses SET last_seen_at = NOW() WHERE id = $1", [row.license_id]);
    await client.query("COMMIT");
    return {
      licenseId: row.license_id,
      tier: row.tier,
      licenseExpiresAt: row.license_expires_at,
      sessionExpiresAt: row.session_expires_at,
      fingerprint
    };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export async function buildServer(config: AppConfig, pool: DatabasePool): Promise<FastifyInstance> {
  const app = Fastify({
    trustProxy: true,
    logger: {
      level: config.NODE_ENV === "production" ? "info" : "debug",
      redact: {
        paths: ["req.headers.authorization", "req.body.licenseKey", "req.body.sessionToken", "req.body.signature"],
        censor: "[REDACTED]"
      }
    },
    bodyLimit: 32 * 1024
  });

  await app.register(helmet, { global: true });
  await app.register(rateLimit, { global: true, max: 120, timeWindow: "1 minute" });

  app.get("/health", async (_request, reply) => {
    await pool.query("SELECT 1");
    return reply.code(200).send({ status: "ok" });
  });

  app.post("/v1/licenses/activate", { config: { rateLimit: { max: 10, timeWindow: "1 minute" } } }, async (request, reply) => {
    const input = activationSchema.parse(request.body);
    assertFresh(input.timestamp, config.CLOCK_SKEW_SECONDS);
    const normalizedKey = normalizeLicenseKey(input.licenseKey);
    const fingerprint = publicKeyFingerprint(input.devicePublicKey);
    const payload = canonical("activate", input.timestamp, input.nonce, normalizedKey, input.deviceName);
    if (!verifyDeviceSignature(input.devicePublicKey, payload, input.signature)) {
      return reply.code(401).send({ error: "invalid_device_signature" });
    }

    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await consumeNonce(client, fingerprint, input.nonce, config.CLOCK_SKEW_SECONDS);
      const result = await client.query<{
        id: string;
        tier: "base" | "premium";
        status: string;
        expires_at: Date | null;
        device_fingerprint: string | null;
      }>(
        "SELECT id, tier, status, expires_at, device_fingerprint FROM licenses WHERE key_hash = $1 FOR UPDATE",
        [keyedHash(normalizedKey, config.LICENSE_PEPPER)]
      );
      const license = result.rows[0];
      const now = new Date();
      if (!license || license.status !== "active" || (license.expires_at && license.expires_at <= now)) {
        await client.query("ROLLBACK");
        return reply.code(401).send({ error: "license_inactive" });
      }
      if (license.device_fingerprint && license.device_fingerprint !== fingerprint) {
        await client.query("ROLLBACK");
        return reply.code(409).send({ error: "license_bound_to_another_device" });
      }
      if (!license.device_fingerprint) {
        await client.query(
          `UPDATE licenses
              SET device_fingerprint = $1, device_public_key = $2, device_name = $3,
                  activated_at = NOW(), last_seen_at = NOW()
            WHERE id = $4`,
          [fingerprint, input.devicePublicKey, input.deviceName, license.id]
        );
      }
      await client.query("UPDATE sessions SET revoked_at = NOW() WHERE license_id = $1 AND revoked_at IS NULL", [license.id]);
      const sessionToken = randomOpaqueToken("arc_sess_");
      const sessionExpiresAt = new Date(Date.now() + config.SESSION_TTL_HOURS * 60 * 60 * 1_000);
      await client.query(
        `INSERT INTO sessions(id, license_id, token_hash, device_fingerprint, expires_at)
         VALUES ($1, $2, $3, $4, $5)`,
        [randomUUID(), license.id, keyedHash(sessionToken, config.LICENSE_PEPPER), fingerprint, sessionExpiresAt]
      );
      await client.query("COMMIT");
      return reply.send({
        sessionToken,
        entitlementToken: issueEntitlement(config.ENTITLEMENT_PRIVATE_KEY_BASE64, {
          licenseId: license.id,
          deviceFingerprint: fingerprint,
          tier: license.tier,
          ttlMinutes: config.ENTITLEMENT_TTL_MINUTES
        }),
        sessionExpiresAt: sessionExpiresAt.toISOString(),
        license: { tier: license.tier, expiresAt: license.expires_at?.toISOString() ?? null }
      });
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  });

  app.post("/v1/licenses/validate", async (request) => {
    const input = sessionSchema.parse(request.body);
    const session = await validateSession(pool, config, input, "validate");
    return {
      active: true,
      tier: session.tier,
      entitlementToken: issueEntitlement(config.ENTITLEMENT_PRIVATE_KEY_BASE64, {
        licenseId: session.licenseId,
        deviceFingerprint: session.fingerprint,
        tier: session.tier,
        ttlMinutes: config.ENTITLEMENT_TTL_MINUTES
      }),
      licenseExpiresAt: session.licenseExpiresAt?.toISOString() ?? null,
      sessionExpiresAt: session.sessionExpiresAt.toISOString()
    };
  });

  app.post("/v1/releases/latest", async (request, reply) => {
    const input = releaseRequestSchema.parse(request.body);
    const session = await validateSession(pool, config, input, "latest-release", [input.minecraftVersion]);
    const allowedTiers = session.tier === "premium" ? ["premium", "base"] : ["base"];
    const release = await pool.query<{
      id: string;
      tier: "base" | "premium";
      version: string;
      minecraft_version: string;
      fabric_loader_version: string;
      download_url: string;
      sha256: string;
      signature: string;
    }>(
      `SELECT id, tier, version, minecraft_version, fabric_loader_version, download_url, sha256, signature
         FROM releases
        WHERE active = TRUE AND minecraft_version = $1 AND tier = ANY($2::text[])
        ORDER BY CASE WHEN tier = $3 THEN 0 ELSE 1 END, created_at DESC
        LIMIT 1`,
      [input.minecraftVersion, allowedTiers, session.tier]
    );
    const row = release.rows[0];
    if (!row) return reply.code(404).send({ error: "no_compatible_release" });
    return {
      licenseTier: session.tier,
      release: {
        id: row.id,
        tier: row.tier,
        version: row.version,
        minecraftVersion: row.minecraft_version,
        fabricLoaderVersion: row.fabric_loader_version,
        downloadUrl: row.download_url,
        sha256: row.sha256,
        signature: row.signature
      }
    };
  });

  app.post("/v1/admin/licenses", async (request, reply) => {
    requireAdmin(request, config.ADMIN_TOKEN);
    const input = createLicenseSchema.parse(request.body);
    const key = generateLicenseKey(input.tier);
    const id = randomUUID();
    await pool.query(
      "INSERT INTO licenses(id, key_hash, key_prefix, tier, expires_at) VALUES ($1, $2, $3, $4, $5)",
      [id, keyedHash(key, config.LICENSE_PEPPER), key.slice(0, 18), input.tier, input.expiresAt ?? null]
    );
    return reply.code(201).send({ id, licenseKey: key, tier: input.tier, expiresAt: input.expiresAt ?? null });
  });

  app.get("/v1/admin/licenses", async (request) => {
    requireAdmin(request, config.ADMIN_TOKEN);
    const result = await pool.query(
      `SELECT id, key_prefix, tier, status, expires_at, device_name, activated_at, last_seen_at, created_at
         FROM licenses ORDER BY created_at DESC LIMIT 500`
    );
    return { licenses: result.rows };
  });

  app.post("/v1/admin/licenses/:id/revoke", async (request, reply) => {
    requireAdmin(request, config.ADMIN_TOKEN);
    const id = z.uuid().parse((request.params as { id: string }).id);
    const result = await pool.query("UPDATE licenses SET status = 'revoked' WHERE id = $1", [id]);
    await pool.query("UPDATE sessions SET revoked_at = NOW() WHERE license_id = $1 AND revoked_at IS NULL", [id]);
    return result.rowCount === 0 ? reply.code(404).send({ error: "not_found" }) : { revoked: true };
  });

  app.post("/v1/admin/licenses/:id/reset-device", async (request, reply) => {
    requireAdmin(request, config.ADMIN_TOKEN);
    const id = z.uuid().parse((request.params as { id: string }).id);
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const result = await client.query(
        `UPDATE licenses SET device_fingerprint = NULL, device_public_key = NULL,
                             device_name = NULL, activated_at = NULL
          WHERE id = $1`,
        [id]
      );
      await client.query("UPDATE sessions SET revoked_at = NOW() WHERE license_id = $1 AND revoked_at IS NULL", [id]);
      await client.query("COMMIT");
      return result.rowCount === 0 ? reply.code(404).send({ error: "not_found" }) : { reset: true };
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  });

  app.post("/v1/admin/releases", async (request, reply) => {
    requireAdmin(request, config.ADMIN_TOKEN);
    const input = createReleaseSchema.parse(request.body);
    const id = randomUUID();
    await pool.query(
      `INSERT INTO releases(id, tier, version, minecraft_version, fabric_loader_version, download_url, sha256, signature)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [id, input.tier, input.version, input.minecraftVersion, input.fabricLoaderVersion,
        input.downloadUrl, input.sha256.toLowerCase(), input.signature]
    );
    return reply.code(201).send({ id, ...input, sha256: input.sha256.toLowerCase() });
  });

  app.setErrorHandler((error, _request, reply) => {
    if (error instanceof z.ZodError) {
      return reply.code(400).send({ error: "invalid_request", details: error.issues });
    }
    const statusCode = (error as Error & { statusCode?: number }).statusCode ?? 500;
    if (statusCode >= 500) app.log.error(error);
    const message = error instanceof Error ? error.message : "Request failed";
    return reply.code(statusCode).send({ error: statusCode >= 500 ? "internal_error" : message });
  });

  return app;
}
