# Arcane License Checker

Railway-ready license and release API for Arcane Client.

## Import this GitHub folder into Railway

1. Create an empty Railway project and add PostgreSQL.
2. Add a service from `155xp/arcane-client` and select the `main` branch.
3. In that service's Settings, set **Root Directory** to `/liscensechecker`.
4. In Variables, set `DATABASE_URL=${{Postgres.DATABASE_URL}}`.
5. Add secret `LICENSE_PEPPER` and `ADMIN_TOKEN` values containing at least 32 random characters each.
6. Set `ENTITLEMENT_PRIVATE_KEY_BASE64` to the local contents of `.secrets/entitlement-private.base64`; never commit that value.
7. Set `ENTITLEMENT_TTL_MINUTES=10`, `NODE_ENV=production`, `SESSION_TTL_HOURS=24`, and `CLOCK_SKEW_SECONDS=120`.
8. Deploy, then set the healthcheck path to `/health` and generate a public domain.

The Docker container applies all SQL migrations before starting the API. Do not add a second migration command in Railway.

The service listens on Railway's injected `PORT` automatically. A successful deployment returns `{"status":"ok"}` from `/health`.

## Local checks

```text
npm install
npm run check
npm run db:migrate
npm run dev
```

Never commit `.env`, `LICENSE_PEPPER`, `ADMIN_TOKEN`, an artifact-signing private key, or raw license keys.

## Create a license

Send an authenticated request to `POST /v1/admin/licenses`:

```json
{
  "tier": "base",
  "expiresAt": null
}
```

The plaintext license key is returned once. The database stores only a keyed hash.

## Publish a release

Sign the built JAR with the offline artifact-signing private key, then send its version, URL, SHA-256, and Base64 Ed25519 signature to `POST /v1/admin/releases`. The loader contains only the matching public key.
