package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModuleCatalogContractTest {
    @Test
    void productContractIsExactlyFortyModules() {
        assertEquals(40, ModuleCatalog.EXPECTED_MODULE_COUNT);
        assertEquals(40, ModuleCatalog.requiredModuleNames().size());
        assertEquals(40, ModuleCatalog.requiredModuleNames().stream().distinct().count());
        assertEquals(true, ModuleCatalog.requiredModuleNames().contains("Freelook"));
    }
}
