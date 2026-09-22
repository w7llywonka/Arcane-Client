package dev.arcaneclient.mixin.additions;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to Minecraft's real local mining target and progress. */
@Mixin(MultiPlayerGameMode.class)
public interface MiningInteractionManagerAccessor {
    @Accessor("destroyProgress")
    float arcaneclient$miningProgress();

    @Accessor("destroyBlockPos")
    BlockPos arcaneclient$miningPos();
}
