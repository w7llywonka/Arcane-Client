import pg from "pg";

const { Pool } = pg;

export function createPool(connectionString: string, production: boolean): pg.Pool {
  return new Pool({
    connectionString,
    max: 10,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 10_000,
    ssl: production ? { rejectUnauthorized: false } : undefined
  });
}

export type DatabasePool = pg.Pool;
export type DatabaseClient = pg.PoolClient;
