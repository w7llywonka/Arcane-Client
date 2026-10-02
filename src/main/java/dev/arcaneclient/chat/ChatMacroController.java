package dev.arcaneclient.chat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(value=EnvType.CLIENT)
public final class ChatMacroController {
    private static final AutomaticChatMacroSchedule AUTOMATIC = new AutomaticChatMacroSchedule();

    private ChatMacroController() {
    }

    public static void tick(Minecraft client) {
        ArcaneConfig config = ArcaneClient.config();
        boolean canSend = config.chatMacros && client.player != null && client.level != null;
        ClientPacketListener connection = client.getConnection();
        boolean sent = false;
        for (int index = 0; index < ArcaneClient.keybinds().chatMacros().size(); ++index) {
            KeyMapping mapping = ArcaneClient.keybinds().chatMacros().get(index);
            while (mapping.consumeClick()) {
                ChatMacroMessage.Outbound outbound;
                if (!canSend || connection == null || sent || (outbound = ChatMacroMessage.parse(config.chatMacro(index))) == null) continue;
                send(connection, outbound);
                sent = true;
            }
        }
        int slot = config.automaticChatMacroSlot;
        ChatMacroMessage.Outbound selected = slot >= 0 && slot < AutomaticChatMacroSchedule.MACRO_COUNT
            ? ChatMacroMessage.parse(config.chatMacro(slot)) : null;
        ChatMacroMessage.Outbound automatic = AUTOMATIC.poll(
            canSend && config.automaticChatMacros && !client.isPaused(), connection,
            slot, config.automaticChatMacroIntervalSeconds, selected, System.nanoTime());
        // Preserve the existing one-message-per-tick limit even when a manual key is pressed.
        if (automatic != null && !sent) send(connection, automatic);
    }

    private static void send(ClientPacketListener connection, ChatMacroMessage.Outbound outbound) {
        if (outbound.command()) {
            connection.sendCommand(outbound.payload());
        } else {
            connection.sendChat(outbound.payload());
        }
    }
}
