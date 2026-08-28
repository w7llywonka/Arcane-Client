package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModuleCatalogContractTest {
    @Test
    void productContractIsExactlyFortyTwoModules() {
        assertEquals(42, ModuleCatalog.EXPECTED_MODULE_COUNT);
        assertEquals(42, ModuleCatalog.requiredModuleNames().size());
        assertEquals(42, ModuleCatalog.requiredModuleNames().stream().distinct().count());
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Freelook"));
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Info HUD"));
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Auto Tool"));
    }
}
