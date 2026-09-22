package dev.arcaneclient.mixin.additions;

import dev.arcaneclient.additions.visual.CustomGlint;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(RenderSetup.class)
public abstract class CustomGlintMixin {
    @Redirect(method = "prepareTextures", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"))
    private AbstractTexture arcaneclient$tintedGlint(TextureManager manager, Identifier id) {
        return CustomGlint.texture(id, manager.getTexture(id));
    }
}
