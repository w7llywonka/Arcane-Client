package dev.arcaneclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("fontManager")
    FontManager arcaneclient$getFontManager();

    @Accessor("rightClickDelay")
    int arcaneclient$getItemUseCooldown();

    @Accessor("rightClickDelay")
    void arcaneclient$setItemUseCooldown(int cooldown);

    @Invoker("startUseItem")
    void arcaneclient$doItemUse();
}
