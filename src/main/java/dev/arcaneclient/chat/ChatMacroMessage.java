package dev.arcaneclient.chat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class ChatMacroMessage {
    public static final int MAX_LENGTH = 256;

    private ChatMacroMessage() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').strip();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    public static Outbound parse(String value) {
        String normalized = ChatMacroMessage.normalize(value);
        if (normalized.isEmpty() || normalized.equals("/")) {
            return null;
        }
        return normalized.charAt(0) == '/' ? new Outbound(true, normalized.substring(1)) : new Outbound(false, normalized);
    }

    @Environment(value=EnvType.CLIENT)
    public record Outbound(boolean command, String payload) {
    }
}
