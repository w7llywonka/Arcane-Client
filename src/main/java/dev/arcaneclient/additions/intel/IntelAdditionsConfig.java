package dev.arcaneclient.additions.intel;

import java.util.ArrayList;
import java.util.List;

/** Received-world overlays, separate from growth evidence and suspicious-chunk scoring. */
public final class IntelAdditionsConfig {
    public boolean blockEsp;
    public boolean blockAll;
    public boolean blockTracers;
    public boolean blockEntityEsp;
    public boolean blockEntityDeepOnly = true;
    public boolean blockEntityTracers;
    /** Legacy shared settings, retained only to migrate existing configurations. */
    public boolean tracers;
    public boolean spawnerNametags;
    public boolean regionMap;
    public boolean staffList;
    public boolean weatherNotifier;
    public boolean keystrokes;
    public boolean activeModules;
    public boolean skinProtect;
    public int range = 96;
    public int blockRange = 96;
    public int blockEntityRange = 96;
    public int espSettingsVersion;
    public int blockColor = 0xFFDF8CBF;
    public int entityColor = 0xFF72CFC6;
    public int mapSize = 96;
    public List<String> blocks = new ArrayList<>(List.of("minecraft:ancient_debris", "minecraft:spawner", "minecraft:trial_spawner"));

    public void sanitize() {
        range = Math.clamp(range, 16, 192);
        if (espSettingsVersion < 1) {
            blockRange = range;
            blockEntityRange = range;
            blockTracers = tracers;
            blockEntityTracers = tracers;
            espSettingsVersion = 1;
        }
        blockRange = Math.clamp(blockRange, 16, 192);
        blockEntityRange = Math.clamp(blockEntityRange, 16, 192);
        mapSize = Math.clamp(mapSize, 64, 160);
        blockColor |= 0xFF000000;
        entityColor |= 0xFF000000;
        if (blocks == null) blocks = new ArrayList<>();
        blocks = new ArrayList<>(blocks.stream().filter(s -> s != null && s.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
            .filter(s -> !s.equals("minecraft:air") && !s.equals("minecraft:cave_air") && !s.equals("minecraft:void_air"))
            .distinct().toList());
    }
}
