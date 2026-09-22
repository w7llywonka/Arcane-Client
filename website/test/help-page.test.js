"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");

test("download help explains the complete one-time code flow", async () => {
  const page = await readFile("help.html", "utf8");
  assert.match(page, /discord\.gg\/DvfZW4Fk5/);
  assert.match(page, /<code>\/code<\/code>/);
  assert.match(page, /only you can see/i);
  assert.match(page, /expires after 10 minutes/i);
  assert.match(page, /works once/i);
  assert.match(page, /Download ARCLoader/);
  assert.match(page, /ARCLoader\.jar/);
  assert.match(page, /only Arcane JAR that belongs/i);
  assert.match(page, /do not add a loose Arcane Client JAR/i);
  assert.match(page, /already\s+included/i);
  assert.match(page, /works on the first\s+Fabric launch/i);
  assert.match(page, /future signed\s+update may ask for one restart/i);
  assert.match(page, /index\.html#download/);
});

test("the main download flow links to help", async () => {
  const page = await readFile("index.html", "utf8");
  assert.match(page, /href="help\.html">How to get a code<\/a>/);
});
