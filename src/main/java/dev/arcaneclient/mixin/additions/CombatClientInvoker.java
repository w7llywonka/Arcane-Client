package dev.arcaneclient.mixin.additions;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface CombatClientInvoker {
    @Invoker("startAttack")
    boolean arcaneclient$invokeCombatAttack();
}
