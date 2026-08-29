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
 * Arcane's Click GUI: one draggable panel split into a category rail, a dense module list and an
 * inspector for the selected module's settings.
 */
@Environment(EnvType.CLIENT)
public final class ArcaneSettingsScreen extends Screen {
    private static final int PANEL_RADIUS = 12;
    private static final int PANEL_MIN_WIDTH = 348;
    private static final int PANEL_MAX_WIDTH = 572;
    private static final int PANEL_MIN_HEIGHT = 208;
    private static final int PANEL_MAX_HEIGHT = 348;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 21;
    private static final int RAIL_WIDTH = 112;
    private static final int INSPECTOR_WIDTH = 208;
    private static final int MIN_LIST_WIDTH = 150;
    private static final int LIST_LABEL_HEIGHT = 19;
    private static final int ROW_HEIGHT = 24;
    private static final int RAIL_ROW_HEIGHT = 24;
    private static final int PAD = 10;
    private static final int TOGGLE_WIDTH = 24;
    private static final int TOGGLE_HEIGHT = 11;
    private static final int DOTS_ZONE = 18;
    private static final int MACRO_COUNT = 4;
    private static final int MACRO_FIELD_WIDTH = 64;
    private static final int BIND_KEY_WIDTH = 44;
    private static final int SETTING_COMPACT_HEIGHT = 22;
    private static final int SETTING_SLIDER_HEIGHT = 32;
    private static final int SETTING_MESSAGE_HEIGHT = 34;
    private static final int SETTING_GAP = 4;

    private final Screen parent;
    private final ArcaneConfig config;
    private final List<GuiCategory> categories;
    private final List<TextFieldWidget> macroInputs = new ArrayList<>();
    private final List<HitRegion> hitRegions = new ArrayList<>();
    private final Map<WrapKey, List<String>> wrapCache = new HashMap<>();
    private final boolean scannerWasEnabled;

    private TextRenderer uiFont;
    private TextFieldWidget searchInput;
    private String searchText = "";
    private String query = "";
    private String fpsLabel = "";
    private long nextFpsUpdateNanos;
    private int panelX, panelY, panelWidth, panelHeight;
    private int listX, listY, listWidth, listHeight;
    private int inspectorX, inspectorY, inspectorWidth, inspectorHeight;
    private int listScroll, listMaxScroll, detailScroll, detailMaxScroll;
    private int panelOffsetX, panelOffsetY;
    private GuiCategory selectedCategory;
    private GuiModule selectedModule;
    private boolean settingsOpen;
    private boolean inspectorOverlay;
    private boolean dragging;
    private int dragStartMouseX, dragStartMouseY, dragStartOffsetX, dragStartOffsetY;
    private GuiSetting.@Nullable Slider draggingSlider;
    private int sliderTrackX;
    private int sliderTrackWidth;
    private @Nullable KeyBinding listeningFor;

    public ArcaneSettingsScreen(Screen parent) {
        super(Text.literal("Arcane Client"));
        this.parent = parent;
        this.config = ArcaneClient.config();
        this.scannerWasEnabled = this.config.enabled;
        this.categories = ModuleCatalog.build(this.config, MinecraftClient.getInstance());
        this.selectedCategory = this.categories.getFirst();
        this.selectedModule = this.selectedCategory.modules().getFirst();
    }

    @Override
    protected void init() {
        ArcaneFont.invalidate();
        this.uiFont = null;
        this.wrapCache.clear();
        layoutPanel();
        this.searchInput = new TextFieldWidget(font(), 0, 0, 80, 12, Text.literal("Search modules"));
        this.searchInput.setDrawsBackground(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setPlaceholder(ArcaneFont.text("Search"));
        styleField(this.searchInput);
        this.searchInput.setText(this.searchText);
        this.searchInput.setChangedListener(this::updateFilter);
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
        updateFilter(this.searchText);
        ensureSelection();
    }

    private void layoutPanel() {
        this.panelWidth = Math.clamp(this.width - 48, PANEL_MIN_WIDTH, PANEL_MAX_WIDTH);
        this.panelWidth = Math.min(this.panelWidth, Math.max(220, this.width - 8));
        this.panelHeight = Math.clamp(this.height - 64, PANEL_MIN_HEIGHT, PANEL_MAX_HEIGHT);
        this.panelHeight = Math.min(this.panelHeight, Math.max(150, this.height - 8));

        int baseX = (this.width - this.panelWidth) / 2;
        int baseY = (this.height - this.panelHeight) / 2;
        this.panelX = Math.clamp(baseX + this.panelOffsetX, 4, Math.max(4, this.width - this.panelWidth - 4));
        this.panelY = Math.clamp(baseY + this.panelOffsetY, 4, Math.max(4, this.height - this.panelHeight - 4));
        this.panelOffsetX = this.panelX - baseX;
        this.panelOffsetY = this.panelY - baseY;

        int bodyY = this.panelY + HEADER_HEIGHT;
        int bodyHeight = this.panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT;
        int columnWidth = this.panelWidth - RAIL_WIDTH;
        boolean roomForBoth = columnWidth - INSPECTOR_WIDTH >= MIN_LIST_WIDTH;
        boolean showInspector = this.selectedModule != null && (roomForBoth || this.settingsOpen);
        this.inspectorOverlay = showInspector && !roomForBoth;
        this.inspectorWidth = !showInspector ? 0 : this.inspectorOverlay ? columnWidth : INSPECTOR_WIDTH;

        this.listX = this.panelX + RAIL_WIDTH;
        this.listY = bodyY + LIST_LABEL_HEIGHT;
        this.listWidth = columnWidth - this.inspectorWidth;
        this.listHeight = bodyHeight - LIST_LABEL_HEIGHT;

        this.inspectorX = this.inspectorOverlay ? this.listX : this.panelX + this.panelWidth - this.inspectorWidth;
        this.inspectorY = bodyY;
        this.inspectorHeight = bodyHeight;
    }

    @Override
    public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        if (this.client == null || this.client.world == null) {
            super.renderBackground(graphics, mouseX, mouseY, deltaTicks);
            return;
        }
        applyBlur(graphics);
        graphics.fill(0, 0, this.width, this.height, theme().backdrop());
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        layoutPanel();
        ClickGuiColors theme = theme();
        this.hitRegions.clear();
        boolean[] macroDrawn = new boolean[MACRO_COUNT];

        drawPanelShell(graphics, theme);
        drawHeader(graphics, mouseX, mouseY, theme);
        drawRail(graphics, mouseX, mouseY, theme);
        if (this.listWidth >= 60) drawModuleList(graphics, mouseX, mouseY, theme);
        if (this.inspectorWidth > 0) drawInspector(graphics, mouseX, mouseY, theme, macroDrawn);
        drawFooter(graphics, theme);
        for (int slot = 0; slot < MACRO_COUNT; slot++) if (!macroDrawn[slot]) hideMacroInput(slot);
        super.render(graphics, mouseX, mouseY, deltaTicks);
    }

    private void drawPanelShell(DrawContext graphics, ClickGuiColors theme) {
        RoundedGui.fill(graphics, this.panelX + 2, this.panelY + 5, this.panelWidth, this.panelHeight, PANEL_RADIUS, 0x5C000000);
        RoundedGui.fill(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL_RADIUS, theme.window());
        RoundedGui.outlineOnly(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL_RADIUS, theme.outline());

        int bodyY = this.panelY + HEADER_HEIGHT;
        int bodyBottom = this.panelY + this.panelHeight - FOOTER_HEIGHT;
        graphics.fill(this.panelX + 1, bodyY - 1, this.panelX + this.panelWidth - 1, bodyY, theme.outlineSoft());
        graphics.fill(this.panelX + 1, bodyBottom, this.panelX + this.panelWidth - 1, bodyBottom + 1, theme.outlineSoft());
        RoundedGui.fill(graphics, this.panelX + 1, bodyY, RAIL_WIDTH - 1, bodyBottom - bodyY, 0, theme.nest());
        graphics.fill(this.panelX + RAIL_WIDTH, bodyY, this.panelX + RAIL_WIDTH + 1, bodyBottom, theme.outlineSoft());
        if (this.inspectorWidth > 0 && !this.inspectorOverlay) {
            graphics.fill(this.inspectorX - 1, bodyY, this.inspectorX, bodyBottom, theme.outlineSoft());
            RoundedGui.fill(graphics, this.inspectorX, bodyY, this.inspectorWidth - 1, bodyBottom - bodyY, 0, theme.nest());
        }
    }

    private void drawHeader(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        this.hitRegions.add(HitRegion.drag(this.panelX, this.panelY, this.panelWidth, HEADER_HEIGHT));
        int brandX = this.panelX + 11;
        int brandY = this.panelY + 9;
        RoundedGui.gradient(graphics, brandX, brandY, 22, 22, 7, theme.accent(), theme.accentAlt(), false);
        int glyphX = brandX + (22 - ArcaneFont.width(font(), "A")) / 2;
        graphics.drawText(font(), ArcaneFont.text("A"), glyphX, brandY + 7, opaque(theme.bar()), false);
        int textY = this.panelY + (HEADER_HEIGHT - lineHeight()) / 2;
        graphics.drawText(font(), ArcaneFont.text("ARCANE"), brandX + 30, textY, theme.text(), false);

        int right = this.panelX + this.panelWidth - 11;
        boolean closeHovered = contains(right - 16, this.panelY + 12, 16, 16, mouseX, mouseY);
        RoundedGui.fill(graphics, right - 16, this.panelY + 12, 16, 16, 6, closeHovered ? theme.hover() : theme.header());
        graphics.drawText(font(), ArcaneFont.text("\u00d7"), right - 16 + (16 - ArcaneFont.width(font(), "\u00d7")) / 2, this.panelY + 16, closeHovered ? theme.text() : theme.faint(), false);
        this.hitRegions.add(HitRegion.close(right - 16, this.panelY + 12, 16, 16));

        int searchWidth = Math.clamp(this.panelWidth / 3, 92, 148);
        int searchX = right - 22 - searchWidth;
        int searchY = this.panelY + 11;
        if (searchX < brandX + 30 + ArcaneFont.width(font(), "ARCANE") + 14) {
            this.searchInput.setVisible(false);
            if (this.searchInput.isFocused()) this.searchInput.setFocused(false);
            return;
        }
        boolean focused = this.searchInput.isFocused();
        RoundedGui.fill(graphics, searchX, searchY, searchWidth, 18, 7, focused ? theme.hover() : theme.header());
        if (focused) RoundedGui.outlineOnly(graphics, searchX, searchY, searchWidth, 18, 7, theme.accent());
        graphics.drawText(font(), ArcaneFont.text("/"), searchX + 8, searchY + 5, focused ? theme.accentBright() : theme.faint(), false);
        this.searchInput.setVisible(true);
        this.searchInput.setX(searchX + 16);
        this.searchInput.setY(searchY + 5);
        this.searchInput.setWidth(searchWidth - 22);
        this.hitRegions.add(HitRegion.passthrough(searchX, searchY, searchWidth, 18));
    }

    private void drawRail(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        int rowY = this.panelY + HEADER_HEIGHT + 7;
        int rowX = this.panelX + 6;
        int rowWidth = RAIL_WIDTH - 12;
        int bodyBottom = this.panelY + this.panelHeight - FOOTER_HEIGHT;
        for (GuiCategory category : this.categories) {
            if (rowY + RAIL_ROW_HEIGHT > bodyBottom) break;
            boolean selected = category == this.selectedCategory && this.query.isEmpty();
            boolean hovered = contains(rowX, rowY, rowWidth, RAIL_ROW_HEIGHT, mouseX, mouseY);
            if (selected) RoundedGui.fill(graphics, rowX, rowY, rowWidth, RAIL_ROW_HEIGHT, 7, theme.active());
            else if (hovered) RoundedGui.fill(graphics, rowX, rowY, rowWidth, RAIL_ROW_HEIGHT, 7, theme.hover());
            if (selected) RoundedGui.gradient(graphics, rowX + 3, rowY + 5, 3, RAIL_ROW_HEIGHT - 10, 1, theme.accent(), theme.accentAlt(), false);

            int labelColor = selected ? theme.text() : hovered ? theme.muted() : theme.faint();
            int labelX = rowX + 11;
            int enabled = category.enabledCount();
            int labelLimit = rowWidth - 16 - (enabled > 0 ? 14 : 0);
            graphics.drawText(font(), ArcaneFont.trimmed(font(), categoryLabel(category.name()), labelLimit), labelX, rowY + (RAIL_ROW_HEIGHT - lineHeight()) / 2, labelColor, false);
            if (enabled > 0) {
                String count = Integer.toString(enabled);
                graphics.drawText(font(), ArcaneFont.text(count), rowX + rowWidth - 6 - ArcaneFont.width(font(), count), rowY + (RAIL_ROW_HEIGHT - lineHeight()) / 2, selected ? theme.accentBright() : theme.accentDim(), false);
            }
            this.hitRegions.add(HitRegion.category(category, rowX, rowY, rowWidth, RAIL_ROW_HEIGHT));
            rowY += RAIL_ROW_HEIGHT + 2;
        }
    }

    private void drawModuleList(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        List<ModuleEntry> entries = visibleEntries();
        String label = this.query.isEmpty()
            ? categoryLabel(this.selectedCategory.name()).toUpperCase(Locale.ROOT)
            : "SEARCH";
        String count = this.query.isEmpty()
            ? entries.size() + " MODULES"
            : entries.size() + (entries.size() == 1 ? " RESULT" : " RESULTS");
        int labelY = this.panelY + HEADER_HEIGHT + (LIST_LABEL_HEIGHT - lineHeight()) / 2;
        graphics.drawText(font(), ArcaneFont.trimmed(font(), label, this.listWidth - 70), this.listX + PAD, labelY, theme.accentBright(), false);
        graphics.drawText(font(), ArcaneFont.text(count), this.listX + this.listWidth - PAD - ArcaneFont.width(font(), count), labelY, theme.faint(), false);

        int rowX = this.listX + PAD;
        int rowWidth = this.listWidth - PAD * 2;
        int contentHeight = Math.max(0, entries.size() * (ROW_HEIGHT + 2) - 2);
        this.listMaxScroll = Math.max(0, contentHeight - this.listHeight + 6);
        this.listScroll = Math.clamp(this.listScroll, 0, this.listMaxScroll);

        graphics.enableScissor(this.listX, this.listY, this.listX + this.listWidth, this.listY + this.listHeight);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = this.listY + 2 + index * (ROW_HEIGHT + 2) - this.listScroll;
            if (rowY + ROW_HEIGHT < this.listY || rowY > this.listY + this.listHeight) continue;
            drawModuleRow(graphics, entries.get(index), rowX, rowY, rowWidth, mouseX, mouseY, theme);
        }
        graphics.disableScissor();
        if (entries.isEmpty()) {
            graphics.drawCenteredTextWithShadow(font(), ArcaneFont.text("No matching modules"), this.listX + this.listWidth / 2, this.listY + this.listHeight / 2 - 4, theme.faint());
        }
        drawScrollBar(graphics, this.listX + this.listWidth - 4, this.listY + 2, this.listHeight - 6, this.listScroll, this.listMaxScroll, contentHeight, theme);
    }

    private void drawModuleRow(DrawContext graphics, ModuleEntry entry, int x, int y, int width, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = entry.module();
        boolean inspected = module == this.selectedModule && this.inspectorWidth > 0;
        boolean hovered = contains(x, y, width, ROW_HEIGHT, mouseX, mouseY) && withinList(y);
        boolean enabled = module.enabled();
        if (inspected) RoundedGui.fill(graphics, x, y, width, ROW_HEIGHT, 7, theme.active());
        else if (hovered) RoundedGui.fill(graphics, x, y, width, ROW_HEIGHT, 7, theme.hover());
        else if (enabled) RoundedGui.fill(graphics, x, y, width, ROW_HEIGHT, 7, theme.row());
        if (enabled) RoundedGui.gradient(graphics, x + 4, y + 6, 3, ROW_HEIGHT - 12, 1, theme.accent(), theme.accentAlt(), false);

        boolean hasSettings = module.hasSettings();
        int right = x + width;
        int dotsX = right - DOTS_ZONE;
        int controlRight = hasSettings ? dotsX : right - 6;
        int textY = y + (ROW_HEIGHT - lineHeight()) / 2;

        int nameLimit = controlRight - (x + 13) - (module.toggleable() ? TOGGLE_WIDTH + 8 : 78);
        int nameColor = inspected ? theme.accentBright() : enabled ? theme.text() : theme.muted();
        graphics.drawText(font(), ArcaneFont.trimmed(font(), module.name(), Math.max(24, nameLimit)), x + 13, textY, nameColor, false);

        if (module.toggleable()) {
            int toggleX = controlRight - TOGGLE_WIDTH - 4;
            int toggleY = y + (ROW_HEIGHT - TOGGLE_HEIGHT) / 2;
            drawSwitch(graphics, toggleX, toggleY, enabled, theme, false);
            if (withinList(y)) this.hitRegions.add(HitRegion.moduleToggle(module, toggleX - 3, y, TOGGLE_WIDTH + 6, ROW_HEIGHT));
        } else if (module.valueLabel() != null) {
            OrderedText value = ArcaneFont.trimmed(font(), module.valueLabel(), 72);
            graphics.drawText(font(), value, controlRight - 4 - font().getWidth(value), textY, theme.accentBright(), false);
        }

        if (withinList(y)) this.hitRegions.add(HitRegion.module(entry.category(), module, x, y, width, ROW_HEIGHT));
        if (hasSettings) {
            boolean dotsHovered = contains(dotsX, y, DOTS_ZONE, ROW_HEIGHT, mouseX, mouseY) && withinList(y);
            int dotColor = inspected ? theme.accentBright() : dotsHovered ? theme.text() : theme.faint();
            for (int dot = 0; dot < 3; dot++) {
                RoundedGui.fill(graphics, dotsX + 4 + dot * 4, y + ROW_HEIGHT / 2 - 1, 2, 2, 1, dotColor);
            }
            if (withinList(y)) this.hitRegions.add(HitRegion.moduleSettings(entry.category(), module, dotsX, y, DOTS_ZONE, ROW_HEIGHT));
        }
    }

    private void drawInspector(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        GuiModule module = this.selectedModule;
        if (module == null) {
            this.settingsOpen = false;
            this.detailMaxScroll = 0;
            return;
        }
        if (this.inspectorOverlay) {
            RoundedGui.fill(graphics, this.inspectorX, this.inspectorY, this.inspectorWidth, this.inspectorHeight, 0, theme.nest());
        }
        int x = this.inspectorX;
        int right = x + this.inspectorWidth - 10;
        graphics.drawText(font(), ArcaneFont.trimmed(font(), categoryLabel(this.selectedCategory.name()).toUpperCase(Locale.ROOT), this.inspectorWidth - 44), x + 10, this.inspectorY + 8, theme.accentBright(), false);

        if (this.inspectorOverlay) {
            boolean closeHovered = contains(right - 14, this.inspectorY + 5, 14, 14, mouseX, mouseY);
            graphics.drawText(font(), ArcaneFont.text("\u00d7"), right - 14 + (14 - ArcaneFont.width(font(), "\u00d7")) / 2, this.inspectorY + 8, closeHovered ? theme.text() : theme.faint(), false);
            this.hitRegions.add(HitRegion.close(right - 14, this.inspectorY + 5, 14, 14));
        }

        int nameY = this.inspectorY + 21;
        int nameLimit = this.inspectorWidth - 20 - (module.toggleable() ? TOGGLE_WIDTH + 10 : 0);
        graphics.drawText(font(), ArcaneFont.trimmed(font(), module.name(), Math.max(30, nameLimit)), x + 10, nameY, theme.text(), false);
        if (module.toggleable()) {
            int toggleX = right - TOGGLE_WIDTH;
            drawSwitch(graphics, toggleX, nameY - 1, module.enabled(), theme, true);
            this.hitRegions.add(HitRegion.moduleToggle(module, toggleX - 4, nameY - 5, TOGGLE_WIDTH + 8, 19));
        }

        int cursor = nameY + lineHeight() + 5;
        List<String> description = wrap(module.description(), this.inspectorWidth - 20);
        for (int line = 0; line < Math.min(2, description.size()); line++) {
            graphics.drawText(font(), ArcaneFont.text(description.get(line)), x + 10, cursor, theme.faint(), false);
            cursor += lineHeight() + 1;
        }
        cursor += 4;
        graphics.fill(x + 10, cursor, x + this.inspectorWidth - 10, cursor + 1, theme.outlineSoft());
        cursor += 6;

        int viewportX = x + 8;
        int viewportY = cursor;
        int viewportWidth = this.inspectorWidth - 16;
        int viewportHeight = this.inspectorY + this.inspectorHeight - viewportY - 6;
        if (viewportHeight <= 0) return;
        if (!module.hasSettings()) {
            RoundedGui.fill(graphics, viewportX, viewportY, viewportWidth, 26, 7, theme.header());
            graphics.drawText(font(), ArcaneFont.trimmed(font(), "No extra controls", viewportWidth - 16), viewportX + 9, viewportY + (26 - lineHeight()) / 2, theme.faint(), false);
            this.detailMaxScroll = 0;
            return;
        }

        int contentHeight = 0;
        for (GuiSetting setting : module.settings()) contentHeight += displayHeight(setting) + SETTING_GAP;
        contentHeight = Math.max(0, contentHeight - SETTING_GAP);
        this.detailMaxScroll = Math.max(0, contentHeight - viewportHeight);
        this.detailScroll = Math.clamp(this.detailScroll, 0, this.detailMaxScroll);

        graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        int settingY = viewportY - this.detailScroll;
        for (GuiSetting setting : module.settings()) {
            int height = displayHeight(setting);
            if (settingY + height >= viewportY && settingY <= viewportY + viewportHeight) {
                drawSetting(graphics, setting, viewportX, settingY, viewportWidth, height, mouseX, mouseY, theme, macroDrawn, viewportY, viewportHeight);
            }
            settingY += height + SETTING_GAP;
        }
        graphics.disableScissor();
        drawScrollBar(graphics, x + this.inspectorWidth - 5, viewportY, viewportHeight, this.detailScroll, this.detailMaxScroll, contentHeight, theme);
    }

    private void drawSetting(
        DrawContext graphics,
        GuiSetting setting,
        int x,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY,
        ClickGuiColors theme,
        boolean[] macroDrawn,
        int viewportY,
        int viewportHeight
    ) {
        boolean interactive = !(setting instanceof GuiSetting.Info);
        boolean visible = y >= viewportY && y + height <= viewportY + viewportHeight;
        boolean hovered = interactive && visible && contains(x, y, width, height, mouseX, mouseY);
        RoundedGui.fill(graphics, x, y, width, height, 7, hovered ? theme.hover() : theme.header());
        int right = x + width - 9;
        int labelY = setting instanceof GuiSetting.Slider || setting instanceof GuiSetting.Message
            ? y + 6
            : y + (height - lineHeight()) / 2;
        int labelLimit = width - 18 - settingControlWidth(setting);
        graphics.drawText(font(), ArcaneFont.trimmed(font(), setting.label(), Math.max(24, labelLimit)), x + 9, labelY, theme.muted(), false);

        switch (setting) {
            case GuiSetting.Toggle toggle -> drawSwitch(graphics, right - TOGGLE_WIDTH, y + (height - TOGGLE_HEIGHT) / 2, toggle.value(), theme, false);
            case GuiSetting.ToggleSwatch toggle -> {
                drawSwatch(graphics, right - TOGGLE_WIDTH - 21, y + (height - 10) / 2, 15, toggle.color(), theme);
                drawSwitch(graphics, right - TOGGLE_WIDTH, y + (height - TOGGLE_HEIGHT) / 2, toggle.value(), theme, false);
            }
            case GuiSetting.Slider slider -> {
                String display = slider.display();
                graphics.drawText(font(), ArcaneFont.text(display), right - ArcaneFont.width(font(), display), labelY, theme.accentBright(), false);
                int trackX = x + 9;
                int trackWidth = width - 18;
                int trackY = y + height - 11;
                RoundedGui.fill(graphics, trackX, trackY, trackWidth, 3, 1, theme.outlineSoft());
                int filled = Math.round(trackWidth * slider.fraction());
                if (filled > 1) RoundedGui.gradient(graphics, trackX, trackY, filled, 3, 1, theme.accent(), theme.accentAlt(), true);
                int knobX = trackX + Math.clamp(filled - 3, 0, Math.max(0, trackWidth - 6));
                RoundedGui.fill(graphics, knobX, trackY - 2, 6, 7, 3, theme.accentBright());
            }
            case GuiSetting.Swatch swatch -> drawSwatch(graphics, right - 26, y + (height - 10) / 2, 26, swatch.color(), theme);
            case GuiSetting.Cycle cycle -> drawValuePill(graphics, right, y, height, cycle.value(), theme);
            case GuiSetting.Bind bind -> drawKeyPill(graphics, right, y, height, bind.mapping(), theme, BIND_KEY_WIDTH);
            case GuiSetting.Info info -> {
                String value = info.value();
                graphics.drawText(font(), ArcaneFont.trimmed(font(), value, width / 2), right - Math.min(ArcaneFont.width(font(), value), width / 2), labelY, theme.accentBright(), false);
            }
            case GuiSetting.Message message -> {
                if (message.mapping() != null) drawKeyPill(graphics, right, y + 1, lineHeight() + 8, message.mapping(), theme, 26);
                int fieldY = y + height - 17;
                RoundedGui.fill(graphics, x + 8, fieldY, width - 16, 14, 5, theme.window());
                TextFieldWidget input = this.macroInputs.get(message.slot());
                input.setX(x + 12);
                input.setY(fieldY + 3);
                input.setWidth(width - 24);
                input.setVisible(visible);
                macroDrawn[message.slot()] = visible;
            }
        }
        if (visible) this.hitRegions.add(HitRegion.setting(setting, x, y, width, height));
    }

    private void drawFooter(DrawContext graphics, ClickGuiColors theme) {
        int footerY = this.panelY + this.panelHeight - FOOTER_HEIGHT;
        int textY = footerY + (FOOTER_HEIGHT - lineHeight()) / 2;
        updateFpsLabel();
        String status = this.fpsLabel + "  ·  " + activeModuleCount() + " ACTIVE";
        graphics.drawText(font(), ArcaneFont.text(status), this.panelX + this.panelWidth - 11 - ArcaneFont.width(font(), status), textY, theme.faint(), false);
        String hint = "Click toggles  ·  ··· opens settings  ·  Middle-click binds";
        int hintLimit = this.panelWidth - 32 - ArcaneFont.width(font(), status);
        if (hintLimit > 60) graphics.drawText(font(), ArcaneFont.trimmed(font(), hint, hintLimit), this.panelX + 11, textY, theme.faint(), false);
    }

    private void drawScrollBar(DrawContext graphics, int x, int y, int height, int offset, int maxOffset, int contentHeight, ClickGuiColors theme) {
        if (maxOffset <= 0 || contentHeight <= 0 || height <= 0) return;
        int thumbHeight = Math.max(16, height * height / Math.max(height, contentHeight));
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + travel * offset / maxOffset;
        RoundedGui.fill(graphics, x, y, 2, height, 1, theme.outlineSoft());
        RoundedGui.gradient(graphics, x, thumbY, 2, thumbHeight, 1, theme.accent(), theme.accentAlt(), false);
    }

    private static void drawSwitch(DrawContext graphics, int x, int y, boolean enabled, ClickGuiColors theme, boolean gradient) {
        if (!enabled) {
            RoundedGui.fill(graphics, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, 5, theme.outlineSoft());
            RoundedGui.fill(graphics, x + 2, y + 2, 7, 7, 3, theme.muted());
            return;
        }
        if (gradient) RoundedGui.gradient(graphics, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, 5, theme.accent(), theme.accentAlt(), true);
        else RoundedGui.fill(graphics, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, 5, theme.accent());
        RoundedGui.fill(graphics, x + TOGGLE_WIDTH - 9, y + 2, 7, 7, 3, opaque(theme.bar()));
    }

    private void drawValuePill(DrawContext graphics, int right, int y, int height, String value, ClickGuiColors theme) {
        OrderedText text = ArcaneFont.trimmed(font(), value, 70);
        int textWidth = font().getWidth(text);
        int width = Math.max(26, textWidth + 12);
        int pillY = y + (height - 15) / 2;
        RoundedGui.fill(graphics, right - width, pillY, width, 15, 5, theme.window());
        graphics.drawText(font(), text, right - width + (width - textWidth) / 2, pillY + (15 - lineHeight()) / 2, theme.accentBright(), false);
    }

    private void drawKeyPill(DrawContext graphics, int right, int y, int height, KeyBinding mapping, ClickGuiColors theme, int maxKeyWidth) {
        boolean listening = this.listeningFor == mapping;
        String raw = listening ? "..." : mapping.isUnbound() ? "—" : mapping.getBoundKeyLocalizedText().getString();
        OrderedText key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        int keyWidth = font().getWidth(key);
        int width = Math.max(22, keyWidth + 10);
        int pillY = y + (height - 15) / 2;
        RoundedGui.fill(graphics, right - width, pillY, width, 15, 5, listening ? theme.active() : theme.window());
        if (listening) RoundedGui.outlineOnly(graphics, right - width, pillY, width, 15, 5, theme.accent());
        graphics.drawText(font(), key, right - width + (width - keyWidth) / 2, pillY + (15 - lineHeight()) / 2, listening ? theme.accentBright() : theme.muted(), false);
    }

    private static void drawSwatch(DrawContext graphics, int x, int y, int width, int color, ClickGuiColors theme) {
        RoundedGui.fill(graphics, x, y, width, 10, 4, color | 0xFF000000);
        RoundedGui.outlineOnly(graphics, x, y, width, 10, 4, theme.outlineSoft());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int amount = (int) Math.round(-verticalAmount * 22.0);
        if (this.inspectorWidth > 0 && contains(this.inspectorX, this.inspectorY, this.inspectorWidth, this.inspectorHeight, mouseX, mouseY)) {
            this.detailScroll = Math.clamp(this.detailScroll + amount, 0, this.detailMaxScroll);
            return true;
        }
        if (contains(this.listX, this.listY, this.listWidth, this.listHeight, mouseX, mouseY)) {
            this.listScroll = Math.clamp(this.listScroll + amount, 0, this.listMaxScroll);
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
        if (button < 0 || button > 2) return super.mouseClicked(click, doubled);
        for (int index = this.hitRegions.size() - 1; index >= 0; index--) {
            HitRegion region = this.hitRegions.get(index);
            if (!region.contains(mouseX, mouseY)) continue;
            switch (region.kind()) {
                case CATEGORY -> {
                    if (button == 0) selectCategory(region.category());
                    setFocused(null);
                    return true;
                }
                case MODULE -> {
                    GuiModule module = region.module();
                    if (module == null) return true;
                    this.selectedCategory = region.category();
                    this.selectedModule = module;
                    setFocused(null);
                    if (button == 2) startListening(firstBind(module));
                    else if (button == 1) openInspector(module);
                    else if (module.toggleable()) module.toggle();
                    else openInspector(module);
                    return true;
                }
                case MODULE_TOGGLE -> {
                    GuiModule module = region.module();
                    if (button == 0 && module != null && module.toggleable()) module.toggle();
                    else if (button == 2 && module != null) startListening(firstBind(module));
                    setFocused(null);
                    return true;
                }
                case MODULE_SETTINGS -> {
                    if (button == 2) {
                        startListening(firstBind(region.module()));
                        return true;
                    }
                    this.selectedCategory = region.category();
                    this.selectedModule = region.module();
                    setFocused(null);
                    openInspector(region.module());
                    return true;
                }
                case SETTING -> {
                    Boolean handled = handleSettingClick(region, click, doubled, mouseX, mouseY, button);
                    if (handled != null) return handled;
                }
                case CLOSE_SETTINGS -> {
                    if (button == 0) {
                        if (this.settingsOpen) {
                            this.settingsOpen = false;
                            this.detailScroll = 0;
                            setFocused(null);
                        } else close();
                    }
                    return true;
                }
                case DRAG_PANEL -> {
                    if (button == 0) beginPanelDrag(mouseX, mouseY);
                    return true;
                }
                case PASSTHROUGH -> {
                    return super.mouseClicked(click, doubled);
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void openInspector(@Nullable GuiModule module) {
        if (module == null) return;
        this.settingsOpen = true;
        this.detailScroll = 0;
    }

    private @Nullable Boolean handleSettingClick(HitRegion region, Click click, boolean doubled, int mouseX, int mouseY, int button) {
        GuiSetting setting = region.setting();
        if (setting == null) return true;
        if (setting instanceof GuiSetting.Message message) {
            int fieldY = region.y() + region.height() - 17;
            if (mouseY >= fieldY) return super.mouseClicked(click, doubled);
            if (button == 0 && message.mapping() != null && mouseX >= region.x() + region.width() - 40) startListening(message.mapping());
            return true;
        }
        if (button != 0) return true;
        setFocused(null);
        int right = region.x() + region.width() - 9;
        switch (setting) {
            case GuiSetting.Toggle toggle -> toggle.toggle();
            case GuiSetting.ToggleSwatch toggle -> {
                if (mouseX >= right - TOGGLE_WIDTH - 21 && mouseX < right - TOGGLE_WIDTH - 4) toggle.cycleColor();
                else toggle.toggle();
            }
            case GuiSetting.Slider slider -> {
                if (mouseY >= region.y() + region.height() - 14) {
                    this.draggingSlider = slider;
                    this.sliderTrackX = region.x() + 9;
                    this.sliderTrackWidth = region.width() - 18;
                    updateSlider(mouseX);
                }
            }
            case GuiSetting.Swatch swatch -> swatch.cycleColor();
            case GuiSetting.Cycle cycle -> cycle.next();
            case GuiSetting.Bind bind -> startListening(bind.mapping());
            case GuiSetting.Info ignored -> { }
            case GuiSetting.Message ignored -> { }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.draggingSlider != null) {
            updateSlider((int) click.x());
            return true;
        }
        if (this.dragging) {
            this.panelOffsetX = this.dragStartOffsetX + (int) click.x() - this.dragStartMouseX;
            this.panelOffsetY = this.dragStartOffsetY + (int) click.y() - this.dragStartMouseY;
            layoutPanel();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.draggingSlider != null) {
            this.draggingSlider = null;
            return true;
        }
        if (this.dragging) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void beginPanelDrag(int mouseX, int mouseY) {
        setFocused(null);
        this.dragging = true;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        this.dragStartOffsetX = this.panelOffsetX;
        this.dragStartOffsetY = this.panelOffsetY;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.listeningFor != null) {
            if (input.key() == 256 || input.key() == 261 || input.key() == 259) setBinding(InputUtil.UNKNOWN_KEY);
            else setBinding(InputUtil.fromKeyCode(input));
            return true;
        }
        if (input.key() == 256 && this.searchInput != null && this.searchInput.isFocused() && !this.searchText.isEmpty()) {
            this.searchInput.setText("");
            this.searchInput.setFocused(false);
            return true;
        }
        if (input.key() == 256 && this.settingsOpen) {
            this.settingsOpen = false;
            this.detailScroll = 0;
            setFocused(null);
            return true;
        }
        return super.keyPressed(input);
    }

    private void selectCategory(@Nullable GuiCategory category) {
        if (category == null) return;
        this.selectedCategory = category;
        this.selectedModule = category.visible().isEmpty() ? null : category.visible().getFirst();
        this.settingsOpen = false;
        this.listScroll = 0;
        this.detailScroll = 0;
    }

    private void ensureSelection() {
        if (this.selectedCategory == null) this.selectedCategory = this.categories.getFirst();
        if (!this.query.isEmpty() && this.selectedCategory.visible().isEmpty()) {
            for (GuiCategory category : this.categories) if (!category.visible().isEmpty()) {
                this.selectedCategory = category;
                break;
            }
        }
        if (this.selectedModule == null || !this.selectedCategory.visible().contains(this.selectedModule)) {
            this.selectedModule = this.selectedCategory.visible().isEmpty() ? null : this.selectedCategory.visible().getFirst();
            this.settingsOpen = false;
            this.detailScroll = 0;
        }
    }

    private List<ModuleEntry> visibleEntries() {
        List<ModuleEntry> entries = new ArrayList<>();
        if (this.query.isEmpty()) {
            for (GuiModule module : this.selectedCategory.visible()) entries.add(new ModuleEntry(this.selectedCategory, module));
            return entries;
        }
        for (GuiCategory category : this.categories) for (GuiModule module : category.visible()) entries.add(new ModuleEntry(category, module));
        return entries;
    }

    private void updateFilter(String value) {
        this.searchText = value;
        this.query = value.trim().toLowerCase(Locale.ROOT);
        for (GuiCategory category : this.categories) category.filter(this.query);
        this.settingsOpen = false;
        this.listScroll = 0;
        ensureSelection();
    }

    private int activeModuleCount() {
        int active = 0;
        for (GuiCategory category : this.categories) for (GuiModule module : category.modules()) if (module.enabled()) active++;
        return active;
    }

    private void updateFpsLabel() {
        long now = System.nanoTime();
        if (now < this.nextFpsUpdateNanos && !this.fpsLabel.isEmpty()) return;
        this.nextFpsUpdateNanos = now + 500_000_000L;
        this.fpsLabel = this.client == null ? "— FPS" : this.client.getCurrentFps() + " FPS";
    }

    private void startListening(@Nullable KeyBinding mapping) {
        if (mapping == null) return;
        this.listeningFor = mapping;
        for (TextFieldWidget input : this.macroInputs) input.setFocused(false);
    }

    private void setBinding(InputUtil.Key key) {
        if (this.listeningFor == null) return;
        this.listeningFor.setBoundKey(key);
        this.listeningFor = null;
        KeyBinding.updateKeysByCode();
        this.client.options.write();
    }

    private void updateSlider(int mouseX) {
        if (this.draggingSlider == null || this.sliderTrackWidth <= 0) return;
        this.draggingSlider.setFraction((float) (mouseX - this.sliderTrackX) / this.sliderTrackWidth);
    }

    private boolean withinList(int y) {
        return y >= this.listY - 1 && y + ROW_HEIGHT <= this.listY + this.listHeight + 1;
    }

    private static @Nullable KeyBinding firstBind(@Nullable GuiModule module) {
        if (module == null) return null;
        for (GuiSetting setting : module.settings()) if (setting instanceof GuiSetting.Bind bind) return bind.mapping();
        return null;
    }

    private static int displayHeight(GuiSetting setting) {
        if (setting instanceof GuiSetting.Slider) return SETTING_SLIDER_HEIGHT;
        if (setting instanceof GuiSetting.Message) return SETTING_MESSAGE_HEIGHT;
        return SETTING_COMPACT_HEIGHT;
    }

    private static int settingControlWidth(GuiSetting setting) {
        return switch (setting) {
            case GuiSetting.ToggleSwatch ignored -> TOGGLE_WIDTH + 24;
            case GuiSetting.Toggle ignored -> TOGGLE_WIDTH + 4;
            case GuiSetting.Swatch ignored -> 30;
            case GuiSetting.Slider ignored -> 40;
            case GuiSetting.Bind ignored -> BIND_KEY_WIDTH + 8;
            case GuiSetting.Cycle ignored -> 60;
            case GuiSetting.Info ignored -> 50;
            case GuiSetting.Message ignored -> 34;
        };
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
            } else line = new StringBuilder(candidate);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return List.copyOf(lines);
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        boolean upper = true;
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            result.append(upper ? Character.toUpperCase(character) : character);
            upper = character == ' ';
        }
        return result.toString();
    }

    /** Category names are stored upper case; acronyms must survive the title-casing. */
    static String categoryLabel(String name) {
        return name.equals("ESP") ? "ESP" : titleCase(name);
    }

    private static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int opaque(int color) { return color | 0xFF000000; }

    private void hideMacroInput(int slot) {
        if (slot < 0 || slot >= this.macroInputs.size()) return;
        TextFieldWidget input = this.macroInputs.get(slot);
        input.setVisible(false);
        input.setFocused(false);
    }

    private ClickGuiColors theme() { return ClickGuiColors.resolve(this.config); }

    private TextRenderer font() {
        if (this.uiFont == null) this.uiFont = ArcaneFont.renderer(MinecraftClient.getInstance());
        return this.uiFont;
    }

    private void styleField(TextFieldWidget input) {
        input.setEditableColor(theme().text());
        input.setUneditableColor(theme().faint());
    }

    private int lineHeight() { return font().fontHeight; }

    public static boolean isOpen(MinecraftClient client) { return client.currentScreen instanceof ArcaneSettingsScreen; }

    @Override
    public void close() { this.client.setScreen(this.parent); }

    @Override
    public void removed() {
        this.config.save();
        this.client.options.write();
        if (!this.scannerWasEnabled && this.config.enabled) ArcaneClient.engine().queueNearby(this.client);
        ArcaneClient.engine().settingsChanged(this.client);
    }

    @Override
    public boolean shouldPause() { return false; }

    private enum RegionKind { CATEGORY, MODULE, MODULE_TOGGLE, MODULE_SETTINGS, SETTING, CLOSE_SETTINGS, DRAG_PANEL, PASSTHROUGH }
    private record WrapKey(String text, int maxWidth) { }
    private record ModuleEntry(GuiCategory category, GuiModule module) { }

    private record HitRegion(RegionKind kind, @Nullable GuiCategory category, @Nullable GuiModule module, @Nullable GuiSetting setting, int x, int y, int width, int height) {
        private static HitRegion category(GuiCategory category, int x, int y, int width, int height) { return new HitRegion(RegionKind.CATEGORY, category, null, null, x, y, width, height); }
        private static HitRegion module(GuiCategory category, GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE, category, module, null, x, y, width, height); }
        private static HitRegion moduleToggle(GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE_TOGGLE, null, module, null, x, y, width, height); }
        private static HitRegion moduleSettings(GuiCategory category, GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE_SETTINGS, category, module, null, x, y, width, height); }
        private static HitRegion setting(GuiSetting setting, int x, int y, int width, int height) { return new HitRegion(RegionKind.SETTING, null, null, setting, x, y, width, height); }
        private static HitRegion close(int x, int y, int width, int height) { return new HitRegion(RegionKind.CLOSE_SETTINGS, null, null, null, x, y, width, height); }
        private static HitRegion drag(int x, int y, int width, int height) { return new HitRegion(RegionKind.DRAG_PANEL, null, null, null, x, y, width, height); }
        private static HitRegion passthrough(int x, int y, int width, int height) { return new HitRegion(RegionKind.PASSTHROUGH, null, null, null, x, y, width, height); }
        private boolean contains(int mouseX, int mouseY) { return ArcaneSettingsScreen.contains(this.x, this.y, this.width, this.height, mouseX, mouseY); }
    }
}
