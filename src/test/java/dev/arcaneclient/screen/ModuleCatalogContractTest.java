package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModuleCatalogContractTest {
    @Test
    void productContractIsExactlyFortyFourModules() {
        assertEquals(44, ModuleCatalog.EXPECTED_MODULE_COUNT);
        assertEquals(44, ModuleCatalog.requiredModuleNames().size());
        assertEquals(44, ModuleCatalog.requiredModuleNames().stream().distinct().count());
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Freelook"));
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Info HUD"));
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Auto Tool"));
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Elytra Assist"));
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Relog"));
    }
}
