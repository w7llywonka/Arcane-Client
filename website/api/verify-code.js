"use strict";

const { COOKIE, createSession } = require("../lib/download-session");

module.exports = async function verifyCode(req, res) {
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ ok: false, error: "METHOD_NOT_ALLOWED" });
  }
  if (!/^application\/json(?:\s*;|$)/i.test(req.headers["content-type"] || "")) {
    return res.status(415).json({ ok: false, error: "JSON_REQUIRED" });
  }
  if (req.headers.origin && req.headers.origin !== "https://" + req.headers.host) {
    return res.status(403).json({ ok: false, error: "INVALID_ORIGIN" });
  }

  let body = req.body;
  try {
    if (typeof body === "string") body = JSON.parse(body);
  } catch {
    return res.status(400).json({ ok: false, error: "INVALID_REQUEST" });
  }
  if (typeof body?.code !== "string" || !body.code.trim() || body.code.length > 256) {
    return res.status(400).json({ ok: false, error: "INVALID_CODE" });
  }
  const minecraftVersion = body.minecraftVersion === "26.3" ? "26.3" : "1.21.11";

  const secret = process.env.BOT_SHARED_SECRET;
  const service = process.env.BOT_SERVICE_URL;
  if (!service || !secret || secret.length < 32) {
    return res.status(503).json({ ok: false, error: "NOT_CONFIGURED" });
  }

  try {
    const target = new URL("/api/redeem", service);
    if (target.protocol !== "https:") throw new Error("INVALID_SERVICE");
    const response = await fetch(target, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + secret,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ code: body.code }),
      signal: AbortSignal.timeout(8000),
    });
    const result = await response.json();
    if (response.ok && result.ok === true) {
      res.setHeader(
        "Set-Cookie",
        COOKIE + "=" + createSession(secret)
          + "; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=300",
      );
      return res.status(200).json({
        ok: true,
        downloadUrl: "/api/download?version=" + encodeURIComponent(minecraftVersion),
      });
    }
    if (result.error === "NOT_CONFIGURED") {
      return res.status(503).json({ ok: false, error: "NOT_CONFIGURED" });
    }
    if (result.error === "INVALID_CODE" || result.error === "INVALID_OR_EXPIRED_CODE") {
      return res.status(401).json({ ok: false, error: "INVALID_OR_EXPIRED_CODE" });
    }
    if (result.error === "WEEKLY_LIMIT" && Number.isFinite(result.retryAfter)) {
      return res.status(429).json({
        ok: false,
        error: "WEEKLY_LIMIT",
        retryAfter: Math.max(1, Math.floor(result.retryAfter)),
      });
    }
    return res.status(503).json({ ok: false, error: "VERIFICATION_UNAVAILABLE" });
  } catch {
    return res.status(503).json({ ok: false, error: "VERIFICATION_UNAVAILABLE" });
  }
};
