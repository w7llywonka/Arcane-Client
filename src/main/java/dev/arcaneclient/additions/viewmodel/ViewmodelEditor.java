package dev.arcaneclient.additions.viewmodel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.freecam.FreecamController;
import dev.arcaneclient.freecam.FreelookController;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Renderer-only transforms with independent main/offhand profiles. */
@Environment(EnvType.CLIENT)
public final class ViewmodelEditor {
    private ViewmodelEditor() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        ViewmodelConfig c = config.viewmodel;
        // Only one hand's controls are shown at a time; switching does not copy
        // settings, and selecting/resetting a profile never toggles the module.
        boolean[] offhand = {false};
        Supplier<ViewmodelConfig.HandConfig> selected = () -> offhand[0] ? c.offHand : c.mainHand;
        return List.of(GuiModule.toggle("Viewmodel Editor",
                "Adjust each first-person hand's position, size, angles and visual swing. Attack timing is unchanged; two-handed maps use the main-hand pose.",
                () -> c.enabled, value -> c.enabled = value)
            .with(new GuiSetting.Cycle("Editing hand", () -> offhand[0] ? "Offhand" : "Main hand", () -> offhand[0] = !offhand[0]))
            .with(new GuiSetting.Slider("Offset X · right", () -> selected.get().offsetX, value -> selected.get().offsetX = value, -100, 100, " cm"))
            .with(new GuiSetting.Slider("Offset Y · up", () -> selected.get().offsetY, value -> selected.get().offsetY = value, -100, 100, " cm"))
            .with(new GuiSetting.Slider("Offset Z · near", () -> selected.get().offsetZ, value -> selected.get().offsetZ = value, -100, 100, " cm"))
            .with(new GuiSetting.Slider("Scale", () -> selected.get().scale, value -> selected.get().scale = value, 25, 200, "%"))
            .with(new GuiSetting.Slider("Rotate X", () -> selected.get().rotateX, value -> selected.get().rotateX = value, -180, 180, "°"))
            .with(new GuiSetting.Slider("Rotate Y", () -> selected.get().rotateY, value -> selected.get().rotateY = value, -180, 180, "°"))
            .with(new GuiSetting.Slider("Rotate Z", () -> selected.get().rotateZ, value -> selected.get().rotateZ = value, -180, 180, "°"))
            .with(new GuiSetting.Slider("Swing amount", () -> selected.get().swingAmount, value -> selected.get().swingAmount = value, 0, 200, "%"))
            .with(new GuiSetting.Toggle("Hide this hand", () -> selected.get().hidden, value -> selected.get().hidden = value))
            .with(new GuiSetting.Cycle("Reset this hand", () -> "Defaults", () -> selected.get().reset()))
            .with(new GuiSetting.Info("Swing", () -> "0% still · 100% vanilla"))
            .build());
    }

    private static boolean active(Minecraft client) {
        return ArcaneClient.config() != null && ArcaneClient.config().viewmodel.enabled
            && client.player != null && client.level != null && client.options.getCameraType().isFirstPerson()
            && !FreecamController.isActive() && !FreelookController.isActive() && !ArcaneVisibility.overlaysHidden();
    }

    private static ViewmodelConfig.HandConfig profile(InteractionHand hand) {
        ViewmodelConfig c = ArcaneClient.config().viewmodel;
        return hand == InteractionHand.MAIN_HAND ? c.mainHand : c.offHand;
    }

    private static InteractionHand handFor(HumanoidArm arm, Minecraft client) {
        return arm == client.player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static boolean hidden(InteractionHand hand) {
        Minecraft client = Minecraft.getInstance();
        return active(client) && profile(hand).hidden;
    }

    public static boolean hidden(HumanoidArm arm) {
        Minecraft client = Minecraft.getInstance();
        return active(client) && profile(handFor(arm, client)).hidden;
    }

    /** Uses the vanilla per-hand push/pop; this method must not add its own frame. */
    public static void transform(PoseStack matrices, InteractionHand hand) {
        Minecraft client = Minecraft.getInstance();
        if (!active(client)) return;
        ViewmodelConfig.HandConfig c = profile(hand);
        matrices.translate(Math.clamp(c.offsetX, -100, 100) / 100.0f,
            Math.clamp(c.offsetY, -100, 100) / 100.0f, Math.clamp(c.offsetZ, -100, 100) / 100.0f);
        if (c.rotateX != 0) matrices.rotate(Axis.XP.rotationDegrees(Math.clamp(c.rotateX, -180, 180)));
        if (c.rotateY != 0) matrices.rotate(Axis.YP.rotationDegrees(Math.clamp(c.rotateY, -180, 180)));
        if (c.rotateZ != 0) matrices.rotate(Axis.ZP.rotationDegrees(Math.clamp(c.rotateZ, -180, 180)));
        float scale = Math.clamp(c.scale, 25, 200) / 100.0f;
        if (scale != 1.0f) matrices.scale(scale, scale, scale);
    }

    public static float swingScale(HumanoidArm arm) {
        Minecraft client = Minecraft.getInstance();
        return active(client) ? Math.clamp(profile(handFor(arm, client)).swingAmount, 0, 200) / 100.0f : 1.0f;
    }

    public static float mainHandSwingScale() {
        Minecraft client = Minecraft.getInstance();
        return active(client) ? Math.clamp(profile(InteractionHand.MAIN_HAND).swingAmount, 0, 200) / 100.0f : 1.0f;
    }
}
