package dev.arcaneclient.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.TraceEngine;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.render.EspRenderer;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import java.util.List;
import java.util.function.ToIntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

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
                .then(command("bedebug", ArcaneCommands::blockEntityDebug))
                .then(command("itemesp", ArcaneCommands::itemEsp))
                .then(command("tunnelesp", ArcaneCommands::tunnelEsp))
                .then(command("autototem", ArcaneCommands::autoTotem))
                .then(command("macros", ArcaneCommands::chatMacros))
                .then(command("tracers", ArcaneCommands::tracers))
                .then(command("stashalerts", ArcaneCommands::stashAlerts))
                .then(command("analysis", ArcaneCommands::analysis))
                .then(command("settings", ignored -> settings()))
                .then(command("profile", ArcaneCommands::profile))
                .then(command("here", ArcaneCommands::here))
                .then(command("list", source -> list(source, 8))
                    .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 20))
                        .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommandManager.literal("threshold")
                    .then(ClientCommandManager.argument("score", IntegerArgumentType.integer(0, 100))
                        .executes(context -> threshold(context.getSource(), IntegerArgumentType.getInteger(context, "score")))))
                .then(ClientCommandManager.literal("radius")
                    .then(ClientCommandManager.argument("chunks", IntegerArgumentType.integer(2, 24))
                        .executes(context -> radius(context.getSource(), IntegerArgumentType.getInteger(context, "chunks")))))
                .then(ClientCommandManager.literal("speed")
                    .then(ClientCommandManager.argument("intensity", IntegerArgumentType.integer(1, 16))
                        .executes(context -> speed(context.getSource(), IntegerArgumentType.getInteger(context, "intensity")))))
                .then(command("rescan", ArcaneCommands::rescan))
                .then(command("clear", ArcaneCommands::clear));

            LiteralCommandNode<FabricClientCommandSource> arcane = dispatcher.register(root);
            dispatcher.register(ClientCommandManager.literal("dtrace").redirect(arcane));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(
        String name,
        ToIntFunction<FabricClientCommandSource> action
    ) {
        return ClientCommandManager.literal(name)
            .executes(context -> action.applyAsInt(context.getSource()));
    }

    private static int status(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        TraceEngine engine = ArcaneClient.engine();
        ArcaneCommands.feedback(source, "Arcane Client " + (config.enabled ? "on" : "off") + ", sensitivity " + config.sensitivity() + "%, radius " + config.scanRadius + ", speed " + config.chunksPerTick + ", deep focus " + (config.deepFocus ? "on" : "off") + ", profile " + config.performanceProfile().label() + ", item/tunnel/totem " + ArcaneCommands.state(config.itemEsp) + "/" + ArcaneCommands.state(config.tunnelEsp) + "/" + ArcaneCommands.state(config.autoTotem) + ", macros " + ArcaneCommands.state(config.chatMacros) + ", flagged " + engine.flaggedCount() + ", queued " + engine.queueSize());
        return 1;
    }

    private static int enabled(FabricClientCommandSource source, boolean enabled) {
        ArcaneClient.config().enabled = enabled;
        if (enabled) {
            ArcaneClient.engine().queueNearby(MinecraftClient.getInstance());
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
        FreecamController.toggle(MinecraftClient.getInstance());
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

    private static int blockEntityDebug(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.blockEntityDebug = !config.blockEntityDebug;
        config.save();
        ArcaneCommands.feedback(source, "Block entity debug " + (config.blockEntityDebug ? "enabled" : "disabled") + "; cached visible targets: " + EspRenderer.targetCount());
        return 1;
    }

    private static int tunnelEsp(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.tunnelEsp = !config.tunnelEsp;
        ArcaneClient.engine().tunnelSettingsChanged(MinecraftClient.getInstance());
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

    private static int stashAlerts(FabricClientCommandSource source) {
        ArcaneConfig config = ArcaneClient.config();
        config.stashAlerts = !config.stashAlerts;
        config.save();
        ArcaneCommands.feedback(source, "Stash alerts " + (config.stashAlerts ? "enabled" : "disabled"));
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
        ArcaneClient.engine().settingsChanged(MinecraftClient.getInstance());
        ArcaneCommands.feedback(source, "Performance profile set to " + config.performanceProfile().label());
        return 1;
    }
    private static int settings() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen((Screen)new ArcaneSettingsScreen(null)));
        return 1;
    }

    private static int here(FabricClientCommandSource source) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        ChunkPos pos = client.player.getChunkPos();
        TraceEngine.ChunkMarker marker = ArcaneClient.engine().markerAt(pos.x, pos.z);
        if (marker == null) {
            ArcaneCommands.feedback(source, "Chunk " + pos.x + ", " + pos.z + ": no evidence yet");
        } else {
            ArcaneCommands.feedback(source, ArcaneCommands.describe(marker));
        }
        return 1;
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
        ArcaneClient.config().threshold = score;
        ArcaneClient.config().save();
        ArcaneCommands.feedback(source, "Suspicion threshold set to " + score);
        return 1;
    }

    private static int radius(FabricClientCommandSource source, int chunks) {
        ArcaneClient.config().scanRadius = chunks;
        ArcaneClient.config().save();
        ArcaneClient.engine().queueNearby(MinecraftClient.getInstance());
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
        ArcaneClient.engine().queueNearby(MinecraftClient.getInstance());
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
        source.sendFeedback((Text)Text.literal((String)text));
    }

    private static String state(boolean enabled) {
        return enabled ? "on" : "off";
    }
}
