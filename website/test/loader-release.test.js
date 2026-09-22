"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createHash } = require("node:crypto");
const { readFile } = require("node:fs/promises");

const expectedManifest = {
  "schemaVersion": 2,
  "artifactType": "arcane-loader",
  "version": "1.2.4+mc1.21.11",
  "minecraftVersion": "1.21.11",
  "downloadUrl": "https://arcane-client-puce.vercel.app/updates/ARCLoader.jar",
  "sha256": "068b549dca41a16617f2fdb0fd214f93b9fe4479f389e326b1b1768fdf79b9bf",
  "signature": "fY69CCIqsxYPuTjPI+h+C82fKhkEYAx6/Mpd4QF95pElPp4fXS6d8cWzX/5a8y3fiPIKQYLMmGsf42709LqADQ==",
  "size": 2538650
};

test("the schema-2 loader feed matches the signed outer JAR", async () => {
  const manifest = JSON.parse(await readFile("updates/loader-release.json", "utf8"));
  assert.deepEqual(manifest, expectedManifest);
  assert.equal(Buffer.from(manifest.signature, "base64").length, 64);

  const filename = new URL(manifest.downloadUrl).pathname.split("/").at(-1);
  const artifact = await readFile("updates/" + filename);
  assert.equal(artifact.length, manifest.size);
  assert.equal(createHash("sha256").update(artifact).digest("hex"), manifest.sha256);
});

test("versioned loader channels are separate and match both signed outer JARs", async () => {
  const channels = [
    {
      version: "1.21.11",
      loaderVersion: "1.2.4+mc1.21.11",
      sha256: "068b549dca41a16617f2fdb0fd214f93b9fe4479f389e326b1b1768fdf79b9bf",
      size: 2538650,
    },
    {
      version: "26.3",
      loaderVersion: "1.2.7+mc26.3",
      sha256: "593cce3d2ad3c9764c6d4163dc467ac3a308b30485708638b46f8c4090d062a8",
      size: 2361894,
    },
  ];

  for (const channel of channels) {
    const directory = "updates/channels/mc-" + channel.version;
    const manifest = JSON.parse(await readFile(directory + "/loader-release.json", "utf8"));
    assert.equal(manifest.schemaVersion, 2);
    assert.equal(manifest.artifactType, "arcane-loader");
    assert.equal(manifest.minecraftVersion, channel.version);
    assert.equal(manifest.version, channel.loaderVersion);
    assert.equal(manifest.sha256, channel.sha256);
    assert.equal(manifest.size, channel.size);
    assert.equal(
      manifest.downloadUrl,
      "https://www.arcaneclient.shop/" + directory + "/ARCLoader.jar",
    );
    assert.equal(Buffer.from(manifest.signature, "base64").length, 64);
    const artifact = await readFile(directory + "/ARCLoader.jar");
    assert.equal(artifact.length, manifest.size);
    assert.equal(createHash("sha256").update(artifact).digest("hex"), manifest.sha256);
  }
});

test("the legacy client feed remains available for loader 1.2.1", async () => {
  const legacy = JSON.parse(await readFile("updates/release.json", "utf8"));
  assert.equal(legacy.schemaVersion, 1);
  assert.equal(legacy.version, "2.9.3+mc1.21.11");
  const bytes = await readFile("updates/" + new URL(legacy.downloadUrl).pathname.split("/").at(-1));
  assert.equal(bytes.length, legacy.size);
  assert.equal(createHash("sha256").update(bytes).digest("hex"), legacy.sha256);
});

test("Vercel serves the loader feed as fresh JSON", async () => {
  const config = JSON.parse(await readFile("vercel.json", "utf8"));
  const rule = config.headers.find(item => item.source === "/updates/loader-release.json");
  assert.ok(rule);
  assert.deepEqual(rule.headers, [
    { key: "Cache-Control", value: "public, max-age=0, must-revalidate" },
    { key: "Content-Type", value: "application/json; charset=utf-8" },
  ]);
  const channelRule = config.headers.find(
    item => item.source === "/updates/channels/(.*)/loader-release.json",
  );
  assert.ok(channelRule);
  assert.deepEqual(channelRule.headers, rule.headers);
});
