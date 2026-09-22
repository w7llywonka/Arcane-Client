package dev.arcaneclient.additions.susfinder;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Closed allowlist: no inventories, block entities, saplings, sound or generic material evidence. */
final class SusBlockSignals {
    private static final Map<BlockState, Signal> STATES = new IdentityHashMap<>();
    private record Signal(SusScoring.Family family) { }
    private SusBlockSignals() { }

    static SusScoring.Family family(BlockState state) {
        return STATES.computeIfAbsent(state, SusBlockSignals::classify).family();
    }

    private static Signal classify(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return new Signal(null);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("minecraft")) return new Signal(null);
        String path = id.getPath();
        return new Signal(switch (path) {
            case "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster" -> SusScoring.Family.AMETHYST;
            case "kelp_plant" -> SusScoring.Family.KELP;
            case "bamboo" -> SusScoring.Family.BAMBOO;
            case "sweet_berry_bush" -> state.getValue(BlockStateProperties.AGE_3) >= 2 ? SusScoring.Family.BERRIES : null;
            case "cave_vines", "cave_vines_plant" -> state.getValue(BlockStateProperties.BERRIES) ? SusScoring.Family.BERRIES : null;
            case "vine", "weeping_vines_plant", "twisting_vines_plant" -> SusScoring.Family.VINES;
            case "pointed_dripstone" -> SusScoring.Family.DRIPSTONE;
            default -> null;
        });
    }

    static boolean geodeShell(BlockState state) {
        return state.is(Blocks.CALCITE) || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }
}
