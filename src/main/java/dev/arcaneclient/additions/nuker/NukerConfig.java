package dev.arcaneclient.additions.nuker;

import java.util.ArrayList;
import java.util.List;

public final class NukerConfig {
    public boolean enabled;
    public boolean holdAttack = true;
    public boolean protectFloor = true;
    public boolean protectBlockEntities = true;
    public int range = 4;
    public int delayTicks = 2;
    public boolean allBlocks = true;
    public List<String> blocks = new ArrayList<>();

    public void sanitize() {
        range = Math.clamp(range, 1, 6);
        delayTicks = Math.clamp(delayTicks, 1, 20);
        blocks = blocks == null ? new ArrayList<>() : new ArrayList<>(blocks.stream()
            .filter(s -> s != null && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            .distinct().limit(4096).toList());
    }

    public boolean accepts(String id, int blockY, int feetY, boolean blockEntity) {
        return (!protectFloor || blockY >= feetY) && (!protectBlockEntities || !blockEntity)
            && (allBlocks || blocks.contains(id));
    }
}
