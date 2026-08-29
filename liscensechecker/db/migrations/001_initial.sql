CREATE TABLE IF NOT EXISTS licenses (
    id UUID PRIMARY KEY,
    key_hash CHAR(64) NOT NULL UNIQUE,
    key_prefix VARCHAR(24) NOT NULL,
    tier VARCHAR(16) NOT NULL CHECK (tier IN ('base', 'premium')),
    status VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
    expires_at TIMESTAMPTZ,
    device_fingerprint CHAR(64),
    device_public_key TEXT,
    device_name VARCHAR(100),
    activated_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY,
    license_id UUID NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    device_fingerprint CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS sessions_license_id_idx ON sessions(license_id);
CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS request_nonces (
    device_fingerprint CHAR(64) NOT NULL,
    nonce VARCHAR(96) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (device_fingerprint, nonce)
);

CREATE INDEX IF NOT EXISTS request_nonces_expires_at_idx ON request_nonces(expires_at);

CREATE TABLE IF NOT EXISTS releases (
    id UUID PRIMARY KEY,
    tier VARCHAR(16) NOT NULL CHECK (tier IN ('base', 'premium')),
    version VARCHAR(64) NOT NULL,
    minecraft_version VARCHAR(32) NOT NULL,
    fabric_loader_version VARCHAR(32) NOT NULL,
    download_url TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    signature TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tier, version, minecraft_version)
);

CREATE TABLE IF NOT EXISTS schema_migrations (
    name TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
