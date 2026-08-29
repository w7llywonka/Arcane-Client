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

/** Arcane's detached, game-first overlay control center. */
@Environment(EnvType.CLIENT)
public final class ArcaneSettingsScreen extends Screen {
    private static final int FRAME_MARGIN = 10;
    private static final int FRAME_MAX_WIDTH = 420;
    private static final int FRAME_MAX_HEIGHT = 48;
    private static final int HEADER_HEIGHT = 48;
    private static final int HERO_HEIGHT = 42;
    private static final int CONTENT_PAD = 10;
    private static final int CARD_GAP = 8;
    private static final int CARD_HEIGHT = 58;
    private static final int SHEET_MARGIN = 12;
    private static final int WORKSPACE_MAX_WIDTH = 342;
    private static final int WORKSPACE_MAX_HEIGHT = 248;
    private static final int SHEET_MAX_HEIGHT = 286;
    private static final int MACRO_COUNT = 4;
    private static final int MACRO_FIELD_WIDTH = 68;
    private static final int BIND_KEY_WIDTH = 48;

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
    private int frameX, frameY, frameWidth, frameHeight;
    private int workspaceX, workspaceY, workspaceWidth, workspaceHeight;
    private int listX, listY, listWidth, listHeight;
    private int detailX, detailY, detailWidth, detailHeight;
    private int listScroll, listMaxScroll, detailScroll, detailMaxScroll;
    private GuiCategory selectedCategory;
    private GuiModule selectedModule;
    private boolean settingsOpen;
    private int workspaceOffsetX, workspaceOffsetY, detailOffsetX, detailOffsetY;
    private @Nullable DragTarget draggingSurface;
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
        layoutFrame();
        int searchWidth = Math.clamp(this.frameWidth / 4, 126, 190);
        int searchX = this.frameX + this.frameWidth - searchWidth - 23;
        this.searchInput = new TextFieldWidget(font(), searchX, this.frameY + 17, searchWidth, 12, Text.literal("Search modules"));
        this.searchInput.setDrawsBackground(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setPlaceholder(ArcaneFont.text("Search modules..."));
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

    private void layoutFrame() {
        boolean compactViewport = this.width < 520;
        int launcherMaxWidth = compactViewport ? 330 : FRAME_MAX_WIDTH;
        this.frameWidth = Math.min(launcherMaxWidth, Math.max(292, this.width - FRAME_MARGIN * 2));
        this.frameHeight = FRAME_MAX_HEIGHT;
        this.frameX = (this.width - this.frameWidth) / 2;
        this.frameY = 8;
        this.detailWidth = this.settingsOpen ? Math.clamp(this.width * 39 / 100, 226, 260) : 0;
        this.workspaceWidth = Math.min(compactViewport ? 300 : WORKSPACE_MAX_WIDTH, this.width - 24);
        if (this.settingsOpen) this.workspaceWidth = Math.min(this.workspaceWidth, Math.max(280, this.width - this.detailWidth - 38));
        int baseWorkspaceX = 14;
        int baseWorkspaceY = this.frameY + HEADER_HEIGHT + 10;
        this.workspaceHeight = Math.max(148, Math.min(WORKSPACE_MAX_HEIGHT, this.height - baseWorkspaceY - 6));
        this.workspaceX = Math.clamp(baseWorkspaceX + this.workspaceOffsetX, 6, Math.max(6, this.width - this.workspaceWidth - 6));
        this.workspaceY = Math.clamp(baseWorkspaceY + this.workspaceOffsetY, this.frameY + HEADER_HEIGHT + 4, Math.max(this.frameY + HEADER_HEIGHT + 4, this.height - this.workspaceHeight - 6));
        this.workspaceOffsetX = this.workspaceX - baseWorkspaceX;
        this.workspaceOffsetY = this.workspaceY - baseWorkspaceY;
        this.listX = this.workspaceX + CONTENT_PAD;
        this.listY = this.workspaceY + HERO_HEIGHT;
        this.listWidth = this.workspaceWidth - CONTENT_PAD * 2;
        this.listHeight = this.workspaceY + this.workspaceHeight - CONTENT_PAD - this.listY;
        int baseDetailX = Math.min(this.width - this.detailWidth - SHEET_MARGIN, this.workspaceX + this.workspaceWidth + SHEET_MARGIN);
        int baseDetailY = this.workspaceY;
        this.detailX = this.settingsOpen ? Math.clamp(baseDetailX + this.detailOffsetX, 6, Math.max(6, this.width - this.detailWidth - 6)) : 0;
        this.detailY = Math.clamp(baseDetailY + this.detailOffsetY, this.frameY + HEADER_HEIGHT + 4, Math.max(this.frameY + HEADER_HEIGHT + 4, this.height - SHEET_MAX_HEIGHT - 6));
        this.detailOffsetX = this.detailX - baseDetailX;
        this.detailOffsetY = this.detailY - baseDetailY;
        this.detailHeight = Math.min(SHEET_MAX_HEIGHT, this.workspaceHeight);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        layoutFrame();
        boolean compactViewport = this.width < 520;
        int searchWidth = Math.clamp(this.frameWidth / 4, 96, 116);
        this.searchInput.setX(this.frameX + this.frameWidth - searchWidth - 11);
        this.searchInput.setY(this.frameY + 18);
        this.searchInput.setWidth(searchWidth);
        this.searchInput.setVisible(!compactViewport);
        if (compactViewport && this.searchInput.isFocused()) this.searchInput.setFocused(false);
        ClickGuiColors theme = theme();
        this.hitRegions.clear();
        boolean[] macroDrawn = new boolean[MACRO_COUNT];
        drawFrame(graphics, theme);
        drawHeader(graphics, mouseX, mouseY, theme);
        drawTelemetry(graphics, theme);
        drawModuleList(graphics, mouseX, mouseY, theme);
        if (this.settingsOpen) drawInspector(graphics, mouseX, mouseY, theme, macroDrawn);
        for (int slot = 0; slot < MACRO_COUNT; slot++) if (!macroDrawn[slot]) hideMacroInput(slot);
        super.render(graphics, mouseX, mouseY, deltaTicks);
    }

    private void drawFrame(DrawContext graphics, ClickGuiColors theme) {
        RoundedGui.fill(graphics, this.frameX + 3, this.frameY + 4, this.frameWidth, this.frameHeight, 15, 0x65000000);
        RoundedGui.fill(graphics, this.frameX, this.frameY, this.frameWidth, this.frameHeight, 15, theme.bar());
        RoundedGui.fill(graphics, this.frameX + 1, this.frameY + 1, this.frameWidth - 2, this.frameHeight - 2, 14, theme.window());
    }

    private void drawTelemetry(DrawContext graphics, ClickGuiColors theme) {
        int widgetWidth = 92;
        int widgetX = this.width - widgetWidth - 12;
        int widgetY = this.frameY;
        if (widgetX < this.frameX + this.frameWidth + 8) return;
        updateFpsLabel();
        RoundedGui.fill(graphics, widgetX + 3, widgetY + 4, widgetWidth, this.frameHeight, 15, 0x65000000);
        RoundedGui.fill(graphics, widgetX, widgetY, widgetWidth, this.frameHeight, 15, theme.window());
        RoundedGui.fill(graphics, widgetX + 9, widgetY + 10, 5, 28, 3, theme.accent());
        graphics.drawText(font(), ArcaneFont.text(this.fpsLabel), widgetX + 22, widgetY + 10, theme.text(), false);
        graphics.drawText(font(), ArcaneFont.text(activeModuleCount() + " ACTIVE"), widgetX + 22, widgetY + 25, theme.faint(), false);
    }

    private void drawHeader(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        boolean compactViewport = this.width < 520;
        int brandX = this.frameX + 10;
        int brandY = this.frameY + 10;
        RoundedGui.fill(graphics, brandX, brandY, 28, 28, 9, theme.accent());
        graphics.drawText(font(), ArcaneFont.text("A/"), brandX + 7, brandY + 10, opaque(theme.bar()), false);

        int searchX = this.searchInput.getX();
        if (!compactViewport) {
            int searchY = this.searchInput.getY();
            int searchWidth = this.searchInput.getWidth();
            boolean focused = this.searchInput.isFocused();
            RoundedGui.fill(graphics, searchX - 21, searchY - 5, searchWidth + 27, 22, 8, focused ? theme.hover() : theme.header());
            graphics.drawText(font(), ArcaneFont.text("/"), searchX - 13, searchY + 1, focused ? theme.accentBright() : theme.faint(), false);
            if (focused) graphics.fill(searchX - 14, searchY + 15, searchX + searchWidth, searchY + 16, theme.accent());
        }

        int tabsX = brandX + 37;
        int tabsY = brandY + 2;
        int tabsWidth = (compactViewport ? this.frameX + this.frameWidth - 10 : searchX - 29) - tabsX;
        int tabWidth = Math.max(28, tabsWidth / this.categories.size());
        for (int index = 0; index < this.categories.size(); index++) {
            GuiCategory category = this.categories.get(index);
            int tabX = tabsX + index * tabWidth;
            int width = tabWidth - 3;
            boolean selected = category == this.selectedCategory && this.query.isEmpty();
            boolean hovered = contains(tabX, tabsY, width, 24, mouseX, mouseY);
            if (selected || hovered) RoundedGui.fill(graphics, tabX, tabsY, width, 24, 8, selected ? theme.active() : theme.hover());
            String glyph = categoryGlyph(category.name());
            int glyphWidth = ArcaneFont.width(font(), glyph);
            graphics.drawText(font(), ArcaneFont.text(glyph), tabX + (width - glyphWidth) / 2, tabsY + 8, selected ? theme.accentBright() : theme.muted(), false);
            if (selected) RoundedGui.fill(graphics, tabX + width / 2 - 2, tabsY + 27, 4, 2, 1, theme.accent());
            this.hitRegions.add(HitRegion.category(category, tabX, tabsY, width, 28));
        }
    }

    private void drawModuleList(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        RoundedGui.fill(graphics, this.workspaceX + 4, this.workspaceY + 5, this.workspaceWidth, this.workspaceHeight, 13, 0x65000000);
        RoundedGui.fill(graphics, this.workspaceX, this.workspaceY, this.workspaceWidth, this.workspaceHeight, 13, theme.window());
        RoundedGui.fill(graphics, this.workspaceX + 1, this.workspaceY + 1, this.workspaceWidth - 2, HERO_HEIGHT - 7, 12, theme.header());
        this.hitRegions.add(HitRegion.dragWorkspace(this.workspaceX + 1, this.workspaceY + 1, this.workspaceWidth - 2, HERO_HEIGHT - 7));
        int titleX = this.workspaceX + 13;
        int titleY = this.workspaceY + 9;
        String title = this.query.isEmpty() ? titleCase(this.selectedCategory.name()) : "Search / " + this.searchText;
        graphics.drawText(font(), ArcaneFont.text(title), titleX, titleY, theme.text(), false);
        String summary = this.query.isEmpty()
            ? this.selectedCategory.visible().size() + " modules · " + this.selectedCategory.enabledCount() + " on"
            : visibleModuleCount() + " matching modules";
        graphics.drawText(font(), ArcaneFont.text(summary), titleX, titleY + 15, theme.faint(), false);
        int statusX = this.workspaceX + this.workspaceWidth - 12;
        for (int dot = 0; dot < 3; dot++) RoundedGui.fill(graphics, statusX - 13 + dot * 6, titleY + 9, 3, 3, 2, theme.faint());

        List<ModuleEntry> entries = visibleEntries();
        int columns = this.listWidth >= 610 ? 3 : 2;
        int cardWidth = (this.listWidth - CARD_GAP * (columns - 1)) / columns;
        int rows = (entries.size() + columns - 1) / columns;
        int contentHeight = Math.max(0, rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP);
        this.listMaxScroll = Math.max(0, contentHeight - this.listHeight);
        this.listScroll = Math.clamp(this.listScroll, 0, this.listMaxScroll);
        graphics.enableScissor(this.listX, this.listY, this.listX + this.listWidth, this.listY + this.listHeight);
        for (int index = 0; index < entries.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int cardX = this.listX + column * (cardWidth + CARD_GAP);
            int cardY = this.listY + row * (CARD_HEIGHT + CARD_GAP) - this.listScroll;
            if (cardY + CARD_HEIGHT >= this.listY && cardY <= this.listY + this.listHeight) {
                drawModuleCard(graphics, entries.get(index), index, cardX, cardY, cardWidth, mouseX, mouseY, theme);
            }
        }
        graphics.disableScissor();
        if (entries.isEmpty()) graphics.drawCenteredTextWithShadow(font(), ArcaneFont.text("No matching modules"), this.listX + this.listWidth / 2, this.listY + this.listHeight / 2, theme.faint());
        drawScrollBar(graphics, this.listX + this.listWidth + 5, this.listY, this.listHeight, this.listScroll, this.listMaxScroll, contentHeight, theme);
    }

    private void drawModuleCard(DrawContext graphics, ModuleEntry entry, int index, int x, int y, int width, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = entry.module();
        boolean selected = module == this.selectedModule && this.settingsOpen;
        boolean hovered = contains(x, y, width, CARD_HEIGHT, mouseX, mouseY);
        int surface = selected ? theme.active() : hovered ? theme.hover() : theme.row();
        RoundedGui.fill(graphics, x, y, width, CARD_HEIGHT, 11, surface);
        if (selected || module.enabled()) RoundedGui.fill(graphics, x, y, 3, CARD_HEIGHT, 2, selected ? theme.accentBright() : theme.accentDim());

        String serial = twoDigits(index + 1);
        graphics.drawText(font(), ArcaneFont.text(serial), x + 12, y + 10, module.enabled() ? theme.accentBright() : theme.faint(), false);
        String category = this.query.isEmpty() ? "MODULE" : titleCase(entry.category().name());
        graphics.drawText(font(), ArcaneFont.text(category), x + 32, y + 10, theme.faint(), false);
        if (module.toggleable()) drawSwitch(graphics, x + width - 36, y + 8, module.enabled(), theme);
        else if (module.valueLabel() != null) {
            OrderedText value = ArcaneFont.trimmed(font(), module.valueLabel(), 48);
            graphics.drawText(font(), value, x + width - font().getWidth(value) - 12, y + 11, theme.accentBright(), false);
        }

        graphics.drawText(font(), ArcaneFont.trimmed(font(), module.name(), width - 26), x + 12, y + 30, selected ? theme.accentBright() : theme.text(), false);
        String meta = shortDescription(module.description(), width - 42);
        graphics.drawText(font(), ArcaneFont.trimmed(font(), meta, width - 42), x + 12, y + 46, theme.muted(), false);
        if (module.hasSettings()) {
            RoundedGui.fill(graphics, x + width - 24, y + CARD_HEIGHT - 19, 13, 13, 7, selected ? theme.accent() : theme.header());
            graphics.drawText(font(), ArcaneFont.text("+"), x + width - 20, y + CARD_HEIGHT - 16, selected ? opaque(theme.bar()) : theme.faint(), false);
        }
        if (y >= this.listY && y + CARD_HEIGHT <= this.listY + this.listHeight) {
            this.hitRegions.add(HitRegion.module(entry.category(), module, x, y, width, CARD_HEIGHT));
        }
    }

    private void drawInspector(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        GuiModule module = this.selectedModule;
        if (module == null) {
            this.settingsOpen = false;
            this.detailMaxScroll = 0;
            return;
        }
        RoundedGui.fill(graphics, this.detailX + 4, this.detailY + 5, this.detailWidth, this.detailHeight, 13, 0x68000000);
        RoundedGui.fill(graphics, this.detailX, this.detailY, this.detailWidth, this.detailHeight, 14, theme.bar());
        RoundedGui.fill(graphics, this.detailX + 1, this.detailY + 1, this.detailWidth - 2, this.detailHeight - 2, 13, theme.window());
        this.hitRegions.add(HitRegion.dragDetail(this.detailX + 1, this.detailY + 1, this.detailWidth - 2, 45));

        int headerX = this.detailX + 12;
        int headerY = this.detailY + 10;
        RoundedGui.fill(graphics, headerX, headerY, 30, 30, 9, theme.accent());
        int moduleIndex = Math.max(0, this.selectedCategory.modules().indexOf(module));
        graphics.drawText(font(), ArcaneFont.text(twoDigits(moduleIndex + 1)), headerX + 7, headerY + 11, opaque(theme.bar()), false);
        graphics.drawText(font(), ArcaneFont.text("CONTROL / " + categoryGlyph(this.selectedCategory.name())), headerX + 39, headerY + 1, theme.accentBright(), false);
        graphics.drawText(font(), ArcaneFont.trimmed(font(), module.name(), this.detailWidth - 118), headerX + 39, headerY + 16, theme.text(), false);

        int closeX = this.detailX + this.detailWidth - 31;
        int closeY = this.detailY + 12;
        boolean closeHovered = contains(closeX, closeY, 18, 18, mouseX, mouseY);
        RoundedGui.fill(graphics, closeX, closeY, 18, 18, 9, closeHovered ? theme.hover() : theme.header());
        graphics.drawText(font(), ArcaneFont.text("×"), closeX + 6, closeY + 5, closeHovered ? theme.text() : theme.faint(), false);
        this.hitRegions.add(HitRegion.close(closeX, closeY, 18, 18));
        if (module.toggleable()) {
            int toggleX = closeX - 36;
            drawSwitch(graphics, toggleX, closeY + 3, module.enabled(), theme);
            this.hitRegions.add(HitRegion.moduleToggle(module, toggleX - 3, closeY - 2, 30, 23));
        }

        int descriptionY = this.detailY + 51;
        graphics.drawText(font(), ArcaneFont.trimmed(font(), module.description(), this.detailWidth - 24), this.detailX + 12, descriptionY, theme.muted(), false);
        graphics.fill(this.detailX + 12, descriptionY + 16, this.detailX + this.detailWidth - 12, descriptionY + 17, theme.outlineSoft());

        int viewportX = this.detailX + 11;
        int viewportY = descriptionY + 25;
        int viewportWidth = this.detailWidth - 22;
        int viewportHeight = this.detailY + this.detailHeight - viewportY - 11;
        int columns = 1;
        int cellWidth = viewportWidth;
        int rows = (module.settings().size() + columns - 1) / columns;
        int contentHeight = Math.max(viewportHeight, rows * 40);
        this.detailMaxScroll = Math.max(0, contentHeight - viewportHeight);
        this.detailScroll = Math.clamp(this.detailScroll, 0, this.detailMaxScroll);
        if (!module.hasSettings()) {
            RoundedGui.fill(graphics, viewportX, viewportY, viewportWidth, 34, 9, theme.header());
            graphics.drawText(font(), ArcaneFont.text("This module has no additional controls."), viewportX + 12, viewportY + 12, theme.faint(), false);
        } else {
            graphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
            for (int index = 0; index < module.settings().size(); index++) {
                int column = index % columns;
                int row = index / columns;
                int cellX = viewportX + column * (cellWidth + CARD_GAP);
                int cellY = viewportY + row * 40 - this.detailScroll;
                if (cellY + 36 >= viewportY && cellY <= viewportY + viewportHeight) {
                    drawSetting(graphics, module.settings().get(index), cellX, cellY, cellWidth, 36, mouseX, mouseY, theme, macroDrawn);
                }
            }
            graphics.disableScissor();
        }
        drawScrollBar(graphics, this.detailX + this.detailWidth - 8, viewportY, viewportHeight, this.detailScroll, this.detailMaxScroll, contentHeight, theme);
    }

    private void drawSetting(DrawContext graphics, GuiSetting setting, int x, int y, int width, int height, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        boolean hovered = contains(x, y, width, height, mouseX, mouseY);
        RoundedGui.fill(graphics, x, y, width, height, 7, hovered && !(setting instanceof GuiSetting.Info) ? theme.hover() : theme.header());
        int right = x + width - 10;
        int textY = y + (setting instanceof GuiSetting.Slider ? 7 : (height - lineHeight()) / 2);
        graphics.drawText(font(), ArcaneFont.text(setting.label()), x + 10, textY, theme.muted(), false);
        switch (setting) {
            case GuiSetting.Toggle toggle -> drawSwitch(graphics, right - 24, y + (height - 11) / 2, toggle.value(), theme);
            case GuiSetting.ToggleSwatch toggle -> {
                drawSwatch(graphics, right - 47, y + (height - 10) / 2, 15, toggle.color(), theme);
                drawSwitch(graphics, right - 24, y + (height - 11) / 2, toggle.value(), theme);
            }
            case GuiSetting.Slider slider -> {
                String display = slider.display();
                graphics.drawText(font(), ArcaneFont.text(display), right - ArcaneFont.width(font(), display), textY, theme.text(), false);
                int trackX = x + 10;
                int trackWidth = width - 20;
                int trackY = y + height - 8;
                RoundedGui.fill(graphics, trackX, trackY, trackWidth, 3, 2, theme.outlineSoft());
                int filled = Math.round(trackWidth * slider.fraction());
                if (filled > 0) RoundedGui.fill(graphics, trackX, trackY, filled, 3, 2, theme.accent());
                RoundedGui.fill(graphics, trackX + Math.clamp(filled - 3, 0, trackWidth - 6), trackY - 2, 6, 7, 3, theme.accentBright());
            }
            case GuiSetting.Swatch swatch -> drawSwatch(graphics, right - 28, y + (height - 10) / 2, 28, swatch.color(), theme);
            case GuiSetting.Cycle cycle -> drawValuePill(graphics, right, y, height, cycle.value(), theme);
            case GuiSetting.Bind bind -> drawKeyPill(graphics, right, y, height, bind.mapping(), theme, BIND_KEY_WIDTH);
            case GuiSetting.Info info -> {
                String value = info.value();
                graphics.drawText(font(), ArcaneFont.text(value), right - ArcaneFont.width(font(), value), textY, theme.accentBright(), false);
            }
            case GuiSetting.Message message -> {
                int fieldX = right - MACRO_FIELD_WIDTH - 39;
                RoundedGui.fill(graphics, fieldX - 5, y + 6, MACRO_FIELD_WIDTH + 10, 16, 6, theme.window());
                TextFieldWidget input = this.macroInputs.get(message.slot());
                input.setX(fieldX);
                input.setY(y + 9);
                input.setVisible(y >= this.detailY && y + height <= this.detailY + this.detailHeight);
                macroDrawn[message.slot()] = input.isVisible();
                if (message.mapping() != null) drawKeyPill(graphics, right, y, height, message.mapping(), theme, 27);
            }
        }
        if (y >= this.detailY && y + height <= this.detailY + this.detailHeight) {
            this.hitRegions.add(HitRegion.setting(setting, x, y, width, height));
        }
    }

    private void drawScrollBar(DrawContext graphics, int x, int y, int height, int offset, int maxOffset, int contentHeight, ClickGuiColors theme) {
        if (maxOffset <= 0 || contentHeight <= 0) return;
        int thumbHeight = Math.max(14, height * height / Math.max(height, contentHeight));
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + travel * offset / maxOffset;
        RoundedGui.fill(graphics, x, thumbY, 2, thumbHeight, 1, theme.accentDim());
    }

    private static void drawSwitch(DrawContext graphics, int x, int y, boolean enabled, ClickGuiColors theme) {
        RoundedGui.fill(graphics, x, y, 24, 11, 6, enabled ? theme.accent() : theme.outlineSoft());
        RoundedGui.fill(graphics, enabled ? x + 15 : x + 2, y + 2, 7, 7, 4, enabled ? opaque(theme.bar()) : theme.muted());
    }

    private void drawValuePill(DrawContext graphics, int right, int y, int height, String value, ClickGuiColors theme) {
        OrderedText text = ArcaneFont.trimmed(font(), value, 74);
        int textWidth = font().getWidth(text);
        int width = Math.max(28, textWidth + 12);
        int pillY = y + (height - 16) / 2;
        RoundedGui.fill(graphics, right - width, pillY, width, 16, 6, theme.window());
        graphics.drawText(font(), text, right - width + 6, pillY + 4, theme.accentBright(), false);
    }

    private void drawKeyPill(DrawContext graphics, int right, int y, int height, KeyBinding mapping, ClickGuiColors theme, int maxKeyWidth) {
        boolean listening = this.listeningFor == mapping;
        String raw = listening ? "..." : mapping.isUnbound() ? "—" : mapping.getBoundKeyLocalizedText().getString();
        OrderedText key = ArcaneFont.trimmed(font(), raw, maxKeyWidth);
        int keyWidth = font().getWidth(key);
        int width = Math.max(24, keyWidth + 10);
        int pillY = y + (height - 16) / 2;
        RoundedGui.fill(graphics, right - width, pillY, width, 16, 6, listening ? theme.active() : theme.window());
        graphics.drawText(font(), key, right - width + (width - keyWidth) / 2, pillY + 4, listening ? theme.text() : theme.muted(), false);
    }

    private static void drawSwatch(DrawContext graphics, int x, int y, int width, int color, ClickGuiColors theme) {
        RoundedGui.fill(graphics, x, y, width, 10, 4, color | 0xFF000000);
        RoundedGui.fill(graphics, x + 3, y + 2, Math.max(1, width - 6), 1, 1, theme.outlineSoft());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int amount = (int) Math.round(-verticalAmount * 24.0);
        if (this.settingsOpen && contains(this.detailX, this.detailY, this.detailWidth, this.detailHeight, mouseX, mouseY)) {
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
                    selectCategory(region.category());
                    setFocused(null);
                    return true;
                }
                case MODULE -> {
                    this.selectedCategory = region.category();
                    this.selectedModule = region.module();
                    this.detailScroll = 0;
                    setFocused(null);
                    if (button == 2) startListening(firstBind(region.module()));
                    else if (button == 1 && region.module().hasSettings()) this.settingsOpen = true;
                    else if (button == 0 && region.module().hasSettings()
                        && mouseX >= region.x() + region.width() - 34
                        && mouseY >= region.y() + region.height() - 25) this.settingsOpen = true;
                    else if (button == 0 && region.module().toggleable()) region.module().toggle();
                    return true;
                }
                case MODULE_TOGGLE -> {
                    if (button == 0 && region.module().toggleable()) region.module().toggle();
                    return true;
                }
                case SETTING -> {
                    Boolean handled = handleSettingClick(region, click, doubled, mouseX, mouseY, button);
                    if (handled != null) return handled;
                }
                case CLOSE_SETTINGS -> {
                    if (button == 0) {
                        this.settingsOpen = false;
                        setFocused(null);
                    }
                    return true;
                }
                case DRAG_WORKSPACE -> {
                    if (button == 0) beginSurfaceDrag(DragTarget.WORKSPACE, mouseX, mouseY);
                    return true;
                }
                case DRAG_DETAIL -> {
                    if (button == 0) beginSurfaceDrag(DragTarget.DETAIL, mouseX, mouseY);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private @Nullable Boolean handleSettingClick(HitRegion region, Click click, boolean doubled, int mouseX, int mouseY, int button) {
        GuiSetting setting = region.setting();
        if (setting == null) return true;
        if (setting instanceof GuiSetting.Message message) {
            int right = region.x() + region.width() - 10;
            int fieldX = right - MACRO_FIELD_WIDTH - 39;
            if (mouseX >= fieldX - 5 && mouseX < fieldX + MACRO_FIELD_WIDTH + 5) return super.mouseClicked(click, doubled);
            if (button == 0 && message.mapping() != null && mouseX >= right - 34) startListening(message.mapping());
            return true;
        }
        if (button != 0) return true;
        setFocused(null);
        switch (setting) {
            case GuiSetting.Toggle toggle -> toggle.toggle();
            case GuiSetting.ToggleSwatch toggle -> {
                int right = region.x() + region.width() - 10;
                if (mouseX >= right - 52 && mouseX < right - 28) toggle.cycleColor();
                else toggle.toggle();
            }
            case GuiSetting.Slider slider -> {
                if (mouseY >= region.y() + region.height() - 13) {
                    this.draggingSlider = slider;
                    this.sliderTrackX = region.x() + 10;
                    this.sliderTrackWidth = region.width() - 20;
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
        if (this.draggingSurface != null) {
            int mouseX = (int) click.x();
            int mouseY = (int) click.y();
            if (this.draggingSurface == DragTarget.WORKSPACE) {
                this.workspaceOffsetX = this.dragStartOffsetX + mouseX - this.dragStartMouseX;
                this.workspaceOffsetY = this.dragStartOffsetY + mouseY - this.dragStartMouseY;
            } else {
                this.detailOffsetX = this.dragStartOffsetX + mouseX - this.dragStartMouseX;
                this.detailOffsetY = this.dragStartOffsetY + mouseY - this.dragStartMouseY;
            }
            layoutFrame();
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
        if (this.draggingSurface != null) {
            this.draggingSurface = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void beginSurfaceDrag(DragTarget target, int mouseX, int mouseY) {
        setFocused(null);
        this.draggingSurface = target;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        this.dragStartOffsetX = target == DragTarget.WORKSPACE ? this.workspaceOffsetX : this.detailOffsetX;
        this.dragStartOffsetY = target == DragTarget.WORKSPACE ? this.workspaceOffsetY : this.detailOffsetY;
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

    private int visibleModuleCount() {
        int count = 0;
        for (GuiCategory category : this.categories) count += category.visible().size();
        return count;
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

    private static @Nullable KeyBinding firstBind(GuiModule module) {
        for (GuiSetting setting : module.settings()) if (setting instanceof GuiSetting.Bind bind) return bind.mapping();
        return null;
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

    private String shortDescription(String description, int maxWidth) {
        return description;
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

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static String categoryGlyph(String name) {
        return switch (name) {
            case "COMBAT" -> "CO";
            case "ESP" -> "ES";
            case "RENDER" -> "RE";
            case "CLIENT" -> "CL";
            case "UTILITY" -> "UT";
            case "BASE FINDING" -> "BF";
            default -> name.substring(0, Math.min(2, name.length()));
        };
    }

    private static int settingHeight(GuiSetting setting) {
        if (setting instanceof GuiSetting.Slider) return 34;
        if (setting instanceof GuiSetting.Message) return 28;
        return 23;
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
    private int proseHeight() { return lineHeight() + 3; }

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

    private enum DragTarget { WORKSPACE, DETAIL }
    private enum RegionKind { CATEGORY, MODULE, MODULE_TOGGLE, SETTING, CLOSE_SETTINGS, DRAG_WORKSPACE, DRAG_DETAIL }
    private record WrapKey(String text, int maxWidth) { }
    private record ModuleEntry(GuiCategory category, GuiModule module) { }

    private record HitRegion(RegionKind kind, @Nullable GuiCategory category, @Nullable GuiModule module, @Nullable GuiSetting setting, int x, int y, int width, int height) {
        private static HitRegion category(GuiCategory category, int x, int y, int width, int height) { return new HitRegion(RegionKind.CATEGORY, category, null, null, x, y, width, height); }
        private static HitRegion module(GuiCategory category, GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE, category, module, null, x, y, width, height); }
        private static HitRegion moduleToggle(GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE_TOGGLE, null, module, null, x, y, width, height); }
        private static HitRegion setting(GuiSetting setting, int x, int y, int width, int height) { return new HitRegion(RegionKind.SETTING, null, null, setting, x, y, width, height); }
        private static HitRegion close(int x, int y, int width, int height) { return new HitRegion(RegionKind.CLOSE_SETTINGS, null, null, null, x, y, width, height); }
        private static HitRegion dragWorkspace(int x, int y, int width, int height) { return new HitRegion(RegionKind.DRAG_WORKSPACE, null, null, null, x, y, width, height); }
        private static HitRegion dragDetail(int x, int y, int width, int height) { return new HitRegion(RegionKind.DRAG_DETAIL, null, null, null, x, y, width, height); }
        private boolean contains(int mouseX, int mouseY) { return ArcaneSettingsScreen.contains(this.x, this.y, this.width, this.height, mouseX, mouseY); }
    }
}
