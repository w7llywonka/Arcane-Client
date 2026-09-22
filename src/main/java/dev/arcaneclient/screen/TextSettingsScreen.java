package dev.arcaneclient.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Small paginated forms; edits remain drafts until Save is chosen. */
public final class TextSettingsScreen extends Screen {
    public record Field(String label, String hint, Supplier<String> value, Consumer<String> setter, int limit) {}
    private final Screen parent;
    private final ArcaneConfig config;
    private final List<Field> fields;
    private final List<String> drafts = new ArrayList<>();
    private final List<EditBox> inputs = new ArrayList<>();
    private final String note;
    private int page, pageSize, x, y, w, h;

    public TextSettingsScreen(Screen parent, ArcaneConfig config, String title, String note, List<Field> fields) {
        super(Component.literal(title));
        this.parent = parent;
        this.config = config;
        this.note = note;
        this.fields = List.copyOf(fields);
        for (Field field : fields) drafts.add(field.value().get());
    }

    @Override protected void init() {
        inputs.clear();
        w = Math.min(460, Math.max(120, width - 16));
        pageSize = Math.max(1, (height - 124) / 53);
        page = Math.min(page, Math.max(0, (fields.size() - 1) / pageSize));
        int start = page * pageSize;
        int count = Math.min(pageSize, fields.size() - start);
        h = Math.min(height - 8, 110 + count * 53);
        x = (width - w) / 2;
        y = (height - h) / 2;
        for (int i = 0; i < count; i++) {
            int index = start + i;
            Field field = fields.get(index);
            EditBox input = new EditBox(font, x + 18, y + 62 + i * 53,
                w - 36, 14, ArcaneFont.text(field.label()));
            input.setBordered(false);
            input.setTextShadow(false);
            input.setMaxLength(field.limit());
            input.addFormatter((value, offset) -> ArcaneFont.text(value).getVisualOrderText());
            input.setTextColor(colors().text());
            input.setValue(drafts.get(index));
            input.setResponder(value -> drafts.set(index, value));
            inputs.add(addRenderableWidget(input));
        }
        int footer = y + h - 29;
        addRenderableWidget(new FormButton(x + 12, footer, 60, "Cancel", this::onClose));
        if (fields.size() > pageSize) {
            FormButton previous = addRenderableWidget(new FormButton(x + 78, footer, 50, "Back", () -> { page--; rebuildWidgets(); }));
            previous.active = page > 0;
            FormButton next = addRenderableWidget(new FormButton(x + 134, footer, 50, "Next", () -> { page++; rebuildWidgets(); }));
            next.active = start + count < fields.size();
        }
        addRenderableWidget(new FormButton(x + w - 74, footer, 62, "Save", () -> {
            for (int i = 0; i < fields.size(); i++) fields.get(i).setter().accept(drafts.get(i));
            config.save();
            onClose();
        }));
        if (!inputs.isEmpty()) setInitialFocus(inputs.getFirst());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
        ClickGuiColors c = colors();
        draw.fill(0, 0, width, height, c.backdrop());
        RoundedGui.fill(draw, x, y, w, h, 7, c.window());
        RoundedGui.outlineOnly(draw, x, y, w, h, 7, c.outline());
        label(draw, title.getString(), x + 12, y + 13, c.text());
        label(draw, note, x + 12, y + 29, c.muted());
        for (int i = 0; i < inputs.size(); i++) {
            Field field = fields.get(page * pageSize + i);
            int row = y + 47 + i * 53;
            label(draw, field.label(), x + 12, row, c.accentBright());
            RoundedGui.fill(draw, x + 12, row + 12, w - 24, 22, 4, c.nest());
            label(draw, field.hint(), x + 12, row + 37, c.faint());
        }
        super.extractRenderState(draw, mouseX, mouseY, delta);
    }

    private void label(GuiGraphicsExtractor draw, String value, int left, int top, int color) {
        draw.text(font, ArcaneFont.trimmed(font, value, w - 24), left, top, color, false);
    }
    private ClickGuiColors colors() { return ClickGuiColors.display(config); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private final class FormButton extends AbstractWidget {
        private final Runnable action;
        FormButton(int x, int y, int w, String label, Runnable action) {
            super(x, y, w, 21, ArcaneFont.text(label));
            this.action = action;
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
            var c = colors();
            RoundedGui.fill(draw, getX(), getY(), getWidth(), getHeight(), 4,
                isHovered() || isFocused() ? c.activeHover() : c.nest());
            draw.text(font, getMessage(), getX() + 9, getY() + 6, active ? c.text() : c.faint(), false);
        }
        @Override public void onClick(MouseButtonEvent click, boolean doubled) { action.run(); }
        @Override public boolean keyPressed(KeyEvent input) {
            if (active && isFocused() && (input.key() == InputConstants.KEY_RETURN || input.key() == InputConstants.KEY_SPACE)) {
                action.run();
                return true;
            }
            return super.keyPressed(input);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }
}
