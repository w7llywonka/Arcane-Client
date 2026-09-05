package dev.arcaneclient.scan;

import dev.arcaneclient.model.SignalCategory;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.BiomeKeys;

@Environment(value=EnvType.CLIENT)
public final class ActivityClassifier {
    private final Map<BlockState, BlockFacts> factsCache = new IdentityHashMap<BlockState, BlockFacts>();
    private static final Set<String> SAPLINGS = Set.of("oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "cherry_sapling", "dark_oak_sapling", "pale_oak_sapling", "mangrove_propagule", "bamboo_sapling");
    private static final Set<String> BREEDING_BLOCKS = Set.of("frogspawn", "turtle_egg", "sniffer_egg");
    private static final Set<String> SNIFFER_CROPS = Set.of("torchflower_crop", "pitcher_crop", "pitcher_plant", "torchflower");
    private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa", "melon_stem", "pumpkin_stem", "attached_melon_stem", "attached_pumpkin_stem", "sweet_berry_bush", "torchflower_crop", "pitcher_crop");
    private static final Set<String> FUNCTIONAL = Set.of("note_block");
    private static final Set<String> REDSTONE = Set.of("redstone_wire", "repeater", "comparator", "observer", "piston", "sticky_piston", "redstone_lamp", "lever", "target", "daylight_detector", "tripwire_hook", "redstone_torch", "redstone_wall_torch", "redstone_block");
    private static final Set<String> AMETHYST = Set.of("budding_amethyst", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud");
    private static final Set<String> HIGH_BLOCK_ENTITIES = Set.of("copper_golem_statue");
    private static final Set<String> FUNCTIONAL_BLOCK_ENTITIES = Set.of("comparator", "daylight_detector", "bed", "sign", "hanging_sign", "banner");

    public Signal classifyBlock(BlockState state) {
        return this.facts(state).signal();
    }

    BlockFacts facts(BlockState state) {
        BlockFacts cached = this.factsCache.get(state);
        if (cached != null) {
            return cached;
        }
        String path = ActivityClassifier.blockPath(state);
        boolean crop = this.isCrop(path);
        BlockFacts facts = new BlockFacts(path, this.classifyBlock(state, path), crop, crop ? ActivityClassifier.growthBucket(state) : -1, this.isFarmland(path), ActivityClassifier.isImportedPlantPath(path), this.isPointedDripstone(path), this.isCauldron(path), this.isAmethystStage(path), this.isOpaqueMaskBlock(state, path), path.equals("spawner"), ActivityClassifier.isTemporalStable(path));
        this.factsCache.put(state, facts);
        return facts;
    }

    boolean isDetailedScanCandidate(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        BlockFacts facts = this.facts(state);
        if (ScannerStorageFilter.isStoragePath(facts.path())) {
            return false;
        }
        if (facts.signal() != null || facts.crop() || facts.farmland() || facts.importedPlantCandidate()
            || facts.pointedDripstone() || facts.cauldron() || facts.spawner() || facts.amethystStage()
            || state.getLuminance() > 0) {
            return true;
        }
        return switch (facts.path()) {
            case "amethyst_block", "calcite", "smooth_basalt", "reinforced_deepslate", "trial_spawner", "vault",
                 "sculk_catalyst", "sculk_sensor", "sculk_shrieker", "sugar_cane" -> true;
            default -> tunnelPassablePath(facts.path());
        };
    }

    public static boolean isCobbledDeepslateTrailPath(String path) {
        return path.equals("cobbled_deepslate")
            || path.equals("cobbled_deepslate_stairs")
            || path.equals("cobbled_deepslate_slab")
            || path.equals("cobbled_deepslate_wall");
    }

    private static boolean tunnelPassablePath(String path) {
        return path.endsWith("torch") || path.endsWith("wall_torch") || path.contains("rail")
            || path.equals("redstone_wire") || path.equals("tripwire") || path.equals("ladder");
    }

    Signal classifyBlock(BlockState state, String path) {
        if (ScannerStorageFilter.isStoragePath(path)) {
            return null;
        }
        if (path.endsWith("_leaves") && state.contains(Properties.PERSISTENT) && ((Boolean)state.get(Properties.PERSISTENT)).booleanValue()) {
            return Signal.PERSISTENT_LEAVES;
        }
        if (BREEDING_BLOCKS.contains(path)) {
            return Signal.BREEDING_EGGS;
        }
        if (SNIFFER_CROPS.contains(path)) {
            return Signal.SNIFFER_CROP;
        }
        if (path.equals("resin_clump")) {
            return Signal.RESIN;
        }
        if (path.contains("copper_golem_statue")) {
            return Signal.COPPER_BASE_BLOCK;
        }
        if (path.equals("respawn_anchor") && state.contains(Properties.CHARGES) && (Integer)state.get(Properties.CHARGES) > 0) {
            return Signal.CHARGED_ANCHOR;
        }
        if (path.equals("sculk_shrieker") && state.contains(Properties.CAN_SUMMON) && !((Boolean)state.get(Properties.CAN_SUMMON)).booleanValue()) {
            return Signal.DISABLED_SHRIEKER;
        }
        if (REDSTONE.contains(path) || path.endsWith("_copper_bulb")) {
            return Signal.REDSTONE;
        }
        if (path.equals("deepslate") && state.contains(Properties.AXIS) && state.get(Properties.AXIS) != Direction.Axis.Y) {
            return Signal.HORIZONTAL_DEEPSLATE;
        }
        if (path.startsWith("cobbled_deepslate") || path.startsWith("polished_deepslate") || path.startsWith("deepslate_brick") || path.startsWith("deepslate_tile") || path.equals("chiseled_deepslate")) {
            return Signal.WORKED_DEEPSLATE;
        }
        if (path.equals("nether_portal")) {
            return Signal.ACTIVE_PORTAL;
        }
        if (path.equals("spawner")) {
            return Signal.MOB_SPAWNER;
        }
        if ((path.equals("campfire") || path.equals("soul_campfire")) && state.contains(Properties.LIT) && ((Boolean)state.get(Properties.LIT)).booleanValue()) {
            return Signal.LIT_CAMPFIRE;
        }
        if (FUNCTIONAL.contains(path)) {
            return Signal.FUNCTIONAL;
        }
        if (SAPLINGS.contains(path)) {
            return Signal.SAPLING;
        }
        if (AMETHYST.contains(path)) {
            return Signal.AMETHYST_STAGE;
        }
        return null;
    }

    public boolean isCrop(BlockState state) {
        return this.isCrop(ActivityClassifier.blockPath(state));
    }

    boolean isCrop(String path) {
        return CROPS.contains(path);
    }

    public boolean isFarmland(BlockState state) {
        return this.isFarmland(ActivityClassifier.blockPath(state));
    }

    boolean isFarmland(String path) {
        return path.equals("farmland");
    }

    public boolean isPointedDripstone(BlockState state) {
        return this.isPointedDripstone(ActivityClassifier.blockPath(state));
    }

    boolean isPointedDripstone(String path) {
        return path.equals("pointed_dripstone");
    }

    public boolean isCauldron(BlockState state) {
        return this.isCauldron(ActivityClassifier.blockPath(state));
    }

    boolean isCauldron(String path) {
        return path.endsWith("cauldron");
    }

    public boolean isAmethystStage(BlockState state) {
        return this.isAmethystStage(ActivityClassifier.blockPath(state));
    }

    boolean isAmethystStage(String path) {
        return AMETHYST.contains(path);
    }

    public boolean isOpaqueMaskBlock(BlockState state) {
        return this.isOpaqueMaskBlock(state, ActivityClassifier.blockPath(state));
    }

    boolean isOpaqueMaskBlock(BlockState state, String path) {
        return (path.equals("stone") || path.equals("deepslate")) && state.isOpaqueFullCube() && state.getOpacity() >= 15;
    }

    public boolean isHighBlockEntityType(String path) {
        return !ScannerStorageFilter.isStoragePath(path) && HIGH_BLOCK_ENTITIES.contains(path);
    }

    public boolean isFunctionalBlockEntityType(String path) {
        return !ScannerStorageFilter.isStoragePath(path) && FUNCTIONAL_BLOCK_ENTITIES.contains(path);
    }

    public boolean isImportedPlant(ClientWorld level, BlockPos pos, BlockState state) {
        return this.isImportedPlant(level, pos, ActivityClassifier.blockPath(state));
    }

    boolean isImportedPlant(ClientWorld level, BlockPos pos, String path) {
        return switch (path) {
            case "sweet_berry_bush" -> {
                if (!level.getBiome(pos).isIn(BiomeTags.IS_TAIGA)) {
                    yield true;
                }
                yield false;
            }
            case "bamboo", "bamboo_sapling", "cocoa" -> {
                if (!level.getBiome(pos).isIn(BiomeTags.IS_JUNGLE)) {
                    yield true;
                }
                yield false;
            }
            case "kelp", "kelp_plant" -> {
                if (!level.getBiome(pos).isIn(BiomeTags.IS_OCEAN)) {
                    yield true;
                }
                yield false;
            }
            case "cactus" -> {
                if (!level.getBiome(pos).matchesKey(BiomeKeys.DESERT) && !level.getBiome(pos).isIn(BiomeTags.IS_BADLANDS)) {
                    yield true;
                }
                yield false;
            }
            case "mangrove_propagule" -> {
                if (!level.getBiome(pos).matchesKey(BiomeKeys.MANGROVE_SWAMP)) {
                    yield true;
                }
                yield false;
            }
            case "nether_wart", "crimson_fungus", "warped_fungus", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant" -> {
                if (!level.getBiome(pos).isIn(BiomeTags.IS_NETHER)) {
                    yield true;
                }
                yield false;
            }
            case "chorus_plant", "chorus_flower" -> {
                if (!level.getBiome(pos).isIn(BiomeTags.IS_END)) {
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean isImportedPlantPath(String path) {
        return switch (path) {
            case "sweet_berry_bush", "bamboo", "bamboo_sapling", "cocoa", "kelp", "kelp_plant", "cactus", "mangrove_propagule", "nether_wart", "crimson_fungus", "warped_fungus", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "chorus_plant", "chorus_flower" -> true;
            default -> false;
        };
    }

    private static int growthBucket(BlockState state) {
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            Object v;
            Property<?> property = entry.getKey();
            if (!property.getName().equals("age") || !((v = entry.getValue()) instanceof Integer)) continue;
            Integer age = (Integer)v;
            int maximum = property.getValues().stream().filter(Integer.class::isInstance).map(Integer.class::cast).mapToInt(Integer::intValue).max().orElse(0);
            return maximum == 0 ? 0 : Math.min(4, age * 4 / maximum);
        }
        return -1;
    }

    private static boolean isTemporalStable(String path) {
        return !ScannerStorageFilter.isStoragePath(path) && !CROPS.contains(path) && !SAPLINGS.contains(path) && !AMETHYST.contains(path) && !path.contains("vine") && !path.contains("kelp") && !path.contains("bamboo") && !path.endsWith("_leaves") && !path.contains("grass") && !path.contains("fern") && !path.contains("flower") && !path.contains("mushroom") && !path.equals("cactus") && !path.equals("sugar_cane") && !path.contains("fire") && !path.contains("snow") && !path.equals("water") && !path.equals("lava") && !path.equals("bubble_column") && !path.equals("pointed_dripstone") && !path.startsWith("chorus_");
    }

    public static String blockPath(BlockState state) {
        return ActivityClassifier.pathOf(Registries.BLOCK.getId(state.getBlock()));
    }

    public static String blockEntityTypePath(BlockEntity blockEntity) {
        return ActivityClassifier.pathOf(Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()));
    }

    public static String entityTypePath(Entity entity) {
        return ActivityClassifier.pathOf(Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    private static String pathOf(Object key) {
        String id = key == null ? "unknown" : key.toString();
        int separator = id.indexOf(58);
        return separator < 0 ? id : id.substring(separator + 1);
    }

    @Environment(value=EnvType.CLIENT)
    record BlockFacts(String path, Signal signal, boolean crop, int growthBucket, boolean farmland, boolean importedPlantCandidate, boolean pointedDripstone, boolean cauldron, boolean amethystStage, boolean opaqueMaskBlock, boolean spawner, boolean temporalStable) {
    }

    @Environment(value=EnvType.CLIENT)
    public static enum Signal {
        PERSISTENT_LEAVES(SignalCategory.PLACED_BLOCK, "persistent leaves", 25, 3, 65),
        SAPLING(SignalCategory.CULTIVATION, "saplings", 15, 3, 45),
        BREEDING_EGGS(SignalCategory.INTERACTION, "player-bred or placed eggs", 110, 15, 180),
        SNIFFER_CROP(SignalCategory.CULTIVATION, "sniffer crops", 85, 10, 150),
        RESIN(SignalCategory.INTERACTION, "resin clumps", 30, 4, 70),
        COPPER_BASE_BLOCK(SignalCategory.INFRASTRUCTURE, "copper golem statues", 125, 12, 200),
        CHARGED_ANCHOR(SignalCategory.INTERACTION, "charged respawn anchor", 120, 10, 180),
        DISABLED_SHRIEKER(SignalCategory.INTERACTION, "non-summoning sculk shrieker", 28, 4, 70),
        FUNCTIONAL(SignalCategory.INFRASTRUCTURE, "functional blocks", 16, 3, 70),
        REDSTONE(SignalCategory.INFRASTRUCTURE, "redstone machinery", 22, 4, 90),
        HORIZONTAL_DEEPSLATE(SignalCategory.PLACED_BLOCK, "horizontal deepslate axis", 90, 8, 170),
        WORKED_DEEPSLATE(SignalCategory.PLACED_BLOCK, "worked deepslate blocks", 12, 1, 35),
        ACTIVE_PORTAL(SignalCategory.INFRASTRUCTURE, "active portal blocks", 70, 5, 130),
        MOB_SPAWNER(SignalCategory.INFRASTRUCTURE, "mob spawners", 40, 8, 80),
        LIT_CAMPFIRE(SignalCategory.INTERACTION, "lit campfires", 35, 5, 80),
        AMETHYST_STAGE(SignalCategory.NATURAL_GROWTH, "static amethyst stages", 1, 0, 3);

        private final SignalCategory category;
        private final String reason;
        private final int baseStrength;
        private final int extraStrength;
        private final int maximumStrength;

        private Signal(SignalCategory category, String reason, int baseStrength, int extraStrength, int maximumStrength) {
            this.category = category;
            this.reason = reason;
            this.baseStrength = baseStrength;
            this.extraStrength = extraStrength;
            this.maximumStrength = maximumStrength;
        }

        public SignalCategory category() {
            return this.category;
        }

        public String reason() {
            return this.reason;
        }

        public int aggregateStrength(int count) {
            if (count <= 0) {
                return 0;
            }
            long value = (long)this.baseStrength + (long)this.extraStrength * (long)(count - 1);
            return (int)Math.min((long)this.maximumStrength, value);
        }
    }
}
