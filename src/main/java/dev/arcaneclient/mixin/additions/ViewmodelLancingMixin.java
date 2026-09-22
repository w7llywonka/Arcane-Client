package dev.arcaneclient.mixin.additions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arcaneclient.additions.viewmodel.ViewmodelEditor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** First-person spear swing helper only; third-person and held-use animations are untouched. */
@Environment(EnvType.CLIENT)
@Mixin(SpearAnimations.class)
public abstract class ViewmodelLancingMixin {
    @Redirect(method = "firstPersonAttack", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private static void arcaneclient$spearSwingTranslation(PoseStack matrices, float x, float y, float z,
                                                           float progress, PoseStack original, int direction, HumanoidArm arm) {
        float amount = ViewmodelEditor.swingScale(arm);
        matrices.translate(x * amount, y * amount, z * amount);
    }

    @Redirect(method = "firstPersonAttack", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void arcaneclient$spearSwingReturn(PoseStack matrices, double x, double y, double z,
                                                      float progress, PoseStack original, int direction, HumanoidArm arm) {
        float amount = ViewmodelEditor.swingScale(arm);
        matrices.translate(x * amount, y * amount, z * amount);
    }

    @Redirect(method = "firstPersonAttack", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
    private static void arcaneclient$spearSwingRotation(PoseStack matrices, Axis axis, float angle,
                                                        float progress, PoseStack original, int direction, HumanoidArm arm) {
        matrices.rotateDegrees(axis, angle * ViewmodelEditor.swingScale(arm));
    }
}
