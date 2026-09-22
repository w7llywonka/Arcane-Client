package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ManifestEndpointTest {
    @Test
    void updaterUsesDedicatedOuterLoaderManifest() {
        assertEquals("https://www.arcaneclient.shop/updates/channels/mc-26.3/loader-release.json",
            ArcaneLoader.PRIMARY_MANIFEST_URL);
        assertEquals("https://arcane-client-puce.vercel.app/updates/channels/mc-26.3/loader-release.json",
            ArcaneLoader.FALLBACK_MANIFEST_URL);
    }
}
