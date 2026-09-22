package dev.arcaneclient.screen;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.additions.configlibrary.ConfigLibrary;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Local profile browser and explicit, paginated change review using the Arcane compact UI. */
public final class ConfigLibraryScreen extends Screen {
    private enum Mode { LIBRARY, IMPORT, REVIEW, RENAME, DELETE }
    private final Screen parent;
    private final ArcaneConfig config;
    private Mode mode = Mode.LIBRARY;
    private List<ConfigLibrary.Profile> profiles = List.of();
    private ConfigLibrary.Review review;
    private String selected = "";
    private String draft = "";
    private String status = "Personal data and keybinds stay local. Loads keep automation off.";
    private boolean includeUi;
    private boolean applied;
    private EditBox input;
    private int page, rows, x, y, w, h;

    public ConfigLibraryScreen(Screen parent, ArcaneConfig config) {
        super(Component.literal("Config library"));
        this.parent = parent;
        this.config = config;
    }

    @Override protected void init() {
        ArcaneFont.invalidate();
        w = Math.min(580, width - 16);
        h = Math.min(440, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        input = null;
        switch (mode) {
            case LIBRARY, IMPORT -> browser();
            case REVIEW -> review();
            case RENAME -> rename();
            case DELETE -> delete();
        }
    }

    private void browser() {
        try {
            ConfigLibrary.importsDirectory();
            profiles = ConfigLibrary.profiles(mode == Mode.IMPORT);
        } catch (IOException failure) { profiles = List.of(); status = message(failure); }
        rows = Math.max(1, (h - 189) / 28);
        page = Math.clamp(page, 0, Math.max(0, (profiles.size() - 1) / rows));
        if (profiles.stream().noneMatch(profile -> profile.name().equals(selected))) selected = "";

        field(50, w - 116, "New preset name");
        button(x + w - 98, y + 47, 86, mode == Mode.IMPORT ? "Import file" : "Save current", () -> run(() -> {
            if (mode == Mode.IMPORT) {
                if (selected.isEmpty()) throw new IOException("Choose an incoming JSON file first.");
                String name = ConfigLibrary.importFile(selected, draft);
                selected = name; mode = Mode.LIBRARY; page = 0;
                status = "Imported " + name + ". Review it before applying.";
            } else {
                ConfigLibrary.save(draft, config, includeUi);
                selected = ConfigLibrary.normalizeName(draft);
                status = "Saved " + selected + ". Existing profiles are never overwritten.";
            }
            draft = ""; rebuildWidgets();
        }));

        int third = (w - 36) / 3;
        if (mode == Mode.LIBRARY) {
            button(x + 12, y + 75, third, "UI / layout: " + (includeUi ? "ON" : "OFF"), () -> { includeUi = !includeUi; rebuildWidgets(); });
            button(x + 18 + third, y + 75, third, "Open folder", () -> run(() -> Blaze3D.openPath(ConfigLibrary.directory())));
        } else {
            button(x + 12, y + 75, third, "Back to library", this::back);
            button(x + 18 + third, y + 75, third, "Open inbox", () -> run(() -> Blaze3D.openPath(ConfigLibrary.importsDirectory())));
        }
        button(x + 24 + third * 2, y + 75, w - 36 - third * 2, "Refresh files", this::rebuildWidgets);

        int start = page * rows;
        for (int row = 0; row < rows && start + row < profiles.size(); row++) {
            String name = profiles.get(start + row).name();
            addRenderableWidget(new ProfileRow(x + 12, y + 104 + row * 28, w - 24, name));
        }
        int navY = y + 108 + rows * 28;
        LibraryButton previous = button(x + 12, navY, 56, "Previous", () -> { page--; rebuildWidgets(); });
        previous.active = page > 0;
        LibraryButton next = button(x + w - 68, navY, 56, "Next", () -> { page++; rebuildWidgets(); });
        next.active = start + rows < profiles.size();

        if (mode == Mode.LIBRARY) {
            int quarter = (w - 42) / 4;
            LibraryButton load = button(x + 12, y + h - 57, quarter, "Review load", () -> run(() -> {
                review = ConfigLibrary.review(selected, config, includeUi); mode = Mode.REVIEW; page = 0; rebuildWidgets();
            }));
            LibraryButton rename = button(x + 18 + quarter, y + h - 57, quarter, "Rename", () -> { mode = Mode.RENAME; draft = selected; rebuildWidgets(); });
            LibraryButton delete = button(x + 24 + quarter * 2, y + h - 57, quarter, "Delete", () -> { mode = Mode.DELETE; rebuildWidgets(); });
            LibraryButton export = button(x + 30 + quarter * 3, y + h - 57, w - 42 - quarter * 3, "Export JSON", () -> run(() -> {
                status = "Exported " + ConfigLibrary.export(selected, includeUi) + ". No personal data included.";
            }));
            load.active = rename.active = delete.active = export.active = !selected.isEmpty();
            button(x + 12, y + h - 31, 90, "Import JSON", () -> {
                mode = Mode.IMPORT; selected = ""; draft = ""; page = 0;
                status = "Drop Arcane profile JSON files into profiles/imports, then Refresh."; rebuildWidgets();
            });
            LibraryButton revert = button(x + 108, y + h - 31, 98, "Revert last apply", () -> run(() -> {
                review = ConfigLibrary.reviewBackup(config); mode = Mode.REVIEW; page = 0; rebuildWidgets();
            }));
            revert.active = ConfigLibrary.hasBackup();
            button(x + w - 70, y + h - 31, 58, "Done", this::onClose);
        } else {
            button(x + 12, y + h - 31, 74, "Cancel", this::back);
        }
        if (input != null) setInitialFocus(input);
    }

    private void review() {
        rows = Math.max(1, (h - 130) / 32);
        page = Math.clamp(page, 0, Math.max(0, (review.changes().size() - 1) / rows));
        button(x + 12, y + h - 31, 70, "Cancel", this::back);
        LibraryButton previous = button(x + 12, y + h - 57, 56, "Previous", () -> { page--; rebuildWidgets(); });
        previous.active = page > 0;
        LibraryButton next = button(x + w - 68, y + h - 57, 56, "Next", () -> { page++; rebuildWidgets(); });
        next.active = (page + 1) * rows < review.changes().size();
        button(x + w - 122, y + h - 31, 110, "Apply these changes", () -> run(() -> {
            ConfigLibrary.apply(review, config, minecraft);
            applied = true; mode = Mode.LIBRARY; page = 0; review = null;
            status = "Applied. Previous settings backed up; automation remains off.";
            rebuildWidgets();
        }));
    }

    private void rename() {
        field(83, w - 36, "New preset name");
        button(x + 12, y + h - 31, 70, "Cancel", this::back);
        button(x + w - 90, y + h - 31, 78, "Rename", () -> run(() -> {
            ConfigLibrary.rename(selected, draft); selected = ConfigLibrary.normalizeName(draft);
            mode = Mode.LIBRARY; draft = ""; status = "Renamed to " + selected + "."; rebuildWidgets();
        }));
        setInitialFocus(input);
    }

    private void delete() {
        button(x + 12, y + h - 31, 70, "Cancel", this::back);
        button(x + w - 106, y + h - 31, 94, "Delete this preset", () -> run(() -> {
            ConfigLibrary.delete(selected); status = "Deleted " + selected + ".json.";
            selected = ""; mode = Mode.LIBRARY; page = 0; rebuildWidgets();
        }));
    }

    private void field(int top, int fieldWidth, String label) {
        input = new EditBox(font, x + 18, y + top + 3, fieldWidth - 12, 12, Component.literal(label));
        input.setBordered(false); input.setTextShadow(false); input.setMaxLength(48);
        input.setHint(ArcaneFont.text(label));
        input.addFormatter((value, offset) -> ArcaneFont.text(value).getVisualOrderText());
        input.setTextColor(colors().text()); input.setValue(draft); input.setResponder(value -> draft = value);
        addRenderableWidget(input);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
        var c = colors();
        draw.fill(0, 0, width, height, c.backdrop());
        RoundedGui.fill(draw, x, y, w, h, 7, c.window());
        RoundedGui.outlineOnly(draw, x, y, w, h, 7, c.outline());
        String heading = switch (mode) {
            case LIBRARY -> "Config library"; case IMPORT -> "Import a profile"; case REVIEW -> "Review changes";
            case RENAME -> "Rename preset"; case DELETE -> "Delete preset?";
        };
        label(draw, heading, x + 12, y + 13, w - 110, c.text());
        label(draw, "LOCAL JSON", x + w - 79, y + 13, 68, c.accentBright());
        if (mode == Mode.REVIEW) {
            label(draw, review.name() + " · " + review.changes().size() + " changes", x + 12, y + 31, w - 24, c.muted());
            label(draw, "Automation stays OFF. Personal data and keybinds stay local.", x + 12, y + 45, w - 24, c.accentBright());
            int start = page * rows;
            for (int row = 0; row < rows && start + row < review.changes().size(); row++) {
                var change = review.changes().get(start + row);
                int top = y + 67 + row * 32;
                RoundedGui.fill(draw, x + 12, top, w - 24, 29, 3, c.nest());
                label(draw, change.field(), x + 19, top + 5, w - 38, c.text());
                label(draw, change.before() + "  >  " + change.after(), x + 19, top + 17, w - 38, c.muted());
            }
            if (review.changes().isEmpty()) label(draw, "No setting differences. Apply still saves a recovery backup.", x + 12, y + 76, w - 24, c.muted());
            pageLabel(draw, review.changes().size(), y + h - 50);
        } else if (mode == Mode.DELETE) {
            label(draw, selected + ".json", x + 12, y + 51, w - 24, c.accentBright());
            label(draw, "This removes the preset file from your library.", x + 12, y + 74, w - 24, c.text());
            label(draw, "Current settings and exported copies are unchanged.", x + 12, y + 91, w - 24, c.muted());
        } else if (mode == Mode.RENAME) {
            label(draw, "Current name: " + selected, x + 12, y + 51, w - 24, c.muted());
            label(draw, "New name", x + 12, y + 68, w - 24, c.accentBright());
        } else {
            label(draw, status, x + 12, y + 31, w - 24, c.muted());
            RoundedGui.fill(draw, x + 12, y + 102, w - 24, rows * 28 + 2, 4, c.nest());
            if (profiles.isEmpty()) label(draw, mode == Mode.IMPORT ? "No incoming JSON files. Open inbox to add one." : "No saved presets. Name one above and Save current.", x + 20, y + 114, w - 40, c.muted());
            pageLabel(draw, profiles.size(), y + 115 + rows * 28);
        }
        if (mode == Mode.RENAME || mode == Mode.DELETE || mode == Mode.REVIEW) {
            // Errors are visible even while a confirmation page remains open.
            if (status.startsWith("Error: ")) label(draw, status, x + 12, y + h - 77, w - 24, c.accentBright());
        }
        if (input != null) {
            RoundedGui.fill(draw, input.getX() - 6, input.getY() - 6, input.getWidth() + 12, 22, 4, c.nest());
            RoundedGui.outlineOnly(draw, input.getX() - 6, input.getY() - 6, input.getWidth() + 12, 22, 4,
                input.isFocused() ? c.accentDim() : c.outlineSoft());
        }
        super.extractRenderState(draw, mouseX, mouseY, delta);
    }

    private void pageLabel(GuiGraphicsExtractor draw, int count, int top) {
        String value = count + " total  /  " + (page + 1) + " of " + Math.max(1, (count + rows - 1) / rows);
        int width = ArcaneFont.width(font, value);
        label(draw, value, x + (w - width) / 2, top, w - 150, colors().faint());
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if ((mode == Mode.LIBRARY || mode == Mode.IMPORT || mode == Mode.REVIEW) && vertical != 0 && mouseX >= x && mouseX < x + w && mouseY >= y + 100 && mouseY < y + h - 70) {
            int count = mode == Mode.REVIEW ? review.changes().size() : profiles.size();
            page = Math.clamp(page + (vertical < 0 ? 1 : -1), 0, Math.max(0, (count - 1) / rows));
            rebuildWidgets(); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void back() {
        mode = Mode.LIBRARY; review = null; page = 0; draft = "";
        status = "Personal data and keybinds stay local. Loads keep automation off."; rebuildWidgets();
    }

    @Override public void onClose() {
        if (mode != Mode.LIBRARY) { back(); return; }
        // A fresh catalog also reloads appearance/layout and cannot retain stale nested bindings.
        minecraft.gui.setScreen(applied && parent instanceof ArcaneSettingsScreen ? new ArcaneSettingsScreen(null) : parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void run(IoAction action) {
        try { action.run(); }
        catch (IOException | RuntimeException failure) { status = "Error: " + message(failure); }
    }
    private static String message(Exception failure) { return failure.getMessage() == null ? "Unable to complete this profile action." : failure.getMessage(); }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private ClickGuiColors colors() { return ClickGuiColors.display(config); }
    private void label(GuiGraphicsExtractor draw, String text, int left, int top, int maxWidth, int color) {
        draw.text(font, ArcaneFont.trimmed(font, text, Math.max(1, maxWidth)), left, top, color, false);
    }
    private LibraryButton button(int left, int top, int width, String text, Runnable action) {
        return addRenderableWidget(new LibraryButton(left, top, width, text, action));
    }

    private final class LibraryButton extends AbstractWidget {
        private final Runnable action;
        LibraryButton(int x, int y, int width, String text, Runnable action) {
            super(x, y, width, 21, ArcaneFont.text(text)); this.action = action;
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
            var c = colors();
            RoundedGui.fill(draw, getX(), getY(), getWidth(), getHeight(), 4, isHovered() || isFocused() ? c.activeHover() : c.nest());
            RoundedGui.outlineOnly(draw, getX(), getY(), getWidth(), getHeight(), 4, isFocused() ? c.accentBright() : c.outlineSoft());
            label(draw, getMessage().getString(), getX() + 6, getY() + 6, getWidth() - 12, active ? c.text() : c.faint());
        }
        @Override public void onClick(MouseButtonEvent click, boolean doubled) { action.run(); }
        @Override public boolean keyPressed(KeyEvent input) {
            if (active && isFocused() && (input.key() == InputConstants.KEY_RETURN || input.key() == InputConstants.KEY_NUMPADENTER || input.key() == InputConstants.KEY_SPACE)) {
                playDownSound(minecraft.getSoundManager()); action.run(); return true;
            }
            return super.keyPressed(input);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }

    private final class ProfileRow extends AbstractWidget {
        private final String name;
        ProfileRow(int x, int y, int width, String name) { super(x, y, width, 26, Component.literal(name)); this.name = name; }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
            var c = colors(); boolean chosen = name.equals(selected);
            RoundedGui.fill(draw, getX() + 2, getY(), getWidth() - 4, 26, 3, chosen ? c.active() : isHovered() || isFocused() ? c.hover() : c.nest());
            label(draw, name, getX() + 8, getY() + 4, getWidth() - 18, c.text());
            label(draw, name + ".json", getX() + 8, getY() + 15, getWidth() - 18, c.muted());
            if (isFocused()) RoundedGui.outlineOnly(draw, getX() + 2, getY(), getWidth() - 4, 26, 3, c.accentBright());
        }
        private void select() {
            selected = name;
            if (mode == Mode.IMPORT) draft = name;
            rebuildWidgets();
        }
        @Override public void onClick(MouseButtonEvent click, boolean doubled) { select(); }
        @Override public boolean keyPressed(KeyEvent input) {
            if (isFocused() && (input.key() == InputConstants.KEY_RETURN || input.key() == InputConstants.KEY_SPACE)) { select(); return true; }
            return super.keyPressed(input);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }
}
