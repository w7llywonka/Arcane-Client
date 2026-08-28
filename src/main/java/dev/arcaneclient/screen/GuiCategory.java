package dev.arcaneclient.screen;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** A draggable, collapsible module window. */
@Environment(EnvType.CLIENT)
public final class GuiCategory {
    private final String name;
    private final List<GuiModule> modules;

    private List<GuiModule> visible;
    private int x;
    private int y;
    private boolean open = true;
    private int lastHeight;

    public GuiCategory(String name, List<GuiModule> modules) {
        this.name = name;
        this.modules = List.copyOf(modules);
        this.visible = this.modules;
    }

    public String name() {
        return this.name;
    }

    public List<GuiModule> modules() {
        return this.modules;
    }

    public List<GuiModule> visible() {
        return this.visible;
    }

    public void filter(String query) {
        if (query.isEmpty()) {
            this.visible = this.modules;
            return;
        }
        List<GuiModule> matched = new ArrayList<>();
        for (GuiModule module : this.modules) {
            if (module.matches(query)) {
                matched.add(module);
            }
        }
        this.visible = List.copyOf(matched);
    }

    public int enabledCount() {
        int enabled = 0;
        for (GuiModule module : this.visible) {
            if (module.enabled()) {
                enabled++;
            }
        }
        return enabled;
    }

    public int toggleableCount() {
        int count = 0;
        for (GuiModule module : this.visible) {
            if (module.toggleable()) {
                count++;
            }
        }
        return count;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean open() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void toggleOpen() {
        this.open = !this.open;
    }

    public int lastHeight() {
        return this.lastHeight;
    }

    public void setLastHeight(int lastHeight) {
        this.lastHeight = lastHeight;
    }

    /**
     * Keeps the window reachable: the title bar always stays on screen, and a window taller than the
     * work area may be dragged above {@code minY} so its last row can still be clicked.
     */
    public void clamp(int screenWidth, int windowWidth, int headerHeight, int minY, int maxY) {
        this.x = Math.clamp(this.x, 2, Math.max(2, screenWidth - windowWidth - 2));
        int lowest = Math.min(minY, maxY - this.lastHeight);
        int highest = Math.max(lowest, maxY - headerHeight);
        this.y = Math.clamp(this.y, lowest, highest);
    }
}
