"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");

test("www serves assets directly while the apex DNS is pending", async () => {
  const config = JSON.parse(await readFile("vercel.json", "utf8"));
  const unsafeRedirect = config.redirects?.find(rule =>
    rule.has?.some(condition =>
      condition.type === "host" && condition.value === "www.arcaneclient.shop",
    ),
  );
  assert.equal(unsafeRedirect, undefined);
});

test("pages declare the apex domain as canonical", async () => {
  const [home, help] = await Promise.all([
    readFile("index.html", "utf8"),
    readFile("help.html", "utf8"),
  ]);
  assert.match(home, /rel="canonical" href="https:\/\/arcaneclient\.shop\/"/);
  assert.match(help, /rel="canonical" href="https:\/\/arcaneclient\.shop\/help\.html"/);
});

test("public assets bypass the previously cached permanent redirect", async () => {
  const [home, help, css] = await Promise.all([
    readFile("index.html", "utf8"),
    readFile("help.html", "utf8"),
    readFile("styles.css", "utf8"),
  ]);
  for (const content of [home, help, css]) {
    assert.match(content, /\?v=2\.9\.1-2/);
  }
  assert.match(home, /styles\.css\?v=2\.9\.1-2/);
  assert.match(home, /script\.js\?v=2\.9\.1-3/);
});
