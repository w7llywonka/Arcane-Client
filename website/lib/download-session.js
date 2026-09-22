"use strict";

const { createHmac, timingSafeEqual, randomBytes } = require("node:crypto");

const COOKIE = "__Secure-arcane_download";

function signature(payload, secret) {
  return createHmac("sha256", secret)
    .update("arcane-download-v1:" + payload)
    .digest("hex");
}

function createSession(secret, now = Date.now()) {
  const payload = Buffer.from(JSON.stringify({
    exp: Math.floor(now / 1000) + 300,
    nonce: randomBytes(16).toString("hex"),
  })).toString("base64url");
  return payload + "." + signature(payload, secret);
}

function validSession(token, secret, now = Date.now()) {
  if (!secret || secret.length < 32 || typeof token !== "string" || token.length > 512) return false;
  const [payload, mac, extra] = token.split(".");
  if (extra || !payload || !/^[a-f0-9]{64}$/.test(mac || "")) return false;
  if (!timingSafeEqual(Buffer.from(mac, "hex"), Buffer.from(signature(payload, secret), "hex"))) return false;
  try {
    const { exp } = JSON.parse(Buffer.from(payload, "base64url"));
    return Number.isInteger(exp) && exp > now / 1000 && exp <= now / 1000 + 300;
  } catch {
    return false;
  }
}

module.exports = { COOKIE, createSession, validSession };
