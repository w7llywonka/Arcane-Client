import { generateKeyPairSync, sign, verify } from "node:crypto";
import test from "node:test";
import assert from "node:assert/strict";
import {
  canonical,
  generateLicenseKey,
  normalizeLicenseKey,
  issueEntitlement,
  publicKeyFingerprint,
  verifyDeviceSignature
} from "../src/crypto.js";

test("normalizes and generates recognizable license keys", () => {
  assert.equal(normalizeLicenseKey("  arc-base-abcd  "), "ARC-BASE-ABCD");
  assert.match(generateLicenseKey("base"), /^ARC-BASE-/);
  assert.match(generateLicenseKey("premium"), /^ARC-PRO-/);
});

test("verifies Ed25519 device proof and rejects altered payloads", () => {
  const { publicKey, privateKey } = generateKeyPairSync("ed25519");
  const publicPem = publicKey.export({ type: "spki", format: "pem" }).toString();
  const payload = canonical("validate", 1234, "abcdefghijklmnop", "arc_sess_example");
  const signature = sign(null, Buffer.from(payload), privateKey).toString("base64");
  assert.equal(verifyDeviceSignature(publicPem, payload, signature), true);
  assert.equal(verifyDeviceSignature(publicPem, `${payload}!`, signature), false);
  assert.match(publicKeyFingerprint(publicPem), /^[0-9a-f]{64}$/);
});

test("issues verifiable short-lived entitlement tokens", () => {
  const { publicKey, privateKey } = generateKeyPairSync("ed25519");
  const privateBase64 = privateKey.export({ type: "pkcs8", format: "der" }).toString("base64");
  const token = issueEntitlement(privateBase64, {
    licenseId: "11111111-1111-4111-8111-111111111111",
    deviceFingerprint: "a".repeat(64),
    tier: "premium",
    ttlMinutes: 10
  });
  const [payload, signature] = token.split(".");
  assert.ok(payload && signature);
  assert.equal(verify(null, Buffer.from(payload, "ascii"), publicKey, Buffer.from(signature, "base64url")), true);
  const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
  assert.equal(claims.aud, "arcane-client");
  assert.equal(claims.tier, "premium");
  assert.equal(claims.device, "a".repeat(64));
  assert.ok(claims.exp > claims.iat);
});