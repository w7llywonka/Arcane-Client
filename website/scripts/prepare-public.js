"use strict";

const { cp, copyFile, mkdir, rm } = require("node:fs/promises");
const { join } = require("node:path");

async function build() {
  const output = join(process.cwd(), "public");
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await Promise.all([
    copyFile("index.html", join(output, "index.html")),
    copyFile("help.html", join(output, "help.html")),
    copyFile("styles.css", join(output, "styles.css")),
    copyFile("script.js", join(output, "script.js")),
    cp("assets", join(output, "assets"), { recursive: true }),
    cp("updates", join(output, "updates"), { recursive: true }),
  ]);
}

build().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
