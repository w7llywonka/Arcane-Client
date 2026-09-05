package dev.arcane.loader;

import java.util.Map;

record ReleaseManifest(
    int schemaVersion,
    String artifactType,
    String version,
    String minecraftVersion,
    String downloadUrl,
    String sha256,
    String signature,
    long size
) {
    static final int SUPPORTED_SCHEMA = 2;
    static final String LOADER_ARTIFACT = "arcane-loader";

    static ReleaseManifest parse(String json) {
        Map<String, Object> values = Json.parseObject(json);
        int schema = Math.toIntExact(number(values, "schemaVersion"));
        if (schema != SUPPORTED_SCHEMA) {
            throw new IllegalArgumentException("Unsupported release manifest schema: " + schema);
        }
        String artifactType = text(values, "artifactType");
        if (!LOADER_ARTIFACT.equals(artifactType)) {
            throw new IllegalArgumentException("Release is not an ARCLoader artifact");
        }
        long size = number(values, "size");
        if (size <= 0) throw new IllegalArgumentException("Release size must be positive");
        String sha256 = text(values, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Release SHA-256 must contain 64 hexadecimal characters");
        }
        String signature = text(values, "signature");
        try {
            if (java.util.Base64.getDecoder().decode(signature).length != 64) {
                throw new IllegalArgumentException("Release signature must be an Ed25519 signature");
            }
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalArgumentException("Release signature must be valid Base64 Ed25519 data", invalidBase64);
        }
        return new ReleaseManifest(
            schema,
            artifactType,
            text(values, "version"),
            text(values, "minecraftVersion"),
            text(values, "downloadUrl"),
            sha256,
            signature,
            size
        );
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing release field: " + key);
        }
        return text;
    }

    private static long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Missing release field: " + key);
        }
        return number.longValue();
    }
}
