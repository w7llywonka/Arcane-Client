"use strict";

const { readFile } = require("node:fs/promises");
const { join } = require("node:path");
const { COOKIE, validSession } = require("./download-session");

module.exports = async function protectedArtifact(req, res, artifact) {
  res.setHeader("Cache-Control", "private, no-store");
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    return res.status(405).end();
  }
  const token = (req.headers.cookie || "")
    .split(";")
    .map(value => value.trim())
    .find(value => value.startsWith(COOKIE + "="))
    ?.slice(COOKIE.length + 1);
  if (!validSession(token, process.env.BOT_SHARED_SECRET)) {
    console.warn("[download] rejected", { artifact: artifact.kind, reason: "CODE_REQUIRED" });
    return res.status(403).json({
      error: "CODE_REQUIRED",
      message: "Enter a one-time Discord code on the official website first.",
    });
  }
  try {
    const data = await readFile(join(
      process.cwd(),
      "private",
      artifact.storageFilename || artifact.filename,
    ));
    res.setHeader("Content-Type", "application/octet-stream");
    res.setHeader("Content-Length", String(data.length));
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    res.setHeader(
      "Content-Disposition",
      'attachment; filename="' + artifact.filename + '"; filename*=UTF-8\'\'' + encodeURIComponent(artifact.filename),
    );
    console.info("[download] served", { artifact: artifact.kind, bytes: data.length });
    return res.status(200).send(data);
  } catch (error) {
    console.error("[download] failed", { artifact: artifact.kind, error: error?.code || "UNKNOWN" });
    return res.status(503).json({ error: "DOWNLOAD_UNAVAILABLE" });
  }
};
