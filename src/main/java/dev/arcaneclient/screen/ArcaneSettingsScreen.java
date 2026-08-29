package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int WINDOW_WIDTH = 132;
    private static final int WINDOW_RADIUS = 6;
    private static final int ROW_RADIUS = 4;
    private static final int HEADER_HEIGHT = 17;
    private static final int MODULE_HEIGHT = 13;
    private static final int NEST_INSET = 10;
    private static final int NEST_PAD = 4;
    private static final int BODY_PAD = 3;
    private static final int TOP_BAR_HEIGHT = 24;
    private static final int BOTTOM_BAR_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int SWITCH_WIDTH = 15;
    private static final int SWITCH_HEIGHT = 8;
    private static final int SETTING_SWITCH_WIDTH = 13;
    private static final int SETTING_SWITCH_HEIGHT = 7;
    private static final int SWATCH_WIDTH = 14;
    private static final int MACRO_COUNT = 4;
    private static final int MACRO_FIELD_WIDTH = 60;
    private static final int BIND_KEY_WIDTH = 34;
    private static final int MACRO_KEY_WIDTH = 26;
    /** Width of the right-hand strip of a macro row that rebinds instead of editing the message. */
    private static final int MACRO_KEY_ZONE = 43;
    private static final int SEARCH_PADDING = 15;

    private final Screen parent;
    private final ArcaneConfig config;
    private final List<GuiCategory> categories;
    private final List<TextFieldWidget> macroInputs = new ArrayList<>();
    private final Map<WrapKey, List<String>> wrapCache = new HashMap<>();
    private final boolean scannerWasEnabled;

    private TextRenderer uiFont;
    private TextFieldWidget searchInput;
    private String searchText = "";
    private String query = "";
    private String fpsLabel = "";
    private long nextFpsUpdateNanos;
    private long lastFrameNanos;
    private float frameDelta;
    private int openWindowBottom;

    private @Nullable GuiCategory draggingCategory;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragMoved;
    private GuiSetting.@Nullable Slider draggingSlider;
    private int sliderTrackX;
    private int sliderTrackWidth;
    private @Nullable KeyBinding listeningFor;
    private @Nullable GuiModule hoveredModule;

    public ArcaneSettingsScreen(Screen parent) {
        super(Text.literal("Arcane Client"));
        this.parent = parent;
        this.config = ArcaneClient.config();
        this.scannerWasEnabled = this.config.enabled;
        this.categories = ModuleCatalog.build(this.config, MinecraftClient.getInstance());
    }

    @Override
    protected void init() {
        // A resize can change the GUI scale, which changes which font variant is sharpest.
        ArcaneFont.invalidate();
        this.uiFont = null;
        this.wrapCache.clear();
        layoutWindows();

        this.searchInput = new TextFieldWidget(font(), searchTextX(), 8, searchWidth(), 11, Text.literal("Search modules"));
        this.searchInput.setDrawsBackground(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setPlaceholder(ArcaneFont.text("Search modules..."));
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

    private void layoutWindows() {
        int total = this.categories.size();
        int columns = Math.clamp((this.width - GAP) / (WINDOW_WIDTH + GAP), 1, total);
        int rowWidth = columns * WINDOW_WIDTH + (columns - 1) * GAP;
        int start = Math.max(2, (this.width - rowWidth) / 2);
        int top = TOP_BAR_HEIGHT + GAP;

        for (int index = 0; index < Math.min(columns, total); index++) {
            GuiCategory category = this.categories.get(index);
            category.moveTo(start + index * (WINDOW_WIDTH + GAP), top);
            category.setOpen(true);
        }

        // Anything that will not fit across the screen is parked as a collapsed title the user can
        // open or drag out, rather than being stacked on top of an already open window.
        int parkedY = this.height - BOTTOM_BAR_HEIGHT - GAP - HEADER_HEIGHT;
        int parkedCount = Math.max(0, total - columns);
        int parkedRows = parkedCount == 0 ? 0 : (parkedCount + columns - 1) / columns;
        this.openWindowBottom = parkedRows == 0
            ? this.height - BOTTOM_BAR_HEIGHT - 4
            : parkedY - (parkedRows - 1) * (HEADER_HEIGHT + 4) - GAP;
        for (int index = columns; index < total; index++) {
            GuiCategory category = this.categories.get(index);
            int slot = index - columns;
            category.moveTo(start + (slot % columns) * (WINDOW_WIDTH + GAP), parkedY - (slot / columns) * (HEADER_HEIGHT + 4));
            category.setOpen(false);
        }
    }

    /** Positions every row of one window and clips tall bodies into a scrollable viewport. */
    private List<Row> layout(GuiCategory category) {
        List<Row> rows = new ArrayList<>();
        int x = category.x();
        int y = category.y();
        rows.add(new Row(RowKind.HEADER, category, null, null, x, y, HEADER_HEIGHT));

        int contentHeight = HEADER_HEIGHT;
        if (category.open()) {
            for (GuiModule module : category.visible()) {
                contentHeight += MODULE_HEIGHT;
                if (!module.expanded() || !module.hasSettings()) continue;
                contentHeight += descriptionHeight(module);
                for (GuiSetting setting : module.settings()) contentHeight += setting.height();
                contentHeight += NEST_PAD;
            }
            contentHeight += BODY_PAD;
        }
        int viewportHeight = category.open()
            ? Math.min(contentHeight, fittedWindowHeight(category, maxWindowHeight(category)))
            : HEADER_HEIGHT;
        category.setLayoutHeights(contentHeight, viewportHeight);

        if (category.open()) {
            int rowY = y + HEADER_HEIGHT - category.scrollOffset();
            for (GuiModule module : category.visible()) {
                rows.add(new Row(RowKind.MODULE, category, module, null, x, rowY, MODULE_HEIGHT));
                rowY += MODULE_HEIGHT;
                if (!module.expanded() || !module.hasSettings()) continue;
                int descriptionHeight = descriptionHeight(module);
                rows.add(new Row(RowKind.DESCRIPTION, category, module, null, x, rowY, descriptionHeight));
                rowY += descriptionHeight;
                for (GuiSetting setting : module.settings()) {
                    rows.add(new Row(RowKind.SETTING, category, module, setting, x, rowY, setting.height()));
                    rowY += setting.height();
                }
                rows.add(new Row(RowKind.NEST_FOOT, category, module, null, x, rowY, NEST_PAD));
                rowY += NEST_PAD;
            }
        }
        return rows;
    }

    private int fittedWindowHeight(GuiCategory category, int maximumHeight) {
        if (maximumHeight <= HEADER_HEIGHT) return HEADER_HEIGHT;
        int available = maximumHeight - HEADER_HEIGHT - BODY_PAD;
        int used = 0;
        for (GuiModule module : category.visible()) {
            if (used + MODULE_HEIGHT > available) break;
            used += MODULE_HEIGHT;
            if (!module.expanded() || !module.hasSettings()) continue;

            int descriptionHeight = descriptionHeight(module);
            if (used + descriptionHeight > available) break;
            used += descriptionHeight;
            boolean settingsFit = true;
            for (GuiSetting setting : module.settings()) {
                if (used + setting.height() > available) {
                    settingsFit = false;
                    break;
                }
                used += setting.height();
            }
            if (!settingsFit || used + NEST_PAD > available) break;
            used += NEST_PAD;
        }
        return HEADER_HEIGHT + used + BODY_PAD;
    }

    private int maxWindowHeight(GuiCategory category) {
        int screenBottom = this.height - BOTTOM_BAR_HEIGHT - 4;
        int bottom = category.y() < this.openWindowBottom ? this.openWindowBottom : screenBottom;
        return Math.max(HEADER_HEIGHT, bottom - category.y());
    }

    private int descriptionHeight(GuiModule module) {
        return 4 + wrap(module.description(), descriptionWidth()).size() * proseHeight() + 3;
    }

    private static int descriptionWidth() {
        return WINDOW_WIDTH - NEST_INSET - 9;
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
        ClickGuiColors theme = theme();
        advanceClock();
        this.hoveredModule = null;
        boolean[] macroDrawn = new boolean[MACRO_COUNT];

        drawBackdrop(graphics, theme);

        for (GuiCategory category : this.categories) {
            layout(category);
            clamp(category);
            List<Row> rows = layout(category);
            drawWindowBody(graphics, category, theme);
            for (Row row : rows) {
                if (row.kind() == RowKind.HEADER) {
                    drawRow(graphics, row, mouseX, mouseY, theme, macroDrawn);
                }
            }
            if (category.open() && category.lastHeight() > HEADER_HEIGHT) {
                // Clipping above the body pad keeps a scrolled window from showing a sliver of the
                // row that follows the last one it can fit.
                graphics.enableScissor(
                    category.x(),
                    category.y() + HEADER_HEIGHT,
                    category.x() + WINDOW_WIDTH,
                    category.y() + category.lastHeight() - BODY_PAD
                );
                drawNestCards(graphics, rows, theme);
                for (Row row : rows) {
                    if (row.kind() != RowKind.HEADER && rowVisible(row)) {
                        drawRow(graphics, row, mouseX, mouseY, theme, macroDrawn);
                    }
                }
                graphics.disableScissor();
                drawScrollBar(graphics, category, theme);
            }
        }

        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            if (!macroDrawn[slot]) {
                hideMacroInput(slot);
            }
        }

        drawTopBar(graphics, mouseX, mouseY, theme);
        drawBottomBar(graphics, theme);
        super.render(graphics, mouseX, mouseY, deltaTicks);
        drawModuleTooltip(graphics, mouseX, mouseY, theme);
    }

    /** Animations run on wall-clock time so they look identical at any frame rate. */
    private void advanceClock() {
        long now = System.nanoTime();
        this.frameDelta = this.lastFrameNanos == 0L ? 0.0f : (now - this.lastFrameNanos) / 1_000_000_000.0f;
        this.lastFrameNanos = now;
    }

    private void drawBackdrop(DrawContext graphics, ClickGuiColors theme) {
        graphics.fill(0, 0, this.width, this.height, theme.backdrop());
        int vignette = Math.min(80, this.height / 4);
        graphics.fillGradient(0, 0, this.width, vignette, 0x4A000000, 0x00000000);
        graphics.fillGradient(0, this.height - vignette, this.width, this.height, 0x00000000, 0x4A000000);
    }

    private boolean rowVisible(Row row) {
        int top = row.category().y() + HEADER_HEIGHT;
        int bottom = row.category().y() + row.category().lastHeight();
        return row.y() < bottom && row.y() + row.height() > top;
    }

    private void drawScrollBar(DrawContext graphics, GuiCategory category, ClickGuiColors theme) {
        if (category.maxScroll() <= 0) return;
        int trackY = category.y() + HEADER_HEIGHT + 3;
        int trackHeight = Math.max(10, category.lastHeight() - HEADER_HEIGHT - 6);
        int visibleBody = category.lastHeight() - HEADER_HEIGHT;
        int contentBody = Math.max(visibleBody, category.contentHeight() - HEADER_HEIGHT);
        int thumbHeight = Math.max(10, trackHeight * visibleBody / contentBody);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackY + (category.maxScroll() == 0 ? 0 : travel * category.scrollOffset() / category.maxScroll());
        // The bar lives in the two-pixel gutter between the row chips and the window border, so a
        // scrollable window never draws over its own content.
        int barX = category.x() + WINDOW_WIDTH - 3;
        RoundedGui.fill(graphics, barX, trackY, 2, trackHeight, 1, theme.outlineSoft());
        RoundedGui.fill(graphics, barX, thumbY, 2, thumbHeight, 1, theme.accent());
    }

    private void drawWindowBody(DrawContext graphics, GuiCategory category, ClickGuiColors theme) {
        int x = category.x();
        int y = category.y();
        int height = category.lastHeight();
        RoundedGui.shadow(graphics, x, y, WINDOW_WIDTH, height, WINDOW_RADIUS, theme.edge(), 3);
        RoundedGui.fill(graphics, x, y, WINDOW_WIDTH, height, WINDOW_RADIUS, theme.window());
        RoundedGui.sheen(graphics, x + WINDOW_RADIUS, y + 1, WINDOW_WIDTH - WINDOW_RADIUS * 2, 9, theme.sheen());
        RoundedGui.outlineOnly(graphics, x, y, WINDOW_WIDTH, height, WINDOW_RADIUS, theme.outlineSoft());
    }

    /** Paints one recessed card behind each open module's description and settings block. */
    private void drawNestCards(DrawContext graphics, List<Row> rows, ClickGuiColors theme) {
        int cardTop = Integer.MIN_VALUE;
        for (Row row : rows) {
            if (row.kind() == RowKind.DESCRIPTION) {
                cardTop = row.y();
            } else if (row.kind() == RowKind.NEST_FOOT && cardTop != Integer.MIN_VALUE) {
                int cardHeight = row.y() + row.height() - cardTop;
                RoundedGui.fill(graphics, row.x() + 3, cardTop, WINDOW_WIDTH - 6, cardHeight, ROW_RADIUS, theme.nest());
                RoundedGui.outlineOnly(graphics, row.x() + 3, cardTop, WINDOW_WIDTH - 6, cardHeight, ROW_RADIUS, theme.outlineSoft());
                RoundedGui.fill(graphics, row.x() + 4, cardTop + 2, 2, cardHeight - 4, 1, theme.accentDim());
                cardTop = Integer.MIN_VALUE;
            }
        }
    }

    private void drawRow(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        switch (row.kind()) {
            case HEADER -> drawHeader(graphics, row, mouseX, mouseY, theme);
            case MODULE -> drawModule(graphics, row, mouseX, mouseY, theme);
            case DESCRIPTION -> drawDescription(graphics, row, theme);
            case SETTING -> drawSetting(graphics, row, mouseX, mouseY, theme, macroDrawn);
            case NEST_FOOT -> {
                // The nest card already covers this row; it exists only to close the block.
            }
        }
    }

    private void drawHeader(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiCategory category = row.category();
        int x = row.x();
        int y = row.y();
        boolean open = category.open();
        float lift = category.headerFraction(row.contains(mouseX, mouseY), this.frameDelta);

        RoundedGui.fill(graphics, x + 2, y + 2, WINDOW_WIDTH - 4, HEADER_HEIGHT - 4, ROW_RADIUS,
            UiColor.blend(theme.header(), theme.hover(), lift * 0.75f));
        RoundedGui.sheen(graphics, x + 5, y + 3, WINDOW_WIDTH - 10, 5, theme.sheen());
        RoundedGui.fill(graphics, x + 6, y + HEADER_HEIGHT - 1, WINDOW_WIDTH - 12, 1, 0,
            open ? UiColor.scaleAlpha(theme.accent(), 0.8f) : theme.outlineSoft());

        int glyphColor = open ? theme.accent() : theme.faint();
        GuiIcons.draw(graphics, GuiIcons.forCategory(category.name()), x + 8, y + (HEADER_HEIGHT - GuiIcons.SIZE) / 2, glyphColor);

        int textY = y + (HEADER_HEIGHT - lineHeight()) / 2;
        int titleColor = open ? theme.accentBright() : UiColor.blend(theme.muted(), theme.text(), lift);
        int badgeRight = x + WINDOW_WIDTH - 19;
        OrderedText title = ArcaneFont.trimmed(font(), category.name(), UiGeometry.labelWidth(x + 19, badgeRight - 4));
        graphics.drawText(font(), title, x + 19, textY, titleColor, false);

        if (category.toggleableCount() > 0) {
            String badge = category.enabledCount() + "/" + category.toggleableCount();
            int badgeColor = category.enabledCount() > 0 ? theme.accentBright() : theme.faint();
            graphics.drawText(font(), ArcaneFont.text(badge), badgeRight - ArcaneFont.width(font(), badge), textY, badgeColor, false);
        }
        drawCollapseGlyph(graphics, x + WINDOW_WIDTH - 11, y + HEADER_HEIGHT / 2, open,
            UiColor.blend(theme.muted(), theme.accentBright(), lift));
    }

    private void drawModule(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        boolean hovered = row.contains(mouseX, mouseY);
        if (hovered) {
            this.hoveredModule = module;
        }

        float on = module.toggleFraction(this.frameDelta);
        float lift = module.hoverFraction(hovered, this.frameDelta);
        float presence = Math.max(on, Math.max(lift * 0.85f, module.expanded() ? 0.35f : 0.0f));
        int calm = UiColor.blend(theme.row(), theme.active(), on);
        int lively = UiColor.blend(theme.hover(), theme.activeHover(), on);
        int chip = UiColor.scaleAlpha(UiColor.blend(calm, lively, lift), presence);
        if (!UiColor.invisible(chip)) {
            RoundedGui.fill(graphics, x + 3, y + 1, WINDOW_WIDTH - 6, MODULE_HEIGHT - 2, ROW_RADIUS, chip);
        }
        if (on > 0.01f) {
            RoundedGui.outlineOnly(graphics, x + 3, y + 1, WINDOW_WIDTH - 6, MODULE_HEIGHT - 2, ROW_RADIUS,
                UiColor.scaleAlpha(theme.accentDim(), on * 0.55f));
            RoundedGui.fill(graphics, x + 4, y + 3, 2, MODULE_HEIGHT - 6, 1, UiColor.scaleAlpha(theme.accent(), on));
        }

        int textY = y + (MODULE_HEIGHT - lineHeight()) / 2;
        int right = x + WINDOW_WIDTH - 7;
        if (module.toggleable()) {
            drawSwitch(graphics, right - SWITCH_WIDTH, y + (MODULE_HEIGHT - SWITCH_HEIGHT) / 2, SWITCH_WIDTH, SWITCH_HEIGHT, on, theme);
            right -= SWITCH_WIDTH + 5;
        }
        if (module.hasSettings()) {
            int caret = UiColor.blend(theme.faint(), theme.accentBright(), Math.max(module.expanded() ? 1.0f : 0.0f, lift));
            if (module.expanded()) {
                drawCaretDown(graphics, right - 5, y + 6, caret);
            } else {
                drawCaretRight(graphics, right - 4, y + 5, caret);
            }
            right -= 9;
        }
        String value = module.valueLabel();
        if (value != null) {
            int valueX = right - ArcaneFont.width(font(), value);
            graphics.drawText(font(), ArcaneFont.text(value), valueX, textY, theme.accentBright(), false);
            right = valueX - 4;
        }
        int labelX = x + 10;
        int labelColor = UiColor.blend(UiColor.blend(theme.muted(), theme.text(), lift), theme.accentBright(), on);
        OrderedText label = ArcaneFont.trimmed(font(), module.name(), UiGeometry.labelWidth(labelX, right - 2));
        graphics.drawText(font(), label, labelX, textY, labelColor, false);
    }

    private void drawDescription(DrawContext graphics, Row row, ClickGuiColors theme) {
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int lineY = row.y() + 4;
        for (String line : wrap(module.description(), descriptionWidth())) {
            graphics.drawText(font(), ArcaneFont.text(line), row.x() + NEST_INSET, lineY,
                UiColor.blend(theme.faint(), theme.muted(), 0.4f), false);
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
        boolean hovered = row.contains(mouseX, mouseY) && !(setting instanceof GuiSetting.Info);
        float lift = setting.hoverFraction(hovered, this.frameDelta);
        int liftChip = UiColor.scaleAlpha(theme.hover(), lift * 0.75f);
        if (!UiColor.invisible(liftChip)) {
            RoundedGui.fill(graphics, x + NEST_INSET - 2, y, WINDOW_WIDTH - NEST_INSET - 4, height, ROW_RADIUS, liftChip);
        }

        int labelX = x + NEST_INSET;
        int right = x + WINDOW_WIDTH - 9;
        int textY = y + (setting instanceof GuiSetting.Slider ? 3 : (height - lineHeight()) / 2);
        int labelColor = UiColor.blend(theme.muted(), theme.text(), lift);
        OrderedText label = ArcaneFont.trimmed(font(), setting.label(), UiGeometry.labelWidth(labelX, settingLabelRight(setting, x, right)));
        graphics.drawText(font(), label, labelX, textY, labelColor, false);

        switch (setting) {
            case GuiSetting.Toggle toggle -> drawSwitch(
                graphics, right - SETTING_SWITCH_WIDTH, y + (height - SETTING_SWITCH_HEIGHT) / 2,
                SETTING_SWITCH_WIDTH, SETTING_SWITCH_HEIGHT, toggle.toggleFraction(this.frameDelta), theme);
            case GuiSetting.ToggleSwatch toggle -> {
                drawSwatch(graphics, swatchX(x), y + (height - 8) / 2, SWATCH_WIDTH, toggle.color(), theme);
                drawSwitch(
                    graphics, right - SETTING_SWITCH_WIDTH, y + (height - SETTING_SWITCH_HEIGHT) / 2,
                    SETTING_SWITCH_WIDTH, SETTING_SWITCH_HEIGHT, toggle.toggleFraction(this.frameDelta), theme);
            }
            case GuiSetting.Slider slider -> {
                String display = slider.display();
                graphics.drawText(font(), ArcaneFont.text(display), right - ArcaneFont.width(font(), display), textY, theme.text(), false);
                int trackX = sliderTrackX(x);
                int trackWidth = sliderTrackWidth();
                int trackY = y + height - 8;
                RoundedGui.fill(graphics, trackX, trackY, trackWidth, 3, 1, theme.track());
                int filled = Math.round(trackWidth * slider.fraction());
                if (filled > 0) {
                    RoundedGui.fill(graphics, trackX, trackY, filled, 3, 1, theme.accent());
                }
                int knobX = trackX + Math.clamp(filled - 2, 0, trackWidth - 5);
                RoundedGui.fill(graphics, knobX, trackY - 1, 5, 5, 2, theme.accentBright());
            }
            case GuiSetting.Swatch swatch -> drawSwatch(graphics, right - 22, y + (height - 8) / 2, 22, swatch.color(), theme);
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
                int fieldX = macroFieldX(x);
                RoundedGui.fill(graphics, fieldX - 3, y + 3, MACRO_FIELD_WIDTH + 6, height - 6, ROW_RADIUS, theme.track());
                RoundedGui.outlineOnly(graphics, fieldX - 3, y + 3, MACRO_FIELD_WIDTH + 6, height - 6, ROW_RADIUS, theme.outlineSoft());
                TextFieldWidget input = this.macroInputs.get(message.slot());
                input.setX(fieldX);
                input.setY(y + 5);
                input.setVisible(true);
                macroDrawn[message.slot()] = true;
                KeyBinding mapping = message.mapping();
                if (mapping != null) {
                    drawKeyPill(graphics, right, y, height, mapping, theme, MACRO_KEY_WIDTH);
                }
            }
        }
    }

    /** Where a setting's own label has to stop so it never runs under that row's control. */
    private int settingLabelRight(GuiSetting setting, int windowX, int right) {
        return switch (setting) {
            case GuiSetting.Toggle ignored -> right - SETTING_SWITCH_WIDTH - 6;
            case GuiSetting.ToggleSwatch ignored -> swatchX(windowX) - 6;
            case GuiSetting.Slider slider -> right - ArcaneFont.width(font(), slider.display()) - 6;
            case GuiSetting.Swatch ignored -> right - 28;
            case GuiSetting.Cycle cycle ->
                right - 18 - ArcaneFont.width(font(), cycle.value()) - ArcaneFont.width(font(), "<");
            case GuiSetting.Bind bind -> right - keyPillWidth(bind.mapping(), BIND_KEY_WIDTH) - 6;
            case GuiSetting.Info info -> right - ArcaneFont.width(font(), info.value()) - 6;
            case GuiSetting.Message ignored -> macroFieldX(windowX) - 5;
        };
    }

    private int keyPillWidth(KeyBinding mapping, int maxKeyWidth) {
        String raw = this.listeningFor == mapping ? "..." : mapping.isUnbound() ? "-" : mapping.getBoundKeyLocalizedText().getString();
        return Math.max(18, font().getWidth(ArcaneFont.trimmed(font(), raw, maxKeyWidth)) + 8);
    }

    private void drawKeyPill(DrawContext graphics, int right, int y, int height, KeyBinding mapping, ClickGuiColors theme, int maxKeyWidth) {
        boolean listening = this.listeningFor == mapping;
        String raw = listening ? "..." : mapping.isUnbound() ? "-" : mapping.getBoundKeyLocalizedText().getString();
        OrderedText key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        int keyWidth = font().getWidth(key);
        int width = Math.max(18, keyWidth + 8);
        int pillHeight = 11;
        int pillY = y + (height - pillHeight) / 2;
        RoundedGui.fill(graphics, right - width, pillY, width, pillHeight, ROW_RADIUS, listening ? theme.accentDim() : theme.track());
        RoundedGui.outlineOnly(graphics, right - width, pillY, width, pillHeight, ROW_RADIUS, listening ? theme.accent() : theme.outlineSoft());
        graphics.drawText(font(), key, right - width + (width - keyWidth) / 2, pillY + (pillHeight - lineHeight()) / 2, listening ? theme.text() : theme.muted(), false);
    }

    private void drawTopBar(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        int barY = 4;
        int barHeight = 18;
        int textY = barY + (barHeight - lineHeight()) / 2;

        int markWidth = ArcaneFont.width(font(), "ARCANE");
        int suffixWidth = ArcaneFont.width(font(), "CLIENT");
        int wordmarkWidth = markWidth + suffixWidth + 28;
        drawPill(graphics, 6, barY, wordmarkWidth, barHeight, theme);
        RoundedGui.fill(graphics, 14, textY, 3, lineHeight(), 1, theme.accent());
        graphics.drawText(font(), ArcaneFont.text("ARCANE"), 22, textY, theme.text(), false);
        graphics.drawText(font(), ArcaneFont.text("CLIENT"), 22 + markWidth + 4, textY, theme.accent(), false);

        int boxX = searchBoxX();
        int boxWidth = searchBoxWidth();
        boolean focused = this.searchInput != null && this.searchInput.isFocused();
        drawPill(graphics, boxX, barY, boxWidth, barHeight, theme);
        if (focused) {
            RoundedGui.outlineOnly(graphics, boxX, barY, boxWidth, barHeight, 7, theme.accent());
        }
        GuiIcons.draw(graphics, GuiIcons.search(), boxX + 5, barY + (barHeight - GuiIcons.SIZE) / 2,
            focused ? theme.accent() : theme.faint());
        if (!this.searchText.isEmpty()) {
            GuiIcons.draw(graphics, GuiIcons.close(), searchClearX(), barY + (barHeight - GuiIcons.SIZE) / 2,
                searchClearContains(mouseX, mouseY) ? theme.accentBright() : theme.faint());
        }

        if (this.width >= 520) {
            updateFpsLabel();
            String status = this.width >= 700
                ? activeModuleCount() + " ACTIVE  ·  " + totalModuleCount() + " MODULES"
                : totalModuleCount() + " MODULES";
            int statusWidth = ArcaneFont.width(font(), status);
            int fpsWidth = ArcaneFont.width(font(), this.fpsLabel);
            int pillWidth = statusWidth + fpsWidth + 28;
            int pillX = this.width - pillWidth - 6;
            drawPill(graphics, pillX, barY, pillWidth, barHeight, theme);
            graphics.drawText(font(), ArcaneFont.text(status), pillX + 10, textY, theme.accentBright(), false);
            graphics.drawText(font(), ArcaneFont.text(this.fpsLabel), pillX + pillWidth - 10 - fpsWidth, textY, theme.muted(), false);
        }
    }

    private void drawBottomBar(DrawContext graphics, ClickGuiColors theme) {
        int y = this.height - BOTTOM_BAR_HEIGHT;
        int barHeight = 16;
        int textY = y + (barHeight - lineHeight()) / 2;
        String hint = this.listeningFor != null
            ? (this.width >= 460 ? "Press a key or mouse button   Esc unbinds" : "Press input   Esc unbinds")
            : (this.width >= 500 ? "LMB toggle   RMB settings   MMB bind   Drag titles" : "LMB toggle   RMB settings");
        int hintWidth = ArcaneFont.width(font(), hint) + 18;
        drawPill(graphics, 6, y, hintWidth, barHeight, theme);
        graphics.drawText(font(), ArcaneFont.text(hint), 15, textY, this.listeningFor != null ? theme.accent() : theme.muted(), false);

        if (!this.query.isEmpty() && this.width >= 360) {
            String results = visibleModuleCount() + " results";
            int resultWidth = ArcaneFont.width(font(), results) + 18;
            int resultX = this.width - resultWidth - 6;
            drawPill(graphics, resultX, y, resultWidth, barHeight, theme);
            graphics.drawText(font(), ArcaneFont.text(results), resultX + 9, textY, theme.accentBright(), false);
        }
    }

    private void drawModuleTooltip(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = this.hoveredModule;
        if (module == null || module.expanded() || this.draggingCategory != null) {
            return;
        }
        List<String> lines = wrap(module.description(), 142);
        int width = ArcaneFont.width(font(), module.name());
        for (String line : lines) {
            width = Math.max(width, ArcaneFont.width(font(), line));
        }
        width += 18;
        int height = 12 + lines.size() * proseHeight() + 8;
        int x = Math.min(mouseX + 12, this.width - width - 4);
        int y = Math.min(mouseY + 12, this.height - height - BOTTOM_BAR_HEIGHT - 4);

        RoundedGui.shadow(graphics, x, y, width, height, WINDOW_RADIUS, theme.edge(), 3);
        RoundedGui.fill(graphics, x, y, width, height, WINDOW_RADIUS, theme.bar());
        RoundedGui.sheen(graphics, x + WINDOW_RADIUS, y + 1, width - WINDOW_RADIUS * 2, 8, theme.sheen());
        RoundedGui.outlineOnly(graphics, x, y, width, height, WINDOW_RADIUS, theme.outlineSoft());
        RoundedGui.fill(graphics, x + 4, y + 5, 2, height - 10, 1, theme.accent());
        graphics.drawText(font(), ArcaneFont.text(module.name()), x + 10, y + 6, theme.accentBright(), false);
        int lineY = y + 8 + lineHeight();
        for (String line : lines) {
            graphics.drawText(font(), ArcaneFont.text(line), x + 10, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------------------------

    /** The shared chrome for every floating bar: shadow, glass body, top sheen, hairline edge. */
    private static void drawPill(DrawContext graphics, int x, int y, int width, int height, ClickGuiColors theme) {
        RoundedGui.shadow(graphics, x, y, width, height, 7, theme.edge(), 2);
        RoundedGui.fill(graphics, x, y, width, height, 7, theme.bar());
        RoundedGui.sheen(graphics, x + 6, y + 1, width - 12, 6, theme.sheen());
        RoundedGui.outlineOnly(graphics, x, y, width, height, 7, theme.outlineSoft());
    }

    private static void drawSwitch(DrawContext graphics, int x, int y, int width, int height, float on, ClickGuiColors theme) {
        int radius = height / 2;
        RoundedGui.fill(graphics, x, y, width, height, radius, UiColor.blend(theme.track(), theme.accent(), on));
        RoundedGui.outlineOnly(graphics, x, y, width, height, radius,
            UiColor.blend(theme.outlineSoft(), UiColor.scaleAlpha(theme.accentBright(), 0.75f), on));
        int knob = height - 2;
        int knobX = x + 1 + Math.round((width - knob - 2) * on);
        RoundedGui.fill(graphics, knobX, y + 1, knob, knob, knob / 2, UiColor.blend(theme.muted(), 0xFFFFFFFF, on));
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

    /** A minus while the window is open and a plus once it is collapsed, centred on {@code cx}. */
    private static void drawCollapseGlyph(DrawContext graphics, int cx, int cy, boolean open, int color) {
        graphics.fill(cx - 3, cy, cx + 4, cy + 1, color);
        if (!open) {
            graphics.fill(cx, cy - 3, cx + 1, cy + 4, color);
        }
    }

    private static void drawSwatch(DrawContext graphics, int x, int y, int width, int color, ClickGuiColors theme) {
        RoundedGui.fill(graphics, x, y, width, 8, 3, color | 0xFF000000);
        RoundedGui.outlineOnly(graphics, x, y, width, 8, 3, theme.outlineSoft());
    }

    // -----------------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int index = this.categories.size() - 1; index >= 0; index--) {
            GuiCategory category = this.categories.get(index);
            if (!category.open() || category.maxScroll() <= 0) continue;
            if (mouseX < category.x() || mouseX >= category.x() + WINDOW_WIDTH) continue;
            if (mouseY < category.y() + HEADER_HEIGHT || mouseY >= category.y() + category.lastHeight()) continue;
            int amount = (int) Math.round(-verticalAmount * MODULE_HEIGHT);
            category.scrollBy(amount == 0 ? (verticalAmount < 0.0 ? MODULE_HEIGHT : -MODULE_HEIGHT) : amount);
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
        if (button == 0 && searchClearContains(mouseX, mouseY)) {
            this.searchInput.setText("");
            setFocused(this.searchInput);
            return true;
        }

        for (int index = this.categories.size() - 1; index >= 0; index--) {
            GuiCategory category = this.categories.get(index);
            for (Row row : layout(category)) {
                if (!rowVisible(row) && row.kind() != RowKind.HEADER) continue;
                if (!row.contains(mouseX, mouseY)) {
                    continue;
                }
                Boolean delegated = handleRowClick(row, click, doubled, mouseX, mouseY, button);
                if (delegated != null) {
                    return delegated;
                }
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
            if (mouseX >= fieldX - 3 && mouseX < fieldX + MACRO_FIELD_WIDTH + 3) {
                return super.mouseClicked(click, doubled);
            }
            if (button == 0 && message.mapping() != null && mouseX >= row.x() + WINDOW_WIDTH - MACRO_KEY_ZONE) {
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
                if (mouseX >= swatchX(row.x()) && mouseX < swatchX(row.x()) + SWATCH_WIDTH) {
                    toggle.cycleColor();
                } else {
                    toggle.toggle();
                }
            }
            case GuiSetting.Slider slider -> {
                if (mouseY >= row.y() + row.height() - 11) {
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
                this.draggingCategory.toggleOpen();
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
        if (input.key() == 256 && this.searchInput != null && this.searchInput.isFocused() && !this.searchText.isEmpty()) {
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
        this.searchText = value;
        this.query = value.trim().toLowerCase(Locale.ROOT);
        for (GuiCategory category : this.categories) {
            category.filter(this.query);
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
        this.fpsLabel = this.client.getCurrentFps() + " FPS";
        this.nextFpsUpdateNanos = now + 250_000_000L;
    }

    // -----------------------------------------------------------------------------------------
    // Geometry helpers
    // -----------------------------------------------------------------------------------------

    private static int sliderTrackX(int windowX) {
        return windowX + NEST_INSET;
    }

    private static int sliderTrackWidth() {
        return WINDOW_WIDTH - NEST_INSET - 9;
    }

    private static int swatchX(int windowX) {
        return windowX + WINDOW_WIDTH - 9 - SETTING_SWITCH_WIDTH - 4 - SWATCH_WIDTH;
    }

    private static int macroFieldX(int windowX) {
        return windowX + NEST_INSET + 10;
    }

    private void hideMacroInput(int slot) {
        TextFieldWidget input = this.macroInputs.get(slot);
        if (!input.visible) {
            return;
        }
        input.setVisible(false);
        input.setFocused(false);
    }

    private void clamp(GuiCategory category) {
        category.clamp(this.width, WINDOW_WIDTH, HEADER_HEIGHT, TOP_BAR_HEIGHT + 4, this.height - BOTTOM_BAR_HEIGHT - 4);
    }

    private int searchWidth() {
        return Math.clamp(this.width - 400, 96, 168);
    }

    private int searchBoxWidth() {
        return searchWidth() + SEARCH_PADDING + 13;
    }

    private int searchBoxX() {
        return (this.width - searchBoxWidth()) / 2;
    }

    private int searchTextX() {
        return searchBoxX() + SEARCH_PADDING;
    }

    private int searchClearX() {
        return searchBoxX() + searchBoxWidth() - 12;
    }

    private boolean searchClearContains(int mouseX, int mouseY) {
        if (this.searchInput == null || this.searchText.isEmpty()) {
            return false;
        }
        int iconX = searchClearX();
        return mouseX >= iconX - 3 && mouseX < iconX + GuiIcons.SIZE + 3 && mouseY >= 4 && mouseY < 22;
    }

    private ClickGuiColors theme() {
        return ClickGuiColors.resolve(this.config);
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
        int height
    ) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + WINDOW_WIDTH && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
