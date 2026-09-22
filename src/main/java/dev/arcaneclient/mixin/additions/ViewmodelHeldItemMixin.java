package dev.arcaneclient.mixin.additions;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arcaneclient.additions.viewmodel.ViewmodelEditor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ViewmodelHeldItemMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$hideViewmodel(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                           float tickProgress, float pitch, InteractionHand hand, float swingProgress,
                                           ItemStack item, float equipProgress, PoseStack matrices,
                                           SubmitNodeCollector queue, int light, CallbackInfo ci) {
        // Cancellation happens before vanilla's push, including the spyglass path.
        if (ViewmodelEditor.hidden(hand)) ci.cancel();
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void arcaneclient$transformViewmodel(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                                float tickProgress, float pitch, InteractionHand hand, float swingProgress,
                                                ItemStack item, float equipProgress, PoseStack matrices,
                                                SubmitNodeCollector queue, int light, CallbackInfo ci) {
        // Exactly one transformation in the vanilla hand scope. Vanilla's pop
        // restores the caller before the other hand, map helpers or later passes.
        ViewmodelEditor.transform(matrices, hand);
    }

    @Inject(method = "renderMapHand", at = @At("HEAD"), cancellable = true)
    private void arcaneclient$hideMapArm(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidArm arm,
                                        PlayerRenderState playerState, CallbackInfo ci) {
        // The two-handed map renders both arms inside its main-hand pass.
        if (ViewmodelEditor.hidden(arm)) ci.cancel();
    }

    // Scale the output of the renderer's swing curves, never its progress/time.
    // Each curve feeds independent displacement or rotation terms, so the idle
    // pose and full swing duration remain unchanged even at zero amplitude.
    @Redirect(method = "swingArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float arcaneclient$itemSwingAmount(double angle, float progress, PoseStack matrices, int direction, HumanoidArm arm) {
        return Mth.sin(angle) * ViewmodelEditor.swingScale(arm);
    }

    @Redirect(method = "applyItemArmAttackTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float arcaneclient$itemSwingRotation(double angle, PoseStack matrices, HumanoidArm arm, float progress) {
        return Mth.sin(angle) * ViewmodelEditor.swingScale(arm);
    }

    @Redirect(method = "renderPlayerArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float arcaneclient$emptyHandSwing(double angle, PoseStack matrices, SubmitNodeCollector queue, int light,
                                             float equipProgress, float swingProgress, HumanoidArm arm,
                                             PlayerRenderState playerState) {
        return Mth.sin(angle) * ViewmodelEditor.swingScale(arm);
    }

    @Redirect(method = "renderOneHandedMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float arcaneclient$singleMapSwing(double angle, PoseStack matrices, SubmitNodeCollector queue, int light,
                                             float equipProgress, HumanoidArm arm, float swingProgress, ItemStack item,
                                             PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
        return Mth.sin(angle) * ViewmodelEditor.swingScale(arm);
    }

    @Redirect(method = "renderTwoHandedMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float arcaneclient$doubleMapSwing(double angle, PoseStack matrices, SubmitNodeCollector queue, int light,
                                             float pitch, float equipProgress, float swingProgress,
                                             PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
        return Mth.sin(angle) * ViewmodelEditor.mainHandSwingScale();
    }
}
