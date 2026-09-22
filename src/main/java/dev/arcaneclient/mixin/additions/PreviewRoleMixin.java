package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.preview.PreviewAdditions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class PreviewRoleMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void arcaneclient$selfRolePreview(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        Component preview = PreviewAdditions.tabName(entry, original);
        if (preview != original) cir.setReturnValue(preview);
    }
}
