package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
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

    private final Screen parent;
    private final ArcaneConfig config;
    private final List<GuiCategory> categories;
    private final List<GuiCategory> windowOrder = new ArrayList<>();
    private final List<TextFieldWidget> macroInputs = new ArrayList<>();
    private final Map<WrapKey, List<String>> wrapCache = new HashMap<>();
    private final Map<GuiModule, Float> hoverAnimations = new IdentityHashMap<>();
    private final Map<GuiCategory, WindowState> stateBeforeSearch = new HashMap<>();
    private final Map<GuiCategory, Integer> windowBottoms = new HashMap<>();
    private final boolean scannerWasEnabled;

    private TextRenderer uiFont;
    private TextFieldWidget searchInput;
    private String searchText = "";
    private String query = "";
    private String fpsLabel = "";
    private long nextFpsUpdateNanos;
    private long lastAnimationNanos;
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
    private @Nullable KeyBinding listeningFor;
    private @Nullable GuiModule hoveredModule;
    private @Nullable GuiModule tooltipModule;
    private long tooltipSince;

    public ArcaneSettingsScreen(Screen parent) {
        super(Text.literal("Arcane Client"));
        this.parent = parent;
        this.config = ArcaneClient.config();
        if (this.config.devUiRevision < 2) {
            this.config.uiOpacityPercent = 100;
            this.config.uiBackgroundDimPercent = 0;
            this.config.devUiRevision = 2;
        }
        this.scannerWasEnabled = this.config.enabled;
        this.categories = new ArrayList<>(ModuleCatalog.build(this.config, MinecraftClient.getInstance()));
        this.categories.sort(java.util.Comparator.comparingInt(category -> category.name().equals("BASE FINDING") ? 0 : 1));
        this.windowOrder.addAll(this.categories);
    }

    @Override
    protected void init() {
        // A resize can change the GUI scale, which changes which font variant is sharpest.
        ArcaneFont.invalidate();
        this.uiFont = null;
        this.wrapCache.clear();
        refreshMetrics();
        this.lastAnimationNanos = System.nanoTime();
        if (!this.windowsLaidOut) {
            layoutWindows(true);
            this.windowsLaidOut = true;
        } else {
            layoutWindows(false);
            if (!this.query.isEmpty()) {
                Map<GuiCategory, WindowState> previousStates = new HashMap<>(this.stateBeforeSearch);
                this.stateBeforeSearch.clear();
                for (GuiCategory category : this.categories) {
                    WindowState previous = previousStates.get(category);
                    this.stateBeforeSearch.put(category, new WindowState(
                        category.x(),
                        category.y(),
                        previous == null ? category.open() : previous.open(),
                        previous == null ? category.scrollOffset() : previous.scrollOffset()
                    ));
                }
            }
        }

        int searchWidth = searchWidth();
        this.searchInput = new TextFieldWidget(font(), searchX(searchWidth), 18, searchWidth, 11, Text.literal("Search modules"));
        this.searchInput.setDrawsBackground(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setPlaceholder(ArcaneFont.text("Find a module"));
        styleField(this.searchInput);
        this.searchInput.setText(this.searchText);
        this.searchInput.setChangedListener(this::updateFilter);
        updateFilter(this.searchText);
        this.addDrawableChild(this.searchInput);

        this.macroInputs.clear();
        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            int index = slot;
            TextFieldWidget input = new TextFieldWidget(font(), 0, 0, MACRO_FIELD_WIDTH, 12, Text.literal("Chat macro " + (slot + 1)));
            input.setDrawsBackground(false);
            input.setTextShadow(false);
            input.setMaxLength(256);
            input.setText(this.config.chatMacro(slot));
            input.setPlaceholder(ArcaneFont.text("message"));
            styleField(input);
            input.setChangedListener(value -> this.config.setChatMacro(index, value));
            input.setVisible(false);
            this.macroInputs.add(this.addDrawableChild(input));
        }
    }

    // -----------------------------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------------------------

    private void layoutWindows(boolean openAll) {
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
        int rowBandHeight = Math.min(headerHeight() + 10 * moduleHeight() + bodyPad(),
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

    /** Positions every row of one window and clips tall bodies into a scrollable viewport. */
    private List<Row> layout(GuiCategory category) {
        List<Row> rows = new ArrayList<>();
        int x = category.x();
        int y = category.y();
        rows.add(new Row(RowKind.HEADER, category, null, null, x, y, windowWidth(), headerHeight()));

        int contentHeight = headerHeight();
        if (category.open()) {
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
            int rowY = y + headerHeight() - category.scrollOffset();
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
        int available = maximumHeight - headerHeight() - bodyPad();
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
        return headerHeight() + used + bodyPad();
    }

    private int maxWindowHeight(GuiCategory category) {
        return Math.max(headerHeight(), availableWindowBottom(category) - category.y());
    }

    private int moduleRowHeight(GuiModule module) {
        return moduleHeight() + (module.valueLabel() == null ? 0 : 6);
    }

    private int descriptionHeight(GuiModule module) {
        return 5 + wrap(module.description(), windowWidth() - NEST_INSET - 16).size() * proseHeight() + 4;
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
    public void render(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        if (refreshMetrics() && this.windowsLaidOut && this.draggingCategory == null) {
            layoutWindows(false);
        }
        updateAnimationStep();
        positionSearchInput();
        ClickGuiColors theme = theme();
        this.hoveredModule = null;
        boolean[] macroDrawn = new boolean[MACRO_COUNT];
        GuiCategory[] macroOwners = new GuiCategory[MACRO_COUNT];

        for (GuiCategory category : this.windowOrder) {
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
                graphics.enableScissor(
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
                        this.macroInputs.get(slot).render(graphics, mouseX, mouseY, deltaTicks);
                        macroDrawn[slot] = true;
                        macroOwners[slot] = category;
                    }
                }
                graphics.disableScissor();
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
        super.render(graphics, mouseX, mouseY, deltaTicks);
        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            if (macroDrawn[slot]) this.macroInputs.get(slot).setVisible(true);
        }
        drawModuleTooltip(graphics, mouseX, mouseY, theme);
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

    private void drawScrollBar(DrawContext graphics, GuiCategory category, ClickGuiColors theme) {
        if (category.maxScroll() <= 0) return;
        int trackY = category.y() + headerHeight() + 3;
        int trackHeight = Math.max(8, category.lastHeight() - headerHeight() - bodyPad() - 6);
        int visibleBody = category.lastHeight() - headerHeight() - bodyPad();
        int contentBody = Math.max(visibleBody, category.contentHeight() - headerHeight() - bodyPad());
        int thumbHeight = Math.max(8, trackHeight * visibleBody / contentBody);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackY + (category.maxScroll() == 0 ? 0 : travel * category.scrollOffset() / category.maxScroll());
        graphics.fill(category.x() + windowWidth() - 3, trackY, category.x() + windowWidth() - 2, trackY + trackHeight, theme.outlineSoft());
        graphics.fill(category.x() + windowWidth() - 4, thumbY, category.x() + windowWidth() - 2, thumbY + thumbHeight, theme.accent());
    }

    private void drawWindowBody(DrawContext graphics, GuiCategory category, ClickGuiColors theme) {
        int x = category.x();
        int y = category.y();
        int height = category.lastHeight();
        RoundedGui.fill(graphics, x + 2, y + 4, windowWidth(), height, windowRadius(), 0x58000000);
        RoundedGui.fill(graphics, x, y, windowWidth(), height, windowRadius(), theme.window());
        RoundedGui.outlineOnly(graphics, x, y, windowWidth(), height, windowRadius(), theme.outline());
    }

    private void drawRow(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        switch (row.kind()) {
            case HEADER -> drawHeader(graphics, row, mouseX, mouseY, theme);
            case MODULE -> drawModule(graphics, row, mouseX, mouseY, theme);
            case DESCRIPTION -> drawDescription(graphics, row, theme);
            case SETTING -> drawSetting(graphics, row, mouseX, mouseY, theme, macroDrawn);
            case NEST_FOOT -> {
                graphics.fill(row.x() + 1, row.y(), row.x() + row.width() - 1, row.y() + row.height(), theme.nest());
                graphics.fill(row.x() + 1, row.y(), row.x() + 3, row.y() + row.height(), theme.accentDim());
            }
        }
    }

    private void drawHeader(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiCategory category = row.category();
        int x = row.x();
        int y = row.y();
        int width = row.width();
        if (row.contains(mouseX, mouseY)) {
            RoundedGui.fill(graphics, x + 3, y + 3, width - 6, headerHeight() - 4, 2, theme.hover());
        }
        int textY = y + (headerHeight() - lineHeight()) / 2;
        OrderedText categoryLabel = ArcaneFont.trimmed(font(), category.name(), width - 20);
        graphics.drawText(font(), categoryLabel, x + (width - font().getWidth(categoryLabel)) / 2, textY, theme.accentBright(), false);
        if (row.contains(mouseX, mouseY) || !category.open()) {
            if (category.open()) drawCaretDown(graphics, x + width - 13, y + (headerHeight() - 5) / 2, theme.muted());
            else drawCaretRight(graphics, x + width - 12, y + (headerHeight() - 5) / 2, theme.muted());
        }
    }

    private void drawModule(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme) {
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
        int baseBackground = enabled ? theme.active() : theme.row();
        int hoverBackground = enabled ? theme.activeHover() : theme.hover();
        int background = mixColor(baseBackground, hoverBackground, hoverAmount);
        graphics.fill(x + 3, y + 1, x + row.width() - 3, y + row.height() - 1, background);
        if (enabled) graphics.fill(x + 3, y + 3, x + 5, y + row.height() - 3, theme.accent());

        int textY = y + (row.height() - lineHeight()) / 2;
        int right = x + row.width() - 10;
        String value = module.valueLabel();
        if (module.toggleable() && value == null) {
            int switchX = right - 15;
            int switchY = y + (row.height() - 8) / 2;
            RoundedGui.fill(graphics, switchX, switchY, 15, 8, 4, enabled ? theme.accentDim() : 0xFF34393F);
            RoundedGui.fill(graphics, switchX + (enabled ? 8 : 1), switchY + 1, 6, 6, 3, enabled ? theme.accentBright() : theme.text());
            right -= 21;
        }
        int labelRight = right - 3;
        if (value != null) {
            textY = y + 2;
            OrderedText valueText = ArcaneFont.trimmed(font(), value, right - x - 14);
            graphics.drawText(font(), valueText, x + 11, y + row.height() - lineHeight() - 2, theme.accent(), false);
        }
        int labelX = x + 11;
        OrderedText label = ArcaneFont.trimmed(font(), module.name(), UiGeometry.labelWidth(labelX, labelRight));
        graphics.drawText(font(), label, labelX, textY, enabled ? theme.accentBright() : theme.text(), false);
    }

    private void drawDescription(DrawContext graphics, Row row, ClickGuiColors theme) {
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        graphics.fill(x + 1, y, x + row.width() - 1, y + row.height(), theme.nest());
        graphics.fill(x + 1, y, x + 3, y + row.height(), theme.accentDim());
        int lineY = y + 5;
        for (String line : wrap(module.description(), row.width() - NEST_INSET - 16)) {
            graphics.drawText(font(), ArcaneFont.text(line), x + NEST_INSET + 5, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    private void drawSetting(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        GuiSetting setting = row.setting();
        if (setting == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        int height = row.height();
        boolean hovered = row.contains(mouseX, mouseY);

        graphics.fill(x + 1, y, x + row.width() - 1, y + height, theme.nest());
        graphics.fill(x + 1, y, x + 3, y + height, theme.accentDim());
        if (hovered && !(setting instanceof GuiSetting.Info)) {
            graphics.fill(x + NEST_INSET, y, x + row.width() - 1, y + height, theme.hover());
        }

        int labelX = x + NEST_INSET + 5;
        int right = x + row.width() - 8;
        int textY = y + (setting instanceof GuiSetting.Slider ? 3 : (height - lineHeight()) / 2);
        int labelRight = settingControlLeft(setting, x, right) - 4;
        OrderedText settingLabel = ArcaneFont.trimmed(
            font(), setting.label(), UiGeometry.labelWidth(labelX, labelRight)
        );
        graphics.drawText(font(), settingLabel, labelX, textY, theme.muted(), false);

        switch (setting) {
            case GuiSetting.Toggle toggle -> drawCheckbox(graphics, right - 9, y + (height - 9) / 2, toggle.value(), theme);
            case GuiSetting.ToggleSwatch toggle -> {
                drawSwatch(graphics, right - 33, y + (height - 9) / 2, 14, toggle.color(), theme);
                drawCheckbox(graphics, right - 9, y + (height - 9) / 2, toggle.value(), theme);
            }
            case GuiSetting.Slider slider -> {
                String display = slider.display();
                graphics.drawText(font(), ArcaneFont.text(display), right - ArcaneFont.width(font(), display), textY, theme.text(), false);
                int trackX = sliderTrackX(x);
                int trackWidth = sliderTrackWidth();
                int trackY = y + height - 9;
                fillRounded(graphics, trackX, trackY, trackWidth, 3, theme.outlineSoft());
                int filled = Math.round(trackWidth * slider.fraction());
                if (filled > 0) {
                    fillRounded(graphics, trackX, trackY, filled, 3, theme.accent());
                }
                fillRounded(graphics, trackX + Math.clamp(filled - 2, 0, trackWidth - 4), trackY - 2, 4, 7, theme.accentBright());
            }
            case GuiSetting.Swatch swatch -> drawSwatch(graphics, right - 22, y + (height - 9) / 2, 22, swatch.color(), theme);
            case GuiSetting.Cycle cycle -> {
                String value = cycle.value();
                int valueWidth = ArcaneFont.width(font(), value);
                graphics.drawText(font(), ArcaneFont.text(">"), right - 4, textY, theme.faint(), false);
                graphics.drawText(font(), ArcaneFont.text(value), right - 8 - valueWidth, textY, theme.accentBright(), false);
                graphics.drawText(font(), ArcaneFont.text("<"), right - 12 - valueWidth - ArcaneFont.width(font(), "<"), textY, theme.faint(), false);
            }
            case GuiSetting.Bind bind -> drawKeyPill(graphics, right, y, height, bind.mapping(), theme, BIND_KEY_WIDTH);
            case GuiSetting.Info info -> {
                String value = info.value();
                graphics.drawText(font(), ArcaneFont.text(value), right - ArcaneFont.width(font(), value), textY, theme.accentBright(), false);
            }
            case GuiSetting.Message message -> {
                int slot = message.slot();
                if (slot >= 0 && slot < this.macroInputs.size() && rowFullyVisible(row)) {
                    int fieldX = macroFieldX(x);
                    int fieldWidth = macroFieldWidth();
                    fillRounded(graphics, fieldX - 3, y + 3, fieldWidth + 6, 14, theme.window());
                    outlineRounded(graphics, fieldX - 3, y + 3, fieldWidth + 6, 14, theme.outlineSoft());
                    TextFieldWidget input = this.macroInputs.get(slot);
                    input.setX(fieldX);
                    input.setY(y + 6);
                    input.setWidth(fieldWidth);
                    input.setVisible(true);
                    macroDrawn[slot] = true;
                } else {
                    hideMacroInput(slot);
                }
                KeyBinding mapping = message.mapping();
                if (mapping != null) {
                    drawKeyPill(graphics, right, y, height, mapping, theme, MACRO_KEY_WIDTH);
                }
            }
        }
    }

    private int settingControlLeft(GuiSetting setting, int windowX, int right) {
        return switch (setting) {
            case GuiSetting.Toggle ignored -> right - 9;
            case GuiSetting.ToggleSwatch ignored -> right - 33;
            case GuiSetting.Slider slider -> right - ArcaneFont.width(font(), slider.display());
            case GuiSetting.Swatch ignored -> right - 22;
            case GuiSetting.Cycle cycle -> {
                int valueWidth = ArcaneFont.width(font(), cycle.value());
                yield right - 12 - valueWidth - ArcaneFont.width(font(), "<");
            }
            case GuiSetting.Bind bind -> right - keyPillWidth(bind.mapping(), BIND_KEY_WIDTH);
            case GuiSetting.Info info -> right - ArcaneFont.width(font(), info.value());
            case GuiSetting.Message ignored -> macroFieldX(windowX) - 3;
        };
    }

    private int keyPillWidth(KeyBinding mapping, int maxKeyWidth) {
        String raw = this.listeningFor == mapping
            ? "..."
            : mapping.isUnbound() ? "-" : mapping.getBoundKeyLocalizedText().getString();
        OrderedText key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        return Math.max(20, font().getWidth(key) + 8);
    }

    private void drawKeyPill(DrawContext graphics, int right, int y, int height, KeyBinding mapping, ClickGuiColors theme, int maxKeyWidth) {
        boolean listening = this.listeningFor == mapping;
        String raw = listening ? "..." : mapping.isUnbound() ? "-" : mapping.getBoundKeyLocalizedText().getString();
        OrderedText key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        int keyWidth = font().getWidth(key);
        int width = Math.max(20, keyWidth + 8);
        int pillY = y + (height - 12) / 2;
        fillRounded(graphics, right - width, pillY, width, 12, listening ? theme.accentDim() : theme.window());
        outlineRounded(graphics, right - width, pillY, width, 12, listening ? theme.accent() : theme.outlineSoft());
        graphics.drawText(font(), key, right - width + (width - keyWidth) / 2, pillY + (12 - lineHeight()) / 2 + 1, listening ? theme.text() : theme.muted(), false);
    }

    private void drawTopBar(DrawContext graphics, ClickGuiColors theme) {
        int textY = 18;
        RoundedGui.fill(graphics, 8, 11, DEV_BUILD ? 93 : 65, 21, 3, theme.bar());
        graphics.drawText(font(), ArcaneFont.text("ARCANE"), 16, textY, theme.text(), false);
        if (DEV_BUILD) graphics.drawText(font(), ArcaneFont.text("DEV"), 75, textY, theme.accentBright(), false);

        if (this.searchInput != null && this.searchInput.visible) {
            int searchX = searchX(searchWidth());
            RoundedGui.fill(graphics, searchX - 16, 13, searchWidth() + 23, 20, 2, theme.window());
            if (this.searchInput.isFocused())
                graphics.fill(searchX - 14, 32, searchX + searchWidth() + 5, 33, theme.accentDim());
            graphics.drawText(font(), ArcaneFont.text("/"), searchX - 10, textY, theme.muted(), false);
        }
    }

    private void drawBottomBar(DrawContext graphics, ClickGuiColors theme) {
        int y = this.height - BOTTOM_BAR_HEIGHT;
        int textY = y + (BOTTOM_BAR_HEIGHT - lineHeight()) / 2;
        String hint = this.listeningFor != null
            ? (this.width >= 460 ? "Press a key or mouse button   Esc unbinds" : "Press input   Esc unbinds")
            : (this.width >= 500 ? "LMB toggle   RMB settings   MMB bind   Drag titles" : "LMB toggle   RMB settings");
        int hintWidth = ArcaneFont.width(font(), hint) + 14;
        int barRadius = Math.min(6, windowRadius());
        RoundedGui.fill(graphics, 7, y + 2, hintWidth, 16, barRadius, 0x38000000);
        RoundedGui.fill(graphics, 6, y, hintWidth, 16, barRadius, theme.bar());
        RoundedGui.outlineOnly(graphics, 6, y, hintWidth, 16, barRadius, theme.outline());
        graphics.drawText(font(), ArcaneFont.text(hint), 13, textY, this.listeningFor != null ? theme.accent() : theme.muted(), false);

        if (!this.query.isEmpty() && this.width >= 360) {
            String results = visibleModuleCount() + " results";
            int resultWidth = ArcaneFont.width(font(), results) + 14;
            int resultX = this.width - resultWidth - 6;
            RoundedGui.fill(graphics, resultX, y, resultWidth, 16, barRadius, theme.bar());
            RoundedGui.outlineOnly(graphics, resultX, y, resultWidth, 16, barRadius, theme.outline());
            graphics.drawText(font(), ArcaneFont.text(results), resultX + 7, textY, theme.accentBright(), false);
        }
    }

    private void drawModuleTooltip(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = this.hoveredModule;
        if (module != this.tooltipModule) {
            this.tooltipModule = module;
            this.tooltipSince = System.nanoTime();
        }
        if (System.nanoTime() - this.tooltipSince < 450_000_000L) return;
        if (module == null || module.expanded() || this.draggingCategory != null) {
            return;
        }
        List<String> lines = wrap(module.description(), 150);
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
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, theme.accent());
        graphics.drawText(font(), ArcaneFont.text(module.name()), x + 8, y + 6, theme.text(), false);
        int lineY = y + 9 + lineHeight();
        for (String line : lines) {
            graphics.drawText(font(), ArcaneFont.text(line), x + 8, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------------------------

    private static void fillRounded(DrawContext graphics, int x, int y, int width, int height, int color) {
        RoundedGui.fill(graphics, x, y, width, height, 2, color);
    }

    private static void outlineRounded(DrawContext graphics, int x, int y, int width, int height, int color) {
        RoundedGui.outlineOnly(graphics, x, y, width, height, 2, color);
    }

    private static void drawCaretRight(DrawContext graphics, int x, int y, int color) {
        for (int step = 0; step < 3; step++) {
            graphics.fill(x + step, y + step, x + step + 1, y + 5 - step, color);
        }
    }

    private static void drawCaretDown(DrawContext graphics, int x, int y, int color) {
        for (int step = 0; step < 3; step++) {
            graphics.fill(x + step, y + step, x + 5 - step, y + step + 1, color);
        }
    }

    private static void drawCheckbox(DrawContext graphics, int x, int y, boolean checked, ClickGuiColors theme) {
        fillRounded(graphics, x, y, 9, 9, checked ? theme.accent() : theme.window());
        outlineRounded(graphics, x, y, 9, 9, checked ? theme.accentBright() : theme.outlineSoft());
        if (!checked) {
            return;
        }
        int mark = theme.bar() | 0xFF000000;
        graphics.fill(x + 2, y + 4, x + 3, y + 7, mark);
        graphics.fill(x + 3, y + 5, x + 4, y + 8, mark);
        graphics.fill(x + 4, y + 4, x + 5, y + 7, mark);
        graphics.fill(x + 5, y + 3, x + 6, y + 6, mark);
        graphics.fill(x + 6, y + 2, x + 7, y + 5, mark);
    }

    private static void drawSwatch(DrawContext graphics, int x, int y, int width, int color, ClickGuiColors theme) {
        fillRounded(graphics, x, y, width, 9, color | 0xFF000000);
        outlineRounded(graphics, x, y, width, 9, theme.outlineSoft());
    }

    // -----------------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.listeningFor != null) {
            setBinding(InputUtil.Type.MOUSE.createFromCode(click.button()));
            return true;
        }

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        int button = click.button();
        if (button < 0 || button > 2) {
            return super.mouseClicked(click, doubled);
        }

        for (int index = this.windowOrder.size() - 1; index >= 0; index--) {
            GuiCategory category = this.windowOrder.get(index);
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
    private @Nullable Boolean handleRowClick(Row row, Click click, boolean doubled, int mouseX, int mouseY, int button) {
        switch (row.kind()) {
            case HEADER -> {
                if (button != 0) {
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
                if (button == 2) {
                    startListening(firstBind(module));
                    return true;
                }
                if (button == 1 || !module.toggleable()) {
                    if (module.hasSettings()) {
                        module.setExpanded(!module.expanded());
                    }
                    return true;
                }
                module.toggle();
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

    private @Nullable Boolean handleSettingClick(Row row, Click click, boolean doubled, int mouseX, int mouseY, int button) {
        GuiSetting setting = row.setting();
        if (setting == null) {
            return true;
        }
        if (setting instanceof GuiSetting.Message message) {
            int fieldX = macroFieldX(row.x());
            if (mouseX >= fieldX - 3 && mouseX < fieldX + macroFieldWidth() + 3) {
                int slot = message.slot();
                if (slot >= 0 && slot < this.macroInputs.size() && rowFullyVisible(row)) {
                    TextFieldWidget input = this.macroInputs.get(slot);
                    input.setX(fieldX);
                    input.setY(row.y() + 6);
                    input.setWidth(macroFieldWidth());
                    input.setVisible(true);
                }
                super.mouseClicked(click, doubled);
                return true;
            }
            if (button == 0 && message.mapping() != null && mouseX >= row.x() + row.width() - 48) {
                startListening(message.mapping());
            }
            return true;
        }
        if (button != 0) {
            return true;
        }
        setFocused(null);
        switch (setting) {
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
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.draggingCategory != null) {
            int newX = (int) click.x() - this.dragOffsetX;
            int newY = (int) click.y() - this.dragOffsetY;
            this.dragMoved |= Math.abs(newX - this.draggingCategory.x()) > 1 || Math.abs(newY - this.draggingCategory.y()) > 1;
            this.draggingCategory.moveTo(newX, newY);
            if (this.dragMoved) this.windowBottoms.remove(this.draggingCategory);
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
    public boolean mouseReleased(Click click) {
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
    public boolean keyPressed(KeyInput input) {
        if (this.listeningFor != null) {
            if (input.key() == 256 || input.key() == 261 || input.key() == 259) {
                setBinding(InputUtil.UNKNOWN_KEY);
            } else {
                setBinding(InputUtil.fromKeyCode(input));
            }
            return true;
        }
        if (input.key() == 256 && this.searchInput != null && !this.searchText.isEmpty()) {
            this.searchInput.setText("");
            this.searchInput.setFocused(false);
            return true;
        }
        return super.keyPressed(input);
    }

    private void startListening(@Nullable KeyBinding mapping) {
        if (mapping == null) {
            return;
        }
        this.listeningFor = mapping;
        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            this.macroInputs.get(slot).setFocused(false);
        }
    }

    private void setBinding(InputUtil.Key key) {
        if (this.listeningFor == null) {
            return;
        }
        this.listeningFor.setBoundKey(key);
        this.listeningFor = null;
        KeyBinding.updateKeysByCode();
        this.client.options.write();
    }

    private void updateSlider(int mouseX) {
        if (this.draggingSlider == null || this.sliderTrackWidth <= 0) {
            return;
        }
        this.draggingSlider.setFraction((float) (mouseX - this.sliderTrackX) / this.sliderTrackWidth);
    }

    private static @Nullable KeyBinding firstBind(GuiModule module) {
        for (GuiSetting setting : module.settings()) {
            if (setting instanceof GuiSetting.Bind bind) {
                return bind.mapping();
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------
    // Search and status
    // -----------------------------------------------------------------------------------------

    private void updateFilter(String value) {
        boolean wasSearching = !this.query.isEmpty();
        this.searchText = value;
        this.query = value.trim().toLowerCase(Locale.ROOT);
        boolean searching = !this.query.isEmpty();

        if (!wasSearching && searching) {
            this.stateBeforeSearch.clear();
            for (GuiCategory category : this.categories) {
                this.stateBeforeSearch.put(category, new WindowState(
                    category.x(), category.y(), category.open(), category.scrollOffset()
                ));
            }
        }
        for (GuiCategory category : this.categories) {
            category.filter(this.query);
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

    private int visibleModuleCount() {
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
        this.fpsLabel = this.client == null ? "-- FPS" : this.client.getCurrentFps() + " FPS";
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
        // Keep square rows, nested settings and their hit areas above the curved footer.
        return Math.max(BODY_PAD, windowRadius() + 1);
    }

    private int settingHeight(GuiSetting setting) {
        int compact = moduleHeight() + 1;
        if (setting instanceof GuiSetting.Slider) return compact + 9;
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
        if (this.config.uiAnimationPercent <= 0 || elapsed <= 0L) {
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
        TextFieldWidget input = this.macroInputs.get(slot);
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
        if (!this.query.isEmpty()) return;
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
        return mouseX >= category.x() && mouseX < category.x() + windowWidth()
            && mouseY >= category.y() && mouseY < category.y() + category.lastHeight();
    }

    private boolean macroOccluded(int slot, @Nullable GuiCategory owner) {
        if (owner == null || slot < 0 || slot >= this.macroInputs.size()) return false;
        int ownerIndex = this.windowOrder.indexOf(owner);
        TextFieldWidget input = this.macroInputs.get(slot);
        int left = input.getX();
        int top = input.getY();
        int right = left + input.getWidth();
        int bottom = top + 12;
        for (int index = ownerIndex + 1; index < this.windowOrder.size(); index++) {
            GuiCategory category = this.windowOrder.get(index);
            if (left < category.x() + windowWidth() && right > category.x()
                && top < category.y() + category.lastHeight() && bottom > category.y()) return true;
        }
        return false;
    }

    private void toggleCategory(GuiCategory category) {
        if (category.open()) {
            // Matching windows stay expanded so filtering can never hide the results it found.
            if (!this.query.isEmpty() && !category.visible().isEmpty()) return;
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
        boolean searchMatch = !this.query.isEmpty() && !category.visible().isEmpty();
        if (this.draggingCategory == category || searchMatch || category.y() >= this.openWindowBottom) return screenBottom;
        return Math.min(this.windowBottoms.getOrDefault(category, screenBottom), screenBottom);
    }

    private int searchWidth() {
        return Math.clamp(this.width / 5, 96, 144);
    }

    private int searchX(int searchWidth) {
        return (this.width - searchWidth) / 2;
    }

    private void positionSearchInput() {
        if (this.searchInput == null) return;
        int width = searchWidth();
        this.searchInput.setX(searchX(width));
        this.searchInput.setY(18);
        this.searchInput.setWidth(width);
        // Never strand an active filter during a resize: a populated search remains reachable.
        boolean visible = this.width >= SEARCH_MIN_SCREEN_WIDTH || !this.query.isEmpty();
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
        ClickGuiColors base = ClickGuiColors.resolve(this.config);
        int opacity = UiGeometry.percentAlpha(this.config.uiOpacityPercent);
        int strongOpacity = Math.clamp(opacity + 10, 0, 255);
        int rowOpacity = opacity;
        int softOpacity = opacity;
        int backdrop = 0;
        return new ClickGuiColors(
            base.accent(), base.accentBright(), base.accentDim(), withAlpha(base.active(), strongOpacity), withAlpha(base.activeHover(), strongOpacity),
            backdrop, withAlpha(base.bar(), strongOpacity), withAlpha(base.window(), opacity), withAlpha(base.header(), strongOpacity), withAlpha(base.row(), rowOpacity),
            withAlpha(base.hover(), strongOpacity), withAlpha(base.nest(), softOpacity), base.outline(), base.outlineSoft(), base.text(), base.muted(), base.faint()
        );
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24;
    }

    private TextRenderer font() {
        if (this.uiFont == null) {
            this.uiFont = ArcaneFont.renderer(this.client == null ? MinecraftClient.getInstance() : this.client);
        }
        return this.uiFont;
    }

    /** Text fields draw plain strings, so they need the style applied through a formatter. */
    private void styleField(TextFieldWidget input) {
        input.addFormatter((value, offset) -> ArcaneFont.text(value).asOrderedText());
        input.setEditableColor(theme().text());
        input.setUneditableColor(theme().faint());
    }

    private int lineHeight() {
        return font().fontHeight;
    }

    /** Leading for wrapped prose, which needs a little more air than a single-line label. */
    private int proseHeight() {
        return font().fontHeight + 1;
    }

    public static boolean isOpen(MinecraftClient client) {
        return client.currentScreen instanceof ArcaneSettingsScreen;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.config.save();
        this.client.options.write();
        if (!this.scannerWasEnabled && this.config.enabled) {
            ArcaneClient.engine().queueNearby(this.client);
        }
        ArcaneClient.engine().settingsChanged(this.client);
    }

    @Override
    public boolean shouldPause() {
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
    /** A floating overlay: vanilla must not blur or darken the world underneath it. */
    @Override
    public void blur() {
    }

    @Override
    public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        if (this.client != null && this.client.world == null) renderPanoramaBackground(graphics, deltaTicks);
    }
}
