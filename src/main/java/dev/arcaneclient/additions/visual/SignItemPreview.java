package dev.arcaneclient.additions.visual;

import dev.arcaneclient.ArcaneClient;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;

/** Compact preview for the front/back text components carried by 26.3 sign items. */
public final class SignItemPreview {
    private static boolean registered;

    private SignItemPreview() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (ArcaneClient.config() == null || !ArcaneClient.config().visualAdditions.signItemPreview) return;
            add(lines, "Front", stack.get(DataComponents.SIGN_TEXT_FRONT));
            add(lines, "Back", stack.get(DataComponents.SIGN_TEXT_BACK));
        });
    }

    private static void add(List<Component> lines, String side, SignText text) {
        if (text == null || !text.hasMessage(false)) return;
        String joined = text.getMessages(false).stream().map(Component::getString)
            .filter(value -> !value.isBlank()).limit(4).reduce((a, b) -> a + " / " + b).orElse("");
        if (joined.isEmpty()) return;
        if (joined.length() > 96) joined = joined.substring(0, 93) + "...";
        lines.add(Component.literal(side + ": " + joined).withStyle(ChatFormatting.GRAY));
    }
}
