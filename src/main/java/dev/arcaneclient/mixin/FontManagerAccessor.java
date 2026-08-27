package dev.arcaneclient.mixin;

import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Invoker("getStorageInternal")
    FontStorage arcaneclient$getStorage(Identifier id);
}
