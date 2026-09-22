package dev.arcaneclient.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Registry-backed item selection with only the visible rows submitted for rendering. */
@Environment(EnvType.CLIENT)
public final class ItemEspPickerScreen extends Screen {
    private static final int ROW_HEIGHT = 32;
    private final Screen parent;
    private final ArcaneConfig config;
    private final List<ItemEntry> entries = new ArrayList<>();
    private final List<ItemEntry> filtered = new ArrayList<>();
    private EditBox search;
    private ItemList itemList;
    private ActionButton clearButton;
    private String searchText = "";
    private boolean selectedOnly;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public ItemEspPickerScreen(Screen parent, ArcaneConfig config) {
        super(Component.literal("Choose Item ESP items"));
        this.parent = parent;
        this.config = config;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ItemStack stack = item.getDefaultInstance();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String name = stack.getHoverName().getString();
            entries.add(new ItemEntry(stack, id, name, (name + " " + id).toLowerCase(Locale.ROOT)));
        }
        entries.sort(Comparator.comparing(ItemEntry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ItemEntry::id));
    }

    @Override
    protected void init() {
        ArcaneFont.invalidate();
        panelWidth = Math.min(520, width - 16);
        panelHeight = Math.min(420, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int innerWidth = panelWidth - 24;
        int left = panelX + 12;
        int filterWidth = 94;
        search = new EditBox(font, left + 7, panelY + 51,
            innerWidth - filterWidth - 20, 12, Component.literal("Search items by name or registry ID"));
        search.setBordered(false);
        search.setTextShadow(false);
        search.setMaxLength(128);
        search.setHint(ArcaneFont.text("Search name or registry ID"));
        search.addFormatter((value, offset) -> ArcaneFont.text(value).getVisualOrderText());
        search.setTextColor(theme().text());
        search.setTextColorUneditable(theme().faint());
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            updateFilter(true);
        });
        addRenderableWidget(search);
        addRenderableWidget(new ActionButton(panelX + panelWidth - 12 - filterWidth, panelY + 46,
            filterWidth, () -> selectedOnly ? "Selected only: ON" : "Selected only: OFF",
            () -> selectedOnly, () -> {
                selectedOnly = !selectedOnly;
                updateFilter(true);
            }));

        int modeWidth = (innerWidth - 6) / 2;
        addRenderableWidget(new ActionButton(left, panelY + 72, modeWidth,
            () -> "All dropped items: " + state(config.itemEspAll), () -> config.itemEspAll,
            () -> config.itemEspAll = !config.itemEspAll));
        addRenderableWidget(new ActionButton(left + modeWidth + 6, panelY + 72, innerWidth - modeWidth - 6,
            () -> "Use preset groups: " + state(config.itemEspUsePresets), () -> config.itemEspUsePresets,
            () -> config.itemEspUsePresets = !config.itemEspUsePresets));

        itemList = addRenderableWidget(new ItemList(left, panelY + 101, innerWidth,
            Math.max(ROW_HEIGHT, panelHeight - 153)));
        clearButton = addRenderableWidget(new ActionButton(left, panelY + panelHeight - 30, 102,
            () -> "Clear selections", () -> false, () -> {
                config.itemEspItems.clear();
                updateFilter(true);
            }));
        addRenderableWidget(new ActionButton(panelX + panelWidth - 76, panelY + panelHeight - 30,
            64, () -> "Done", () -> true, this::onClose));
        updateFilter(true);
        setInitialFocus(search);
    }

    private void updateFilter(boolean resetScroll) {
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        String[] words = query.isEmpty() ? new String[0] : query.split("\\s+");
        filtered.clear();
        for (ItemEntry entry : entries) {
            if (selectedOnly && !config.itemEspItems.contains(entry.id())) continue;
            boolean matches = true;
            for (String word : words) {
                if (!entry.search().contains(word)) {
                    matches = false;
                    break;
                }
            }
            if (matches) filtered.add(entry);
        }
        if (itemList != null) {
            if (resetScroll) {
                itemList.scroll = 0;
                itemList.cursor = 0;
            }
            itemList.clampScroll();
            itemList.cursor = Math.clamp(itemList.cursor, 0, Math.max(0, filtered.size() - 1));
        }
        if (clearButton != null) clearButton.active = !config.itemEspItems.isEmpty();
    }

    private void toggleItem(int index) {
        if (index < 0 || index >= filtered.size()) return;
        String id = filtered.get(index).id();
        if (!config.itemEspItems.remove(id)) config.itemEspItems.add(id);
        updateFilter(false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ClickGuiColors colors = theme();
        context.fill(0, 0, width, height, colors.backdrop());
        RoundedGui.fill(context, panelX, panelY, panelWidth, panelHeight, 7, colors.window());
        RoundedGui.outlineOnly(context, panelX, panelY, panelWidth, panelHeight, 7, colors.outline());
        label(context, "Choose items", panelX + 12, panelY + 13, colors.text());
        String heading = "ITEM ESP";
        label(context, heading, panelX + panelWidth - 12 - ArcaneFont.width(font, heading),
            panelY + 13, colors.accentBright());
        String description = config.itemEspAll ? "All dropped items are included. Your picks are kept for later."
            : config.itemEspUsePresets ? "Your picks + enabled preset groups. Click an item to select it."
            : "Only your picks are included. Click an item to select it.";
        context.text(font, ArcaneFont.trimmed(font, description, panelWidth - 24),
            panelX + 12, panelY + 30, colors.muted(), false);
        RoundedGui.fill(context, panelX + 12, panelY + 46, panelWidth - 124, 22, 4, colors.nest());
        RoundedGui.outlineOnly(context, panelX + 12, panelY + 46, panelWidth - 124, 22, 4,
            search.isFocused() ? colors.accentDim() : colors.outlineSoft());
        String count = config.itemEspItems.size() + " selected  /  " + filtered.size() + " shown";
        label(context, count, panelX + 12, panelY + panelHeight - 45, colors.muted());
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.shortcutKey() == InputConstants.KEYCODE_F && input.hasControlDownWithQuirk()) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (search.isFocused() && input.key() == InputConstants.KEY_DOWN) {
            setFocused(itemList);
            itemList.setFocused(true);
            itemList.ensureCursorVisible();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ClickGuiColors theme() {
        return ClickGuiColors.display(config);
    }

    private void label(GuiGraphicsExtractor context, String value, int x, int y, int color) {
        context.text(font, ArcaneFont.text(value), x, y, color, false);
    }

    private static String state(boolean value) {
        return value ? "ON" : "OFF";
    }

    private record ItemEntry(ItemStack stack, String id, String name, String search) {}

    private final class ActionButton extends AbstractWidget {
        private final Supplier<String> label;
        private final BooleanSupplier selected;
        private final Runnable action;

        private ActionButton(int x, int y, int width, Supplier<String> label,
                             BooleanSupplier selected, Runnable action) {
            super(x, y, width, 22, ArcaneFont.text(label.get()));
            this.label = label;
            this.selected = selected;
            this.action = action;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            ClickGuiColors colors = theme();
            boolean highlighted = isHovered() || isFocused();
            int background = selected.getAsBoolean() ? (highlighted ? colors.activeHover() : colors.active())
                : highlighted ? colors.hover() : colors.nest();
            RoundedGui.fill(context, getX(), getY(), getWidth(), getHeight(), 4, background);
            RoundedGui.outlineOnly(context, getX(), getY(), getWidth(), getHeight(), 4,
                isFocused() ? colors.accentBright() : colors.outlineSoft());
            String text = label.get();
            setMessage(ArcaneFont.text(text));
            int textWidth = Math.min(getWidth() - 10, ArcaneFont.width(font, text));
            context.text(font, ArcaneFont.trimmed(font, text, getWidth() - 10),
                getX() + (getWidth() - textWidth) / 2, getY() + 7, active ? colors.text() : colors.faint(), false);
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            action.run();
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (active && isFocused() && (input.key() == InputConstants.KEY_RETURN
                || input.key() == InputConstants.KEY_NUMPADENTER || input.key() == InputConstants.KEY_SPACE)) {
                playDownSound(minecraft.getSoundManager());
                action.run();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }

    private final class ItemList extends AbstractWidget {
        private double scroll;
        private int cursor;
        private boolean draggingScrollbar;

        private ItemList(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Items. Arrow keys move, Space selects."));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            ClickGuiColors colors = theme();
            RoundedGui.fill(context, getX(), getY(), getWidth(), getHeight(), 4, colors.nest());
            RoundedGui.outlineOnly(context, getX(), getY(), getWidth(), getHeight(), 4,
                isFocused() ? colors.accentDim() : colors.outlineSoft());
            int first = (int) scroll / ROW_HEIGHT;
            int bottom = getY() + getHeight();
            int rowWidth = getWidth() - 9;
            context.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, bottom - 1);
            for (int index = first; index < filtered.size(); index++) {
                int y = getY() + index * ROW_HEIGHT - (int) scroll;
                if (y >= bottom) break;
                ItemEntry entry = filtered.get(index);
                boolean selected = config.itemEspItems.contains(entry.id());
                boolean hovering = mouseX >= getX() && mouseX < getX() + rowWidth
                    && mouseY >= Math.max(y, getY()) && mouseY < Math.min(y + ROW_HEIGHT, bottom);
                if (selected || hovering || isFocused() && index == cursor) {
                    RoundedGui.fill(context, getX() + 2, y + 1, rowWidth - 3, ROW_HEIGHT - 2, 3,
                        selected ? colors.active() : colors.hover());
                }
                if (isFocused() && index == cursor) {
                    RoundedGui.outlineOnly(context, getX() + 2, y + 1, rowWidth - 3, ROW_HEIGHT - 2,
                        3, colors.accentDim());
                }
                context.item(entry.stack(), getX() + 7, y + 8);
                int textWidth = rowWidth - 54;
                context.text(font, ArcaneFont.trimmed(font, entry.name(), textWidth),
                    getX() + 30, y + 6, colors.text(), false);
                context.text(font, ArcaneFont.trimmed(font, entry.id(), textWidth),
                    getX() + 30, y + 18, colors.muted(), false);
                int checkX = getX() + rowWidth - 17;
                RoundedGui.outlineOnly(context, checkX, y + 11, 10, 10, 3,
                    selected ? config.itemEspCustomColor | 0xFF000000 : colors.outline());
                if (selected) RoundedGui.fill(context, checkX + 2, y + 13, 6, 6, 2,
                    config.itemEspCustomColor | 0xFF000000);
            }
            if (filtered.isEmpty()) {
                String empty = selectedOnly ? "No selected items match this search." : "No items match this search.";
                context.text(font, ArcaneFont.trimmed(font, empty, getWidth() - 20),
                    getX() + 10, getY() + 12, colors.muted(), false);
            }
            context.disableScissor();
            if (maxScroll() > 0) {
                int thumbHeight = thumbHeight();
                int thumbY = getY() + 2 + (int) (scroll / maxScroll() * (getHeight() - 4 - thumbHeight));
                RoundedGui.fill(context, getX() + getWidth() - 6, getY() + 2, 3, getHeight() - 4,
                    1, colors.outlineSoft());
                RoundedGui.fill(context, getX() + getWidth() - 6, thumbY, 3, thumbHeight,
                    1, isHovered() || draggingScrollbar ? colors.accentBright() : colors.accentDim());
            }
            String narration = filtered.isEmpty() ? "No matching items" : filtered.get(cursor).name() + ", "
                + filtered.get(cursor).id() + ", "
                + (config.itemEspItems.contains(filtered.get(cursor).id()) ? "selected" : "not selected");
            setMessage(Component.literal(narration + ". Arrow keys move, Space selects."));
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            if (click.x() >= getX() + getWidth() - 9 && maxScroll() > 0) {
                draggingScrollbar = true;
                scrollToMouse(click.y());
                return;
            }
            int index = (int) ((click.y() - getY() + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                cursor = index;
                toggleItem(index);
            }
        }

        @Override
        protected void onDrag(MouseButtonEvent click, double deltaX, double deltaY) {
            if (draggingScrollbar) scrollToMouse(click.y());
        }

        @Override
        public void onRelease(MouseButtonEvent click) {
            draggingScrollbar = false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            scroll -= verticalAmount * ROW_HEIGHT * 2;
            clampScroll();
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (!isFocused() || filtered.isEmpty()) return false;
            int page = Math.max(1, getHeight() / ROW_HEIGHT);
            switch (input.key()) {
                case InputConstants.KEY_UP -> cursor--;
                case InputConstants.KEY_DOWN -> cursor++;
                case InputConstants.KEY_PAGEUP -> cursor -= page;
                case InputConstants.KEY_PAGEDOWN -> cursor += page;
                case InputConstants.KEY_HOME -> cursor = 0;
                case InputConstants.KEY_END -> cursor = filtered.size() - 1;
                case InputConstants.KEY_SPACE, InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                    playDownSound(minecraft.getSoundManager());
                    toggleItem(cursor);
                }
                default -> { return false; }
            }
            cursor = Math.clamp(cursor, 0, Math.max(0, filtered.size() - 1));
            ensureCursorVisible();
            return true;
        }

        private int maxScroll() {
            return Math.max(0, filtered.size() * ROW_HEIGHT - getHeight());
        }

        private int thumbHeight() {
            return Math.max(14, (getHeight() - 4) * getHeight() / (filtered.size() * ROW_HEIGHT));
        }

        private void scrollToMouse(double mouseY) {
            int travel = getHeight() - 4 - thumbHeight();
            if (travel > 0) scroll = (mouseY - getY() - 2 - thumbHeight() / 2.0) / travel * maxScroll();
            clampScroll();
        }

        private void clampScroll() {
            scroll = Math.clamp(scroll, 0, maxScroll());
        }

        private void ensureCursorVisible() {
            int top = cursor * ROW_HEIGHT;
            if (top < scroll) scroll = top;
            if (top + ROW_HEIGHT > scroll + getHeight()) scroll = top + ROW_HEIGHT - getHeight();
            clampScroll();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }
}
