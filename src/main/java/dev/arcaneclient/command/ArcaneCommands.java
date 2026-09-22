package dev.arcaneclient.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.WaypointStore;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.List;
import java.util.function.ToIntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

@Environment(value=EnvType.CLIENT)
public final class ArcaneCommands {
    private ArcaneCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = command("arcane", ArcaneCommands::status)
                .then(command("on", source -> enabled(source, true)))
                .then(command("off", source -> enabled(source, false)))
                .then(command("overlay", ArcaneCommands::overlay))
                .then(command("hud", ArcaneCommands::hud))
                .then(command("freecam", ArcaneCommands::freecam))
                .then(command("esp", ArcaneCommands::esp))
                .then(command("itemesp", ArcaneCommands::itemEsp))
                .then(command("tunnelesp", ArcaneCommands::tunnelEsp))
                .then(command("autototem", ArcaneCommands::autoTotem))
                .then(command("macros", ArcaneCommands::chatMacros))
                .then(command("tracers", ArcaneCommands::tracers))
                .then(command("analysis", ArcaneCommands::analysis))
                .then(command("amethystcheck", ArcaneCommands::amethystCheck))
                .then(command("settings", ignored -> settings()))
                .then(command("profile", ArcaneCommands::profile))
                .then(command("here", ArcaneCommands::here))
                .then(ClientCommands.literal("waypoint")
                    .then(command("list", ArcaneCommands::waypointList))
                    .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes(context -> waypointAdd(context, StringArgumentType.getString(context, "name")))))
                    .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes(context -> waypointRemove(context, StringArgumentType.getString(context, "name"))))))
                .then(command("list", source -> list(source, 8))
                    .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 20))
                        .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommands.literal("threshold")
                    .then(ClientCommands.argument("score", IntegerArgumentType.integer(0, 100))
                        .executes(context -> threshold(context.getSource(), IntegerArgumentType.getInteger(context, "score")))))
                .then(ClientCommands.literal("radius")
                    .then(ClientCommands.argument("chunks", IntegerArgumentType.integer(2, 24))
                        .executes(context -> radius(context.getSource(), IntegerArgumentType.getInteger(context, "chunks")))))
                .then(ClientCommands.literal("speed")
                    .then(ClientCommands.argument("intensity", IntegerArgumentType.integer(1, 16))
                        .executes(context -> speed(context.getSource(), IntegerArgumentType.getInteger(context, "intensity")))))
                .then(command("rescan", ArcaneCommands::rescan))
                .then(command("clear", ArcaneCommands::clear));

            LiteralCommandNode<FabricClientCommandSource> arcane = dispatcher.register(root);
            dispatcher.register(ClientCommands.literal("dtrace").redirect(arcane));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(
        String name,
        ToIntFunction<FabricClientCommandSource> action
    ) {
        return ClientCommands.literal(name)
            .executes(context -> action.applyAsInt(context.getSource()));
    }

    private static int amethystCheck(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return 0;
        var camera = client.gameRenderer.mainCamera().position();
        var block = net.minecraft.core.BlockPos.containing(camera);
        var center = new ChunkPos(
            net.minecraft.core.SectionPos.blockToSectionCoord(block.getX()),
            net.minecraft.core.SectionPos.blockToSectionCoord(block.getZ())
        );
        var counts = dev.arcaneclient.scan.AmethystDiagnostics.read(client.level, center);
        feedback(source, "Received blocks near camera (" + counts.chunks() + " loaded chunks, all heights): shell "
            + counts.shell() + ", budding " + counts.budding() + ", S/M/L/C " + counts.stages());
        var engine = ArcaneClient.engine();
        feedback(source, "ESP index (all indexed chunks): " + engine.amethystStageCount(0) + "/"
            + engine.amethystStageCount(1) + "/" + engine.amethystStageCount(2) + "/" + engine.amethystStageCount(3)
            + "; queued " + engine.queueSize());
        if (counts.growthBlocks() == 0) {
            feedback(source, "No buds or clusters are present in that received area. This cannot distinguish hidden blocks from absent growth.");
        }
        return 1;
    }

    private static int status(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        TraceEngine engine = ArcaneClient.engine();
        ArcaneCommands.feedback(source, "Arcane Client " + (config.enabled ? "on" : "off") + ", sensitivity " + config.sensitivity() + "%, radius " + config.scanRadius + ", speed " + config.chunksPerTick + ", profile " + config.performanceProfile().label() + ", item/tunnel/totem " + ArcaneCommands.state(config.itemEsp) + "/" + ArcaneCommands.state(config.tunnelEsp) + "/" + ArcaneCommands.state(config.autoTotem) + ", macros " + ArcaneCommands.state(config.chatMacros) + ", flagged " + engine.flaggedCount() + ", queued " + engine.queueSize());
        return 1;
    }

    private static int enabled(FabricClientCommandSource source, boolean enabled) {
        ArcaneClient.config().enabled = enabled;
        if (enabled) {
            ArcaneClient.engine().queueNearby(Minecraft.getInstance());
        }
        ArcaneClient.config().save();
        ArcaneCommands.feedback(source, "Scanner " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static int overlay(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.overlay = !config.overlay;
        config.save();
        ArcaneCommands.feedback(source, "Chunk outlines " + (config.overlay ? "enabled" : "disabled"));
        return 1;
    }

    private static int hud(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.hud = !config.hud;
        config.save();
        ArcaneCommands.feedback(source, "HUD " + (config.hud ? "enabled" : "disabled"));
        return 1;
    }

    private static int freecam(FabricClientCommandSource source) {
        FreecamController.toggle(Minecraft.getInstance());
        ArcaneCommands.feedback(source, "Freecam " + (FreecamController.isActive() ? "enabled" : "disabled"));
        return 1;
    }

    private static int esp(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.esp = !config.esp;
        config.save();
        ArcaneCommands.feedback(source, "Storage ESP " + (config.esp ? "enabled" : "disabled"));
        return 1;
    }

    private static int itemEsp(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.itemEsp = !config.itemEsp;
        config.save();
        ArcaneCommands.feedback(source, "Item ESP " + (config.itemEsp ? "enabled" : "disabled"));
        return 1;
    }

    private static int tunnelEsp(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.tunnelEsp = !config.tunnelEsp;
        ArcaneClient.engine().tunnelSettingsChanged(Minecraft.getInstance());
        config.save();
        ArcaneCommands.feedback(source, "Tunnel ESP " + (config.tunnelEsp ? "enabled" : "disabled"));
        return 1;
    }

    private static int autoTotem(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.autoTotem = !config.autoTotem;
        config.save();
        ArcaneCommands.feedback(source, "Auto Totem " + (config.autoTotem ? "enabled" : "disabled"));
        return 1;
    }

    private static int chatMacros(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.chatMacros = !config.chatMacros;
        config.save();
        ArcaneCommands.feedback(source, "Chat macros " + (config.chatMacros ? "enabled" : "disabled"));
        return 1;
    }

    private static int tracers(FabricClientCommandSource source) {
        boolean enabled;
        ArcaneConfig config = ArcaneClient.config();
        config.storageTracers = enabled = !config.storageTracers || !config.itemTracers;
        config.itemTracers = enabled;
        config.save();
        ArcaneCommands.feedback(source, "All ESP tracers " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static int analysis(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.chunkAnalysis = !config.chunkAnalysis;
        config.save();
        ArcaneCommands.feedback(source, "Current chunk analysis " + (config.chunkAnalysis ? "enabled" : "disabled"));
        return 1;
    }

    private static int profile(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.cyclePerformanceProfile();
        config.save();
        ArcaneClient.engine().settingsChanged(Minecraft.getInstance());
        ArcaneCommands.feedback(source, "Performance profile set to " + config.performanceProfile().label());
        return 1;
    }
    private static int settings() {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.gui.setScreen((Screen)new ArcaneSettingsScreen(null)));
        return 1;
    }

    private static int here(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return 0;
        }
        ChunkPos pos = client.player.chunkPosition();
        TraceEngine.ChunkMarker marker = ArcaneClient.engine().markerAt(pos.x(), pos.z());
        if (marker == null) {
            ArcaneCommands.feedback(source, "Chunk " + pos.x() + ", " + pos.z() + ": no evidence yet");
        } else {
            ArcaneCommands.feedback(source, ArcaneCommands.describe(marker));
        }
        return 1;
    }

    private static int waypointAdd(CommandContext<FabricClientCommandSource> context, String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return 0;
        WaypointStore.Waypoint waypoint = WaypointStore.add(client, name, client.player.blockPosition());
        ArcaneCommands.feedback(context.getSource(), "Waypoint saved: " + waypoint.name() + " at "
            + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z());
        return 1;
    }

    private static int waypointRemove(CommandContext<FabricClientCommandSource> context, String name) {
        boolean removed = WaypointStore.remove(Minecraft.getInstance(), name);
        ArcaneCommands.feedback(context.getSource(), removed ? "Waypoint removed: " + WaypointStore.cleanName(name) : "No waypoint named " + WaypointStore.cleanName(name));
        return removed ? 1 : 0;
    }

    private static int waypointList(FabricClientCommandSource source) {
        List<WaypointStore.Waypoint> waypoints = WaypointStore.current(Minecraft.getInstance());
        if (waypoints.isEmpty()) {
            ArcaneCommands.feedback(source, "No waypoints in this server and dimension");
            return 1;
        }
        for (WaypointStore.Waypoint waypoint : waypoints) {
            ArcaneCommands.feedback(source, waypoint.name() + ": " + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z());
        }
        return waypoints.size();
    }

    private static int list(FabricClientCommandSource source, int count) {
        List<TraceEngine.ChunkMarker> markers = ArcaneClient.engine().top(count);
        if (markers.isEmpty()) {
            ArcaneCommands.feedback(source, "No evidence recorded in this dimension yet");
            return 1;
        }
        for (TraceEngine.ChunkMarker marker : markers) {
            ArcaneCommands.feedback(source, ArcaneCommands.describe(marker));
        }
        return markers.size();
    }

    private static int threshold(FabricClientCommandSource source, int score) {
        ArcaneClient.config().setSensitivity(100 - score);
        ArcaneClient.config().save();
        ArcaneCommands.feedback(source, "Grown blocks required: " + ArcaneClient.config().grownBlocksRequired);
        return 1;
    }

    private static int radius(FabricClientCommandSource source, int chunks) {
        ArcaneClient.config().scanRadius = chunks;
        ArcaneClient.config().save();
        ArcaneClient.engine().queueNearby(Minecraft.getInstance());
        ArcaneCommands.feedback(source, "Scan radius set to " + chunks + " chunks");
        return 1;
    }

    private static int speed(FabricClientCommandSource source, int intensity) {
        ArcaneClient.config().chunksPerTick = intensity;
        ArcaneClient.config().save();
        ArcaneCommands.feedback(source, "Scan intensity set to " + intensity + "; full-accuracy coverage remains enabled");
        return 1;
    }

    private static int rescan(FabricClientCommandSource source) {
        ArcaneClient.engine().queueNearby(Minecraft.getInstance());
        ArcaneCommands.feedback(source, "Nearby loaded chunks queued for a rescan");
        return 1;
    }

    private static int clear(FabricClientCommandSource source) {
        ArcaneClient.engine().clearCurrent();
        ArcaneCommands.feedback(source, "Cleared recorded evidence for this server dimension");
        return 1;
    }

    private static String describe(TraceEngine.ChunkMarker marker) {
        String reason = marker.reasons().isEmpty() ? "no explanation" : String.join((CharSequence)", ", marker.reasons());
        return "[" + marker.score() + "] chunk " + marker.chunkX() + ", " + marker.chunkZ() + ": " + reason;
    }

    private static void feedback(FabricClientCommandSource source, String text) {
        source.sendFeedback((Component)Component.literal((String)text));
    }

    private static String state(boolean enabled) {
        return enabled ? "on" : "off";
    }
}
