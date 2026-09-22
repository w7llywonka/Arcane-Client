"use strict";

const protectedArtifact = require("../lib/protected-artifact");

module.exports = function downloadLoader(req, res) {
  const requestedVersion = Array.isArray(req.query?.version)
    ? req.query.version[0]
    : req.query?.version;
  const version = requestedVersion === "26.3" ? "26.3" : "1.21.11";
  const storageFilename = version === "26.3"
    ? "ARCLoader-26.3.jar"
    : "ARCLoader.jar";

  return protectedArtifact(req, res, {
    kind: "fabric-loader-" + version,
    filename: "ARCLoader.jar",
    storageFilename,
  });
};
