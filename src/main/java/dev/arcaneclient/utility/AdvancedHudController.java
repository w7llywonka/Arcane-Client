package dev.arcaneclient.utility;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ClickGuiColors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Two grouped, configurable panels: live player status and one-pass inventory totals.
 * A five-tick snapshot keeps inventory scans and formatted-string work out of the render loop.
 */
@Environment(EnvType.CLIENT)
public final class AdvancedHudController {
    private static final Identifier HUD_ID = ArcaneClient.id("advanced_hud");
    private static final int MAX_COLUMN_WIDTH = 200;
    private static final int SNAPSHOT_INTERVAL_TICKS = 5;
    private static List<String> cachedLines = List.of();
    private static int refreshIn;
    private static boolean cachedStreamerMode;
    private static boolean cachedStatusHud;
    private static boolean cachedInventoryHud;

    private AdvancedHudController() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, (graphics, tickCounter) -> render(graphics));
    }

    public static void reset() {
        cachedLines = List.of();
        refreshIn = 0;
        cachedStreamerMode = false;
        cachedStatusHud = false;
        cachedInventoryHud = false;
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.player == null || client.level == null || (!config.statusHud && !config.inventoryHud)) {
            reset();
            return;
        }
        boolean displayModeChanged = cachedStreamerMode != config.streamerMode
            || cachedStatusHud != config.statusHud
            || cachedInventoryHud != config.inventoryHud;
        if (!displayModeChanged && refreshIn > 0) {
            refreshIn--;
            return;
        }
        cachedLines = buildLines(client, config);
        cachedStreamerMode = config.streamerMode;
        cachedStatusHud = config.statusHud;
        cachedInventoryHud = config.inventoryHud;
        refreshIn = SNAPSHOT_INTERVAL_TICKS - 1;
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.player == null || client.level == null || ArcaneVisibility.overlaysHidden()
            || ArcaneSettingsScreen.isOpen(client)) return;
        if (cachedStreamerMode != config.streamerMode) return;
        List<String> lines = cachedLines;
        if (lines.isEmpty()) return;

        Font font = ArcaneFont.renderer(client);
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        int columnCount = lines.size() > 10 ? 2 : 1;
        int rows = (lines.size() + columnCount - 1) / columnCount;
        int[] widths = new int[columnCount];
        for (int i = 0; i < lines.size(); i++) {
            widths[i / rows] = Math.min(MAX_COLUMN_WIDTH, Math.max(widths[i / rows], ArcaneFont.width(font, lines.get(i)) + 14));
        }
        int totalWidth = 0;
        for (int width : widths) totalWidth += Math.max(92, width);
        totalWidth += (columnCount - 1) * 5;
        int totalHeight = rows * (font.lineHeight + 3) + 9;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float requestedScale = config.hudScalePercent / 100.0f;
        float fitScale = Math.min((screenWidth - 14.0f) / totalWidth, (screenHeight - 14.0f) / totalHeight);
        float scale = Math.max(0.5f, Math.min(requestedScale, fitScale));
        int scaledWidth = Math.round(totalWidth * scale);
        int scaledHeight = Math.round(totalHeight * scale);
        boolean right = config.hudAnchor == 1 || config.hudAnchor == 3;
        boolean bottom = config.hudAnchor >= 2;
        int originX = right ? screenWidth - scaledWidth - 7 : 7;
        int originY = bottom ? screenHeight - scaledHeight - 7 : 7;

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(scale, scale);
        int x = 0;
        int alpha = Math.clamp(config.uiOpacityPercent * 255 / 100, 0, 255);
        int panel = alpha << 24 | colors.window() & 0x00FFFFFF;
        for (int column = 0; column < columnCount; column++) {
            int width = Math.max(92, widths[column]);
            graphics.fill(x, 0, x + width, totalHeight, panel);
            graphics.fill(x, 0, x + width, 2, colors.accent());
            int y = 6;
            int start = column * rows;
            int end = Math.min(lines.size(), start + rows);
            for (int index = start; index < end; index++) {
                graphics.text(font, ArcaneFont.trimmed(font, lines.get(index), width - 14), x + 7, y, colors.text(), false);
                y += font.lineHeight + 3;
            }
            x += width + 5;
        }
        graphics.pose().popMatrix();
    }

    static List<String> lines(Minecraft client, ArcaneConfig config) {
        return buildLines(client, config);
    }

    private static List<String> buildLines(Minecraft client, ArcaneConfig config) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return List.of();
        ArrayList<String> lines = new ArrayList<>(18);
        if (config.statusHud) addStatusLines(lines, client, player, config);
        if (config.inventoryHud) addInventoryLines(lines, player, config, InventorySnapshot.capture(player));
        return List.copyOf(lines);
    }

    private static void addStatusLines(
        List<String> lines,
        Minecraft client,
        LocalPlayer player,
        ArcaneConfig config
    ) {
        if (config.hudHealth) lines.add(String.format(Locale.ROOT, "HEALTH  %.1f ♥", player.getHealth() / 2.0f));
        if (config.hudHunger) lines.add("HUNGER  " + player.getFoodData().getFoodLevel() + "/20");
        if (config.hudArmor) lines.add("ARMOR  " + player.getArmorValue() + " PTS · MIN " + minimumArmorDurability(player) + "%");
        if (config.hudAir && player.getAirSupply() < player.getMaxAirSupply()) {
            lines.add("AIR  " + Math.max(0, player.getAirSupply()) * 100 / Math.max(1, player.getMaxAirSupply()) + "%");
        }
        if (config.hudExperience) lines.add("XP  " + player.experienceLevel + " · " + Math.round(player.experienceProgress * 100.0f) + "%");
        if (config.hudTarget) addIfPresent(lines, targetLine(client, config.streamerMode));
        ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
        if (config.hudElytra && elytra.is(Items.ELYTRA)) {
            lines.add("ELYTRA  " + (player.isFallFlying() ? "GLIDING · " : "READY · ")
                + (elytra.isDamageableItem() ? durability(elytra) + "%" : "NONE"));
        }
        if (config.hudMount) addIfPresent(lines, mountLine(player));
        if (config.hudPotionTimers) addIfPresent(lines, potionLine(player));
        if (config.deathCoordinates && config.hudDeathBeacon && QualityOfLifeController.lastDeath() != null) {
            addIfPresent(lines, config.streamerMode ? "DEATH  REDACTED" : deathBeaconLine(player, client.level.dimension().identifier().getPath()));
        }
    }

    private static void addInventoryLines(
        List<String> lines,
        LocalPlayer player,
        ArcaneConfig config,
        InventorySnapshot snapshot
    ) {
        ItemStack held = player.getMainHandItem();
        if (config.hudHeldDurability && held.isDamageableItem()) lines.add("HELD  " + durability(held) + "%");
        if (config.hudTotems) addCount(lines, "TOTEMS", snapshot.totems());
        if (config.hudRockets) addCount(lines, "ROCKETS", snapshot.rockets());
        if (config.hudPearls) addCount(lines, "PEARLS", snapshot.pearls());
        if (config.hudGapples) addCount(lines, "GAPPLES", snapshot.gapples());
        if (config.hudCrystals) addCount(lines, "CRYSTALS", snapshot.crystals());
        if (config.hudArrows) addCount(lines, "ARROWS", snapshot.arrows());
        if (config.hudInventorySpace) lines.add("SLOTS  " + snapshot.emptySlots() + "/36");
    }

    private static String targetLine(Minecraft client, boolean streamerMode) {
        if (!(client.hitResult instanceof EntityHitResult hit)) return null;
        boolean playerTarget = hit.getEntity() instanceof net.minecraft.world.entity.player.Player;
        String name = StreamerPrivacy.entityName(streamerMode, playerTarget, hit.getEntity().getName().getString());
        if (hit.getEntity() instanceof LivingEntity living) {
            return String.format(Locale.ROOT, "TARGET  %s · %.1f ♥", name, living.getHealth() / 2.0f);
        }
        return "TARGET  " + name;
    }

    private static String mountLine(LocalPlayer player) {
        if (!(player.getVehicle() instanceof LivingEntity living)) return null;
        return String.format(Locale.ROOT, "MOUNT  %s · %.1f ♥", living.getName().getString(), living.getHealth() / 2.0f);
    }

    private static String potionLine(LocalPlayer player) {
        List<MobEffectInstance> effects = player.getActiveEffects().stream()
            .sorted(Comparator.comparingInt(MobEffectInstance::getDuration))
            .limit(3)
            .toList();
        if (effects.isEmpty()) return null;
        StringBuilder value = new StringBuilder("POTIONS  ");
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) value.append(" · ");
            MobEffectInstance effect = effects.get(i);
            value.append(effect.getEffect().value().getDisplayName().getString())
                .append(' ').append(AdvancedHudFormatter.duration(effect.getDuration() / 20L));
        }
        return value.toString();
    }

    private static String deathBeaconLine(LocalPlayer player, String dimension) {
        QualityOfLifeController.LastDeath death = QualityOfLifeController.lastDeath();
        if (death == null) return null;
        if (!death.dimension().equals(dimension)) return "DEATH  " + readable(death.dimension());
        double dx = death.x() - player.getX();
        double dz = death.z() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        String direction = angle >= -45 && angle < 45 ? "S" : angle >= 45 && angle < 135 ? "W" : angle >= -135 && angle < -45 ? "E" : "N";
        return "DEATH  " + direction + " · " + AdvancedHudFormatter.distance(distance);
    }

    private static int minimumArmorDurability(LocalPlayer player) {
        int minimum = 100;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isDamageableItem()) return 0;
            minimum = Math.min(minimum, durability(stack));
        }
        return minimum;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) lines.add(value);
    }

    private static void addCount(List<String> lines, String label, int count) {
        if (count > 0) lines.add(label + "  " + count);
    }

    private static int durability(ItemStack stack) {
        return AdvancedHudFormatter.durabilityPercent(stack.getDamageValue(), stack.getMaxDamage());
    }

    private static String readable(String id) {
        return id.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private record InventorySnapshot(
        int totems,
        int rockets,
        int pearls,
        int gapples,
        int crystals,
        int arrows,
        int emptySlots
    ) {
        private static InventorySnapshot capture(LocalPlayer player) {
            int totems = 0;
            int rockets = 0;
            int pearls = 0;
            int gapples = 0;
            int crystals = 0;
            int arrows = 0;
            int empty = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (slot < 36 && stack.isEmpty()) empty++;
                if (stack.is(Items.TOTEM_OF_UNDYING)) totems += stack.getCount();
                else if (stack.is(Items.FIREWORK_ROCKET)) rockets += stack.getCount();
                else if (stack.is(Items.ENDER_PEARL)) pearls += stack.getCount();
                else if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) gapples += stack.getCount();
                else if (stack.is(Items.END_CRYSTAL)) crystals += stack.getCount();
                else if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) arrows += stack.getCount();
            }
            return new InventorySnapshot(totems, rockets, pearls, gapples, crystals, arrows, empty);
        }
    }
}
