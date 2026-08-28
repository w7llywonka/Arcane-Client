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
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * The Arcane Click GUI: one draggable window per category, a module row per feature, and nested
 * settings that open on right click. Everything on screen is a view over {@link ArcaneConfig}; the
 * scan, render, and input systems are untouched by this class.
 */
@Environment(EnvType.CLIENT)
public final class ArcaneSettingsScreen extends Screen {
    private static final int WINDOW_WIDTH = 168;
    private static final int HEADER_HEIGHT = 20;
    private static final int MODULE_HEIGHT = 17;
    private static final int NEST_INSET = 8;
    private static final int NEST_PAD = 3;
    private static final int BODY_PAD = 3;
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int BOTTOM_BAR_HEIGHT = 20;
    private static final int GAP = 8;
    private static final int MACRO_COUNT = 4;
    private static final int MACRO_FIELD_WIDTH = 84;
    private static final int BIND_KEY_WIDTH = 44;
    private static final int MACRO_KEY_WIDTH = 32;

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
        layoutWindows();

        int searchWidth = searchWidth();
        this.searchInput = new TextFieldWidget(font(), searchX(searchWidth), 9, searchWidth, 13, Text.literal("Search modules"));
        this.searchInput.setDrawsBackground(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setPlaceholder(Text.literal("Search modules..."));
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
            input.setPlaceholder(Text.literal("message"));
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
        for (int index = columns; index < total; index++) {
            GuiCategory category = this.categories.get(index);
            int slot = index - columns;
            category.moveTo(start + (slot % columns) * (WINDOW_WIDTH + GAP), parkedY - (slot / columns) * (HEADER_HEIGHT + 4));
            category.setOpen(false);
        }
    }

    /** Positions every row of one window, top to bottom, and records the window's total height. */
    private List<Row> layout(GuiCategory category) {
        List<Row> rows = new ArrayList<>();
        int x = category.x();
        int y = category.y();
        rows.add(new Row(RowKind.HEADER, category, null, null, x, y, HEADER_HEIGHT));

        int height = HEADER_HEIGHT;
        if (category.open()) {
            int rowY = y + HEADER_HEIGHT;
            for (GuiModule module : category.visible()) {
                rows.add(new Row(RowKind.MODULE, category, module, null, x, rowY, MODULE_HEIGHT));
                rowY += MODULE_HEIGHT;
                if (!module.expanded() || !module.hasSettings()) {
                    continue;
                }
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
            height = rowY - y + BODY_PAD;
        }
        category.setLastHeight(height);
        return rows;
    }

    private int descriptionHeight(GuiModule module) {
        return 5 + wrap(module.description(), WINDOW_WIDTH - NEST_INSET - 16).size() * proseHeight() + 4;
    }

    private List<String> wrap(String text, int maxWidth) {
        return this.wrapCache.computeIfAbsent(new WrapKey(text, maxWidth), key -> wrapLines(key.text(), key.maxWidth()));
    }

    private List<String> wrapLines(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font().getWidth(candidate) > maxWidth) {
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
        ClickGuiTheme theme = theme();
        this.hoveredModule = null;
        boolean[] macroDrawn = new boolean[MACRO_COUNT];

        this.renderInGameBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, theme.backdrop());

        for (GuiCategory category : this.categories) {
            layout(category);
            clamp(category);
            List<Row> rows = layout(category);
            drawWindowBody(graphics, category, theme);
            for (Row row : rows) {
                drawRow(graphics, row, mouseX, mouseY, theme, macroDrawn);
            }
        }

        for (int slot = 0; slot < MACRO_COUNT; slot++) {
            if (!macroDrawn[slot]) {
                hideMacroInput(slot);
            }
        }

        drawTopBar(graphics, theme);
        drawBottomBar(graphics, theme);
        super.render(graphics, mouseX, mouseY, deltaTicks);
        drawModuleTooltip(graphics, mouseX, mouseY, theme);
    }

    private void drawWindowBody(DrawContext graphics, GuiCategory category, ClickGuiTheme theme) {
        int x = category.x();
        int y = category.y();
        int height = category.lastHeight();
        fillRounded(graphics, x + 2, y + 3, WINDOW_WIDTH, height, 0x60000000);
        fillRounded(graphics, x, y, WINDOW_WIDTH, height, theme.window());
        outlineRounded(graphics, x, y, WINDOW_WIDTH, height, theme.outlineSoft());
    }

    private void drawRow(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiTheme theme, boolean[] macroDrawn) {
        switch (row.kind()) {
            case HEADER -> drawHeader(graphics, row, mouseX, mouseY, theme);
            case MODULE -> drawModule(graphics, row, mouseX, mouseY, theme);
            case DESCRIPTION -> drawDescription(graphics, row, theme);
            case SETTING -> drawSetting(graphics, row, mouseX, mouseY, theme, macroDrawn);
            case NEST_FOOT -> {
                graphics.fill(row.x() + 1, row.y(), row.x() + WINDOW_WIDTH - 1, row.y() + row.height(), theme.nest());
                graphics.fill(row.x() + 1, row.y(), row.x() + 3, row.y() + row.height(), theme.accentDim());
            }
        }
    }

    private void drawHeader(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiTheme theme) {
        GuiCategory category = row.category();
        int x = row.x();
        int y = row.y();
        fillRounded(graphics, x + 1, y + 1, WINDOW_WIDTH - 2, HEADER_HEIGHT - 1, theme.header());
        if (row.contains(mouseX, mouseY)) {
            graphics.fill(x + 1, y + 1, x + WINDOW_WIDTH - 1, y + HEADER_HEIGHT, 0x14FFFFFF);
        }
        graphics.fill(x + 1, y + HEADER_HEIGHT - 1, x + WINDOW_WIDTH - 1, y + HEADER_HEIGHT, category.open() ? theme.accentDim() : theme.outline());
        graphics.fill(x + 8, y + 6, x + 11, y + 14, theme.accent());

        int textY = y + (HEADER_HEIGHT - lineHeight()) / 2;
        graphics.drawText(font(), category.name(), x + 16, textY, theme.text(), false);

        if (category.toggleableCount() > 0) {
            String badge = category.enabledCount() + "/" + category.toggleableCount();
            graphics.drawText(font(), badge, x + WINDOW_WIDTH - 22 - font().getWidth(badge), textY, theme.faint(), false);
        }
        if (category.open()) {
            drawCaretDown(graphics, x + WINDOW_WIDTH - 15, y + 9, theme.muted());
        } else {
            drawCaretRight(graphics, x + WINDOW_WIDTH - 14, y + 8, theme.muted());
        }
    }

    private void drawModule(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiTheme theme) {
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        boolean hovered = row.contains(mouseX, mouseY);
        boolean enabled = module.enabled();
        if (hovered) {
            this.hoveredModule = module;
        }

        int background = enabled
            ? (hovered ? theme.activeHover() : theme.active())
            : (hovered ? theme.hover() : theme.row());
        graphics.fill(x + 1, y, x + WINDOW_WIDTH - 1, y + MODULE_HEIGHT, background);
        graphics.fill(x + 1, y + MODULE_HEIGHT - 1, x + WINDOW_WIDTH - 1, y + MODULE_HEIGHT, 0x1A000000);
        if (enabled) {
            graphics.fill(x + 1, y, x + 3, y + MODULE_HEIGHT, theme.accent());
        }

        int textY = y + (MODULE_HEIGHT - lineHeight()) / 2;
        graphics.drawText(font(), module.name(), x + 10, textY, enabled ? theme.text() : theme.muted(), false);

        int right = x + WINDOW_WIDTH - 8;
        if (module.hasSettings()) {
            int caretColor = module.expanded() ? theme.accentBright() : enabled ? theme.text() : theme.faint();
            if (module.expanded()) {
                drawCaretDown(graphics, right - 5, y + 7, caretColor);
            } else {
                drawCaretRight(graphics, right - 4, y + 6, caretColor);
            }
            right -= 12;
        }
        String value = module.valueLabel();
        if (value != null) {
            graphics.drawText(font(), value, right - font().getWidth(value), textY, theme.accentBright(), false);
        }
    }

    private void drawDescription(DrawContext graphics, Row row, ClickGuiTheme theme) {
        GuiModule module = row.module();
        if (module == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        graphics.fill(x + 1, y, x + WINDOW_WIDTH - 1, y + row.height(), theme.nest());
        graphics.fill(x + 1, y, x + 3, y + row.height(), theme.accentDim());
        int lineY = y + 5;
        for (String line : wrap(module.description(), WINDOW_WIDTH - NEST_INSET - 16)) {
            graphics.drawText(font(), line, x + NEST_INSET + 5, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    private void drawSetting(DrawContext graphics, Row row, int mouseX, int mouseY, ClickGuiTheme theme, boolean[] macroDrawn) {
        GuiSetting setting = row.setting();
        if (setting == null) {
            return;
        }
        int x = row.x();
        int y = row.y();
        int height = row.height();
        boolean hovered = row.contains(mouseX, mouseY);

        graphics.fill(x + 1, y, x + WINDOW_WIDTH - 1, y + height, theme.nest());
        graphics.fill(x + 1, y, x + 3, y + height, theme.accentDim());
        if (hovered && !(setting instanceof GuiSetting.Info)) {
            graphics.fill(x + NEST_INSET, y, x + WINDOW_WIDTH - 1, y + height, theme.hover());
        }

        int labelX = x + NEST_INSET + 5;
        int right = x + WINDOW_WIDTH - 8;
        int textY = y + (setting instanceof GuiSetting.Slider ? 3 : (height - lineHeight()) / 2);
        graphics.drawText(font(), setting.label(), labelX, textY, theme.muted(), false);

        switch (setting) {
            case GuiSetting.Toggle toggle -> drawCheckbox(graphics, right - 9, y + (height - 9) / 2, toggle.value(), theme);
            case GuiSetting.ToggleSwatch toggle -> {
                drawSwatch(graphics, right - 33, y + (height - 9) / 2, 14, toggle.color(), theme);
                drawCheckbox(graphics, right - 9, y + (height - 9) / 2, toggle.value(), theme);
            }
            case GuiSetting.Slider slider -> {
                String display = slider.display();
                graphics.drawText(font(), display, right - font().getWidth(display), textY, theme.text(), false);
                int trackX = sliderTrackX(x);
                int trackWidth = sliderTrackWidth();
                int trackY = y + height - 9;
                fillRounded(graphics, trackX, trackY, trackWidth, 3, theme.outline());
                int filled = Math.round(trackWidth * slider.fraction());
                if (filled > 0) {
                    fillRounded(graphics, trackX, trackY, filled, 3, theme.accent());
                }
                fillRounded(graphics, trackX + Math.clamp(filled - 2, 0, trackWidth - 4), trackY - 2, 4, 7, theme.accentBright());
            }
            case GuiSetting.Swatch swatch -> drawSwatch(graphics, right - 22, y + (height - 9) / 2, 22, swatch.color(), theme);
            case GuiSetting.Cycle cycle -> {
                String value = cycle.value();
                int valueWidth = font().getWidth(value);
                graphics.drawText(font(), ">", right - 4, textY, theme.faint(), false);
                graphics.drawText(font(), value, right - 8 - valueWidth, textY, theme.accentBright(), false);
                graphics.drawText(font(), "<", right - 12 - valueWidth - font().getWidth("<"), textY, theme.faint(), false);
            }
            case GuiSetting.Bind bind -> drawKeyPill(graphics, right, y, height, bind.mapping(), theme, BIND_KEY_WIDTH);
            case GuiSetting.Info info -> {
                String value = info.value();
                graphics.drawText(font(), value, right - font().getWidth(value), textY, theme.accentBright(), false);
            }
            case GuiSetting.Message message -> {
                int fieldX = macroFieldX(x);
                fillRounded(graphics, fieldX - 3, y + 3, MACRO_FIELD_WIDTH + 6, 14, theme.window());
                outlineRounded(graphics, fieldX - 3, y + 3, MACRO_FIELD_WIDTH + 6, 14, theme.outline());
                TextFieldWidget input = this.macroInputs.get(message.slot());
                input.setX(fieldX);
                input.setY(y + 6);
                input.setVisible(true);
                macroDrawn[message.slot()] = true;
                KeyBinding mapping = message.mapping();
                if (mapping != null) {
                    drawKeyPill(graphics, right, y, height, mapping, theme, MACRO_KEY_WIDTH);
                }
            }
        }
    }

    private void drawKeyPill(DrawContext graphics, int right, int y, int height, KeyBinding mapping, ClickGuiTheme theme, int maxKeyWidth) {
        boolean listening = this.listeningFor == mapping;
        String key = listening ? "..." : mapping.isUnbound() ? "-" : mapping.getBoundKeyLocalizedText().getString();
        key = font().trimToWidth(key, maxKeyWidth);
        int width = Math.max(20, font().getWidth(key) + 8);
        int pillY = y + (height - 12) / 2;
        fillRounded(graphics, right - width, pillY, width, 12, listening ? theme.accentDim() : theme.window());
        outlineRounded(graphics, right - width, pillY, width, 12, listening ? theme.accent() : theme.outline());
        graphics.drawText(font(), key, right - width + (width - font().getWidth(key)) / 2, pillY + (12 - lineHeight()) / 2 + 1, listening ? theme.text() : theme.muted(), false);
    }

    private void drawTopBar(DrawContext graphics, ClickGuiTheme theme) {
        graphics.fill(0, 0, this.width, TOP_BAR_HEIGHT, theme.bar());
        graphics.fill(0, TOP_BAR_HEIGHT - 1, this.width, TOP_BAR_HEIGHT, theme.outline());

        int textY = (TOP_BAR_HEIGHT - lineHeight()) / 2 - 1;
        fillRounded(graphics, 10, textY - 1, 3, lineHeight() + 3, theme.accent());
        graphics.drawText(font(), "ARCANE", 18, textY, theme.text(), true);
        int clientX = 20 + font().getWidth("ARCANE");
        graphics.drawText(font(), "CLIENT", clientX, textY, theme.accent(), true);

        int searchWidth = searchWidth();
        int searchX = searchX(searchWidth);
        boolean focused = this.searchInput != null && this.searchInput.isFocused();
        fillRounded(graphics, searchX - 6, 6, searchWidth + 12, 19, theme.window());
        outlineRounded(graphics, searchX - 6, 6, searchWidth + 12, 19, focused ? theme.accent() : theme.outline());

        if (this.width >= 480) {
            updateFpsLabel();
            String status = activeModuleCount() + "/" + toggleableModuleCount() + " ACTIVE";
            int right = this.width - 10;
            graphics.drawText(font(), this.fpsLabel, right - font().getWidth(this.fpsLabel), textY, theme.muted(), false);
            right -= font().getWidth(this.fpsLabel) + 12;
            graphics.drawText(font(), status, right - font().getWidth(status), textY, theme.accentBright(), false);
        }
    }

    private void drawBottomBar(DrawContext graphics, ClickGuiTheme theme) {
        int y = this.height - BOTTOM_BAR_HEIGHT;
        graphics.fill(0, y, this.width, this.height, theme.bar());
        graphics.fill(0, y, this.width, y + 1, theme.outline());

        int textY = y + (BOTTOM_BAR_HEIGHT - lineHeight()) / 2;
        String hint = this.listeningFor != null
            ? "Press a key or mouse button   Esc unbinds"
            : "LMB toggle   RMB settings   MMB bind   Drag a title to arrange";
        graphics.drawText(font(), hint, 10, textY, this.listeningFor != null ? theme.accent() : theme.muted(), false);

        if (!this.query.isEmpty()) {
            String results = visibleModuleCount() + " results";
            graphics.drawText(font(), results, this.width - 10 - font().getWidth(results), textY, theme.accentBright(), false);
        }
    }

    private void drawModuleTooltip(DrawContext graphics, int mouseX, int mouseY, ClickGuiTheme theme) {
        GuiModule module = this.hoveredModule;
        if (module == null || module.expanded() || this.draggingCategory != null) {
            return;
        }
        List<String> lines = wrap(module.description(), 150);
        int width = font().getWidth(module.name());
        for (String line : lines) {
            width = Math.max(width, font().getWidth(line));
        }
        width += 14;
        int height = 13 + lines.size() * proseHeight() + 8;
        int x = Math.min(mouseX + 12, this.width - width - 4);
        int y = Math.min(mouseY + 12, this.height - height - BOTTOM_BAR_HEIGHT - 4);

        fillRounded(graphics, x + 2, y + 2, width, height, 0x60000000);
        fillRounded(graphics, x, y, width, height, theme.bar());
        outlineRounded(graphics, x, y, width, height, theme.outline());
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, theme.accent());
        graphics.drawText(font(), module.name(), x + 8, y + 6, theme.text(), false);
        int lineY = y + 9 + lineHeight();
        for (String line : lines) {
            graphics.drawText(font(), line, x + 8, lineY, theme.faint(), false);
            lineY += proseHeight();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------------------------

    private static void fillRounded(DrawContext graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private static void outlineRounded(DrawContext graphics, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            return;
        }
        graphics.fill(x + 1, y, x + width - 1, y + 1, color);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
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

    private static void drawCheckbox(DrawContext graphics, int x, int y, boolean checked, ClickGuiTheme theme) {
        fillRounded(graphics, x, y, 9, 9, checked ? theme.accent() : theme.window());
        outlineRounded(graphics, x, y, 9, 9, checked ? theme.accentBright() : theme.outline());
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

    private static void drawSwatch(DrawContext graphics, int x, int y, int width, int color, ClickGuiTheme theme) {
        fillRounded(graphics, x, y, width, 9, color | 0xFF000000);
        outlineRounded(graphics, x, y, width, 9, theme.outline());
    }

    // -----------------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------------

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

        for (int index = this.categories.size() - 1; index >= 0; index--) {
            GuiCategory category = this.categories.get(index);
            for (Row row : layout(category)) {
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
            if (button == 0 && message.mapping() != null && mouseX >= row.x() + WINDOW_WIDTH - 48) {
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
                if (mouseX >= row.x() + WINDOW_WIDTH - 41 && mouseX < row.x() + WINDOW_WIDTH - 27) {
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

    private int toggleableModuleCount() {
        int count = 0;
        for (GuiCategory category : this.categories) {
            for (GuiModule module : category.modules()) {
                if (module.toggleable()) {
                    count++;
                }
            }
        }
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
        return windowX + NEST_INSET + 5;
    }

    private static int sliderTrackWidth() {
        return WINDOW_WIDTH - NEST_INSET - 18;
    }

    private static int macroFieldX(int windowX) {
        return windowX + NEST_INSET + 17;
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
        return Math.clamp(this.width - 420, 110, 200);
    }

    private int searchX(int searchWidth) {
        return (this.width - searchWidth) / 2;
    }

    private ClickGuiTheme theme() {
        return ClickGuiTheme.fromConfig(this.config.uiTheme);
    }

    private TextRenderer font() {
        if (this.uiFont == null) {
            this.uiFont = ArcaneFont.renderer(this.client == null ? MinecraftClient.getInstance() : this.client);
        }
        return this.uiFont;
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
