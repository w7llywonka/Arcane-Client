package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class GuiModuleGroupTest {
    @Test
    void aggregateGroupParticipatesInEnabledFilteringAndChildSearch() {
        AtomicBoolean child = new AtomicBoolean();
        GuiModule module = GuiModule.group("Entity ESP", "Grouped entity rendering", child::get,
                () -> child.get() ? "1 ON" : "")
            .with(new GuiSetting.Toggle("Player ESP", "Player outlines and labels", child::get, child::set))
            .build();

        assertTrue(module.group());
        assertFalse(module.toggleable());
        assertFalse(module.enabled());
        assertEquals("", module.valueLabel());
        assertTrue(module.matches("player esp"));
        assertTrue(module.matches("player outlines"));
        ((GuiSetting.Toggle) module.settings().getFirst()).toggle();
        assertTrue(module.enabled());
        assertEquals("1 ON", module.valueLabel());
    }
}
