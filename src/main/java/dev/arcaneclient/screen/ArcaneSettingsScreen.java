package dev.arcaneclient.screen;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.ArcaneKeybinds;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.EspRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class ArcaneSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 148;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 18;
    private static final int SETTING_HEIGHT = 16;
    private static final int SLIDER_HEIGHT = 27;
    private static final int MACRO_ROW_HEIGHT = 20;
    private static final int TOP_BAR_HEIGHT = 28;
    private static final int GAP = 8;
    private static final Theme[] THEMES = Theme.values();
    private static final ItemEspCategory[] ITEM_CATEGORIES = ItemEspCategory.values();

    private final Screen parent;
    private final ArcaneConfig config;
    private final boolean scannerWasEnabled;
    private final Panel finderPanel = new Panel();
    private final Panel renderPanel = new Panel();
    private final Panel playerPanel = new Panel();
    private final Panel bindsPanel = new Panel();
    private final Panel chatPanel = new Panel();
    private final List<TextFieldWidget> macroInputs = new ArrayList<>();
    private final List<ToggleRow> finderSettings;
    private final List<VisualRow> visualRows;
    private final List<ToggleRow> playerRows;
    private final List<ArcaneKeybinds.Entry> bindingRows;
    private final ToggleRow storageTracerRow;
    private final ToggleRow itemTracerRow;

    private List<VisualRow> filteredVisualRows;
    private List<ToggleRow> filteredPlayerRows;
    private List<ArcaneKeybinds.Entry> filteredBindingRows;
    private TextRenderer uiFont;

    private TextFieldWidget searchInput;
    private String searchText = "";
    private String normalizedQuery = "";
    private boolean macroInputsVisible;
    private String performanceLabel = "";
    private String compactFpsLabel = "";
    private long nextPerformanceLabelUpdateNanos;
    private boolean finderExpanded;
    private boolean chatExpanded;
    private VisualModule expandedVisual;
    private boolean draggingSensitivity;
    private Panel draggingPanel;
    private int panelDragOffsetX;
    private int panelDragOffsetY;
    private boolean panelMoved;
    private KeyBinding listeningFor;

    public ArcaneSettingsScreen(Screen parent) {
        super(Text.literal("Arcane Client"));
        this.parent = parent;
        this.config = ArcaneClient.config();
        this.scannerWasEnabled = this.config.enabled;
        this.finderSettings = List.of(
            new ToggleRow("Deep focus", () -> this.config.deepFocus, value -> this.config.deepFocus = value),
            new ToggleRow("Farms", () -> this.config.farmSignals, value -> this.config.farmSignals = value),
            new ToggleRow("Machines", () -> this.config.machineSignals, value -> this.config.machineSignals = value),
            new ToggleRow("Player blocks", () -> this.config.playerBlockSignals, value -> this.config.playerBlockSignals = value),
            new ToggleRow("Live activity", () -> this.config.packetSignals, value -> this.config.packetSignals = value),
            new ToggleRow("Light leaks", () -> this.config.lightSignals, value -> this.config.lightSignals = value),
            new ToggleRow("Entities", () -> this.config.entitySignals, value -> this.config.entitySignals = value)
        );
        this.visualRows = List.of(
            new VisualRow(VisualModule.CHUNK_TILES, "Chunk tiles", () -> this.config.overlay, value -> this.config.overlay = value),
            new VisualRow(VisualModule.CHUNK_RADAR, "Radar HUD", () -> this.config.hud, value -> this.config.hud = value),
            new VisualRow(VisualModule.CHUNK_ANALYSIS, "Chunk intel", () -> this.config.chunkAnalysis, value -> this.config.chunkAnalysis = value),
            new VisualRow(VisualModule.STORAGE_ESP, "Storage ESP", () -> this.config.esp, value -> this.config.esp = value),
            new VisualRow(VisualModule.ITEM_ESP, "Item ESP", () -> this.config.itemEsp, value -> this.config.itemEsp = value),
            new VisualRow(VisualModule.TUNNEL_ESP, "Tunnel ESP", () -> this.config.tunnelEsp, value -> {
                this.config.tunnelEsp = value;
                ArcaneClient.engine().tunnelSettingsChanged(this.client);
            }),
            new VisualRow(VisualModule.STASH_ALERTS, "Stash alerts", () -> this.config.stashAlerts, value -> this.config.stashAlerts = value),
            new VisualRow(VisualModule.BLOCK_ENTITY_DEBUG, "ESP diagnostics", () -> this.config.blockEntityDebug, value -> this.config.blockEntityDebug = value)
        );
        this.playerRows = List.of(
            new ToggleRow("Auto Totem", () -> this.config.autoTotem, value -> this.config.autoTotem = value),
            new ToggleRow("Freecam", FreecamController::isActive, value -> {
                if (value != FreecamController.isActive()) FreecamController.toggle(this.client);
            })
        );
        this.bindingRows = ArcaneClient.keybinds().editable();
        this.storageTracerRow = new ToggleRow("Tracers", () -> this.config.storageTracers, value -> this.config.storageTracers = value);
        this.itemTracerRow = new ToggleRow("Tracers", () -> this.config.itemTracers, value -> this.config.itemTracers = value);
        this.filteredVisualRows = this.visualRows;
        this.filteredPlayerRows = this.playerRows;
        this.filteredBindingRows = this.bindingRows;
    }

    @Override
    protected void init() {
        layoutPanels();
        int searchWidth = searchWidth();
        this.searchInput = new TextFieldWidget(font(), searchX(searchWidth), 7, searchWidth, 15, Text.literal("Search modules"));
        this.searchInput.setDrawsBackground(false);
        this.searchInput.setTextShadow(false);
        this.searchInput.setMaxLength(48);
        this.searchInput.setPlaceholder(Text.literal("Search modules..."));
        this.searchInput.setText(this.searchText);
        this.searchInput.setChangedListener(this::updateFilter);
        updateFilter(this.searchText);
        this.addDrawableChild(this.searchInput);

        this.macroInputs.clear();
        for (int index = 0; index < 4; index++) {
            int slot = index;
            TextFieldWidget input = new TextFieldWidget(font(), 0, 0, 84, 14, Text.literal("Chat macro " + (index + 1)));
            input.setDrawsBackground(false);
            input.setTextShadow(false);
            input.setMaxLength(256);
            input.setText(this.config.chatMacro(index));
            input.setPlaceholder(Text.literal("message"));
            input.setChangedListener(value -> this.config.setChatMacro(slot, value));
            input.setVisible(false);
            this.macroInputs.add(this.addDrawableChild(input));
        }
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float deltaTicks) {
        Theme theme = theme();
        this.renderInGameBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, theme.backdrop);
        drawTopBar(graphics, mouseX, mouseY, theme);
        clampPanels();
        drawFinder(graphics, mouseX, mouseY, theme);
        drawRender(graphics, mouseX, mouseY, theme);
        drawPlayer(graphics, mouseX, mouseY, theme);
        drawBinds(graphics, mouseX, mouseY, theme);
        drawChat(graphics, mouseX, mouseY, theme);
        drawBottomBar(graphics, theme);
        super.render(graphics, mouseX, mouseY, deltaTicks);
    }

    private void drawTopBar(DrawContext graphics, int mouseX, int mouseY, Theme theme) {
        graphics.fill(0, 0, this.width, TOP_BAR_HEIGHT, theme.topBar);
        graphics.fill(0, TOP_BAR_HEIGHT - 2, this.width / 2, TOP_BAR_HEIGHT, theme.accent);
        graphics.fill(this.width / 2, TOP_BAR_HEIGHT - 2, this.width, TOP_BAR_HEIGHT, theme.secondary);
        graphics.drawText(font(), "ARCANE", 8, 9, theme.text, true);
        int clientX = 10 + font().getWidth("ARCANE");
        graphics.drawText(font(), "CLIENT", clientX, 9, theme.accent, true);
        if (this.width >= 360) {
            updatePerformanceLabel();
            String status = this.width >= 560 ? this.performanceLabel : this.compactFpsLabel;
            graphics.drawText(font(), status, clientX + font().getWidth("CLIENT") + 12, 9, theme.muted, false);
        }

        int searchWidth = searchWidth();
        int searchX = searchX(searchWidth);
        int searchBorder = this.searchInput != null && this.searchInput.isFocused() ? theme.accent : theme.border;
        fillSoftRect(graphics, searchX - 5, 5, searchWidth + 10, 18, searchBorder);
        fillSoftRect(graphics, searchX - 4, 6, searchWidth + 8, 16, theme.setting);

        int themeX = themeButtonX();
        boolean hovered = inside(mouseX, mouseY, themeX, 5, 84, 18);
        fillSoftRect(graphics, themeX, 5, 84, 18, hovered ? theme.accent : theme.border);
        fillSoftRect(graphics, themeX + 1, 6, 82, 16, hovered ? theme.hover : theme.setting);
        graphics.drawCenteredTextWithShadow(font(), theme.label + "  >", themeX + 42, 9, theme.text);
    }

    private void drawBottomBar(DrawContext graphics, Theme theme) {
        int y = this.height - 18;
        graphics.fill(0, y, this.width, this.height, theme.topBar);
        String help = this.listeningFor == null
            ? "LMB toggle   RMB configure   Drag headers to arrange"
            : "Press a key or mouse button   Esc clears";
        graphics.drawText(font(), help, 7, y + 5, this.listeningFor == null ? theme.muted : theme.accent, false);
        if (!query().isEmpty()) {
            String result = visibleModuleCount() + " results";
            graphics.drawText(font(), result, this.width - 7 - font().getWidth(result), y + 5, theme.secondary, false);
        }
    }

    private void drawFinder(DrawContext graphics, int mouseX, int mouseY, Theme theme) {
        boolean visible = matches("Base scanner");
        int settingsHeight = this.finderExpanded ? SLIDER_HEIGHT + finderSettings().size() * SETTING_HEIGHT : 0;
        int height = HEADER_HEIGHT + (this.finderPanel.open && visible ? ROW_HEIGHT + settingsHeight : 0);
        drawPanel(graphics, this.finderPanel, height, "DISCOVERY", visible ? (this.config.enabled ? "1/1" : "0/1") : "0/0", mouseX, mouseY, theme);
        if (!this.finderPanel.open || !visible) return;

        int rowY = this.finderPanel.y + HEADER_HEIGHT;
        drawModuleRow(graphics, this.finderPanel.x, rowY, "Base scanner", this.config.enabled, true, this.finderExpanded, mouseX, mouseY, theme);
        if (!this.finderExpanded) return;
        int sliderY = rowY + ROW_HEIGHT;
        drawSensitivity(graphics, this.finderPanel.x, sliderY, mouseX, mouseY, theme);
        int settingY = sliderY + SLIDER_HEIGHT;
        for (ToggleRow setting : finderSettings()) {
            drawSettingToggle(graphics, this.finderPanel.x, settingY, setting, mouseX, mouseY, theme);
            settingY += SETTING_HEIGHT;
        }
    }

    private void drawRender(DrawContext graphics, int mouseX, int mouseY, Theme theme) {
        List<VisualRow> rows = visibleVisualRows();
        int contentHeight = rows.size() * ROW_HEIGHT;
        if (this.expandedVisual != null && containsVisualModule(rows, this.expandedVisual)) {
            contentHeight += visualSettingsHeight(this.expandedVisual);
        }
        int height = HEADER_HEIGHT + (this.renderPanel.open ? contentHeight : 0);
        drawPanel(graphics, this.renderPanel, height, "VISUAL", activeVisualCount(rows) + "/" + rows.size(), mouseX, mouseY, theme);
        if (!this.renderPanel.open) return;
        int rowY = this.renderPanel.y + HEADER_HEIGHT;
        for (VisualRow row : rows) {
            boolean expanded = row.module() == this.expandedVisual;
            drawModuleRow(graphics, this.renderPanel.x, rowY, row.label(), row.value().getAsBoolean(), row.hasSettings(), expanded, mouseX, mouseY, theme);
            rowY += ROW_HEIGHT;
            if (expanded) {
                drawVisualSettings(graphics, row.module(), this.renderPanel.x, rowY, mouseX, mouseY, theme);
                rowY += visualSettingsHeight(row.module());
            }
        }
    }

    private void drawPlayer(DrawContext graphics, int mouseX, int mouseY, Theme theme) {
        List<ToggleRow> rows = visiblePlayerRows();
        boolean profileVisible = performanceProfileVisible();
        int rowCount = rows.size() + (profileVisible ? 1 : 0);
        int height = HEADER_HEIGHT + (this.playerPanel.open ? rowCount * ROW_HEIGHT : 0);
        drawPanel(graphics, this.playerPanel, height, "PLAYER", activeToggleCount(rows) + "/" + rows.size(), mouseX, mouseY, theme);
        if (!this.playerPanel.open) return;
        int rowY = this.playerPanel.y + HEADER_HEIGHT;
        for (ToggleRow row : rows) {
            drawModuleRow(graphics, this.playerPanel.x, rowY, row.label(), row.value().getAsBoolean(), false, false, mouseX, mouseY, theme);
            rowY += ROW_HEIGHT;
        }
        if (profileVisible) {
            drawPerformanceRow(graphics, this.playerPanel.x, rowY, mouseX, mouseY, theme);
        }
    }

    private void drawPerformanceRow(DrawContext graphics, int x, int y, int mouseX, int mouseY, Theme theme) {
        drawRowBackground(graphics, x, y, ROW_HEIGHT, mouseX, mouseY, theme);
        graphics.fill(x + 8, y + 7, x + 11, y + 10, theme.accent);
        graphics.drawText(font(), "Performance", x + 15, y + 5, theme.text, false);
        String label = this.config.performanceProfile().label();
        graphics.drawText(font(), label, x + PANEL_WIDTH - 8 - font().getWidth(label), y + 5, theme.secondary, false);
    }

    private void drawBinds(DrawContext graphics, int mouseX, int mouseY, Theme theme) {
        List<ArcaneKeybinds.Entry> entries = visibleBindings();
        int height = HEADER_HEIGHT + (this.bindsPanel.open ? entries.size() * ROW_HEIGHT : 0);
        drawPanel(graphics, this.bindsPanel, height, "KEYBINDS", Integer.toString(entries.size()), mouseX, mouseY, theme);
        if (!this.bindsPanel.open) return;
        int rowY = this.bindsPanel.y + HEADER_HEIGHT;
        for (ArcaneKeybinds.Entry entry : entries) {
            drawRowBackground(graphics, this.bindsPanel.x, rowY, ROW_HEIGHT, mouseX, mouseY, theme);
            graphics.drawText(font(), entry.label(), this.bindsPanel.x + 8, rowY + 5, theme.text, false);
            String key = this.listeningFor == entry.mapping() ? "..." : entry.mapping().getBoundKeyLocalizedText().getString();
            key = font().trimToWidth(key, 46);
            int color = this.listeningFor == entry.mapping() ? theme.accent : theme.muted;
            graphics.drawText(font(), key, this.bindsPanel.x + PANEL_WIDTH - 8 - font().getWidth(key), rowY + 5, color, false);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawChat(DrawContext graphics, int mouseX, int mouseY, Theme theme) {
        boolean visible = matches("Chat macros");
        setMacroInputsVisible(this.chatPanel.open && this.chatExpanded && visible);
        int settingsHeight = this.chatExpanded ? MACRO_ROW_HEIGHT * 4 : 0;
        int height = HEADER_HEIGHT + (this.chatPanel.open && visible ? ROW_HEIGHT + settingsHeight : 0);
        drawPanel(graphics, this.chatPanel, height, "SOCIAL", visible ? (this.config.chatMacros ? "1/1" : "0/1") : "0/0", mouseX, mouseY, theme);
        if (!this.chatPanel.open || !visible) return;

        int moduleY = this.chatPanel.y + HEADER_HEIGHT;
        drawModuleRow(graphics, this.chatPanel.x, moduleY, "Chat macros", this.config.chatMacros, true, this.chatExpanded, mouseX, mouseY, theme);
        if (!this.chatExpanded) return;

        int rowY = moduleY + ROW_HEIGHT;
        for (int index = 0; index < 4; index++) {
            graphics.fill(this.chatPanel.x + 1, rowY, this.chatPanel.x + PANEL_WIDTH - 1, rowY + MACRO_ROW_HEIGHT, theme.setting);
            graphics.fill(this.chatPanel.x + 7, rowY + 5, this.chatPanel.x + 10, rowY + 15, theme.secondary);
            graphics.drawText(font(), "M" + (index + 1), this.chatPanel.x + 14, rowY + 6, theme.muted, false);
            int inputX = this.chatPanel.x + 31;
            graphics.fill(inputX - 2, rowY + 3, inputX + 82, rowY + 18, theme.row);
            graphics.drawStrokedRectangle(inputX - 2, rowY + 3, 84, 15, theme.border);
            TextFieldWidget input = this.macroInputs.get(index);
            input.setX(inputX);
            input.setY(rowY + 4);
            input.setVisible(true);

            KeyBinding mapping = ArcaneClient.keybinds().chatMacros().get(index);
            String key = this.listeningFor == mapping ? "..." : mapping.getBoundKeyLocalizedText().getString();
            key = font().trimToWidth(key, 26);
            if (inside(mouseX, mouseY, this.chatPanel.x + 116, rowY, 32, MACRO_ROW_HEIGHT)) {
                graphics.fill(this.chatPanel.x + 115, rowY + 2, this.chatPanel.x + PANEL_WIDTH - 2, rowY + 18, theme.hover);
            }
            int keyColor = this.listeningFor == mapping ? theme.accent : theme.muted;
            graphics.drawText(font(), key, this.chatPanel.x + PANEL_WIDTH - 6 - font().getWidth(key), rowY + 6, keyColor, false);
            rowY += MACRO_ROW_HEIGHT;
        }
    }

    private void drawPanel(DrawContext graphics, Panel panel, int height, String title, String badge, int mouseX, int mouseY, Theme theme) {
        panel.lastHeight = height;
        fillSoftRect(graphics, panel.x + 3, panel.y + 4, PANEL_WIDTH, height, 0x50000000);
        fillSoftRect(graphics, panel.x, panel.y, PANEL_WIDTH, height, theme.border);
        fillSoftRect(graphics, panel.x + 1, panel.y + 1, PANEL_WIDTH - 2, height - 2, theme.panel);
        graphics.fill(panel.x + 2, panel.y + 3, panel.x + PANEL_WIDTH - 2, panel.y + HEADER_HEIGHT, theme.header);
        graphics.fill(panel.x + 9, panel.y + 1, panel.x + 47, panel.y + 3, theme.accent);
        if (inside(mouseX, mouseY, panel.x, panel.y, PANEL_WIDTH, HEADER_HEIGHT)) {
            graphics.fill(panel.x + 2, panel.y + 3, panel.x + PANEL_WIDTH - 2, panel.y + HEADER_HEIGHT, 0x18FFFFFF);
        }
        graphics.drawText(font(), title, panel.x + 9, panel.y + 6, theme.text, false);
        int toggleX = panel.x + PANEL_WIDTH - 13;
        graphics.drawText(font(), panel.open ? "−" : "+", toggleX, panel.y + 6, theme.muted, false);
        if (badge != null && !badge.isEmpty()) {
            int badgeX = toggleX - 7 - font().getWidth(badge);
            graphics.drawText(font(), badge, badgeX, panel.y + 6, theme.secondary, false);
        }
    }

    private void drawModuleRow(DrawContext graphics, int x, int y, String label, boolean enabled, boolean hasSettings, boolean expanded, int mouseX, int mouseY, Theme theme) {
        drawRowBackground(graphics, x, y, ROW_HEIGHT, mouseX, mouseY, theme);
        fillSoftRect(graphics, x + 8, y + 7, 5, 5, enabled ? theme.accent : theme.border);
        graphics.drawText(font(), label, x + 17, y + 5, enabled ? theme.text : theme.muted, false);
        if (hasSettings) graphics.drawText(font(), expanded ? "⌄" : "›", x + PANEL_WIDTH - 40, y + 5, theme.muted, false);
        drawSwitch(graphics, x + PANEL_WIDTH - 27, y + 4, enabled, theme);
    }

    private void drawSettingToggle(DrawContext graphics, int x, int y, ToggleRow row, int mouseX, int mouseY, Theme theme) {
        graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.setting);
        graphics.fill(x + 7, y, x + 8, y + SETTING_HEIGHT, theme.border);
        if (inside(mouseX, mouseY, x, y, PANEL_WIDTH, SETTING_HEIGHT)) {
            graphics.fill(x + 8, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.hover);
        }
        graphics.drawText(font(), row.label(), x + 13, y + 4, theme.muted, false);
        drawSwitch(graphics, x + PANEL_WIDTH - 25, y + 4, row.value().getAsBoolean(), theme);
    }

    private void drawSensitivity(DrawContext graphics, int x, int y, int mouseX, int mouseY, Theme theme) {
        graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SLIDER_HEIGHT, theme.setting);
        graphics.fill(x + 7, y, x + 8, y + SLIDER_HEIGHT, theme.border);
        if (inside(mouseX, mouseY, x, y, PANEL_WIDTH, SLIDER_HEIGHT) || this.draggingSensitivity) {
            graphics.fill(x + 8, y, x + PANEL_WIDTH - 1, y + SLIDER_HEIGHT, theme.hover);
        }
        graphics.drawText(font(), "Sensitivity", x + 13, y + 4, theme.muted, false);
        String value = this.config.sensitivity() + "%";
        graphics.drawText(font(), value, x + PANEL_WIDTH - 8 - font().getWidth(value), y + 4, theme.text, false);
        int barX = x + 13;
        int barY = y + 19;
        int barWidth = PANEL_WIDTH - 26;
        graphics.fill(barX, barY, barX + barWidth, barY + 3, theme.border);
        int progress = Math.round(barWidth * this.config.sensitivity() / 100.0f);
        graphics.fill(barX, barY, barX + progress, barY + 3, theme.accent);
        graphics.fill(barX + Math.max(0, progress - 1), barY - 2, barX + progress + 2, barY + 5, theme.text);
    }

    private void drawVisualSettings(DrawContext graphics, VisualModule module, int x, int y, int mouseX, int mouseY, Theme theme) {
        switch (module) {
            case STORAGE_ESP -> drawSettingToggle(graphics, x, y, this.storageTracerRow, mouseX, mouseY, theme);
            case ITEM_ESP -> {
                drawSettingToggle(graphics, x, y, this.itemTracerRow, mouseX, mouseY, theme);
                int settingY = y + SETTING_HEIGHT;
                for (ItemEspCategory category : ITEM_CATEGORIES) {
                    drawItemSetting(graphics, x, settingY, category, mouseX, mouseY, theme);
                    settingY += SETTING_HEIGHT;
                }
            }
            case TUNNEL_ESP -> {
                drawColorSetting(graphics, x, y, "1 x 2 tunnels", this.config.tunnelTwoByOneColor, mouseX, mouseY, theme);
                drawColorSetting(graphics, x, y + SETTING_HEIGHT, "3 x 3 tunnels", this.config.tunnelThreeByThreeColor, mouseX, mouseY, theme);
            }
            case BLOCK_ENTITY_DEBUG -> {
                graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.setting);
                graphics.drawText(font(), "Visible targets", x + 13, y + 4, theme.muted, false);
                String count = Integer.toString(EspRenderer.targetCount());
                graphics.drawText(font(), count, x + PANEL_WIDTH - 8 - font().getWidth(count), y + 4, theme.secondary, false);
            }
            default -> {
            }
        }
    }

    private void drawItemSetting(DrawContext graphics, int x, int y, ItemEspCategory category, int mouseX, int mouseY, Theme theme) {
        graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.setting);
        if (inside(mouseX, mouseY, x, y, PANEL_WIDTH, SETTING_HEIGHT)) {
            graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.hover);
        }
        graphics.drawText(font(), category.label(), x + 13, y + 4, theme.muted, false);
        drawSwatch(graphics, x + PANEL_WIDTH - 46, y + 4, this.config.itemEspColor(category), theme);
        drawSwitch(graphics, x + PANEL_WIDTH - 25, y + 4, this.config.itemEspEnabled(category), theme);
    }

    private void drawColorSetting(DrawContext graphics, int x, int y, String label, int color, int mouseX, int mouseY, Theme theme) {
        graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.setting);
        if (inside(mouseX, mouseY, x, y, PANEL_WIDTH, SETTING_HEIGHT)) {
            graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + SETTING_HEIGHT, theme.hover);
        }
        graphics.drawText(font(), label, x + 13, y + 4, theme.muted, false);
        drawSwatch(graphics, x + PANEL_WIDTH - 19, y + 4, color, theme);
    }

    private void drawRowBackground(DrawContext graphics, int x, int y, int height, int mouseX, int mouseY, Theme theme) {
        graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + height, theme.row);
        if (inside(mouseX, mouseY, x, y, PANEL_WIDTH, height)) {
            graphics.fill(x + 1, y, x + PANEL_WIDTH - 1, y + height, theme.hover);
        }
    }

    private static void drawSwitch(DrawContext graphics, int x, int y, boolean enabled, Theme theme) {
        fillSoftRect(graphics, x, y, 20, 10, enabled ? theme.accent : theme.border);
        int knobX = enabled ? x + 11 : x + 2;
        fillSoftRect(graphics, knobX, y + 2, 7, 6, enabled ? theme.text : theme.muted);
    }

    private static void drawSwatch(DrawContext graphics, int x, int y, int color, Theme theme) {
        fillSoftRect(graphics, x, y, 10, 10, theme.border);
        fillSoftRect(graphics, x + 1, y + 1, 8, 8, color);
    }

    private static void fillSoftRect(DrawContext graphics, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.listeningFor != null) {
            setBinding(InputUtil.Type.MOUSE.createFromCode(click.button()));
            return true;
        }
        int button = click.button();
        if (button != 0 && button != 1) return super.mouseClicked(click, doubled);

        int mouseX = (int) click.x();
        int mouseY = (int) click.y();
        if (button == 0 && inside(mouseX, mouseY, themeButtonX(), 5, 84, 18)) {
            this.config.uiTheme = (this.config.uiTheme + 1) % THEMES.length;
            return true;
        }

        Panel header = headerAt(mouseX, mouseY);
        if (header != null && button == 0) {
            this.draggingPanel = header;
            this.panelDragOffsetX = mouseX - header.x;
            this.panelDragOffsetY = mouseY - header.y;
            this.panelMoved = false;
            return true;
        }
        if (handleFinderClick(mouseX, mouseY, button)
            || handleRenderClick(mouseX, mouseY, button)
            || handlePlayerClick(mouseX, mouseY, button)
            || handleChatClick(mouseX, mouseY, button)) return true;
        if (button == 0 && handleBindClick(mouseX, mouseY)) return true;
        return super.mouseClicked(click, doubled);
    }

    private boolean handleFinderClick(int mouseX, int mouseY, int button) {
        if (!this.finderPanel.open || !matches("Base scanner")) return false;
        int moduleY = this.finderPanel.y + HEADER_HEIGHT;
        if (inside(mouseX, mouseY, this.finderPanel.x, moduleY, PANEL_WIDTH, ROW_HEIGHT)) {
            if (button == 1) this.finderExpanded = !this.finderExpanded;
            else this.config.enabled = !this.config.enabled;
            return true;
        }
        if (!this.finderExpanded || button != 0) return false;

        int sliderY = moduleY + ROW_HEIGHT;
        if (inside(mouseX, mouseY, this.finderPanel.x, sliderY, PANEL_WIDTH, SLIDER_HEIGHT)) {
            this.draggingSensitivity = true;
            updateSensitivity(mouseX);
            return true;
        }
        int settingsY = sliderY + SLIDER_HEIGHT;
        List<ToggleRow> settings = finderSettings();
        int index = rowIndex(mouseY, settingsY, settings.size(), SETTING_HEIGHT);
        if (index >= 0 && inside(mouseX, mouseY, this.finderPanel.x, settingsY, PANEL_WIDTH, settings.size() * SETTING_HEIGHT)) {
            settings.get(index).toggle();
            return true;
        }
        return false;
    }

    private boolean handleRenderClick(int mouseX, int mouseY, int button) {
        if (!this.renderPanel.open) return false;
        int rowY = this.renderPanel.y + HEADER_HEIGHT;
        for (VisualRow row : visibleVisualRows()) {
            if (inside(mouseX, mouseY, this.renderPanel.x, rowY, PANEL_WIDTH, ROW_HEIGHT)) {
                if (button == 1 && row.hasSettings()) {
                    this.expandedVisual = this.expandedVisual == row.module() ? null : row.module();
                } else if (button == 0) row.toggle();
                return true;
            }
            rowY += ROW_HEIGHT;
            if (row.module() != this.expandedVisual) continue;
            int height = visualSettingsHeight(row.module());
            if (inside(mouseX, mouseY, this.renderPanel.x, rowY, PANEL_WIDTH, height)) {
                return handleVisualSettingClick(row.module(), mouseX, mouseY, rowY, button);
            }
            rowY += height;
        }
        return false;
    }

    private boolean handleVisualSettingClick(VisualModule module, int mouseX, int mouseY, int settingsY, int button) {
        if (button != 0) return true;
        switch (module) {
            case STORAGE_ESP -> this.config.storageTracers = !this.config.storageTracers;
            case ITEM_ESP -> {
                int index = rowIndex(mouseY, settingsY, ITEM_CATEGORIES.length + 1, SETTING_HEIGHT);
                if (index == 0) this.config.itemTracers = !this.config.itemTracers;
                else if (index > 0) {
                    ItemEspCategory category = ITEM_CATEGORIES[index - 1];
                    int swatchX = this.renderPanel.x + PANEL_WIDTH - 46;
                    if (inside(mouseX, mouseY, swatchX, settingsY + index * SETTING_HEIGHT + 3, 12, 12)) {
                        this.config.setItemEspColor(category, ColorPalette.next(this.config.itemEspColor(category)));
                    } else this.config.setItemEspEnabled(category, !this.config.itemEspEnabled(category));
                }
            }
            case TUNNEL_ESP -> {
                int index = rowIndex(mouseY, settingsY, 2, SETTING_HEIGHT);
                if (index == 0) this.config.tunnelTwoByOneColor = ColorPalette.next(this.config.tunnelTwoByOneColor);
                else if (index == 1) this.config.tunnelThreeByThreeColor = ColorPalette.next(this.config.tunnelThreeByThreeColor);
            }
            default -> {
            }
        }
        return true;
    }

    private boolean handlePlayerClick(int mouseX, int mouseY, int button) {
        if (!this.playerPanel.open || button != 0) return false;
        int rowY = this.playerPanel.y + HEADER_HEIGHT;
        List<ToggleRow> rows = visiblePlayerRows();
        int index = rowIndex(mouseY, rowY, rows.size(), ROW_HEIGHT);
        if (index >= 0 && inside(mouseX, mouseY, this.playerPanel.x, rowY, PANEL_WIDTH, rows.size() * ROW_HEIGHT)) {
            rows.get(index).toggle();
            return true;
        }
        int profileY = rowY + rows.size() * ROW_HEIGHT;
        if (performanceProfileVisible() && inside(mouseX, mouseY, this.playerPanel.x, profileY, PANEL_WIDTH, ROW_HEIGHT)) {
            this.config.cyclePerformanceProfile();
            this.nextPerformanceLabelUpdateNanos = 0L;
            ArcaneClient.engine().settingsChanged(this.client);
            return true;
        }
        return false;
    }

    private boolean handleBindClick(int mouseX, int mouseY) {
        if (!this.bindsPanel.open) return false;
        int rowY = this.bindsPanel.y + HEADER_HEIGHT;
        List<ArcaneKeybinds.Entry> entries = visibleBindings();
        int index = rowIndex(mouseY, rowY, entries.size(), ROW_HEIGHT);
        if (index >= 0 && inside(mouseX, mouseY, this.bindsPanel.x, rowY, PANEL_WIDTH, entries.size() * ROW_HEIGHT)) {
            this.listeningFor = entries.get(index).mapping();
            return true;
        }
        return false;
    }

    private boolean handleChatClick(int mouseX, int mouseY, int button) {
        if (!this.chatPanel.open || !matches("Chat macros")) return false;
        int moduleY = this.chatPanel.y + HEADER_HEIGHT;
        if (inside(mouseX, mouseY, this.chatPanel.x, moduleY, PANEL_WIDTH, ROW_HEIGHT)) {
            if (button == 1) this.chatExpanded = !this.chatExpanded;
            else this.config.chatMacros = !this.config.chatMacros;
            return true;
        }
        if (!this.chatExpanded || button != 0) return false;
        int settingsY = moduleY + ROW_HEIGHT;
        int index = rowIndex(mouseY, settingsY, 4, MACRO_ROW_HEIGHT);
        if (index >= 0 && inside(mouseX, mouseY, this.chatPanel.x + 115, settingsY + index * MACRO_ROW_HEIGHT, 33, MACRO_ROW_HEIGHT)) {
            this.listeningFor = ArcaneClient.keybinds().chatMacros().get(index);
            setMacroInputsVisible(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.draggingPanel != null) {
            int newX = (int) click.x() - this.panelDragOffsetX;
            int newY = (int) click.y() - this.panelDragOffsetY;
            this.panelMoved |= Math.abs(newX - this.draggingPanel.x) > 1 || Math.abs(newY - this.draggingPanel.y) > 1;
            this.draggingPanel.x = newX;
            this.draggingPanel.y = newY;
            this.draggingPanel.clamp(this.width, this.height);
            return true;
        }
        if (this.draggingSensitivity) {
            updateSensitivity((int) click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.draggingPanel != null) {
            if (!this.panelMoved) this.draggingPanel.open = !this.draggingPanel.open;
            this.draggingPanel = null;
            return true;
        }
        if (this.draggingSensitivity) {
            this.draggingSensitivity = false;
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

    private void setBinding(InputUtil.Key key) {
        this.listeningFor.setBoundKey(key);
        this.listeningFor = null;
        KeyBinding.updateKeysByCode();
        this.client.options.write();
    }

    private void updateSensitivity(int mouseX) {
        int barX = this.finderPanel.x + 13;
        int barWidth = PANEL_WIDTH - 26;
        this.config.setSensitivity(Math.round(100.0f * (mouseX - barX) / barWidth));
    }

    private void setMacroInputsVisible(boolean visible) {
        if (this.macroInputsVisible == visible) return;
        this.macroInputsVisible = visible;
        for (TextFieldWidget input : this.macroInputs) {
            input.setVisible(visible);
            if (!visible) input.setFocused(false);
        }
    }

    private TextRenderer font() {
        if (this.uiFont == null) {
            this.uiFont = ArcaneFont.renderer(this.client);
        }
        return this.uiFont;
    }

    private List<ToggleRow> finderSettings() {
        return this.finderSettings;
    }

    private List<VisualRow> visualRows() {
        return this.visualRows;
    }

    private List<ToggleRow> playerRows() {
        return this.playerRows;
    }

    private List<VisualRow> visibleVisualRows() {
        return this.filteredVisualRows;
    }

    private List<ToggleRow> visiblePlayerRows() {
        return this.filteredPlayerRows;
    }

    private List<ArcaneKeybinds.Entry> visibleBindings() {
        return this.filteredBindingRows;
    }

    private void updateFilter(String value) {
        this.searchText = value;
        this.normalizedQuery = value.trim().toLowerCase(Locale.ROOT);
        if (this.normalizedQuery.isEmpty()) {
            this.filteredVisualRows = this.visualRows;
            this.filteredPlayerRows = this.playerRows;
            this.filteredBindingRows = this.bindingRows;
            return;
        }

        ArrayList<VisualRow> visuals = new ArrayList<>();
        for (VisualRow row : this.visualRows) {
            if (matches(row.label())) visuals.add(row);
        }
        this.filteredVisualRows = List.copyOf(visuals);

        ArrayList<ToggleRow> players = new ArrayList<>();
        for (ToggleRow row : this.playerRows) {
            if (matches(row.label())) players.add(row);
        }
        this.filteredPlayerRows = List.copyOf(players);

        ArrayList<ArcaneKeybinds.Entry> bindings = new ArrayList<>();
        for (ArcaneKeybinds.Entry entry : this.bindingRows) {
            if (matches(entry.label())) bindings.add(entry);
        }
        this.filteredBindingRows = List.copyOf(bindings);
    }

    private boolean matches(String label) {
        return this.normalizedQuery.isEmpty() || label.toLowerCase(Locale.ROOT).contains(this.normalizedQuery);
    }

    private String query() {
        return this.normalizedQuery;
    }

    private int visibleModuleCount() {
        return (matches("Base scanner") ? 1 : 0)
            + visibleVisualRows().size()
            + visiblePlayerRows().size()
            + (matches("Chat macros") ? 1 : 0)
            + (performanceProfileVisible() ? 1 : 0);
    }

    private boolean performanceProfileVisible() {
        return matches("Performance profile High FPS Balanced Quality");
    }

    private void updatePerformanceLabel() {
        long now = System.nanoTime();
        if (now < this.nextPerformanceLabelUpdateNanos && !this.performanceLabel.isEmpty()) return;
        this.compactFpsLabel = this.client.getCurrentFps() + " FPS";
        this.performanceLabel = activeModuleCount() + " ACTIVE   " + this.compactFpsLabel + "   "
            + this.config.performanceProfile().label();
        this.nextPerformanceLabelUpdateNanos = now + 250_000_000L;
    }
    private int activeModuleCount() {
        int active = 0;
        if (this.config.enabled) active++;
        if (this.config.overlay) active++;
        if (this.config.hud) active++;
        if (this.config.chunkAnalysis) active++;
        if (this.config.esp) active++;
        if (this.config.itemEsp) active++;
        if (this.config.tunnelEsp) active++;
        if (this.config.stashAlerts) active++;
        if (this.config.blockEntityDebug) active++;
        if (this.config.autoTotem) active++;
        if (FreecamController.isActive()) active++;
        if (this.config.chatMacros) active++;
        return active;
    }

    private static int activeVisualCount(List<VisualRow> rows) {
        int active = 0;
        for (VisualRow row : rows) {
            if (row.value().getAsBoolean()) active++;
        }
        return active;
    }

    private static int activeToggleCount(List<ToggleRow> rows) {
        int active = 0;
        for (ToggleRow row : rows) {
            if (row.value().getAsBoolean()) active++;
        }
        return active;
    }

    private static boolean containsVisualModule(List<VisualRow> rows, VisualModule module) {
        for (VisualRow row : rows) {
            if (row.module() == module) return true;
        }
        return false;
    }

    private static int visualSettingsHeight(VisualModule module) {
        return switch (module) {
            case STORAGE_ESP, BLOCK_ENTITY_DEBUG -> SETTING_HEIGHT;
            case ITEM_ESP -> SETTING_HEIGHT * (ITEM_CATEGORIES.length + 1);
            case TUNNEL_ESP -> SETTING_HEIGHT * 2;
            default -> 0;
        };
    }

    private Panel headerAt(int mouseX, int mouseY) {
        for (Panel panel : new Panel[]{this.chatPanel, this.bindsPanel, this.playerPanel, this.renderPanel, this.finderPanel}) {
            if (inside(mouseX, mouseY, panel.x, panel.y, PANEL_WIDTH, HEADER_HEIGHT)) return panel;
        }
        return null;
    }

    private void layoutPanels() {
        int top = TOP_BAR_HEIGHT + 8;
        int requiredFive = PANEL_WIDTH * 5 + GAP * 6;
        int requiredFour = PANEL_WIDTH * 4 + GAP * 5;
        int requiredThree = PANEL_WIDTH * 3 + GAP * 4;
        int requiredTwo = PANEL_WIDTH * 2 + GAP * 3;

        if (this.width >= requiredFive) {
            int start = (this.width - (PANEL_WIDTH * 5 + GAP * 4)) / 2;
            place(this.finderPanel, start, top);
            place(this.renderPanel, start + (PANEL_WIDTH + GAP), top);
            place(this.playerPanel, start + (PANEL_WIDTH + GAP) * 2, top);
            place(this.bindsPanel, start + (PANEL_WIDTH + GAP) * 3, top);
            place(this.chatPanel, start + (PANEL_WIDTH + GAP) * 4, top);
        } else if (this.width >= requiredFour) {
            int start = (this.width - (PANEL_WIDTH * 4 + GAP * 3)) / 2;
            place(this.finderPanel, start, top);
            place(this.renderPanel, start + (PANEL_WIDTH + GAP), top);
            place(this.playerPanel, start + (PANEL_WIDTH + GAP) * 2, top);
            place(this.bindsPanel, start + (PANEL_WIDTH + GAP) * 3, top);
            place(this.chatPanel, start, top + 78);
        } else if (this.width >= requiredThree) {
            int start = (this.width - (PANEL_WIDTH * 3 + GAP * 2)) / 2;
            place(this.finderPanel, start, top);
            place(this.renderPanel, start + (PANEL_WIDTH + GAP), top);
            place(this.playerPanel, start + (PANEL_WIDTH + GAP) * 2, top);
            place(this.bindsPanel, start, top + 46);
            this.bindsPanel.open = false;
            place(this.chatPanel, start + (PANEL_WIDTH + GAP) * 2, top + 78);
        } else if (this.width >= requiredTwo) {
            int start = (this.width - (PANEL_WIDTH * 2 + GAP)) / 2;
            place(this.finderPanel, start, top);
            place(this.renderPanel, start + PANEL_WIDTH + GAP, top);
            place(this.playerPanel, start, top + 46);
            place(this.chatPanel, start, top + 128);
            place(this.bindsPanel, start + PANEL_WIDTH + GAP, this.height - 18 - HEADER_HEIGHT);
            this.bindsPanel.open = false;
        } else {
            int x = Math.max(0, (this.width - PANEL_WIDTH) / 2);
            place(this.finderPanel, x, top);
            place(this.renderPanel, x, top + 26);
            place(this.playerPanel, x, top + 52);
            place(this.bindsPanel, x, top + 78);
            place(this.chatPanel, x, top + 104);
            this.finderPanel.open = false;
            this.renderPanel.open = false;
            this.playerPanel.open = false;
            this.bindsPanel.open = false;
            this.chatPanel.open = false;
        }
    }

    private static void place(Panel panel, int x, int y) {
        panel.x = x;
        panel.y = y;
    }

    private int searchWidth() {
        return Math.clamp(this.width - 360, 96, 180);
    }

    private int searchX(int searchWidth) {
        return (this.width - searchWidth) / 2;
    }

    private int themeButtonX() {
        return this.width - 92;
    }

    private void clampPanels() {
        this.finderPanel.clamp(this.width, this.height);
        this.renderPanel.clamp(this.width, this.height);
        this.playerPanel.clamp(this.width, this.height);
        this.bindsPanel.clamp(this.width, this.height);
        this.chatPanel.clamp(this.width, this.height);
    }

    private Theme theme() {
        return THEMES[Math.floorMod(this.config.uiTheme, THEMES.length)];
    }

    private static int rowIndex(int mouseY, int startY, int count, int rowHeight) {
        int index = (mouseY - startY) / rowHeight;
        return mouseY >= startY && index >= 0 && index < count ? index : -1;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static boolean isOpen(net.minecraft.client.MinecraftClient client) {
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
        if (!this.scannerWasEnabled && this.config.enabled) ArcaneClient.engine().queueNearby(this.client);
        ArcaneClient.engine().settingsChanged(this.client);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Environment(EnvType.CLIENT)
    private static final class Panel {
        private int x;
        private int y;
        private boolean open = true;
        private int lastHeight = HEADER_HEIGHT;

        private void clamp(int screenWidth, int screenHeight) {
            this.x = Math.clamp(this.x, 0, Math.max(0, screenWidth - PANEL_WIDTH));
            this.y = Math.clamp(this.y, TOP_BAR_HEIGHT + 4, Math.max(TOP_BAR_HEIGHT + 4, screenHeight - this.lastHeight - 18));
        }
    }

    @Environment(EnvType.CLIENT)
    private record ToggleRow(String label, BooleanSupplier value, Consumer<Boolean> setter) {
        private void toggle() {
            this.setter.accept(!this.value.getAsBoolean());
        }
    }

    @Environment(EnvType.CLIENT)
    private enum VisualModule {
        CHUNK_TILES(false),
        CHUNK_RADAR(false),
        CHUNK_ANALYSIS(false),
        STORAGE_ESP(true),
        ITEM_ESP(true),
        TUNNEL_ESP(true),
        STASH_ALERTS(false),
        BLOCK_ENTITY_DEBUG(true);

        private final boolean hasSettings;

        VisualModule(boolean hasSettings) {
            this.hasSettings = hasSettings;
        }
    }

    @Environment(EnvType.CLIENT)
    private record VisualRow(VisualModule module, String label, BooleanSupplier value, Consumer<Boolean> setter) {
        private boolean hasSettings() {
            return this.module.hasSettings;
        }

        private void toggle() {
            this.setter.accept(!this.value.getAsBoolean());
        }
    }

    @Environment(EnvType.CLIENT)
    private enum Theme {
        ARCANE("ARCANE", 0xFFA855F7, 0xFF22D3EE),
        EMBER("EMBER", 0xFFFB7185, 0xFFF59E0B),
        VERDANT("VERDANT", 0xFF34D399, 0xFF2DD4BF);

        private final String label;
        private final int accent;
        private final int secondary;
        private final int backdrop = 0xE60A0714;
        private final int topBar = 0xF00C0916;
        private final int panel = 0xFA120F20;
        private final int header = 0xF0181429;
        private final int row = 0xF2181428;
        private final int setting = 0xF21D1830;
        private final int hover = 0xF02A2342;
        private final int border = 0xFF4B4168;
        private final int text = 0xFFF8FAFC;
        private final int muted = 0xFF94A3B8;

        Theme(String label, int accent, int secondary) {
            this.label = label;
            this.accent = accent;
            this.secondary = secondary;
        }
    }
}
