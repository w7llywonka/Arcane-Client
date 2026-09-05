package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.arcaneclient.ArcaneConfig;
import org.junit.jupiter.api.Test;

final class ModuleCatalogContractTest {
    @Test
    void catalogCoreContainsOnlyDistinctImplementedAnchors() {
        assertEquals(
            ModuleCatalog.requiredCoreModuleNames().size(),
            ModuleCatalog.requiredCoreModuleNames().stream().distinct().count()
        );
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Chunk Finder"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Evidence Points"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Amethyst ESP"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Access Trail ESP"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("ESP Debug"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Auto Armor"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Search"));
        assertTrue(ModuleCatalog.requiredCoreModuleNames().contains("Durability Guard"));
        assertFalse(ModuleCatalog.requiredCoreModuleNames().contains("Health Alert"));
        assertFalse(ModuleCatalog.requiredCoreModuleNames().contains("Inventory Full Alert"));
        assertFalse(ModuleCatalog.requiredCoreModuleNames().contains("Weather HUD"));
    }

    @Test
    void freshConfigKeepsSecondaryHudPanelsOptIn() {
        ArcaneConfig config = new ArcaneConfig();
        assertTrue(config.hud, "the base radar remains the primary scanner readout");
        assertFalse(config.infoHud, "secondary telemetry should be opt-in on a clean install");
        assertFalse(config.overlay, "world tiles should not be forced onto a clean HUD");
        assertFalse(config.esp, "storage ESP should be opt-in");
        assertFalse(config.storageChatAlerts, "chat discovery notifications should be opt-in");
        assertFalse(config.blockEntityDebug, "underground block-entity debug should be opt-in");
        assertTrue(config.clusterInference, "neighboring-chunk corroboration is part of the legacy base finder");
        assertFalse(config.chunkAnalysis, "the detail panel should not crowd a fresh HUD");
        assertFalse(config.evidencePoints, "exact world observations stay opt-in independently of chunk tiles");
        assertFalse(config.amethystEsp, "amethyst observations should be opt-in");
        assertFalse(config.accessTrailEsp, "access-trail observations should be opt-in");
        assertFalse(config.statusHud, "status lines must be explicitly enabled");
        assertFalse(config.inventoryHud, "inventory totals must be explicitly enabled");
        assertTrue(config.uiOpacityPercent >= 94, "the overlay should be substantially opaque by default");
        assertTrue(config.uiCornerRadius <= 3, "the overlay should retain its compact square silhouette");
    }
}
