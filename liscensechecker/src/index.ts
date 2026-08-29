import { loadConfig } from "./config.js";
import { createPool } from "./db.js";
import { buildServer } from "./server.js";

const config = loadConfig();
const pool = createPool(config.DATABASE_URL, config.NODE_ENV === "production");
const app = await buildServer(config, pool);

const shutdown = async (signal: string): Promise<void> => {
  app.log.info({ signal }, "Shutting down");
  await app.close();
  await pool.end();
  process.exit(0);
};

process.on("SIGTERM", () => void shutdown("SIGTERM"));
process.on("SIGINT", () => void shutdown("SIGINT"));

await app.listen({ host: "0.0.0.0", port: config.PORT });
