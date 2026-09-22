"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile, readdir } = require("node:fs/promises");
const verifyCode = require("../api/verify-code");
const download = require("../api/download");
const { COOKIE, createSession, validSession } = require("../lib/download-session");

const secret = "test-only-secret-".repeat(4);

function response() {
  return {
    headers: {},
    statusCode: 200,
    setHeader(key, value) { this.headers[key] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    send(value) { this.body = value; return this; },
    end() { return this; },
  };
}

function zipEntryNames(buffer) {
  const minimum = Math.max(0, buffer.length - 65_557);
  let end = -1;
  for (let offset = buffer.length - 22; offset >= minimum; offset--) {
    if (buffer.readUInt32LE(offset) === 0x06054b50) {
      end = offset;
      break;
    }
  }
  assert.notEqual(end, -1, "downloaded artifact must be a ZIP/JAR");
  const count = buffer.readUInt16LE(end + 10);
  let offset = buffer.readUInt32LE(end + 16);
  const names = [];
  for (let index = 0; index < count; index++) {
    assert.equal(buffer.readUInt32LE(offset), 0x02014b50, "invalid JAR directory");
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    names.push(buffer.subarray(offset + 46, offset + 46 + nameLength).toString("utf8"));
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return names;
}

test("download sessions expire and reject tampering", () => {
  const now = 1_700_000_000_000;
  const token = createSession(secret, now);
  assert.equal(validSession(token, secret, now), true);
  assert.equal(validSession(token, secret, now + 301_000), false);
  assert.equal(validSession(token + "x", secret, now), false);
});

test("the website grants a download only after the bot accepts the code", async () => {
  process.env.BOT_SHARED_SECRET = secret;
  process.env.BOT_SERVICE_URL = "https://bot.example";
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async (url, options) => {
      assert.equal(String(url), "https://bot.example/api/redeem");
      assert.equal(options.headers.Authorization, "Bearer " + secret);
      return { ok: true, json: async () => ({ ok: true }) };
    };
    const req = {
      method: "POST",
      headers: {
        "content-type": "application/json",
        host: "arcaneclient.shop",
        origin: "https://arcaneclient.shop",
      },
      body: {
        code: "ABCD-1234-ABCD-1234-ABCD-1234",
        minecraftVersion: "26.3",
      },
    };
    const res = response();
    await verifyCode(req, res);
    assert.equal(res.statusCode, 200);
    assert.deepEqual(res.body, {
      ok: true,
      downloadUrl: "/api/download?version=26.3",
    });
    assert.match(res.headers["Set-Cookie"], /HttpOnly; Secure; SameSite=Strict/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("the protected route serves the matching Fabric-recognized ARCLoader", async () => {
  process.env.BOT_SHARED_SECRET = secret;
  let res = response();
  await download({ method: "GET", headers: {} }, res);
  assert.equal(res.statusCode, 403);

  const token = createSession(secret);
  res = response();
  await download({ method: "GET", headers: { cookie: COOKIE + "=" + token } }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.headers["Content-Type"], "application/octet-stream");
  assert.equal(res.headers["X-Content-Type-Options"], "nosniff");
  assert.equal(res.headers["Cross-Origin-Resource-Policy"], "same-origin");
  assert.match(res.headers["Content-Disposition"], /^attachment;.*ARCLoader\.jar/);
  const expected = await readFile("private/ARCLoader.jar");
  assert.equal(res.headers["Content-Length"], String(expected.length));
  assert.deepEqual(res.body, expected);
  const entries = zipEntryNames(expected);
  assert.equal(entries.includes("fabric.mod.json"), true);
  assert.equal(entries.includes("dev/arcane/loader/ArcaneFabricLoader.class"), true);
  assert.equal(
    entries.includes("META-INF/jars/arcane-client-2.9.3+mc1.21.11.jar"),
    true,
    "the loader must contain Arcane Client for the first Fabric launch",
  );

  res = response();
  await download({
    method: "GET",
    headers: { cookie: COOKIE + "=" + token },
    query: { version: "26.3" },
  }, res);
  assert.equal(res.statusCode, 200);
  const expected263 = await readFile("private/ARCLoader-26.3.jar");
  assert.equal(res.headers["Content-Length"], String(expected263.length));
  assert.deepEqual(res.body, expected263);
  const entries263 = zipEntryNames(expected263);
  assert.equal(entries263.includes("fabric.mod.json"), true);
  assert.equal(entries263.includes("dev/arcane/loader/ArcaneFabricLoader.class"), true);
  assert.equal(
    entries263.includes("META-INF/jars/arcane-client-2.9.4+mc26.3.jar"),
    true,
    "the 26.3 loader must contain only the matching Arcane Client build",
  );
  assert.equal(
    entries263.includes("META-INF/jars/arcane-client-2.9.3+mc1.21.11.jar"),
    false,
  );
  assert.deepEqual(
    (await readdir("private")).filter(name => name.endsWith(".jar")).sort(),
    ["ARCLoader-26.3.jar", "ARCLoader.jar"],
  );
});

test("browser code uses the server route instead of a fixed hash", async () => {
  const script = await readFile("script.js", "utf8");
  assert.match(script, /fetch\("\/api\/verify-code"/);
  assert.match(script, /minecraftVersion: selectedVersion\(\)/);
  assert.match(script, /\/api\\\/download\\\?version=/);
  assert.match(script, /searchParams\.get\("version"\)/);
  assert.match(script, /dialog\.showModal\(\)/);
  assert.match(
    script,
    /reset\(\);[\s\S]*requestedOption\.checked = true;[\s\S]*updateVersionCopy\(\);[\s\S]*dialog\.showModal\(\)/,
  );
  assert.doesNotMatch(script, /loaderUrl|loader-file|\/api\/loader/);
  assert.doesNotMatch(script, /expectedHash|crypto\.subtle/);
});

test("the download dialog offers only the Fabric loader", async () => {
  const page = await readFile("index.html", "utf8");
  assert.match(page, /download="ARCLoader\.jar"/);
  assert.match(page, /value="1\.21\.11" checked/);
  assert.match(page, /value="26\.3"/);
  assert.match(page, />Download ARCLoader /);
  assert.doesNotMatch(page, /ARCANE LOADER 1\.2\.2|Arcane-Loader-1\.2\.2/);
  assert.match(page, /Arcane loads on the first launch/i);
  assert.match(page, /only the loader JAR belongs in/i);
  assert.match(page, /do not add a loose Arcane Client JAR/i);
  assert.match(page, /future signed update\s+may ask for one restart/i);
  assert.doesNotMatch(page, /Launch, then restart|Launch once, then restart/i);
  assert.doesNotMatch(page, /loader-file|automatic updater|standalone updater|Download Fabric mod/i);
});
