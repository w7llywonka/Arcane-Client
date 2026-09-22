package dev.arcaneclient.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads native edit state; keyboard, clipboard and selection behavior remain vanilla. */
@Mixin(EditBox.class)
public interface ArcaneTextFieldAccessor {
    @Accessor("displayPos") int arcane$firstCharacterIndex();
    @Accessor("highlightPos") int arcane$selectionEnd();
}
