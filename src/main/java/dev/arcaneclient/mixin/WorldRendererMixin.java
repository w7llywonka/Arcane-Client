package dev.arcaneclient.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.arcaneclient.ArcaneClient;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$hideWeather(WeatherRenderState state, RenderPass renderPass, CallbackInfo ci) {
        if (ArcaneClient.config() != null && ArcaneClient.config().noRender) ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$hideOitWeather(OitStage stage, WeatherRenderState state, RenderPass renderPass, CallbackInfo ci) {
        if (ArcaneClient.config() != null && ArcaneClient.config().noRender) ci.cancel();
    }
}
