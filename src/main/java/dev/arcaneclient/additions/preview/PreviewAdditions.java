package dev.arcaneclient.additions.preview;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.ArcaneFont;
import dev.arcaneclient.screen.ArcaneSettingsScreen;
import dev.arcaneclient.screen.ClickGuiColors;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import dev.arcaneclient.screen.RoundedGui;
import dev.arcaneclient.screen.TextSettingsScreen;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Three local presentation previews. No outgoing chat, commands, packets or scoreboard writes. */
@Environment(EnvType.CLIENT)
public final class PreviewAdditions {
    private static final String LABEL = "[LOCAL PREVIEW]";
    private static String payStatus = "Nothing previewed";
    private static boolean registered;

    private PreviewAdditions() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        PreviewConfig c = config.preview;
        return List.of(
            GuiModule.toggle("Fake Pay", "A labeled local payment preview. Nothing is paid or sent to a server; use /arcanepreview pay <player> <amount>.",
                    () -> c.fakePay, value -> c.fakePay = value)
                .with(new GuiSetting.Cycle("Payment text", () -> "Configure", () -> paymentForm(client, config)))
                .with(new GuiSetting.Cycle("Local preview", () -> "Show in chat", () -> showPay(client, c.payRecipient, c.payAmount)))
                .with(new GuiSetting.Info("Result", () -> payStatus))
                .build(),
            GuiModule.toggle("Fake Roles", "Adds a labeled local role preview to your own tab-list name. Other players and server roles are unchanged.",
                    () -> c.fakeRoles, value -> c.fakeRoles = value)
                .with(new GuiSetting.Cycle("Role text", () -> "Configure", () -> roleForm(client, config)))
                .with(new GuiSetting.Swatch("Role color", () -> c.roleColor, value -> c.roleColor = value))
                .with(new GuiSetting.Info("Visible to", () -> "Only you · marked preview"))
                .build(),
            GuiModule.toggle("Fake Stats", "Shows a separate labeled local sidebar preview. Does not replace or modify the server's real statistics.",
                    () -> c.fakeStats, value -> c.fakeStats = value)
                .with(new GuiSetting.Cycle("Labels and values", () -> "Configure", () -> statsForm(client, config)))
                .with(new GuiSetting.Toggle("Left side", () -> c.statsOnLeft, value -> c.statsOnLeft = value))
                .with(new GuiSetting.Slider("Horizontal margin", () -> c.statsMarginX, value -> c.statsMarginX = value, 0, 400, " px"))
                .with(new GuiSetting.Slider("Top margin", () -> c.statsMarginY, value -> c.statsMarginY = value, 0, 400, " px"))
                .build()
        );
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            ClientCommands.literal("arcanepreview")
                .executes(context -> info(Minecraft.getInstance(), "Use /arcanepreview pay <player> <amount>. Nothing is sent to a server."))
                .then(ClientCommands.literal("pay")
                    .executes(context -> {
                        PreviewConfig c = ArcaneClient.config().preview;
                        return showPay(Minecraft.getInstance(), c.payRecipient, c.payAmount);
                    })
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                        .then(ClientCommands.argument("amount", StringArgumentType.word())
                            .executes(context -> showPay(Minecraft.getInstance(),
                                StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "amount"))))))));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ArcaneClient.id("local_stats_preview"),
            (graphics, tickCounter) -> renderStats(graphics));
    }

    public static void tick(Minecraft client) {
        // Presentation is derived directly from config; no server state is cached or changed.
    }

    public static void reset(Minecraft client) {
        payStatus = "Nothing previewed";
    }

    private static int showPay(Minecraft client, String recipient, String rawAmount) {
        if (client.player == null || client.level == null) {
            payStatus = "Join a world first";
            return 0;
        }
        PreviewConfig c = ArcaneClient.config().preview;
        if (!c.fakePay) return info(client, "Enable Fake Pay to show a local preview.");
        String name = PreviewConfig.player(recipient);
        if (name.isBlank() || !name.equals(recipient)) {
            payStatus = "Enter a valid username";
            return info(client, "Choose a 1–16 character Minecraft username in Payment text or the command.");
        }
        String amount = PreviewConfig.clean(rawAmount, 18);
        if (!amount.matches("[0-9]{1,12}(?:\\.[0-9]{1,2})?")) {
            payStatus = "Use a positive amount";
            return info(client, "Amount must be a positive number with up to two decimal places.");
        }
        BigDecimal value = new BigDecimal(amount);
        if (value.signum() <= 0) {
            payStatus = "Use a positive amount";
            return info(client, "Amount must be greater than zero.");
        }
        String currency = PreviewConfig.clean(c.payCurrency, 8);
        String formatted = value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        payStatus = "Added to your local chat";
        return info(client, "Simulated payment to " + name + ": " + currency + formatted + ". No transaction occurred.");
    }

    private static int info(Minecraft client, String body) {
        if (client.gui == null) return 0;
        ClickGuiColors colors = ClickGuiColors.resolve(ArcaneClient.config());
        var message = ArcaneFont.text(LABEL + " ").copy().withStyle(style -> style.withColor(colors.accentBright()));
        message.append(ArcaneFont.text(body).copy().withStyle(style -> style.withColor(colors.text())));
            client.gui.hud.getChat().addClientSystemMessage(message);
        return 1;
    }

    /** Called only by the tab-list rendering hook; returns a new Text without touching its entry. */
    public static Component tabName(PlayerInfo entry, Component original) {
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || client.player == null || !config.preview.fakeRoles || config.streamerMode
            || ArcaneVisibility.overlaysHidden() || !entry.getProfile().id().equals(client.player.getUUID())) return original;
        String role = PreviewConfig.clean(config.preview.role, 24);
        if (role.isBlank()) return original;
        var preview = ArcaneFont.text(LABEL + " ").copy().withStyle(style -> style.withColor(0xFF79CDD3));
        preview.append(ArcaneFont.text("[" + role + "] ").copy().withStyle(style -> style.withColor(config.preview.roleColor & 0xFFFFFF)));
        preview.append(original.copy());
        return preview;
    }

    private static void paymentForm(Minecraft client, ArcaneConfig config) {
        PreviewConfig c = config.preview;
        client.gui.setScreen(new TextSettingsScreen(client.gui.screen(), config, "Fake Pay · Local preview",
            "Local chat mockup only. No money moves.", List.of(
                new TextSettingsScreen.Field("Recipient", "Minecraft username shown in the preview.", () -> c.payRecipient, value -> c.payRecipient = PreviewConfig.player(value), 16),
                new TextSettingsScreen.Field("Amount", "Positive number, at most two decimal places.", () -> c.payAmount, value -> c.payAmount = PreviewConfig.clean(value, 18), 18),
                new TextSettingsScreen.Field("Currency label", "Visual text only; not an account or currency integration.", () -> c.payCurrency, value -> c.payCurrency = PreviewConfig.clean(value, 8), 8)
            )));
    }

    private static void roleForm(Minecraft client, ArcaneConfig config) {
        PreviewConfig c = config.preview;
        client.gui.setScreen(new TextSettingsScreen(client.gui.screen(), config, "Fake Roles · Local preview",
            "Only your tab-list name gains a marked preview.", List.of(
                new TextSettingsScreen.Field("Role label", "The fixed LOCAL PREVIEW marker always remains visible.", () -> c.role, value -> c.role = PreviewConfig.clean(value, 24), 24)
            )));
    }

    private static void statsForm(Minecraft client, ArcaneConfig config) {
        PreviewConfig c = config.preview;
        client.gui.setScreen(new TextSettingsScreen(client.gui.screen(), config, "Fake Stats · Local preview",
            "Separate preview panel; real server statistics stay intact.", List.of(
                new TextSettingsScreen.Field("Title", "Displayed below the fixed LOCAL PREVIEW marker.", () -> c.statsTitle, value -> c.statsTitle = PreviewConfig.clean(value, 32), 32),
                new TextSettingsScreen.Field("First label", "Leave a label empty to hide its row.", () -> c.firstLabel, value -> c.firstLabel = PreviewConfig.clean(value, 24), 24),
                new TextSettingsScreen.Field("First value", "Text shown only in this preview.", () -> c.firstValue, value -> c.firstValue = PreviewConfig.clean(value, 24), 24),
                new TextSettingsScreen.Field("Second label", "Leave a label empty to hide its row.", () -> c.secondLabel, value -> c.secondLabel = PreviewConfig.clean(value, 24), 24),
                new TextSettingsScreen.Field("Second value", "Text shown only in this preview.", () -> c.secondValue, value -> c.secondValue = PreviewConfig.clean(value, 24), 24),
                new TextSettingsScreen.Field("Third label", "Leave a label empty to hide its row.", () -> c.thirdLabel, value -> c.thirdLabel = PreviewConfig.clean(value, 24), 24),
                new TextSettingsScreen.Field("Third value", "Text shown only in this preview.", () -> c.thirdValue, value -> c.thirdValue = PreviewConfig.clean(value, 24), 24)
            )));
    }

    private static void renderStats(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        ArcaneConfig config = ArcaneClient.config();
        if (config == null || !config.preview.fakeStats || config.streamerMode || ArcaneVisibility.overlaysHidden()
            || client.level == null || client.player == null || client.gui.hud.isHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        PreviewConfig c = config.preview;
        Font font = ArcaneFont.renderer(client);
        ClickGuiColors colors = ClickGuiColors.resolve(config);
        String[] labels = {c.firstLabel, c.secondLabel, c.thirdLabel};
        String[] values = {c.firstValue, c.secondValue, c.thirdValue};
        int count = 0;
        for (String label : labels) if (!label.isBlank()) count++;
        int width = Math.min(214, graphics.guiWidth() - 16);
        int height = 44 + count * 15;
        if (width < 150 || graphics.guiHeight() < height + 16) return;
        int x = Math.clamp(c.statsOnLeft ? c.statsMarginX : graphics.guiWidth() - width - c.statsMarginX,
            8, graphics.guiWidth() - width - 8);
        int y = Math.clamp(c.statsMarginY, 8, graphics.guiHeight() - height - 8);
        RoundedGui.fill(graphics, x + 1, y + 2, width, height, 7, 0x40000000);
        RoundedGui.outline(graphics, x, y, width, height, 7, 1, colors.outline(), colors.window());
        RoundedGui.fill(graphics, x + 9, y + 8, 3, 9, 1, colors.accent());
        graphics.text(font, ArcaneFont.text(LABEL), x + 18, y + 8, colors.accentBright(), false);
        graphics.text(font, ArcaneFont.trimmed(font, c.statsTitle, width - 20), x + 10, y + 22, colors.text(), false);
        int row = y + 39;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].isBlank()) continue;
            int valueWidth = Math.min(width / 2 - 12, ArcaneFont.width(font, values[i]));
            graphics.text(font, ArcaneFont.trimmed(font, labels[i], width / 2 - 14), x + 10, row, colors.muted(), false);
            graphics.text(font, ArcaneFont.trimmed(font, values[i], width / 2 - 12), x + width - valueWidth - 10, row, colors.text(), false);
            row += 15;
        }
    }
}
