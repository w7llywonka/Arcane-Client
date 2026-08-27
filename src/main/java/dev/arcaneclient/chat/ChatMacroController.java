package dev.arcaneclient.chat;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.chat.ChatMacroMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;

@Environment(value=EnvType.CLIENT)
public final class ChatMacroController {
    private ChatMacroController() {
    }

    public static void tick(MinecraftClient client) {
        boolean canSend = ArcaneClient.config().chatMacros && client.player != null;
        ClientPlayNetworkHandler connection = client.getNetworkHandler();
        boolean sent = false;
        for (int index = 0; index < ArcaneClient.keybinds().chatMacros().size(); ++index) {
            KeyBinding mapping = ArcaneClient.keybinds().chatMacros().get(index);
            while (mapping.wasPressed()) {
                ChatMacroMessage.Outbound outbound;
                if (!canSend || connection == null || sent || (outbound = ChatMacroMessage.parse(ArcaneClient.config().chatMacro(index))) == null) continue;
                if (outbound.command()) {
                    connection.sendChatCommand(outbound.payload());
                } else {
                    connection.sendChatMessage(outbound.payload());
                }
                sent = true;
            }
        }
    }
}
