package dev.arcaneclient.screen;

import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_MIDDLE;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_RIGHT;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.vector.VectorUi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The Arcane Click GUI: one draggable window per category, a module row per feature, and nested
 * settings that open on right click. Everything on screen is a view over {@link ArcaneConfig}; the
 * scan, render, and input systems are untouched by this class.
 */
@Environment(EnvType.CLIENT)
public final class ArcaneSettingsScreen extends Screen {
    private static final boolean DEV_BUILD = net.fabricmc.loader.api.FabricLoader.getInstance()
        .getModContainer(ArcaneClient.MOD_ID).orElseThrow()
        .getMetadata().getVersion().getFriendlyString().contains("-dev.");
    private static final int NEST_INSET = 8;
    private static final int NEST_PAD = 3;
    private static final int BODY_PAD = 1;
    private static final int TOP_BAR_HEIGHT = 34;
    private static final int BOTTOM_BAR_HEIGHT = 20;
    private static final int MACRO_COUNT = 4;
    private static final int MACRO_FIELD_WIDTH = 60;
    private static final int BIND_KEY_WIDTH = 38;
    private static final int MACRO_KEY_WIDTH = 28;
    private static final int SEARCH_MIN_SCREEN_WIDTH = 440;
    private static final int VISIBLE_ROWS = 10;

    private final Screen parent;
    private final ArcaneConfig config;
    private final List<GuiCategory> categories;
    private final List<GuiCategory> windowOrder = new ArrayList<>();
    private final List<EditBox> macroInputs = new ArrayList<>();
    private final Map<WrapKey, List<String>> wrapCache = new HashMap<>();
    private final Map<GuiModule, Float> hoverAnimations = new IdentityHashMap<>();
    private final Map<GuiModule, Float> enabledAnimations = new IdentityHashMap<>();
    private final Map<GuiCategory, WindowState> stateBeforeSearch = new HashMap<>();
    private final Map<GuiCategory, Integer> windowBottoms = new HashMap<>();
    private final boolean scannerWasEnabled;
    private final ThemePanel themePanel;

    private Font uiFont;
    private EditBox searchInput;
    private String searchText = "";
    private String query = "";
    private boolean filteringActive;
    private boolean searchShortcutFocused;
    private String fpsLabel = "";
    private long nextFpsUpdateNanos;
    private long lastAnimationNanos;
    private boolean lastVectorFrame;
    private float animationStep = 1.0f;
    private int openWindowBottom;
    private boolean windowsLaidOut;
    private UiGeometry.ClickGuiMetrics metrics = UiGeometry.clickGuiMetrics(1, 1, 100, 100);

    private @Nullable GuiCategory draggingCategory;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragMoved;
    private GuiSetting.@Nullable Slider draggingSlider;
    private int sliderTrackX;
    private int sliderTrackWidth;
    private @Nullable KeyMapping listeningFor;
    private @Nullable GuiModule hoveredModule;
    private @Nullable GuiModule tooltipModule;
    private long tooltipSince;

    public ArcaneSettingsScreen(Screen parent) {
        super(Component.literal("Arcane Client"));
        this.parent = parent;
        this.config = ArcaneClient.config();
        if (this.config.devUiRevision < 4) {
            this.config.uiOpacityPercent = 82;
            this.config.uiBackgroundDimPercent = 0;
            this.config.uiCornerRadius = 10;
            this.config.devUiRevision = 4;
        }
        if (this.config.devUiRevision < 5) {
            this.config.uiOpacityPercent = 90;
            this.config.uiCornerRadius = 6;
            this.config.uiBackgroundDimPercent = 18;
            this.config.devUiRevision = 5;
        }
        if (this.config.devUiRevision < 6) {
            // Keep the user's colors and panel positions; only retire the always-open editor.
            this.config.uiThemesOpen = false;
            this.config.uiSingleSettings = true;
            this.config.devUiRevision = 6;
        }
        this.themePanel = new ThemePanel(this.config);
        this.scannerWasEnabled = this.config.enabled;
        this.categories = new ArrayList<>(ModuleCatalog.build(this.config, Minecraft.getInstance()));
        this.categories.sort(java.util.Comparator.comparingInt(category -> category.name().equals("BASE FINDING") ? 0 : 1));
        this.windowOrder.addAll(this.categories);
    }

    @Override
    protected void init() {
        sizeCanvas();
        if (this.config.uiThemesX < 0 || this.config.uiThemesY < 0) {
            this.themePanel.setPosition(Math.max(4, this.width - 113), Math.max(42, this.height - 258));
        }
        // A resize can change the GUI scale, which changes which font variant is sharpest.
        ArcaneFont.invalidate();
        this.uiFont = null;
        this.wrapCache.clear();
        refreshMetrics();
        this.lastAnimationNanos = System.nanoTime();
        if (!this.windowsLaidOut) {
            layoutWindows(true);
            restorePanelLayout();
            this.windowsLaidOut = true;
        } else {
            layoutWindows(false);
            if (this.filteringActive) {
                Map<GuiCategory, WindowState> previousStates = new HashMap<>(this.stateBeforeSearch);
                this.stateBeforeSearch.clear();
                for (GuiCategory category : this.categories) {
                    WindowState previous = previousStates.get(category);
                    this.stateBeforeSearch.put(category, new WindowState(
                        previous == null ? category.x() : previous.x(),
                        previous == null ? category.y() : previous.y(),
                        previous == null ? category.open() : previous.open(),
                        previous == null ? category.scrollOffset() : previous.scrollOffset()
                    ));
                }
            }
        }

        int searchWidth = searchWidth();
        this.searchInput = new EditBox(font(), searchX(searchWidth), 14, searchWidth, 11, Component.literal("Search modules"));
        this.searchInput.setBordered(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setHint(ArcaneFont.text("Find a module"));
        styleField(this.searchInput);
        this.searchInput.setValue(this.searchText);
        this.searchInput.setResponder(this::updateFilter);
        updateFilter(this.searchText);
        this.addRenderableWidget(this.searchInput);

        this.macroInputs.clear();
        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            int index = slot;
            EditBox input = new EditBox(font(), 0, 0, MACRO_FIELD_WIDTH, 12, Component.literal("Chat macro " + (slot + 1)));
            input.setBordered(false);
            input.setTextShadow(false);
            input.setMaxLength(256);
            input.setValue(this.config.chatMacro(slot));
            input.setHint(ArcaneFont.text("message"));
            styleField(input);
            input.setResponder(value -> this.config.setChatMacro(index, value));
            input.setVisible(false);
            this.macroInputs.add(this.addRenderableWidget(input));
        }
    }

    // -----------------------------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------------------------

    private void layoutWindows(boolean openAll) {
        if (!openAll && this.config.uiLayoutCustomized) {
            this.openWindowBottom = this.height - BOTTOM_BAR_HEIGHT - 4;
            this.windowBottoms.clear();
            for (GuiCategory category : this.categories) {
                layout(category);
                clamp(category);
            }
            return;
        }
        int total = this.categories.size();
        int columns = UiGeometry.clickGuiColumns(this.width, windowWidth(), gap(), total);
        int top = TOP_BAR_HEIGHT + gap();
        int screenBottom = this.height - BOTTOM_BAR_HEIGHT - 4;
        int rows = Math.max(1, (total + columns - 1) / columns);
        int usableHeight = Math.max(
            rows * headerHeight(),
            screenBottom - top - (rows - 1) * gap()
        );
        // Keep the lower half of a desktop viewport free; longer lists scroll inside their panel.
        int rowBandHeight = Math.min(headerHeight() + VISIBLE_ROWS * moduleHeight() + bodyPad(),
            Math.max(headerHeight(), usableHeight / rows));
        this.openWindowBottom = screenBottom;
        this.windowBottoms.clear();

        int first = 0;
        int rowY = top;
        for (int row = 0; row < rows; row++) {
            int count = (total - first + (rows - row) - 1) / (rows - row);
            int rowWidth = count * windowWidth() + (count - 1) * gap();
            int start = Math.max(2, (this.width - rowWidth) / 2);
            int tallestBody = 0;
            for (int column = 0; column < count; column++) {
                int body = 0;
                for (GuiModule module : this.categories.get(first + column).visible()) body += moduleRowHeight(module);
                tallestBody = Math.max(tallestBody, body);
            }
            int band = Math.min(rowBandHeight, headerHeight() + tallestBody + bodyPad());
            int rowBottom = Math.min(screenBottom, rowY + band);
            for (int column = 0; column < count; column++) {
                GuiCategory category = this.categories.get(first + column);
                category.moveTo(start + column * (windowWidth() + gap()), rowY);
                if (openAll) category.setOpen(true);
                this.windowBottoms.put(category, rowBottom);
            }
            first += count;
            rowY += band + gap();
        }
    }

    private void restorePanelLayout() {
        if (this.config.uiPanelLayout == null || this.config.uiPanelLayout.isEmpty()) return;
        for (GuiCategory category : this.categories) {
            ArcaneConfig.PanelLayout saved = this.config.uiPanelLayout.get(category.name());
            if (saved == null) continue;
            if (this.config.uiLayoutCustomized) {
                category.moveTo(saved.x(), saved.y());
                this.windowBottoms.remove(category);
            }
            category.setOpen(saved.open());
            layout(category);
            category.scrollBy(saved.scroll());
            clamp(category);
        }
        this.windowOrder.sort(java.util.Comparator.comparingInt(category -> {
            ArcaneConfig.PanelLayout saved = this.config.uiPanelLayout.get(category.name());
            return saved == null ? Integer.MAX_VALUE : saved.order();
        }));
    }

    public void resetPanelLayout() {
        this.config.uiShowEnabledOnly = false;
        this.config.uiShowFavoritesOnly = false;
        this.filteringActive = false;
        this.stateBeforeSearch.clear();
        this.searchText = this.query = "";
        this.config.uiPanelLayout = new java.util.LinkedHashMap<>();
        this.config.uiLayoutCustomized = false;
        this.windowOrder.clear();
        this.windowOrder.addAll(this.categories);
        if (this.searchInput != null) this.searchInput.setValue("");
        updateFilter("");
        layoutWindows(true);
    }

    private void savePanelLayout() {
        var saved = new java.util.LinkedHashMap<String, ArcaneConfig.PanelLayout>();
        for (GuiCategory category : this.categories) {
            // Filtering temporarily opens/repositions panels; don't overwrite the user's layout.
            WindowState original = this.stateBeforeSearch.get(category);
            saved.put(category.name(), new ArcaneConfig.PanelLayout(
                original == null ? category.x() : original.x(),
                original == null ? category.y() : original.y(),
                original == null ? category.open() : original.open(),
                original == null ? category.scrollOffset() : original.scrollOffset(),
                this.windowOrder.indexOf(category)));
        }
        this.config.uiPanelLayout = saved;
    }

    /** Positions every row of one window and clips tall bodies into a scrollable viewport. */
    private List<Row> layout(GuiCategory category) {
        List<Row> rows = new ArrayList<>();
        int x = category.x();
        int y = category.y();
        rows.add(new Row(RowKind.HEADER, category, null, null, x, y, windowWidth(), headerHeight()));

        int contentHeight = headerHeight();
        if (category.open()) {
            contentHeight += 3;
            for (GuiModule module : category.visible()) {
                contentHeight += moduleRowHeight(module);
                if (!module.expanded() || !module.hasSettings()) continue;
                contentHeight += descriptionHeight(module);
                for (GuiSetting setting : module.settings()) contentHeight += settingHeight(setting);
                contentHeight += NEST_PAD;
            }
            contentHeight += bodyPad();
        }
        int viewportHeight = category.open()
            ? Math.min(contentHeight, fittedWindowHeight(category, maxWindowHeight(category)))
            : headerHeight();
        category.setLayoutHeights(contentHeight, viewportHeight);

        if (category.open()) {
            int rowY = y + headerHeight() + 3 - category.scrollOffset();
            for (GuiModule module : category.visible()) {
                rows.add(new Row(RowKind.MODULE, category, module, null, x, rowY, windowWidth(), moduleRowHeight(module)));
                rowY += moduleRowHeight(module);
                if (!module.expanded() || !module.hasSettings()) continue;
                int descriptionHeight = descriptionHeight(module);
                rows.add(new Row(RowKind.DESCRIPTION, category, module, null, x, rowY, windowWidth(), descriptionHeight));
                rowY += descriptionHeight;
                for (GuiSetting setting : module.settings()) {
                    int settingHeight = settingHeight(setting);
                    rows.add(new Row(RowKind.SETTING, category, module, setting, x, rowY, windowWidth(), settingHeight));
                    rowY += settingHeight;
                }
                rows.add(new Row(RowKind.NEST_FOOT, category, module, null, x, rowY, windowWidth(), NEST_PAD));
                rowY += NEST_PAD;
            }
        }
        return rows;
    }

    private int fittedWindowHeight(GuiCategory category, int maximumHeight) {
        if (maximumHeight <= headerHeight()) return headerHeight();
        int available = maximumHeight - headerHeight() - bodyPad() - 3;
        int used = 0;
        for (GuiModule module : category.visible()) {
            if (used + moduleRowHeight(module) > available) break;
            used += moduleRowHeight(module);
            if (!module.expanded() || !module.hasSettings()) continue;

            int descriptionHeight = descriptionHeight(module);
            if (used + descriptionHeight > available) break;
            used += descriptionHeight;
            boolean settingsFit = true;
            for (GuiSetting setting : module.settings()) {
                int settingHeight = settingHeight(setting);
                if (used + settingHeight > available) {
                    settingsFit = false;
                    break;
                }
                used += settingHeight;
            }
            if (!settingsFit || used + NEST_PAD > available) break;
            used += NEST_PAD;
        }
        return headerHeight() + used + bodyPad() + 3;
    }

    private int maxWindowHeight(GuiCategory category) {
        return Math.max(headerHeight(), availableWindowBottom(category) - category.y());
    }

    private int moduleRowHeight(GuiModule module) {
        return moduleHeight();
    }

    private int descriptionHeight(GuiModule module) {
        return 0; // Explanations remain in hover tooltips; inline settings stay compact.
    }

    private List<String> wrap(String text, int maxWidth) {
        return this.wrapCache.computeIfAbsent(new WrapKey(text, maxWidth), key -> wrapLines(key.text(), key.maxWidth()));
    }

    private List<String> wrapLines(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && ArcaneFont.width(font(), candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    // -----------------------------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        mouseX = (int) Math.round(mouseX * inputScaleX());
        mouseY = (int) Math.round(mouseY * inputScaleY());
        if (refreshMetrics() && this.windowsLaidOut && this.draggingCategory == null) {
            layoutWindows(false);
        }
        updateAnimationStep();
        positionSearchInput();
        ClickGuiColors theme = theme();
        if (this.config.uiBlur && this.minecraft != null && this.minecraft.level != null) extractBlurredBackground(graphics);
        boolean vector = this.config.uiVectorRendering && VectorUi.begin(graphics, this.width, this.height);
        if (vector != this.lastVectorFrame) { this.wrapCache.clear(); this.lastVectorFrame = vector; }
        if (!vector) {
            graphics.pose().pushMatrix();
            graphics.pose().scale((float)(1.0 / inputScaleX()), (float)(1.0 / inputScaleY()));
        }
        try {
        if ((theme.backdrop() >>> 24) > 0) UiDraw.fill(graphics, 0, 0, this.width, this.height, theme.backdrop());
        this.hoveredModule = null;
        boolean[] macroDrawn = new boolean[MACRO_COUNT];
        GuiCategory[] macroOwners = new GuiCategory[MACRO_COUNT];

        for (GuiCategory category : this.windowOrder) {
            if (!categoryVisible(category)) continue;
            boolean[] categoryMacros = new boolean[MACRO_COUNT];
            layout(category);
            clamp(category);
            List<Row> rows = layout(category);
            drawWindowBody(graphics, category, theme);
            if (windowContains(category, mouseX, mouseY)) this.hoveredModule = null;
            for (Row row : rows) {
                if (row.kind() == RowKind.HEADER) {
                    drawRow(graphics, row, mouseX, mouseY, theme, categoryMacros);
                }
            }
            if (category.open() && category.lastHeight() > headerHeight()) {
                UiDraw.enableScissor(graphics,
                    category.x(),
                    category.y() + headerHeight(),
                    category.x() + windowWidth(),
                    category.y() + category.lastHeight() - bodyPad()
                );
                for (Row row : rows) {
                    if (row.kind() != RowKind.HEADER && rowVisible(row)) {
                        drawRow(graphics, row, mouseX, mouseY, theme, categoryMacros);
                    }
                }
                for (int slot = 0; slot < MACRO_COUNT; slot++) {
                    if (categoryMacros[slot]) {
                        UiDraw.field(graphics, font(), this.macroInputs.get(slot), "message", mouseX, mouseY, deltaTicks, theme);
                        macroDrawn[slot] = true;
                        macroOwners[slot] = category;
                    }
                }
                UiDraw.disableScissor(graphics);
                drawScrollBar(graphics, category, theme);
            }
        }

        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            if (macroDrawn[slot] && macroOccluded(slot, macroOwners[slot])) macroDrawn[slot] = false;
            if (macroDrawn[slot]) {
                // Prevent Screen.render from drawing it again outside the window scissor.
                this.macroInputs.get(slot).setVisible(false);
            } else {
                hideMacroInput(slot);
            }
        }

        drawTopBar(graphics, theme);
        drawBottomBar(graphics, theme);
        if (this.filteringActive && visibleModuleCount() == 0) {
            String message = "No matching modules  ·  Esc to clear";
            int messageWidth = ArcaneFont.width(font(), message) + 28;
            int messageX = Math.max(4, (this.width - messageWidth) / 2);
            RoundedGui.fill(graphics, messageX, this.height / 2 - 15, messageWidth, 30, 12, theme.bar());
            UiDraw.text(graphics, font(), ArcaneFont.text(message), messageX + 14, this.height / 2 - 4, theme.muted(), false);
        }
        if (vector) UiDraw.field(graphics, font(), this.searchInput, "Find a module", mouseX, mouseY, deltaTicks, theme);
        else super.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            if (macroDrawn[slot]) this.macroInputs.get(slot).setVisible(true);
        }
        drawModuleTooltip(graphics, mouseX, mouseY, theme);
        this.themePanel.render(graphics, mouseX, mouseY, this.width, this.height);
        } finally {
            if (vector) VectorUi.finish();
            else graphics.pose().popMatrix();
        }
    }

    private boolean rowVisible(Row row) {
        int top = row.category().y() + headerHeight();
        int bottom = row.category().y() + row.category().lastHeight() - bodyPad();
        return row.y() < bottom && row.y() + row.height() > top;
    }

    private boolean rowFullyVisible(Row row) {
        int top = row.category().y() + headerHeight();
        int bottom = row.category().y() + row.category().lastHeight() - bodyPad();
        return row.y() >= top && row.y() + row.height() <= bottom;
    }

    private boolean rowHitVisible(Row row, int mouseY) {
        int top = row.category().y() + headerHeight();
        int bottom = row.category().y() + row.category().lastHeight() - bodyPad();
        return rowVisible(row) && mouseY >= top && mouseY < bottom;
    }

    private void drawScrollBar(GuiGraphicsExtractor graphics, GuiCategory category, ClickGuiColors theme) {
        if (category.maxScroll() <= 0) return;
        int trackY = category.y() + headerHeight() + 3;
        int trackHeight = Math.max(8, category.lastHeight() - headerHeight() - bodyPad() - 6);
        int visibleBody = category.lastHeight() - headerHeight() - bodyPad();
        int contentBody = Math.max(visibleBody, category.contentHeight() - headerHeight() - bodyPad());
        int thumbHeight = Math.max(8, trackHeight * visibleBody / contentBody);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackY + (category.maxScroll() == 0 ? 0 : travel * category.scrollOffset() / category.maxScroll());
        RoundedGui.fill(graphics, category.x() + windowWidth() - 4, trackY, 2, trackHeight, 1, theme.outlineSoft());
        RoundedGui.fill(graphics, category.x() + windowWidth() - 5, thumbY, 3, thumbHeight, 2, withAlpha(theme.muted(), 90));
    }

    private void drawWindowBody(GuiGraphicsExtractor graphics, GuiCategory category, ClickGuiColors theme) {
        int x = category.x(), y = category.y(), h = category.lastHeight(), radius = windowRadius();
        if (VectorUi.recording()) {
            VectorUi.shadow(x, y + 1, windowWidth(), h, radius, 5, 0x42000000);
            RoundedGui.fill(graphics, x, y, windowWidth(), h, radius, theme.window());
            VectorUi.pushClip(x, y, windowWidth(), headerHeight());
            VectorUi.gradient(x, y, windowWidth(), headerHeight() + (category.open() ? radius : 0), radius,
                mixColor(theme.header(), theme.accent(), 0.055f), theme.header());
            VectorUi.popClip();
        } else {
            RoundedGui.fill(graphics, x - 2, y + 2, windowWidth() + 4, h + 2, radius + 2, 0x3A000000);
            RoundedGui.fill(graphics, x, y, windowWidth(), h, radius, theme.window());
            RoundedGui.fill(graphics, x, y, windowWidth(), headerHeight(), radius, theme.header());
        }
        RoundedGui.outlineOnly(graphics, x, y, windowWidth(), h, radius, withAlpha(theme.text(), 20));
        RoundedGui.fill(graphics, x + 8, y, Math.max(14, windowWidth() / 4), 1, 1, withAlpha(theme.accentBright(), 185));
    }

    private void drawRow(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        switch (row.kind()) {
            case HEADER -> drawHeader(graphics, row, mouseX, mouseY, theme);
            case MODULE -> drawModule(graphics, row, mouseX, mouseY, theme);
            case DESCRIPTION -> drawDescription(graphics, row, theme);
            case SETTING -> drawSetting(graphics, row, mouseX, mouseY, theme, macroDrawn);
            case NEST_FOOT -> {
                // Leave a little air between a module's configuration and the next module.
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiCategory category = row.category();
        int x = row.x(), y = row.y(), width = row.width();
        if (row.contains(mouseX, mouseY))
            RoundedGui.fill(graphics, x + 3, y + 3, width - 6, headerHeight() - 6, 5, withAlpha(theme.accent(), 24));
        String title = category.name().equals("ESP") ? "ESP"
            : category.name().substring(0, 1) + category.name().substring(1).toLowerCase(Locale.ROOT);
        int iconY = y + (headerHeight() - 13) / 2;
        RoundedGui.fill(graphics, x + 5, iconY, 13, 13, 4, withAlpha(theme.accent(), 28));
        RoundedGui.outlineOnly(graphics, x + 5, iconY, 13, 13, 4, withAlpha(theme.accentBright(), 45));
        UiIcons.category(graphics, category.name(), x + 7, y + (headerHeight() - 9) / 2, theme.accentBright());
        FormattedCharSequence categoryLabel = ArcaneFont.trimmed(font(), title, width - 35);
        UiDraw.textSized(graphics, font(), categoryLabel, x + 22, y + (headerHeight() - 9) / 2,
            8.25f, theme.text(), false);
        if (category.open()) {
            if (VectorUi.recording()) VectorUi.line(x + 1, y + headerHeight() - 0.5f, x + width - 1,
                y + headerHeight() - 0.5f, 0.75f, withAlpha(theme.text(), 24));
            else UiDraw.fill(graphics, x + 1, y + headerHeight() - 1, x + width - 1, y + headerHeight(), withAlpha(theme.text(), 24));
        }
        if (category.open()) drawCaretDown(graphics, x + width - 11, y + (headerHeight() - 5) / 2, theme.muted());
        else drawCaretRight(graphics, x + width - 10, y + (headerHeight() - 5) / 2, theme.muted());
    }

    private void drawModule(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        boolean hovered = row.contains(mouseX, mouseY) && rowHitVisible(row, mouseY);
        boolean enabled = module.enabled();
        if (hovered) {
            this.hoveredModule = module;
        }

        float hoverAmount = animatedHover(module, hovered);
        float enabledAmount = animatedEnabled(module, enabled);
        int baseBackground = mixColor(withAlpha(theme.text(), 5), withAlpha(theme.accent(), 28), enabledAmount);
        int hoverBackground = enabled ? theme.activeHover() : theme.hover();
        int background = mixColor(baseBackground, hoverBackground, hoverAmount);
        RoundedGui.fill(graphics, x + 4, y + 1, row.width() - 8, row.height() - 2, Math.min(4, windowRadius()), background);
        if (enabledAmount > 0.01f) {
            RoundedGui.fill(graphics, x + 4, y + 4, 2, Math.max(2, row.height() - 8), 1,
                withAlpha(theme.accentBright(), Math.round(225 * enabledAmount)));
        }
        UiDraw.fill(graphics, x + 10, y + row.height() - 1, x + row.width() - 10, y + row.height(),
            withAlpha(theme.text(), enabled ? 10 : 7));

        int textY = y + (row.height() - lineHeight()) / 2;
        int right = x + row.width() - 10;
        if (module.toggleable()) {
            int toggleX = x + row.width() - 24;
            int toggleY = y + (row.height() - 8) / 2;
            RoundedGui.fill(graphics, toggleX, toggleY, 16, 8, 4,
                mixColor(withAlpha(theme.text(), 27), withAlpha(theme.accent(), 180), enabledAmount));
            RoundedGui.outlineOnly(graphics, toggleX, toggleY, 16, 8, 4,
                withAlpha(enabled ? theme.accentBright() : theme.text(), enabled ? 75 : 24));
            RoundedGui.fill(graphics, toggleX + 1 + Math.round(8 * enabledAmount), toggleY + 1, 6, 6, 3,
                enabled ? theme.text() : theme.muted());
            right -= 18;
        } else if (module.hasSettings()) {
            if (module.expanded()) drawCaretDown(graphics, x + row.width() - 13, y + (row.height() - 5) / 2, theme.accent());
            else drawCaretRight(graphics, x + row.width() - 12, y + (row.height() - 5) / 2, theme.muted());
            right -= 9;
            String status = module.valueLabel();
            if (status != null && !status.isBlank()) {
                FormattedCharSequence value = ArcaneFont.trimmed(font(), status, 36);
                int valueWidth = ArcaneFont.width(font(), value);
                UiDraw.textSized(graphics, font(), value, right - valueWidth, textY + 1, 5.75f,
                    enabled ? theme.accentBright() : theme.faint(), false);
                right -= valueWidth + 5;
            }
        }
        int labelRight = right - 3;
        int labelX = x + 12;
        if (this.config.uiFavorites.contains(module.name())) {
            RoundedGui.fill(graphics, x + 7, textY + 3, 2, 3, 1, theme.accentBright());
        }
        FormattedCharSequence label = ArcaneFont.trimmed(font(), module.name(), UiGeometry.labelWidth(labelX, labelRight));
        UiDraw.text(graphics, font(), label, labelX, textY, enabled ? theme.text() : mixColor(theme.muted(), theme.text(), 0.25f), false);
    }

    private void drawDescription(GuiGraphicsExtractor graphics, Row row, ClickGuiColors theme) {
        if (row.height() <= 0) return;
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        RoundedGui.fill(graphics, x + 7, y + 1, row.width() - 14, row.height() - 2, 5, theme.nest());
        int lineY = y + 5;
        for (String line : wrap(module.description(), row.width() - NEST_INSET - 16)) {
            UiDraw.text(graphics, font(), ArcaneFont.text(line), x + NEST_INSET + 5, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    private void drawSetting(GuiGraphicsExtractor graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        GuiSetting setting = row.setting();
        if (setting == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        int height = row.height();
        boolean section = setting instanceof GuiSetting.Section;
        boolean groupHeader = setting instanceof GuiSetting.Toggle toggle && toggle.groupHeader();
        boolean hovered = row.contains(mouseX, mouseY) && !section;

        if (!section) {
            int background = groupHeader ? mixColor(theme.nest(), withAlpha(theme.accent(), 28), 0.45f) : theme.nest();
            RoundedGui.fill(graphics, x + 7, y + 1, row.width() - 14, height - 2, 4, background);
            RoundedGui.fill(graphics, x + 7, y + 3, groupHeader ? 2 : 1, Math.max(1, height - 6), 1,
                withAlpha(theme.accent(), groupHeader ? 155 : 70));
        }
        if (hovered && !(setting instanceof GuiSetting.Info)) {
            RoundedGui.fill(graphics, x + 8, y + 1, row.width() - 15, height - 2, 4, theme.hover());
        }

        int labelX = x + NEST_INSET + 5;
        int right = x + row.width() - 8;
        int textY = y + (setting instanceof GuiSetting.Slider ? 3 : (height - lineHeight()) / 2);
        int labelRight = settingControlLeft(setting, x, right) - 4;
        FormattedCharSequence settingLabel = ArcaneFont.trimmed(
            font(), setting.label(), UiGeometry.labelWidth(labelX, labelRight)
        );
        UiDraw.textSized(graphics, font(), settingLabel, labelX, textY, section ? 5.75f : 6.25f,
            section || groupHeader ? theme.accentBright() : theme.muted(), false);

        switch (setting) {
            case GuiSetting.Section ignored -> UiDraw.fill(graphics,
                labelX + Math.min(ArcaneFont.width(font(), setting.label()) + 7, row.width() / 2), y + height / 2,
                x + row.width() - 10, y + height / 2 + 1, withAlpha(theme.text(), 18));
            case GuiSetting.Toggle toggle -> drawCheckbox(graphics, right - 9, y + (height - 9) / 2, toggle.value(), theme);
            case GuiSetting.ToggleSwatch toggle -> {
                drawSwatch(graphics, right - 33, y + (height - 9) / 2, 14, toggle.color(), theme);
                drawCheckbox(graphics, right - 9, y + (height - 9) / 2, toggle.value(), theme);
            }
            case GuiSetting.Slider slider -> {
                String display = slider.display();
                UiDraw.text(graphics, font(), ArcaneFont.text(display), right - ArcaneFont.width(font(), display), textY, theme.text(), false);
                int trackX = sliderTrackX(x);
                int trackWidth = sliderTrackWidth();
                int trackY = y + height - 9;
                fillRounded(graphics, trackX, trackY, trackWidth, 3, theme.outlineSoft());
                int filled = Math.round(trackWidth * slider.fraction());
                if (filled > 0) {
                    fillRounded(graphics, trackX, trackY, filled, 3, theme.accent());
                }
                RoundedGui.fill(graphics, trackX + Math.clamp(filled - 3, 0, trackWidth - 7), trackY - 2, 7, 7, 3, theme.accentBright());
            }
            case GuiSetting.Swatch swatch -> drawSwatch(graphics, right - 22, y + (height - 9) / 2, 22, swatch.color(), theme);
            case GuiSetting.Cycle cycle -> {
                int arrowWidth = ArcaneFont.width(font(), ">");
                FormattedCharSequence value = boundedSettingValue(cycle, cycle.value(), row.width(), arrowWidth + 4);
                int valueRight = right - arrowWidth - 4;
                UiDraw.text(graphics, font(), ArcaneFont.text(">"), right - arrowWidth, textY, theme.faint(), false);
                UiDraw.text(graphics, font(), value, valueRight - ArcaneFont.width(font(), value), textY, theme.accentBright(), false);
            }
            case GuiSetting.Bind bind -> drawKeyPill(graphics, right, y, height, bind.mapping(), theme, BIND_KEY_WIDTH);
            case GuiSetting.Info info -> {
                FormattedCharSequence value = boundedSettingValue(info, info.value(), row.width(), 0);
                UiDraw.text(graphics, font(), value, right - ArcaneFont.width(font(), value), textY, theme.accentBright(), false);
            }
            case GuiSetting.Message message -> {
                int slot = message.slot();
                if (slot >= 0 && slot < this.macroInputs.size() && rowFullyVisible(row)) {
                    int fieldX = macroFieldX(x);
                    int fieldWidth = macroFieldWidth();
                    fillRounded(graphics, fieldX - 3, y + 3, fieldWidth + 6, 14, theme.window());
                    outlineRounded(graphics, fieldX - 3, y + 3, fieldWidth + 6, 14, theme.outlineSoft());
                    EditBox input = this.macroInputs.get(slot);
                    input.setX(fieldX);
                    input.setY(y + 6);
                    input.setWidth(fieldWidth);
                    input.setVisible(true);
                    macroDrawn[slot] = true;
                } else {
                    hideMacroInput(slot);
                }
                KeyMapping mapping = message.mapping();
                if (mapping != null) {
                    drawKeyPill(graphics, right, y, height, mapping, theme, MACRO_KEY_WIDTH);
                }
            }
        }
    }

    private int settingControlLeft(GuiSetting setting, int windowX, int right) {
        return switch (setting) {
            case GuiSetting.Section ignored -> right;
            case GuiSetting.Toggle ignored -> right - 9;
            case GuiSetting.ToggleSwatch ignored -> right - 33;
            case GuiSetting.Slider slider -> right - ArcaneFont.width(font(), slider.display());
            case GuiSetting.Swatch ignored -> right - 22;
            case GuiSetting.Cycle cycle -> {
                int adornmentWidth = ArcaneFont.width(font(), ">") + 4;
                FormattedCharSequence value = boundedSettingValue(cycle, cycle.value(), right - windowX + 8, adornmentWidth);
                yield right - adornmentWidth - ArcaneFont.width(font(), value);
            }
            case GuiSetting.Bind bind -> right - keyPillWidth(bind.mapping(), BIND_KEY_WIDTH);
            case GuiSetting.Info info -> right - ArcaneFont.width(font(), boundedSettingValue(info, info.value(), right - windowX + 8, 0));
            case GuiSetting.Message ignored -> macroFieldX(windowX) - 3;
        };
    }

    private FormattedCharSequence boundedSettingValue(GuiSetting setting, String value, int rowWidth, int adornmentWidth) {
        int budget = UiTextLayout.valueWidthBudget(rowWidth, ArcaneFont.width(font(), setting.label()), adornmentWidth);
        return ArcaneFont.trimmed(font(), value, budget);
    }

    private int keyPillWidth(KeyMapping mapping, int maxKeyWidth) {
        String raw = this.listeningFor == mapping
            ? "..."
            : mapping.isUnbound() ? "-" : mapping.getTranslatedKeyMessage().getString();
        FormattedCharSequence key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        return Math.max(20, ArcaneFont.width(font(), key) + 8);
    }

    private void drawKeyPill(GuiGraphicsExtractor graphics, int right, int y, int height, KeyMapping mapping, ClickGuiColors theme, int maxKeyWidth) {
        boolean listening = this.listeningFor == mapping;
        String raw = listening ? "..." : mapping.isUnbound() ? "-" : mapping.getTranslatedKeyMessage().getString();
        FormattedCharSequence key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        int keyWidth = ArcaneFont.width(font(), key);
        int width = Math.max(20, keyWidth + 8);
        int pillY = y + (height - 12) / 2;
        fillRounded(graphics, right - width, pillY, width, 12, listening ? theme.accentDim() : theme.window());
        outlineRounded(graphics, right - width, pillY, width, 12, listening ? theme.accent() : theme.outlineSoft());
        UiDraw.text(graphics, font(), key, right - width + (width - keyWidth) / 2, pillY + (12 - lineHeight()) / 2 + 1, listening ? theme.text() : theme.muted(), false);
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, ClickGuiColors theme) {
        RoundedGui.fill(graphics, 8, 10, 18, 18, 6, withAlpha(theme.accent(), 215));
        RoundedGui.outlineOnly(graphics, 8, 10, 18, 18, 6, withAlpha(theme.accentBright(), 110));
        UiDraw.textSized(graphics, font(), ArcaneFont.text("A"), 14, 15, 8.0f, theme.text(), false);
        UiDraw.textSized(graphics, font(), ArcaneFont.text("Arcane"), 31, 15, 9.0f, theme.text(), false);
        if (DEV_BUILD) {
            RoundedGui.fill(graphics, 68, 13, 22, 12, 5, withAlpha(theme.accent(), 28));
            RoundedGui.outlineOnly(graphics, 68, 13, 22, 12, 5, withAlpha(theme.accentBright(), 42));
            UiDraw.textSized(graphics, font(), ArcaneFont.text("DEV"), 73, 16, 5.5f, theme.accentBright(), false);
        }
        if (this.searchInput != null && this.searchInput.visible) {
            int x = searchX(searchWidth());
            RoundedGui.fill(graphics, x - 13, 10, searchWidth() + 20, 18, 7, theme.window());
            RoundedGui.outlineOnly(graphics, x - 13, 10, searchWidth() + 20, 18, 7,
                withAlpha(this.searchInput.isFocused() ? theme.accent() : theme.text(), this.searchInput.isFocused() ? 170 : 22));
            RoundedGui.outlineOnly(graphics, x - 9, 15, 4, 4, 2, theme.muted());
            smoothStroke(graphics, x - 5, 19, 2, (float)Math.PI / 4, theme.muted());
        }
        if (this.width >= 520) {
            drawFilterChip(graphics, this.width - 116, 50, "Enabled", this.config.uiShowEnabledOnly, theme);
            drawFilterChip(graphics, this.width - 61, 54, "Favorites", this.config.uiShowFavoritesOnly, theme);
        }
    }

    private void drawFilterChip(GuiGraphicsExtractor graphics, int x, int width, String label, boolean selected, ClickGuiColors theme) {
        RoundedGui.fill(graphics, x, 11, width, 22, 7, selected ? theme.active() : theme.bar());
        RoundedGui.outlineOnly(graphics, x, 11, width, 22, 7, withAlpha(selected ? theme.accent() : theme.text(), selected ? 90 : 18));
        UiDraw.text(graphics, font(), ArcaneFont.text(label), x + (width - ArcaneFont.width(font(), label)) / 2, 18,
            selected ? theme.accentBright() : theme.muted(), false);
    }

    private void drawBottomBar(GuiGraphicsExtractor graphics, ClickGuiColors theme) {
        int y = this.height - BOTTOM_BAR_HEIGHT;
        String hint = this.listeningFor != null ? "Press a key · Esc to unbind"
            : this.hoveredModule != null ? "Right click settings · Shift-click favorite" : "";
        if (!hint.isEmpty()) {
            int reserved = this.width < 520 ? 190 : 110;
            FormattedCharSequence hintText = ArcaneFont.trimmed(font(), hint, Math.max(0, this.width - reserved));
            UiDraw.textSized(graphics, font(), hintText, 8, y + 6, 6.25f,
                this.listeningFor == null ? withAlpha(theme.muted(), 160) : theme.accentBright(), false);
        }
        if (this.width < 520) {
            drawCompactFilterChip(graphics, this.width - 176, 34, "On", this.config.uiShowEnabledOnly, theme);
            drawCompactFilterChip(graphics, this.width - 137, 34, "Fav", this.config.uiShowFavoritesOnly, theme);
        }
        RoundedGui.fill(graphics, this.width - 98, y, 45, 16, 6, theme.bar());
        RoundedGui.outlineOnly(graphics, this.width - 98, y, 45, 16, 6, withAlpha(theme.text(), 18));
        UiDraw.text(graphics, font(), ArcaneFont.text("Configs"), this.width - 91, y + 4, theme.muted(), false);
        RoundedGui.fill(graphics, this.width - 48, y, 42, 16, 6, theme.bar());
        RoundedGui.outlineOnly(graphics, this.width - 48, y, 42, 16, 6, withAlpha(theme.accent(), 34));
        UiDraw.text(graphics, font(), ArcaneFont.text("Themes"), this.width - 40, y + 4, theme.accentBright(), false);
        if (this.filteringActive && this.width >= 520) {
            String results = visibleModuleCount() + " results";
            UiDraw.textSized(graphics, font(), ArcaneFont.text(results), this.width - 159, y + 5, 6.25f, theme.muted(), false);
        }
    }

    private void drawCompactFilterChip(GuiGraphicsExtractor graphics, int x, int width, String label, boolean selected, ClickGuiColors theme) {
        int y = this.height - BOTTOM_BAR_HEIGHT;
        RoundedGui.fill(graphics, x, y, width, 16, 6, selected ? theme.active() : theme.bar());
        RoundedGui.outlineOnly(graphics, x, y, width, 16, 6,
            withAlpha(selected ? theme.accent() : theme.text(), selected ? 90 : 18));
        UiDraw.textSized(graphics, font(), ArcaneFont.text(label), x + (width - ArcaneFont.width(font(), label)) / 2,
            y + 5, 5.75f, selected ? theme.accentBright() : theme.muted(), false);
    }

    private void drawModuleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = this.hoveredModule;
        if (module != this.tooltipModule) {
            this.tooltipModule = module;
            this.tooltipSince = System.nanoTime();
        }
        if (System.nanoTime() - this.tooltipSince < 450_000_000L) return;
        if (module == null || module.expanded() || this.draggingCategory != null) {
            return;
        }
        String value = module.valueLabel();
        List<String> lines = wrap((value == null ? "" : value + " · ") + module.description(), 150);
        int width = ArcaneFont.width(font(), module.name());
        for (String line : lines) {
            width = Math.max(width, ArcaneFont.width(font(), line));
        }
        width += 14;
        int height = 13 + lines.size() * proseHeight() + 8;
        int x = Math.max(0, Math.min(mouseX + 12, this.width - width - 4));
        int y = Math.max(0, Math.min(mouseY + 12, this.height - height - BOTTOM_BAR_HEIGHT - 4));

        RoundedGui.fill(graphics, x + 2, y + 2, width, height, Math.min(7, windowRadius()), 0x60000000);
        RoundedGui.fill(graphics, x, y, width, height, Math.min(7, windowRadius()), theme.bar());
        RoundedGui.outlineOnly(graphics, x, y, width, height, Math.min(7, windowRadius()), theme.outline());
        UiDraw.fill(graphics, x + 1, y + 1, x + 3, y + height - 1, theme.accent());
        UiDraw.text(graphics, font(), ArcaneFont.text(module.name()), x + 8, y + 6, theme.text(), false);
        int lineY = y + 9 + lineHeight();
        for (String line : lines) {
            UiDraw.text(graphics, font(), ArcaneFont.text(line), x + 8, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------------------------

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        RoundedGui.fill(graphics, x, y, width, height, 2, color);
    }

    private static void outlineRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        RoundedGui.outlineOnly(graphics, x, y, width, height, 2, color);
    }

    private static void drawCaretRight(GuiGraphicsExtractor graphics, int x, int y, int color) {
        smoothStroke(graphics, x, y, 4, (float)Math.PI / 4, color);
        smoothStroke(graphics, x + 3, y + 3, 4, (float)Math.PI * 3 / 4, color);
    }

    private static void drawCaretDown(GuiGraphicsExtractor graphics, int x, int y, int color) {
        smoothStroke(graphics, x, y + 1, 4, (float)Math.PI / 4, color);
        smoothStroke(graphics, x + 3, y + 4, 4, -(float)Math.PI / 4, color);
    }

    private static void smoothStroke(GuiGraphicsExtractor graphics, int x, int y, int length, float angle, int color) {
        if (VectorUi.recording()) {
            VectorUi.line(x, y, x + (float)Math.cos(angle) * length, y + (float)Math.sin(angle) * length, 0.8f, color);
            return;
        }
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate((float)x, (float)y);
        matrices.rotate(angle);
        RoundedGui.fill(graphics, 0, 0, length, 1, 1, color);
        matrices.popMatrix();
    }

    private static void drawCheckbox(GuiGraphicsExtractor graphics, int x, int y, boolean checked, ClickGuiColors theme) {
        RoundedGui.fill(graphics, x + 2, y + 1, 7, 7, 2, checked ? theme.accent() : 0xFF24202D);
        if (checked) {
            smoothStroke(graphics, x + 3, y + 4, 2, (float)Math.PI / 4, theme.text());
            smoothStroke(graphics, x + 4, y + 6, 4, -(float)Math.PI / 4, theme.text());
        } else RoundedGui.outlineOnly(graphics, x + 2, y + 1, 7, 7, 2, theme.outlineSoft());
    }

    private static void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int width, int color, ClickGuiColors theme) {
        fillRounded(graphics, x, y, width, 9, color | 0xFF000000);
        outlineRounded(graphics, x, y, width, 9, theme.outlineSoft());
    }

    // -----------------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        mouseX *= inputScaleX();
        mouseY *= inputScaleY();
        if (this.themePanel.contains((int)mouseX, (int)mouseY)) return true;
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        for (int index = this.windowOrder.size() - 1; index >= 0; index--) {
            GuiCategory category = this.windowOrder.get(index);
            if (!windowContains(category, (int) mouseX, (int) mouseY)) continue;
            bringToFront(category);
            if (category.open() && category.maxScroll() > 0 && mouseY >= category.y() + headerHeight()) {
                int amount = (int) Math.round(-verticalAmount * moduleHeight());
                category.scrollBy(amount == 0 ? (verticalAmount < 0.0 ? moduleHeight() : -moduleHeight()) : amount);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        click = canvasClick(click);
        if (this.listeningFor != null) {
            setBinding(InputConstants.Type.MOUSE.getOrCreate(click.button()));
            return true;
        }

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();
        if (this.themePanel.mouseClicked(click)) { setFocused(null); return true; }
        if (button == SDL_BUTTON_LEFT && mouseX >= this.width - 98 && mouseX < this.width - 53
            && mouseY >= this.height - BOTTOM_BAR_HEIGHT && mouseY < this.height - 4) {
            this.minecraft.gui.setScreen(new ConfigLibraryScreen(this, this.config));
            return true;
        }
        if (button == SDL_BUTTON_LEFT && mouseX >= this.width - 48 && mouseY >= this.height - BOTTOM_BAR_HEIGHT) {
            this.themePanel.setOpen(!this.themePanel.isOpen());
            return true;
        }
        if (button == SDL_BUTTON_LEFT && this.width < 520
            && mouseY >= this.height - BOTTOM_BAR_HEIGHT && mouseY < this.height - 4) {
            if (mouseX >= this.width - 176 && mouseX < this.width - 142) {
                setModuleFilters(!this.config.uiShowEnabledOnly, this.config.uiShowFavoritesOnly);
                return true;
            }
            if (mouseX >= this.width - 137 && mouseX < this.width - 103) {
                setModuleFilters(this.config.uiShowEnabledOnly, !this.config.uiShowFavoritesOnly);
                return true;
            }
        }
        if (button == SDL_BUTTON_LEFT && mouseY >= 11 && mouseY < 33 && this.width >= 520) {
            int chipX = this.width - 116;
            if (mouseX >= chipX && mouseX < chipX + 50) {
                setModuleFilters(!this.config.uiShowEnabledOnly, this.config.uiShowFavoritesOnly);
                return true;
            }
            if (mouseX >= chipX + 55 && mouseX < chipX + 109) {
                setModuleFilters(this.config.uiShowEnabledOnly, !this.config.uiShowFavoritesOnly);
                return true;
            }
        }
        if (button < SDL_BUTTON_LEFT || button > SDL_BUTTON_RIGHT) {
            return super.mouseClicked(click, doubled);
        }

        for (int index = this.windowOrder.size() - 1; index >= 0; index--) {
            GuiCategory category = this.windowOrder.get(index);
            if (!categoryVisible(category)) continue;
            for (Row row : layout(category)) {
                if (row.kind() != RowKind.HEADER && !rowHitVisible(row, mouseY)) continue;
                if (!row.contains(mouseX, mouseY)) {
                    continue;
                }
                bringToFront(category);
                Boolean delegated = handleRowClick(row, click, doubled, mouseX, mouseY, button);
                if (delegated != null) {
                    return delegated;
                }
            }
            if (windowContains(category, mouseX, mouseY)) {
                bringToFront(category);
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    /** Returns the click result, or {@code null} when the row ignores this button and drawing continues. */
    private @Nullable Boolean handleRowClick(Row row, MouseButtonEvent click, boolean doubled, int mouseX, int mouseY, int button) {
        switch (row.kind()) {
            case HEADER -> {
                if (button != SDL_BUTTON_LEFT) {
                    return true;
                }
                this.draggingCategory = row.category();
                this.dragOffsetX = mouseX - row.category().x();
                this.dragOffsetY = mouseY - row.category().y();
                this.dragMoved = false;
                return true;
            }
            case MODULE -> {
                GuiModule module = row.module();
                if (module == null) {
                    return true;
                }
                setFocused(null);
                if (button == SDL_BUTTON_LEFT && (click.modifiers() & 1) != 0) {
                    toggleFavorite(module.name());
                    return true;
                }
                if (button == SDL_BUTTON_MIDDLE) {
                    if (!module.group()) startListening(firstBind(module));
                    return true;
                }
                if (button == SDL_BUTTON_RIGHT) {
                    if (module.hasSettings()) {
                        toggleModuleSettings(module);
                    }
                    return true;
                }
                if (button == SDL_BUTTON_LEFT && module.toggleable()) {
                    module.toggle();
                    if (this.config.uiShowEnabledOnly) updateFilter(this.searchText);
                }
                return true;
            }
            case DESCRIPTION, NEST_FOOT -> {
                return true;
            }
            case SETTING -> {
                return handleSettingClick(row, click, doubled, mouseX, mouseY, button);
            }
        }
        return true;
    }

    private @Nullable Boolean handleSettingClick(Row row, MouseButtonEvent click, boolean doubled, int mouseX, int mouseY, int button) {
        GuiSetting setting = row.setting();
        if (setting == null) {
            return true;
        }
        if (setting instanceof GuiSetting.Message message) {
            int fieldX = macroFieldX(row.x());
            if (mouseX >= fieldX - 3 && mouseX < fieldX + macroFieldWidth() + 3) {
                int slot = message.slot();
                if (slot >= 0 && slot < this.macroInputs.size() && rowFullyVisible(row)) {
                    EditBox input = this.macroInputs.get(slot);
                    input.setX(fieldX);
                    input.setY(row.y() + 6);
                    input.setWidth(macroFieldWidth());
                    input.setVisible(true);
                }
                super.mouseClicked(click, doubled);
                return true;
            }
            if (button == SDL_BUTTON_LEFT && message.mapping() != null && mouseX >= row.x() + row.width() - 48) {
                startListening(message.mapping());
            }
            return true;
        }
        if (button != SDL_BUTTON_LEFT) {
            return true;
        }
        setFocused(null);
        switch (setting) {
            case GuiSetting.Section ignored -> {
            }
            case GuiSetting.Toggle toggle -> toggle.toggle();
            case GuiSetting.ToggleSwatch toggle -> {
                if (mouseX >= row.x() + row.width() - 41 && mouseX < row.x() + row.width() - 27) {
                    toggle.cycleColor();
                } else {
                    toggle.toggle();
                }
            }
            case GuiSetting.Slider slider -> {
                if (mouseY >= row.y() + row.height() - 13) {
                    this.draggingSlider = slider;
                    this.sliderTrackX = sliderTrackX(row.x());
                    this.sliderTrackWidth = sliderTrackWidth();
                    updateSlider(mouseX);
                }
            }
            case GuiSetting.Swatch swatch -> swatch.cycleColor();
            case GuiSetting.Cycle cycle -> cycle.next();
            case GuiSetting.Bind bind -> startListening(bind.mapping());
            case GuiSetting.Info ignored -> {
            }
            case GuiSetting.Message ignored -> {
            }
        }
        if (this.config.uiShowEnabledOnly
            && (setting instanceof GuiSetting.Toggle || setting instanceof GuiSetting.ToggleSwatch)) {
            updateFilter(this.searchText);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        click = canvasClick(click);
        offsetX *= inputScaleX();
        offsetY *= inputScaleY();
        if (this.themePanel.mouseDragged(click, offsetX, offsetY)) return true;
        if (this.draggingCategory != null) {
            int newX = (int) click.x() - this.dragOffsetX;
            int newY = (int) click.y() - this.dragOffsetY;
            this.dragMoved |= Math.abs(newX - this.draggingCategory.x()) > 1 || Math.abs(newY - this.draggingCategory.y()) > 1;
            this.draggingCategory.moveTo(newX, newY);
            if (this.dragMoved) {
                this.config.uiLayoutCustomized = true;
                this.windowBottoms.remove(this.draggingCategory);
            }
            clamp(this.draggingCategory);
            return true;
        }
        if (this.draggingSlider != null) {
            updateSlider((int) click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        click = canvasClick(click);
        if (this.themePanel.mouseReleased(click)) return true;
        if (this.draggingCategory != null) {
            if (!this.dragMoved) {
                toggleCategory(this.draggingCategory);
            }
            this.draggingCategory = null;
            return true;
        }
        if (this.draggingSlider != null) {
            this.draggingSlider = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.listeningFor != null) {
            if (input.key() == 256 || input.key() == 261 || input.key() == 259) {
                setBinding(InputConstants.UNKNOWN);
            } else {
                setBinding(InputConstants.getKey(input));
            }
            return true;
        }
        if (input.key() == 70 && (input.modifiers() & 2) != 0 && this.searchInput != null) {
            this.searchShortcutFocused = true;
            this.searchInput.setVisible(true);
            setFocused(this.searchInput);
            this.searchInput.setFocused(true);
            return true;
        }
        if (input.key() == 256 && this.filteringActive) {
            setModuleFilters(false, false);
            this.searchInput.setValue("");
            this.searchInput.setFocused(false);
            this.searchShortcutFocused = false;
            return true;
        }
        if (input.key() == 256 && this.searchInput != null && !this.searchText.isEmpty()) {
            this.searchInput.setValue("");
            this.searchInput.setFocused(false);
            return true;
        }
        return super.keyPressed(input);
    }

    private void startListening(@Nullable KeyMapping mapping) {
        if (mapping == null) {
            return;
        }
        this.listeningFor = mapping;
        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            this.macroInputs.get(slot).setFocused(false);
        }
    }

    private void setBinding(InputConstants.Key key) {
        if (this.listeningFor == null) {
            return;
        }
        this.listeningFor.setKey(key);
        this.listeningFor = null;
        KeyMapping.resetMapping();
        this.minecraft.options.save();
    }

    private void updateSlider(int mouseX) {
        if (this.draggingSlider == null || this.sliderTrackWidth <= 0) {
            return;
        }
        this.draggingSlider.setFraction((float) (mouseX - this.sliderTrackX) / this.sliderTrackWidth);
    }

    private static @Nullable KeyMapping firstBind(GuiModule module) {
        for (GuiSetting setting : module.settings()) {
            if (setting instanceof GuiSetting.Bind bind) {
                return bind.mapping();
            }
        }
        return null;
    }

    /** Opening a detail section does not enable its module or disturb saved panel positions. */
    public void toggleModuleSettings(GuiModule module) {
        if (this.categories.stream().noneMatch(c -> c.modules().contains(module)) || !module.hasSettings()) return;
        boolean expanded = !module.expanded();
        if (expanded && this.config.uiSingleSettings) {
            for (GuiCategory category : this.categories) {
                for (GuiModule other : category.modules()) other.setExpanded(false);
            }
        }
        module.setExpanded(expanded);
        this.wrapCache.clear();
    }

    // -----------------------------------------------------------------------------------------
    // Search and status
    // -----------------------------------------------------------------------------------------

    private void updateFilter(String value) {
        boolean wasSearching = this.filteringActive;
        this.searchText = value;
        this.query = value.trim().toLowerCase(Locale.ROOT);
        boolean searching = !this.query.isEmpty() || this.config.uiShowEnabledOnly || this.config.uiShowFavoritesOnly;
        this.filteringActive = searching;

        if (!wasSearching && searching) {
            this.stateBeforeSearch.clear();
            for (GuiCategory category : this.categories) {
                this.stateBeforeSearch.put(category, new WindowState(
                    category.x(), category.y(), category.open(), category.scrollOffset()
                ));
            }
        }
        for (GuiCategory category : this.categories) {
            category.filter(this.query, module -> (!this.config.uiShowEnabledOnly || module.enabled())
                && (!this.config.uiShowFavoritesOnly || this.config.uiFavorites.contains(module.name())));
        }
        if (searching) {
            for (GuiCategory category : this.categories) {
                boolean matched = !category.visible().isEmpty();
                category.setOpen(matched);
                if (matched) ensureOpenInUsableArea(category);
            }
        } else if (wasSearching) {
            for (GuiCategory category : this.categories) {
                WindowState state = this.stateBeforeSearch.get(category);
                if (state != null) {
                    category.moveTo(state.x(), state.y());
                    category.setOpen(state.open());
                    layout(category);
                    category.scrollBy(state.scrollOffset() - category.scrollOffset());
                }
            }
            this.stateBeforeSearch.clear();
            resolveResizeHeaderCollisions();
        }
    }

    public void setModuleFilters(boolean enabledOnly, boolean favoritesOnly) {
        this.config.uiShowEnabledOnly = enabledOnly;
        this.config.uiShowFavoritesOnly = favoritesOnly;
        updateFilter(this.searchText);
    }

    public void toggleFavorite(String name) {
        boolean exists = this.categories.stream().anyMatch(category -> category.modules().stream().anyMatch(module -> module.name().equals(name)));
        if (!exists) return;
        if (!this.config.uiFavorites.remove(name)) this.config.uiFavorites.add(name);
        updateFilter(this.searchText);
    }

    public int visibleModuleCount() {
        int count = 0;
        for (GuiCategory category : this.categories) {
            count += category.visible().size();
        }
        return count;
    }

    private int activeModuleCount() {
        int active = 0;
        for (GuiCategory category : this.categories) {
            for (GuiModule module : category.modules()) {
                if (module.enabled()) {
                    active++;
                }
            }
        }
        return active;
    }

    private int totalModuleCount() {
        int count = 0;
        for (GuiCategory category : this.categories) count += category.modules().size();
        return count;
    }

    private void updateFpsLabel() {
        long now = System.nanoTime();
        if (now < this.nextFpsUpdateNanos && !this.fpsLabel.isEmpty()) {
            return;
        }
        this.fpsLabel = this.minecraft == null ? "-- FPS" : this.minecraft.getFps() + " FPS";
        this.nextFpsUpdateNanos = now + 250_000_000L;
    }

    // -----------------------------------------------------------------------------------------
    // Geometry helpers
    // -----------------------------------------------------------------------------------------

    private boolean refreshMetrics() {
        UiGeometry.ClickGuiMetrics next = UiGeometry.clickGuiMetrics(
            this.width,
            this.categories.size(),
            this.config.uiScalePercent,
            this.config.uiDensityPercent
        );
        next = UiGeometry.fitClickGuiHeight(next, this.width,
            this.height - TOP_BAR_HEIGHT - next.gap() - BOTTOM_BAR_HEIGHT - 4, this.categories.size());
        if (next.equals(this.metrics)) return false;
        this.metrics = next;
        this.wrapCache.clear();
        return true;
    }

    private int windowWidth() {
        return this.metrics.windowWidth();
    }

    private int headerHeight() {
        return this.metrics.headerHeight();
    }

    private int moduleHeight() {
        return this.metrics.moduleHeight();
    }

    private int gap() {
        return this.metrics.gap();
    }

    private int windowRadius() {
        return Math.clamp(
            Math.round(this.config.uiCornerRadius * this.config.uiScalePercent / 100.0f),
            0,
            12
        );
    }

    private int bodyPad() {
        // Rows are inset five pixels and rounded themselves, so a small footer clears the curve.
        return Math.max(BODY_PAD, (windowRadius() + 3) / 4);
    }

    private int settingHeight(GuiSetting setting) {
        int compact = Math.max(12, moduleHeight() - 5);
        if (setting instanceof GuiSetting.Section) return 12;
        if (setting instanceof GuiSetting.Slider) return compact + 5;
        if (setting instanceof GuiSetting.Message) return compact + 5;
        return compact;
    }

    private int macroFieldWidth() {
        return Math.clamp(windowWidth() - NEST_INSET - 17 - MACRO_KEY_WIDTH - 14, 30, MACRO_FIELD_WIDTH);
    }

    private void updateAnimationStep() {
        long now = System.nanoTime();
        long elapsed = this.lastAnimationNanos == 0L ? 0L : now - this.lastAnimationNanos;
        this.lastAnimationNanos = now;
        if (this.config.uiReducedMotion || this.config.uiAnimationPercent <= 0 || elapsed <= 0L) {
            this.animationStep = 1.0f;
            return;
        }
        float seconds = Math.min(0.1f, elapsed / 1_000_000_000.0f);
        this.animationStep = Math.clamp(seconds * 14.0f * this.config.uiAnimationPercent / 100.0f, 0.0f, 1.0f);
    }

    private float animatedHover(GuiModule module, boolean hovered) {
        float current = this.hoverAnimations.getOrDefault(module, 0.0f);
        float target = hovered ? 1.0f : 0.0f;
        float next = current + (target - current) * this.animationStep;
        if (Math.abs(next - target) < 0.01f) next = target;
        if (next == 0.0f && !hovered) {
            this.hoverAnimations.remove(module);
        } else {
            this.hoverAnimations.put(module, next);
        }
        return next;
    }

    private float animatedEnabled(GuiModule module, boolean enabled) {
        float target = enabled ? 1.0f : 0.0f;
        float current = this.enabledAnimations.getOrDefault(module, target);
        float next = current + (target - current) * this.animationStep;
        if (Math.abs(next - target) < 0.01f) next = target;
        this.enabledAnimations.put(module, next);
        return next;
    }

    private static int mixColor(int from, int to, float amount) {
        float value = Math.clamp(amount, 0.0f, 1.0f);
        int alpha = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * value);
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * value);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * value);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * value);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int sliderTrackX(int windowX) {
        return windowX + NEST_INSET + 5;
    }

    private int sliderTrackWidth() {
        return windowWidth() - NEST_INSET - 18;
    }

    private static int macroFieldX(int windowX) {
        return windowX + NEST_INSET + 17;
    }

    private void hideMacroInput(int slot) {
        if (slot < 0 || slot >= this.macroInputs.size()) return;
        EditBox input = this.macroInputs.get(slot);
        if (!input.visible) {
            return;
        }
        input.setVisible(false);
        input.setFocused(false);
    }

    private void clamp(GuiCategory category) {
        category.clamp(this.width, windowWidth(), headerHeight(), TOP_BAR_HEIGHT + 4, this.height - BOTTOM_BAR_HEIGHT - 4);
    }

    /**
     * A GUI-scale resize can clamp several formerly separated windows onto the same title position.
     * Preserve every window that still fits, and park only the colliding titles as compact rails.
     */
    private void resolveResizeHeaderCollisions() {
        if (this.filteringActive) return;
        List<GuiCategory> placed = new ArrayList<>();
        for (GuiCategory category : this.categories) {
            layout(category);
            clamp(category);
            if (headerOverlapsAny(category, placed)) parkHeader(category, placed);
            placed.add(category);
        }
    }

    private void parkHeader(GuiCategory category, List<GuiCategory> placed) {
        int columns = UiGeometry.clickGuiColumns(this.width, windowWidth(), gap(), this.categories.size());
        int rowWidth = columns * windowWidth() + (columns - 1) * gap();
        int start = Math.max(2, (this.width - rowWidth) / 2);
        int bottom = this.height - BOTTOM_BAR_HEIGHT - 4 - headerHeight();
        category.setOpen(false);
        for (int slot = 0; slot < this.categories.size() * 2; slot++) {
            int x = start + (slot % columns) * (windowWidth() + gap());
            int y = bottom - (slot / columns) * headerHeight();
            if (y < TOP_BAR_HEIGHT + 4) break;
            category.moveTo(x, y);
            layout(category);
            clamp(category);
            if (!headerOverlapsAny(category, placed)) return;
        }
        category.moveTo(2, TOP_BAR_HEIGHT + 4);
        layout(category);
        clamp(category);
    }

    private boolean headerOverlapsAny(GuiCategory category, List<GuiCategory> placed) {
        for (GuiCategory other : placed) {
            if (category.x() < other.x() + windowWidth() && category.x() + windowWidth() > other.x()
                && category.y() < other.y() + headerHeight() && category.y() + headerHeight() > other.y()) {
                return true;
            }
        }
        return false;
    }

    private boolean windowContains(GuiCategory category, int mouseX, int mouseY) {
        return categoryVisible(category) && mouseX >= category.x() && mouseX < category.x() + windowWidth()
            && mouseY >= category.y() && mouseY < category.y() + category.lastHeight();
    }

    private boolean categoryVisible(GuiCategory category) {
        return !this.filteringActive || !category.visible().isEmpty();
    }

    private boolean macroOccluded(int slot, @Nullable GuiCategory owner) {
        if (owner == null || slot < 0 || slot >= this.macroInputs.size()) return false;
        int ownerIndex = this.windowOrder.indexOf(owner);
        EditBox input = this.macroInputs.get(slot);
        int left = input.getX();
        int top = input.getY();
        int right = left + input.getWidth();
        int bottom = top + 12;
        for (int index = ownerIndex + 1; index < this.windowOrder.size(); index++) {
            GuiCategory category = this.windowOrder.get(index);
            if (!categoryVisible(category)) continue;
            if (left < category.x() + windowWidth() && right > category.x()
                && top < category.y() + category.lastHeight() && bottom > category.y()) return true;
        }
        return false;
    }

    private void toggleCategory(GuiCategory category) {
        if (category.open()) {
            // Matching windows stay expanded so filtering can never hide the results it found.
            if (this.filteringActive && !category.visible().isEmpty()) return;
            category.setOpen(false);
            return;
        }
        category.setOpen(true);
        ensureOpenInUsableArea(category);
    }

    /** Moves a parked title upward before opening so at least a useful slice of rows is reachable. */
    private void ensureOpenInUsableArea(GuiCategory category) {
        int screenBottom = this.height - BOTTOM_BAR_HEIGHT - 4;
        int available = availableWindowBottom(category) - category.y() - headerHeight();
        int rows = Math.max(1, Math.min(3, category.visible().size()));
        int neededBody = rows * moduleHeight() + bodyPad();
        if (available < neededBody) {
            int cascade = Math.floorMod(this.categories.indexOf(category), 4) * 5;
            int targetY = TOP_BAR_HEIGHT + gap() + cascade;
            category.moveTo(category.x(), Math.min(targetY, Math.max(TOP_BAR_HEIGHT + 4, screenBottom - headerHeight() - neededBody)));
        }
        layout(category);
        clamp(category);
        layout(category);
    }

    private int availableWindowBottom(GuiCategory category) {
        int screenBottom = this.height - BOTTOM_BAR_HEIGHT - 4;
        if (this.config.uiLayoutCustomized) {
            return Math.min(screenBottom, category.y() + headerHeight() + VISIBLE_ROWS * moduleHeight() + bodyPad());
        }
        boolean searchMatch = this.filteringActive && !category.visible().isEmpty();
        if (this.draggingCategory == category || searchMatch || category.y() >= this.openWindowBottom) return screenBottom;
        return Math.min(this.windowBottoms.getOrDefault(category, screenBottom), screenBottom);
    }

    private int searchWidth() {
        return Math.min(120, Math.max(76, this.width - 160));
    }

    private int searchX(int searchWidth) {
        return (this.width - searchWidth) / 2;
    }

    private void positionSearchInput() {
        if (this.searchInput == null) return;
        int width = searchWidth();
        this.searchInput.setX(searchX(width));
        this.searchInput.setY(14);
        this.searchInput.setWidth(width);
        // Never strand an active filter during a resize: a populated search remains reachable.
        boolean visible = this.width >= SEARCH_MIN_SCREEN_WIDTH || !this.query.isEmpty() || this.searchShortcutFocused;
        this.searchInput.setVisible(visible);
        if (!visible && this.searchInput.isFocused()) this.searchInput.setFocused(false);
    }

    private void bringToFront(GuiCategory category) {
        if (this.windowOrder.remove(category)) {
            this.windowOrder.add(category);
        }
    }

    /** Applies the live opacity and backdrop controls without changing the shared HUD palette. */
    private ClickGuiColors theme() {
        return ClickGuiColors.display(this.config);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24;
    }

    private Font font() {
        if (this.uiFont == null) {
            this.uiFont = ArcaneFont.renderer(this.minecraft == null ? Minecraft.getInstance() : this.minecraft);
        }
        return this.uiFont;
    }

    /** Text fields draw plain strings, so they need the style applied through a formatter. */
    private void styleField(EditBox input) {
        input.addFormatter((value, offset) -> ArcaneFont.text(value).getVisualOrderText());
        input.setTextColor(theme().text());
        input.setTextColorUneditable(theme().faint());
    }

    private int lineHeight() {
        return 8;
    }

    /** Leading for wrapped prose, which needs a little more air than a single-line label. */
    private int proseHeight() {
        return 9;
    }

    public static boolean isOpen(Minecraft client) {
        return client.gui.screen() instanceof ArcaneSettingsScreen;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        savePanelLayout();
        this.config.save();
        this.minecraft.options.save();
        if (!this.scannerWasEnabled && this.config.enabled) {
            ArcaneClient.engine().queueNearby(this.minecraft);
        }
        ArcaneClient.engine().settingsChanged(this.minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Environment(EnvType.CLIENT)
    private record WrapKey(String text, int maxWidth) {
    }

    @Environment(EnvType.CLIENT)
    private record WindowState(int x, int y, boolean open, int scrollOffset) {
    }

    @Environment(EnvType.CLIENT)
    private enum RowKind {
        HEADER,
        MODULE,
        DESCRIPTION,
        SETTING,
        NEST_FOOT
    }

    @Environment(EnvType.CLIENT)
    private record Row(
        RowKind kind,
        GuiCategory category,
        @Nullable GuiModule module,
        @Nullable GuiSetting setting,
        int x,
        int y,
        int width,
        int height
    ) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        if (this.minecraft != null && this.minecraft.level == null) extractPanorama(graphics, deltaTicks);
    }

    private void sizeCanvas() {
        if (this.minecraft == null) return;
        var window = this.minecraft.getWindow();
        float scale = 2.0f * Math.max(1.0f, window.getHeight() / 1080.0f);
        this.width = Math.max(1, Math.round(window.getWidth() / scale));
        this.height = Math.max(1, Math.round(window.getHeight() / scale));
    }

    private double inputScaleX() {
        return this.minecraft == null ? 1.0 : (double)this.width / Math.max(1, this.minecraft.getWindow().getGuiScaledWidth());
    }

    private double inputScaleY() {
        return this.minecraft == null ? 1.0 : (double)this.height / Math.max(1, this.minecraft.getWindow().getGuiScaledHeight());
    }

    private MouseButtonEvent canvasClick(MouseButtonEvent click) {
        return new MouseButtonEvent(click.x() * inputScaleX(), click.y() * inputScaleY(), click.buttonInfo());
    }
}
