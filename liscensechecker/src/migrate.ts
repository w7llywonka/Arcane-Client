import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { loadConfig } from "./config.js";
import { createPool } from "./db.js";

const config = loadConfig();
const pool = createPool(config.DATABASE_URL, config.NODE_ENV === "production");
const directory = path.resolve(process.cwd(), "db", "migrations");

try {
  const files = (await readdir(directory)).filter((name) => name.endsWith(".sql")).sort();
  for (const name of files) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(`
        CREATE TABLE IF NOT EXISTS schema_migrations (
          name TEXT PRIMARY KEY,
          applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
      `);
      const existing = await client.query("SELECT 1 FROM schema_migrations WHERE name = $1", [name]);
      if (existing.rowCount === 0) {
        await client.query(await readFile(path.join(directory, name), "utf8"));
        await client.query("INSERT INTO schema_migrations(name) VALUES ($1)", [name]);
        console.log(`Applied migration ${name}`);
      }
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }
} finally {
  await pool.end();
}
