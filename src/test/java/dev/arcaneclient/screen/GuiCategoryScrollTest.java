package dev.arcaneclient.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GuiCategoryScrollTest {
    @Test
    void scrollOffsetClampsToViewportAndShrinkingContent() {
        GuiCategory category = new GuiCategory("TEST", List.of());
        category.setLayoutHeights(200, 80);

        category.scrollBy(50);
        assertEquals(50, category.scrollOffset());
        category.scrollBy(500);
        assertEquals(120, category.scrollOffset());
        category.scrollBy(-500);
        assertEquals(0, category.scrollOffset());

        category.scrollBy(90);
        category.setLayoutHeights(100, 80);
        assertEquals(20, category.scrollOffset());
        category.setLayoutHeights(60, 80);
        assertEquals(0, category.scrollOffset());
    }
}
