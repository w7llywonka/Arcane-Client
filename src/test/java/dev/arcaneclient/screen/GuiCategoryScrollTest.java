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

    @Test
    void denseCategoryStillSupportsLargeModuleLists() {
        GuiCategory category = new GuiCategory("HUD", List.of());
        int denseHeader = 16;
        int denseRows = 32 * 12;
        category.setLayoutHeights(denseHeader + denseRows, 280);

        assertEquals(120, category.maxScroll());
        category.scrollBy(12 * 7);
        assertEquals(84, category.scrollOffset());
    }
}
