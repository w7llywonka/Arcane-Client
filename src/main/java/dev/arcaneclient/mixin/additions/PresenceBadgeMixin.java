package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.presence.ArcanePresence;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PresenceBadgeMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void arcane$presenceBadge(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(ArcanePresence.tabName(entry, cir.getReturnValue()));
    }
}
