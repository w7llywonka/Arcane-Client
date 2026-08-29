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

/** Arcane's application-style control center. */
@Environment(EnvType.CLIENT)
public final class ArcaneSettingsScreen extends Screen {
    private static final int FRAME_MARGIN = 10;
    private static final int FRAME_MAX_WIDTH = 760;
    private static final int FRAME_MAX_HEIGHT = 430;
    private static final int SIDEBAR_WIDTH = 116;
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 22;
    private static final int CONTENT_PAD = 10;
    private static final int PANEL_GAP = 9;
    private static final int MODULE_ROW_HEIGHT = 39;
    private static final int NAV_ROW_HEIGHT = 27;
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
        int searchWidth = Math.clamp(this.workspaceWidth / 3, 116, 190);
        int searchX = this.frameX + this.frameWidth - searchWidth - 14;
        this.searchInput = new TextFieldWidget(font(), searchX, this.frameY + 18, searchWidth, 12, Text.literal("Search modules"));
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
        this.frameWidth = Math.min(FRAME_MAX_WIDTH, Math.max(430, this.width - FRAME_MARGIN * 2));
        this.frameHeight = Math.min(FRAME_MAX_HEIGHT, Math.max(260, this.height - FRAME_MARGIN * 2));
        this.frameX = (this.width - this.frameWidth) / 2;
        this.frameY = (this.height - this.frameHeight) / 2;
        this.workspaceX = this.frameX + SIDEBAR_WIDTH;
        this.workspaceY = this.frameY + HEADER_HEIGHT;
        this.workspaceWidth = this.frameWidth - SIDEBAR_WIDTH;
        this.workspaceHeight = this.frameHeight - HEADER_HEIGHT - FOOTER_HEIGHT;
        int innerWidth = this.workspaceWidth - CONTENT_PAD * 2;
        this.listWidth = Math.clamp(innerWidth * 43 / 100, 188, 260);
        this.listX = this.workspaceX + CONTENT_PAD;
        this.listY = this.workspaceY + CONTENT_PAD;
        this.listHeight = this.workspaceHeight - CONTENT_PAD * 2;
        this.detailX = this.listX + this.listWidth + PANEL_GAP;
        this.detailY = this.listY;
        this.detailWidth = Math.max(185, innerWidth - this.listWidth - PANEL_GAP);
        this.detailHeight = this.listHeight;
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        ClickGuiColors theme = theme();
        this.hitRegions.clear();
        boolean[] macroDrawn = new boolean[MACRO_COUNT];
        graphics.fill(0, 0, this.width, this.height, theme.backdrop());
        drawFrame(graphics, theme);
        drawSidebar(graphics, mouseX, mouseY, theme);
        drawHeader(graphics, theme);
        drawModuleList(graphics, mouseX, mouseY, theme);
        drawInspector(graphics, mouseX, mouseY, theme, macroDrawn);
        drawFooter(graphics, theme);
        for (int slot = 0; slot < MACRO_COUNT; slot++) if (!macroDrawn[slot]) hideMacroInput(slot);
        super.render(graphics, mouseX, mouseY, deltaTicks);
    }

    private void drawFrame(DrawContext graphics, ClickGuiColors theme) {
        RoundedGui.fill(graphics, this.frameX + 4, this.frameY + 6, this.frameWidth, this.frameHeight, 12, 0x62000000);
        RoundedGui.fill(graphics, this.frameX, this.frameY, this.frameWidth, this.frameHeight, 12, theme.bar());
        RoundedGui.fill(graphics, this.frameX + 1, this.frameY + 1, SIDEBAR_WIDTH - 1, this.frameHeight - 2, 11, theme.window());
        graphics.fill(this.workspaceX, this.frameY + HEADER_HEIGHT, this.frameX + this.frameWidth, this.frameY + this.frameHeight - FOOTER_HEIGHT, theme.nest());
        graphics.fill(this.workspaceX, this.frameY + 12, this.workspaceX + 1, this.frameY + this.frameHeight - 12, theme.outlineSoft());
        graphics.fill(this.workspaceX + 12, this.workspaceY - 1, this.frameX + this.frameWidth - 12, this.workspaceY, theme.outlineSoft());
    }

    private void drawSidebar(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        int brandX = this.frameX + 14;
        int brandY = this.frameY + 13;
        RoundedGui.fill(graphics, brandX, brandY, 22, 22, 7, theme.accent());
        graphics.drawText(font(), ArcaneFont.text("A"), brandX + 7, brandY + 7, opaque(theme.bar()), false);
        graphics.drawText(font(), ArcaneFont.text("ARCANE"), brandX + 29, brandY + 4, theme.text(), false);
        graphics.drawText(font(), ArcaneFont.text("CLIENT"), brandX + 29, brandY + 14, theme.accentBright(), false);

        int navY = this.frameY + 58;
        graphics.drawText(font(), ArcaneFont.text("WORKSPACE"), brandX, navY, theme.faint(), false);
        navY += 15;
        for (int index = 0; index < this.categories.size(); index++) {
            GuiCategory category = this.categories.get(index);
            boolean selected = category == this.selectedCategory;
            boolean hovered = contains(brandX - 5, navY, SIDEBAR_WIDTH - 18, NAV_ROW_HEIGHT - 3, mouseX, mouseY);
            if (selected) {
                RoundedGui.fill(graphics, brandX - 5, navY, SIDEBAR_WIDTH - 18, NAV_ROW_HEIGHT - 3, 7, theme.active());
                RoundedGui.fill(graphics, brandX - 5, navY + 7, 3, NAV_ROW_HEIGHT - 17, 2, theme.accent());
            } else if (hovered) {
                RoundedGui.fill(graphics, brandX - 5, navY, SIDEBAR_WIDTH - 18, NAV_ROW_HEIGHT - 3, 7, theme.hover());
            }
            String number = String.format(Locale.ROOT, "%02d", index + 1);
            int textY = navY + (NAV_ROW_HEIGHT - 3 - lineHeight()) / 2;
            graphics.drawText(font(), ArcaneFont.text(number), brandX + 2, textY, selected ? theme.accentBright() : theme.faint(), false);
            OrderedText label = ArcaneFont.trimmed(font(), titleCase(category.name()), SIDEBAR_WIDTH - 54);
            graphics.drawText(font(), label, brandX + 24, textY, selected ? theme.text() : theme.muted(), false);
            this.hitRegions.add(HitRegion.category(category, brandX - 5, navY, SIDEBAR_WIDTH - 18, NAV_ROW_HEIGHT - 3));
            navY += NAV_ROW_HEIGHT;
        }

        if (this.frameHeight >= 310) {
            int cardX = this.frameX + 10;
            int cardY = this.frameY + this.frameHeight - 54;
            RoundedGui.fill(graphics, cardX, cardY, SIDEBAR_WIDTH - 20, 39, 8, theme.header());
            RoundedGui.fill(graphics, cardX + 9, cardY + 10, 7, 7, 4, theme.accent());
            graphics.drawText(font(), ArcaneFont.text("SYSTEM ONLINE"), cardX + 21, cardY + 8, theme.text(), false);
            graphics.drawText(font(), ArcaneFont.text(activeModuleCount() + " active modules"), cardX + 9, cardY + 22, theme.faint(), false);
        }
    }

    private void drawHeader(DrawContext graphics, ClickGuiColors theme) {
        int x = this.workspaceX + 14;
        int y = this.frameY + 11;
        String categoryName = this.query.isEmpty() ? titleCase(this.selectedCategory.name()) : "Search results";
        graphics.drawText(font(), ArcaneFont.text(categoryName), x, y, theme.text(), false);
        String context = this.query.isEmpty()
            ? this.selectedCategory.visible().size() + " modules · " + this.selectedCategory.enabledCount() + " active"
            : visibleModuleCount() + " matches across Arcane";
        graphics.drawText(font(), ArcaneFont.text(context), x, y + 15, theme.faint(), false);
        int searchX = this.searchInput.getX();
        int searchY = this.searchInput.getY();
        int searchWidth = this.searchInput.getWidth();
        boolean focused = this.searchInput.isFocused();
        RoundedGui.fill(graphics, searchX - 25, searchY - 5, searchWidth + 31, 22, 8, focused ? theme.hover() : theme.header());
        graphics.drawText(font(), ArcaneFont.text("⌕"), searchX - 17, searchY + 1, focused ? theme.accentBright() : theme.faint(), false);
        if (focused) graphics.fill(searchX - 18, searchY + 15, searchX + searchWidth, searchY + 16, theme.accentDim());
    }

    private void drawModuleList(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme) {
        RoundedGui.fill(graphics, this.listX, this.listY, this.listWidth, this.listHeight, 10, theme.window());
        graphics.drawText(font(), ArcaneFont.text("MODULES"), this.listX + 12, this.listY + 10, theme.faint(), false);
        List<ModuleEntry> entries = visibleEntries();
        int viewportY = this.listY + 27;
        int viewportHeight = this.listHeight - 34;
        int contentHeight = entries.size() * MODULE_ROW_HEIGHT;
        this.listMaxScroll = Math.max(0, contentHeight - viewportHeight);
        this.listScroll = Math.clamp(this.listScroll, 0, this.listMaxScroll);
        graphics.enableScissor(this.listX + 5, viewportY, this.listX + this.listWidth - 5, viewportY + viewportHeight);
        int rowY = viewportY - this.listScroll;
        for (ModuleEntry entry : entries) {
            if (rowY + MODULE_ROW_HEIGHT >= viewportY && rowY <= viewportY + viewportHeight) drawModuleCard(graphics, entry, rowY, mouseX, mouseY, theme);
            rowY += MODULE_ROW_HEIGHT;
        }
        graphics.disableScissor();
        if (entries.isEmpty()) graphics.drawCenteredTextWithShadow(font(), ArcaneFont.text("No matching modules"), this.listX + this.listWidth / 2, this.listY + this.listHeight / 2, theme.faint());
        drawScrollBar(graphics, this.listX + this.listWidth - 5, viewportY, viewportHeight, this.listScroll, this.listMaxScroll, contentHeight, theme);
    }

    private void drawModuleCard(DrawContext graphics, ModuleEntry entry, int y, int mouseX, int mouseY, ClickGuiColors theme) {
        GuiModule module = entry.module();
        int x = this.listX + 7;
        int width = this.listWidth - 14;
        int height = MODULE_ROW_HEIGHT - 5;
        boolean selected = module == this.selectedModule;
        boolean hovered = contains(x, y, width, height, mouseX, mouseY);
        RoundedGui.fill(graphics, x, y, width, height, 8, selected ? theme.active() : hovered ? theme.hover() : theme.row());
        if (selected) RoundedGui.fill(graphics, x, y + 9, 3, height - 18, 2, theme.accent());
        int labelX = x + 11;
        int labelY = y + 7;
        int toggleReserve = module.toggleable() ? 37 : 10;
        graphics.drawText(font(), ArcaneFont.trimmed(font(), module.name(), width - toggleReserve - 18), labelX, labelY, selected ? theme.text() : theme.muted(), false);
        String meta = this.query.isEmpty() ? shortDescription(module.description(), width - 56) : titleCase(entry.category().name());
        graphics.drawText(font(), ArcaneFont.trimmed(font(), meta, width - 56), labelX, labelY + 13, theme.faint(), false);
        if (module.toggleable()) drawSwitch(graphics, x + width - 30, y + 11, module.enabled(), theme);
        else if (module.valueLabel() != null) {
            OrderedText value = ArcaneFont.trimmed(font(), module.valueLabel(), 48);
            graphics.drawText(font(), value, x + width - font().getWidth(value) - 9, labelY + 13, theme.accentBright(), false);
        }
        int viewportTop = this.listY + 27;
        int viewportBottom = this.listY + this.listHeight - 7;
        if (y >= viewportTop && y + height <= viewportBottom) {
            this.hitRegions.add(HitRegion.module(entry.category(), module, x, y, width, height));
        }
    }

    private void drawInspector(DrawContext graphics, int mouseX, int mouseY, ClickGuiColors theme, boolean[] macroDrawn) {
        RoundedGui.fill(graphics, this.detailX, this.detailY, this.detailWidth, this.detailHeight, 10, theme.window());
        GuiModule module = this.selectedModule;
        if (module == null) {
            graphics.drawCenteredTextWithShadow(font(), ArcaneFont.text("Select a module"), this.detailX + this.detailWidth / 2, this.detailY + this.detailHeight / 2, theme.faint());
            this.detailMaxScroll = 0;
            return;
        }
        int pad = 13;
        int contentX = this.detailX + pad;
        int contentWidth = this.detailWidth - pad * 2;
        List<String> description = wrap(module.description(), Math.max(70, contentWidth - 2));
        int introHeight = 50 + description.size() * proseHeight();
        int settingsHeight = module.hasSettings() ? 18 : 0;
        for (GuiSetting setting : module.settings()) settingsHeight += settingHeight(setting) + 4;
        int contentHeight = introHeight + settingsHeight + 12;
        this.detailMaxScroll = Math.max(0, contentHeight - this.detailHeight);
        this.detailScroll = Math.clamp(this.detailScroll, 0, this.detailMaxScroll);
        graphics.enableScissor(this.detailX + 1, this.detailY + 1, this.detailX + this.detailWidth - 1, this.detailY + this.detailHeight - 1);
        int y = this.detailY + 12 - this.detailScroll;
        graphics.drawText(font(), ArcaneFont.text("MODULE SETTINGS"), contentX, y, theme.faint(), false);
        y += 17;
        graphics.drawText(font(), ArcaneFont.text(module.name()), contentX, y, theme.text(), false);
        if (module.toggleable()) {
            drawSwitch(graphics, this.detailX + this.detailWidth - 37, y - 2, module.enabled(), theme);
            if (y - 6 >= this.detailY && y + 16 <= this.detailY + this.detailHeight) {
                this.hitRegions.add(HitRegion.moduleToggle(module, this.detailX + this.detailWidth - 42, y - 6, 34, 22));
            }
        }
        y += 17;
        for (String line : description) {
            graphics.drawText(font(), ArcaneFont.text(line), contentX, y, theme.muted(), false);
            y += proseHeight();
        }
        y += 8;
        if (!module.hasSettings()) {
            RoundedGui.fill(graphics, contentX, y, contentWidth, 30, 8, theme.header());
            graphics.drawText(font(), ArcaneFont.text("No additional configuration"), contentX + 10, y + 10, theme.faint(), false);
        } else {
            graphics.drawText(font(), ArcaneFont.text("CONFIGURATION"), contentX, y, theme.faint(), false);
            y += 15;
            for (GuiSetting setting : module.settings()) {
                int height = settingHeight(setting);
                drawSetting(graphics, setting, contentX, y, contentWidth, height, mouseX, mouseY, theme, macroDrawn);
                y += height + 4;
            }
        }
        graphics.disableScissor();
        drawScrollBar(graphics, this.detailX + this.detailWidth - 5, this.detailY + 6, this.detailHeight - 12, this.detailScroll, this.detailMaxScroll, contentHeight, theme);
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

    private void drawFooter(DrawContext graphics, ClickGuiColors theme) {
        int y = this.frameY + this.frameHeight - FOOTER_HEIGHT;
        graphics.fill(this.workspaceX + 12, y, this.frameX + this.frameWidth - 12, y + 1, theme.outlineSoft());
        int textY = y + (FOOTER_HEIGHT - lineHeight()) / 2;
        String hint = this.listeningFor != null ? "Press a key · Esc clears the bind" : "Left click toggles · Right click configures · Middle click binds";
        graphics.drawText(font(), ArcaneFont.text(hint), this.workspaceX + 14, textY, this.listeningFor != null ? theme.accentBright() : theme.faint(), false);
        updateFpsLabel();
        int right = this.frameX + this.frameWidth - 14;
        graphics.drawText(font(), ArcaneFont.text(this.fpsLabel), right - ArcaneFont.width(font(), this.fpsLabel), textY, theme.muted(), false);
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
        if (contains(this.listX, this.listY, this.listWidth, this.listHeight, mouseX, mouseY)) {
            this.listScroll = Math.clamp(this.listScroll + amount, 0, this.listMaxScroll);
            return true;
        }
        if (contains(this.detailX, this.detailY, this.detailWidth, this.detailHeight, mouseX, mouseY)) {
            this.detailScroll = Math.clamp(this.detailScroll + amount, 0, this.detailMaxScroll);
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
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.draggingSlider != null) {
            this.draggingSlider = null;
            return true;
        }
        return super.mouseReleased(click);
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
        return super.keyPressed(input);
    }

    private void selectCategory(@Nullable GuiCategory category) {
        if (category == null) return;
        this.selectedCategory = category;
        this.selectedModule = category.visible().isEmpty() ? null : category.visible().getFirst();
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

    private enum RegionKind { CATEGORY, MODULE, MODULE_TOGGLE, SETTING }
    private record WrapKey(String text, int maxWidth) { }
    private record ModuleEntry(GuiCategory category, GuiModule module) { }

    private record HitRegion(RegionKind kind, @Nullable GuiCategory category, @Nullable GuiModule module, @Nullable GuiSetting setting, int x, int y, int width, int height) {
        private static HitRegion category(GuiCategory category, int x, int y, int width, int height) { return new HitRegion(RegionKind.CATEGORY, category, null, null, x, y, width, height); }
        private static HitRegion module(GuiCategory category, GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE, category, module, null, x, y, width, height); }
        private static HitRegion moduleToggle(GuiModule module, int x, int y, int width, int height) { return new HitRegion(RegionKind.MODULE_TOGGLE, null, module, null, x, y, width, height); }
        private static HitRegion setting(GuiSetting setting, int x, int y, int width, int height) { return new HitRegion(RegionKind.SETTING, null, null, setting, x, y, width, height); }
        private boolean contains(int mouseX, int mouseY) { return ArcaneSettingsScreen.contains(this.x, this.y, this.width, this.height, mouseX, mouseY); }
    }
}
