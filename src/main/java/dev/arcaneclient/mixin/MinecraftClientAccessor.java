package dev.arcaneclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("fontManager")
    FontManager arcaneclient$getFontManager();

    @Accessor("itemUseCooldown")
    int arcaneclient$getItemUseCooldown();

    @Invoker("doItemUse")
    void arcaneclient$doItemUse();
}
