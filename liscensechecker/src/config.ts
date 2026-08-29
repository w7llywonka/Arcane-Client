import { z } from "zod";

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  DATABASE_URL: z.string().min(1),
  LICENSE_PEPPER: z.string().min(32),
  ADMIN_TOKEN: z.string().min(32),
  ENTITLEMENT_PRIVATE_KEY_BASE64: z.string().min(40),
  ENTITLEMENT_TTL_MINUTES: z.coerce.number().int().min(2).max(60).default(10),
  SESSION_TTL_HOURS: z.coerce.number().int().min(1).max(24 * 30).default(24),
  CLOCK_SKEW_SECONDS: z.coerce.number().int().min(30).max(600).default(120)
});

export type AppConfig = z.infer<typeof schema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const parsed = schema.safeParse(env);
  if (!parsed.success) {
    throw new Error(`Invalid environment configuration: ${parsed.error.message}`);
  }
  return parsed.data;
}
