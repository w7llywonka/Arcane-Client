package dev.arcaneclient;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.arcaneclient.screen.ClickGuiTheme;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArcaneConfigUiTest {
    private static final Gson GSON = new Gson();

    @Test
    void newInstallUsesGlassDefaultsWithoutOptingIntoFilters() {
        ArcaneConfig config = new ArcaneConfig();
        assertEquals(82, config.uiOpacityPercent);
        assertEquals(10, config.uiCornerRadius);
        assertTrue(config.uiBlur);
        assertFalse(config.uiReducedMotion);
        assertFalse(config.uiShowEnabledOnly);
        assertFalse(config.uiShowFavoritesOnly);
        assertTrue(config.uiFavorites.isEmpty());
        assertFalse(config.uiThemesOpen);
        assertTrue(config.uiSingleSettings);
    }

    @Test
    void everyThemeSurvivesSerializationAndConfigurationClamping() throws ReflectiveOperationException {
        for (ClickGuiTheme theme : ClickGuiTheme.values()) {
            ArcaneConfig config = new ArcaneConfig();
            config.uiTheme = theme.ordinal();
            ArcaneConfig reloaded = roundTrip(config);
            assertEquals(theme.ordinal(), reloaded.uiTheme, theme.label());
            assertEquals(theme, ClickGuiTheme.fromConfig(reloaded.uiTheme));
        }
    }

    @Test
    void customPreferencesAndLayoutSurviveReloadWithoutBeingReset() throws ReflectiveOperationException {
        ArcaneConfig config = new ArcaneConfig();
        config.uiBlur = false;
        config.uiReducedMotion = true;
        config.uiShowEnabledOnly = true;
        config.uiShowFavoritesOnly = true;
        config.uiSingleSettings = false;
        config.uiThemesOpen = true;
        config.uiFavorites.addAll(Set.of("Chunk Finder", "Freecam"));
        config.uiOpacityPercent = 96;
        config.uiCornerRadius = 4;
        config.uiLayoutCustomized = true;
        ArcaneConfig.PanelLayout panel = new ArcaneConfig.PanelLayout(133, 72, false, 18, 4);
        config.uiPanelLayout.put("BASE FINDING", panel);
        config.uiAccentColor = 0xFF12AB34;
        config.uiPanelColor = 0xFF203040;
        config.uiTextColor = 0xFFEEDDCC;
        ArcaneConfig reloaded = roundTrip(config);
        assertFalse(reloaded.uiBlur);
        assertTrue(reloaded.uiReducedMotion);
        assertTrue(reloaded.uiShowEnabledOnly);
        assertTrue(reloaded.uiShowFavoritesOnly);
        assertFalse(reloaded.uiSingleSettings);
        assertTrue(reloaded.uiThemesOpen);
        assertEquals(config.uiFavorites, reloaded.uiFavorites);
        assertEquals(96, reloaded.uiOpacityPercent);
        assertEquals(4, reloaded.uiCornerRadius);
        assertTrue(reloaded.uiLayoutCustomized);
        assertEquals(panel, reloaded.uiPanelLayout.get("BASE FINDING"));
        assertEquals(0xFF12AB34, reloaded.uiAccentColor);
        assertEquals(0xFF203040, reloaded.uiPanelColor);
        assertEquals(0xFFEEDDCC, reloaded.uiTextColor);
    }

    @Test
    void absentOrNullCollectionsRecoverToMutableEmptyCollections() throws ReflectiveOperationException {
        ArcaneConfig config = GSON.fromJson("{\"uiFavorites\":null,\"uiPanelLayout\":null}", ArcaneConfig.class);
        clamp(config);
        assertNotNull(config.uiFavorites);
        assertNotNull(config.uiPanelLayout);
        assertTrue(config.uiFavorites.isEmpty());
        assertTrue(config.uiPanelLayout.isEmpty());
        assertDoesNotThrow(() -> config.uiFavorites.add("Freecam"));
        assertDoesNotThrow(() -> config.uiPanelLayout.put("RENDER", new ArcaneConfig.PanelLayout(1, 2, true, 0, 0)));
        ArcaneConfig legacy = GSON.fromJson("{}", ArcaneConfig.class);
        assertTrue(legacy.uiBlur);
        assertFalse(legacy.uiReducedMotion);
        assertNotNull(legacy.uiFavorites);
    }

    @Test
    void invalidFavoritesAndNullLayoutsAreDiscardedWithoutChangingValidNames() throws ReflectiveOperationException {
        ArcaneConfig config = new ArcaneConfig();
        config.uiFavorites = new LinkedHashSet<>(Arrays.asList(null, "", "  ", " Freecam ", "Freecam", "Chunk Finder", "bad\nname", "x".repeat(97)));
        config.uiPanelLayout = new LinkedHashMap<>();
        ArcaneConfig.PanelLayout panel = new ArcaneConfig.PanelLayout(10, 20, true, 3, 1);
        config.uiPanelLayout.put(null, panel);
        config.uiPanelLayout.put(" ", panel);
        config.uiPanelLayout.put("RENDER", null);
        config.uiPanelLayout.put("ESP", panel);
        clamp(config);
        assertEquals(Set.of("Freecam", "Chunk Finder"), config.uiFavorites);
        assertEquals(Map.of("ESP", panel), config.uiPanelLayout);
    }

    @Test
    void favoritesAreBoundedAndImmutableInputCollectionsBecomeEditable() throws ReflectiveOperationException {
        ArcaneConfig config = new ArcaneConfig();
        for (int i = 0; i < 200; i++) config.uiFavorites.add("Module " + i);
        clamp(config);
        assertEquals(128, config.uiFavorites.size());
        config.uiFavorites = Set.of("Freecam");
        config.uiPanelLayout = Map.of("RENDER", new ArcaneConfig.PanelLayout(1, 2, true, 0, 0));
        clamp(config);
        assertDoesNotThrow(() -> config.uiFavorites.add("Chunk Finder"));
        assertDoesNotThrow(() -> config.uiPanelLayout.clear());
    }

    @Test
    void themeIndexIsClampedAgainstActualPaletteCount() throws ReflectiveOperationException {
        ArcaneConfig config = new ArcaneConfig();
        config.uiTheme = -99;
        clamp(config);
        assertEquals(0, config.uiTheme);
        config.uiTheme = 999;
        clamp(config);
        assertEquals(ClickGuiTheme.count() - 1, config.uiTheme);
    }

    private static ArcaneConfig roundTrip(ArcaneConfig config) throws ReflectiveOperationException {
        ArcaneConfig result = GSON.fromJson(GSON.toJson(config), ArcaneConfig.class);
        clamp(result);
        return result;
    }

    private static void clamp(ArcaneConfig config) throws ReflectiveOperationException {
        Method method = ArcaneConfig.class.getDeclaredMethod("clamp");
        method.setAccessible(true);
        method.invoke(config);
    }
}
