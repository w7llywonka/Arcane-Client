package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.utility.UtilityAdditions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class SpawnerBreakProgressMixin {
    @Inject(method = "destroyBlockProgress", at = @At("TAIL"))
    private void arcaneclient$spawnerBreakAlert(int entityId, BlockPos pos, int progress, CallbackInfo ci) {
        UtilityAdditions.onBlockBreaking(entityId, pos, progress);
    }
}
