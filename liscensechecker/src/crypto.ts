import {
  createHash,
  createHmac,
  createPrivateKey,
  createPublicKey,
  randomBytes,
  sign,
  timingSafeEqual,
  verify
} from "node:crypto";

export function normalizeLicenseKey(value: string): string {
  return value.trim().toUpperCase().replace(/\s+/g, "");
}

export function keyedHash(value: string, pepper: string): string {
  return createHmac("sha256", pepper).update(value, "utf8").digest("hex");
}

export function sha256(value: string | Buffer): string {
  return createHash("sha256").update(value).digest("hex");
}

export function randomOpaqueToken(prefix: string): string {
  return `${prefix}${randomBytes(32).toString("base64url")}`;
}

export function generateLicenseKey(tier: "base" | "premium"): string {
  const body = randomBytes(20).toString("base64url").toUpperCase().replace(/[_-]/g, "X").slice(0, 25);
  const groups = body.match(/.{1,5}/g)?.join("-") ?? body;
  return `ARC-${tier === "premium" ? "PRO" : "BASE"}-${groups}`;
}

export function publicKeyFingerprint(publicKeyPem: string): string {
  const key = createPublicKey(publicKeyPem);
  if (key.asymmetricKeyType !== "ed25519") {
    throw new Error("Device key must be Ed25519");
  }
  const der = key.export({ type: "spki", format: "der" });
  return sha256(der);
}

export function verifyDeviceSignature(publicKeyPem: string, payload: string, signatureBase64: string): boolean {
  try {
    const key = createPublicKey(publicKeyPem);
    if (key.asymmetricKeyType !== "ed25519") return false;
    const signature = Buffer.from(signatureBase64, "base64");
    return verify(null, Buffer.from(payload, "utf8"), key, signature);
  } catch {
    return false;
  }
}

export function canonical(operation: string, timestamp: number, nonce: string, ...parts: string[]): string {
  return [operation, timestamp.toString(), nonce, ...parts].join("\n");
}

export function constantTimeEqual(left: string, right: string): boolean {
  const leftHash = createHash("sha256").update(left).digest();
  const rightHash = createHash("sha256").update(right).digest();
  return timingSafeEqual(leftHash, rightHash);
}

export function issueEntitlement(
  privateKeyBase64: string,
  claims: {
    licenseId: string;
    deviceFingerprint: string;
    tier: "base" | "premium";
    ttlMinutes: number;
  }
): string {
  const now = Math.floor(Date.now() / 1_000);
  const payload = Buffer.from(JSON.stringify({
    iss: "arcane-license-server",
    aud: "arcane-client",
    sub: claims.licenseId,
    device: claims.deviceFingerprint,
    tier: claims.tier,
    iat: now,
    exp: now + claims.ttlMinutes * 60,
    jti: randomBytes(16).toString("base64url")
  }), "utf8").toString("base64url");
  const privateKey = createPrivateKey({
    key: Buffer.from(privateKeyBase64, "base64"),
    format: "der",
    type: "pkcs8"
  });
  if (privateKey.asymmetricKeyType !== "ed25519") throw new Error("Entitlement signing key must be Ed25519");
  const signature = sign(null, Buffer.from(payload, "ascii"), privateKey).toString("base64url");
  return `${payload}.${signature}`;
}