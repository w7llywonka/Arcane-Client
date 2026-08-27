package dev.arcaneclient.scan;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;

@Environment(EnvType.CLIENT)
public final class ActivityClassifier {
    private static final Set<String> FIELD_CROPS = Set.of(
        "wheat",
        "carrots",
        "potatoes",
        "beetroots",
        "nether_wart",
        "cocoa",
        "melon_stem",
        "pumpkin_stem",
        "attached_melon_stem",
        "attached_pumpkin_stem",
        "sweet_berry_bush",
        "torchflower_crop",
        "pitcher_crop",
        "pitcher_plant",
        "torchflower"
    );
    private static final Set<String> SAPLINGS = Set.of(
        "oak_sapling",
        "spruce_sapling",
        "birch_sapling",
        "jungle_sapling",
        "acacia_sapling",
        "cherry_sapling",
        "dark_oak_sapling",
        "pale_oak_sapling",
        "mangrove_propagule"
    );
    private static final Set<String> VERTICAL_GROWTH = Set.of(
        "sugar_cane",
        "cactus",
        "bamboo",
        "bamboo_sapling",
        "kelp",
        "kelp_plant",
        "cave_vines",
        "cave_vines_plant",
        "weeping_vines",
        "weeping_vines_plant",
        "twisting_vines",
        "twisting_vines_plant",
        "chorus_plant",
        "chorus_flower"
    );
    private static final Set<String> IMPORTED_GROWTH = Set.of(
        "sweet_berry_bush",
        "bamboo",
        "bamboo_sapling",
        "cocoa",
        "kelp",
        "kelp_plant",
        "cactus",
        "mangrove_propagule",
        "nether_wart",
        "weeping_vines",
        "weeping_vines_plant",
        "twisting_vines",
        "twisting_vines_plant",
        "chorus_plant",
        "chorus_flower"
    );

    private final Map<BlockState, BlockFacts> factsCache = new IdentityHashMap<>();

    BlockFacts facts(BlockState state) {
        BlockFacts cached = this.factsCache.get(state);
        if (cached != null) {
            return cached;
        }

        String path = blockPath(state);
        GrowthKind kind = growthKind(path);
        Stage stage = kind == GrowthKind.NONE ? Stage.NONE : stage(state);
        BlockFacts facts = new BlockFacts(
            path,
            kind,
            stage.value(),
            stage.maximum(),
            path.equals("farmland"),
            IMPORTED_GROWTH.contains(path)
        );
        this.factsCache.put(state, facts);
        return facts;
    }

    public boolean isRelevant(BlockState state) {
        BlockFacts facts = this.facts(state);
        return facts.isGrowth() || facts.farmland();
    }

    public GrowthObservation observe(BlockState state) {
        BlockFacts facts = this.facts(state);
        return new GrowthObservation(facts.path(), facts.kind(), facts.stage(), facts.maximumStage());
    }

    boolean isImportedPlant(ClientWorld world, BlockPos pos, String path) {
        return switch (path) {
            case "sweet_berry_bush" -> !world.getBiome(pos).isIn(BiomeTags.IS_TAIGA);
            case "bamboo", "bamboo_sapling", "cocoa" -> !world.getBiome(pos).isIn(BiomeTags.IS_JUNGLE);
            case "kelp", "kelp_plant" -> !world.getBiome(pos).isIn(BiomeTags.IS_OCEAN);
            case "cactus" -> !world.getBiome(pos).matchesKey(BiomeKeys.DESERT)
                && !world.getBiome(pos).isIn(BiomeTags.IS_BADLANDS);
            case "mangrove_propagule" -> !world.getBiome(pos).matchesKey(BiomeKeys.MANGROVE_SWAMP);
            case "nether_wart", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant" ->
                !world.getBiome(pos).isIn(BiomeTags.IS_NETHER);
            case "chorus_plant", "chorus_flower" -> !world.getBiome(pos).isIn(BiomeTags.IS_END);
            default -> false;
        };
    }

    private static GrowthKind growthKind(String path) {
        if (FIELD_CROPS.contains(path)) {
            return GrowthKind.FIELD;
        }
        if (SAPLINGS.contains(path)) {
            return GrowthKind.SAPLING;
        }
        if (VERTICAL_GROWTH.contains(path)) {
            return GrowthKind.VERTICAL;
        }
        return GrowthKind.NONE;
    }

    private static Stage stage(BlockState state) {
        Stage fallback = Stage.NONE;
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            Property<?> property = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Integer current)) {
                continue;
            }
            String name = property.getName();
            if (!name.equals("age") && !name.equals("stage") && !name.equals("flower_amount")) {
                continue;
            }

            int maximum = 0;
            for (Comparable<?> candidate : property.getValues()) {
                if (candidate instanceof Integer integer) {
                    maximum = Math.max(maximum, integer);
                }
            }
            Stage found = new Stage(current, maximum);
            if (name.equals("age")) {
                return found;
            }
            fallback = found;
        }
        return fallback;
    }

    public static String blockPath(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        return id == null ? "unknown" : id.getPath();
    }

    @Environment(EnvType.CLIENT)
    public enum GrowthKind {
        NONE,
        FIELD,
        SAPLING,
        VERTICAL
    }

    @Environment(EnvType.CLIENT)
    public record GrowthObservation(String path, GrowthKind kind, int stage, int maximumStage) {
        public boolean isGrowth() {
            return this.kind != GrowthKind.NONE;
        }

        public boolean hasStages() {
            return this.stage >= 0 && this.maximumStage > 0;
        }

        public boolean mature() {
            return this.hasStages() && this.stage >= this.maximumStage;
        }
    }

    @Environment(EnvType.CLIENT)
    record BlockFacts(
        String path,
        GrowthKind kind,
        int stage,
        int maximumStage,
        boolean farmland,
        boolean importedPlantCandidate
    ) {
        boolean isGrowth() {
            return this.kind != GrowthKind.NONE;
        }

        boolean mature() {
            return this.maximumStage > 0 && this.stage >= this.maximumStage;
        }

        int growthBucket() {
            if (this.stage < 0 || this.maximumStage <= 0) {
                return -1;
            }
            return Math.min(4, this.stage * 4 / this.maximumStage);
        }
    }

    private record Stage(int value, int maximum) {
        private static final Stage NONE = new Stage(-1, -1);
    }
}
