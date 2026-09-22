package dev.arcaneclient.render;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.esp.ItemEspCategory;
import dev.arcaneclient.performance.PerformanceProfile;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(value=EnvType.CLIENT)
public final class ItemEspRenderer {
    private static final VoxelShape ITEM_BOX = Shapes.box((double)-0.28, (double)-0.05, (double)-0.28, (double)0.28, (double)0.55, (double)0.28);
    private static List<Target> targets = List.of();
    private static long lastRefresh = Long.MIN_VALUE;
    private static Set<String> indexedIds = Set.of();
    private static Set<Item> selectedItems = Set.of();
    private static int filterSignature;

    private ItemEspRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ItemEspRenderer::render);
    }

    public static void reset() {
        targets = List.of();
        lastRefresh = Long.MIN_VALUE;
        indexedIds = Set.of();
        selectedItems = Set.of();
        filterSignature = 0;
    }

    public static void tick(Minecraft client) {
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.itemEsp || client.level == null || client.getCameraEntity() == null) {
            reset();
            return;
        }
        PerformanceProfile profile = config.performanceProfile();
        long gameTime = client.level.getGameTime();
        int signature = Objects.hash(config.itemEspAll, config.itemEspUsePresets, config.itemEspItems,
            config.itemEspCustomColor, config.itemEspRange, config.itemEspTotems, config.itemEspCrystals,
            config.itemEspElytra, config.itemEspShulkers, config.itemEspGapples, config.itemEspValuables,
            config.itemEspTotemColor, config.itemEspCrystalColor, config.itemEspElytraColor,
            config.itemEspShulkerColor, config.itemEspGappleColor, config.itemEspValuableColor, profile);
        boolean selectionChanged = !indexedIds.equals(config.itemEspItems);
        if (selectionChanged) {
            indexedIds = Set.copyOf(config.itemEspItems);
            HashSet<Item> resolved = new HashSet<>();
            for (String itemId : indexedIds) {
                Identifier id = Identifier.tryParse(itemId);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.getValue(id) != Items.AIR) resolved.add(BuiltInRegistries.ITEM.getValue(id));
            }
            selectedItems = Set.copyOf(resolved);
        }
        if (!selectionChanged && signature == filterSignature && lastRefresh != Long.MIN_VALUE && gameTime >= lastRefresh && gameTime - lastRefresh < profile.itemRefreshTicks()) {
            return;
        }
        filterSignature = signature;
        lastRefresh = gameTime;
        if (!config.itemEspAll && selectedItems.isEmpty() && (!config.itemEspUsePresets
            || !(config.itemEspTotems || config.itemEspCrystals || config.itemEspElytra || config.itemEspShulkers || config.itemEspGapples || config.itemEspValuables))) {
            targets = List.of();
            return;
        }
        Entity camera = client.getCameraEntity();
        int targetLimit = profile.itemTargetLimit();
        double maxDistanceSquared = (double)config.itemEspRange * config.itemEspRange;
        PriorityQueue<Candidate> nearest = new PriorityQueue<>(targetLimit, Comparator.comparingDouble(Candidate::distanceSquared).reversed());
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || !item.isAlive()) continue;
            double distance = item.distanceToSqr(camera);
            if (distance > maxDistanceSquared || nearest.size() == targetLimit && distance >= nearest.peek().distanceSquared()) continue;
            int color = matchColor(item.getItem(), config);
            if (color == 0) continue;
            if (nearest.size() == targetLimit) nearest.remove();
            nearest.add(new Candidate(item, color, distance));
        }
        targets = nearest.stream().sorted(Comparator.comparingDouble(Candidate::distanceSquared))
            .map(candidate -> new Target(candidate.entity(), candidate.color())).toList();
    }

    /** Explicit selections override category colors; presets remain backward compatible. */
    private static int matchColor(ItemStack stack, ArcaneConfig config) {
        if (stack.isEmpty()) return 0;
        if (selectedItems.contains(stack.getItem())) return config.itemEspCustomColor;
        if (config.itemEspUsePresets) {
            ItemEspCategory category = ItemEspCategory.match(stack);
            if (category != null && config.itemEspEnabled(category)) return config.itemEspColor(category);
        }
        return config.itemEspAll ? config.itemEspCustomColor : 0;
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.itemEsp || targets.isEmpty() || client.level == null) {
            return;
        }
        if (context.poseStack() == null) {
            return;
        }
        Vec3 camera = Render263.camera(context);
        Render263.Batch batch = Render263.batch(context);
        for (Target target : targets) {
            ItemEntity entity = target.entity();
            if (entity.isRemoved() || !entity.isAlive() || client.level == null
                || client.level.getEntity(entity.getId()) != entity || matchColor(entity.getItem(), config) == 0
                || entity.distanceToSqr(camera) > (double)config.itemEspRange * config.itemEspRange) continue;
            Vec3 position = entity.getPosition(Render263.partialTick(context));
            batch.outline(ITEM_BOX, position.x, position.y, position.z, target.color(), 2.0f, true);
            if (!config.itemTracers) continue;
            batch.line(camera.x, camera.y, camera.z, position.x, position.y + 0.25, position.z,
                target.color(), 1.25f, true);
        }
        batch.submit();
    }

    @Environment(value=EnvType.CLIENT)
    private record Target(ItemEntity entity, int color) {
    }

    private record Candidate(ItemEntity entity, int color, double distanceSquared) { }
}
