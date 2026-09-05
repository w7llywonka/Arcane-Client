package dev.arcaneclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.arcaneclient.ArcaneClient;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$hideWeather(FrameGraphBuilder frameGraph, GpuBufferSlice bufferSlice, CallbackInfo ci) {
        if (ArcaneClient.config() != null && ArcaneClient.config().noRender) ci.cancel();
    }
}
