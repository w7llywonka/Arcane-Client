package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VersionComparatorTest {
    @Test
    void ignoresMinecraftBuildMetadataForLoaderOrdering() {
        assertEquals(0, VersionComparator.compare("1.2.2+mc1.21.11", "1.2.2+other"));
        assertTrue(VersionComparator.compare("1.2.10+mc1.21.11", "1.2.9+mc1.21.11") > 0);
    }

    @Test
    void prereleaseSortsBeforeStable() {
        assertTrue(VersionComparator.compare("1.2.3-beta.2", "1.2.3") < 0);
        assertTrue(VersionComparator.compare("1.2.3-beta.10", "1.2.3-beta.2") > 0);
    }
}
