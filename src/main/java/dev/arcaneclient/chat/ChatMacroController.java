package dev.arcaneclient.chat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.chat.ChatMacroMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(value=EnvType.CLIENT)
public final class ChatMacroController {
    private ChatMacroController() {
    }

    public static void tick(Minecraft client) {
        boolean canSend = ArcaneClient.config().chatMacros && client.player != null;
        ClientPacketListener connection = client.getConnection();
        boolean sent = false;
        for (int index = 0; index < ArcaneClient.keybinds().chatMacros().size(); ++index) {
            KeyMapping mapping = ArcaneClient.keybinds().chatMacros().get(index);
            while (mapping.consumeClick()) {
                ChatMacroMessage.Outbound outbound;
                if (!canSend || connection == null || sent || (outbound = ChatMacroMessage.parse(ArcaneClient.config().chatMacro(index))) == null) continue;
                if (outbound.command()) {
                    connection.sendCommand(outbound.payload());
                } else {
                    connection.sendChat(outbound.payload());
                }
                sent = true;
            }
        }
    }
}
