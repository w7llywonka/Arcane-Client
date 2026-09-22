package dev.arcaneclient.additions.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Reused model states submitted through vanilla's queue, including special item models and glint. */
public final class NametagEquipment {
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };
    private final ItemStackRenderState[] states = new ItemStackRenderState[SLOTS.length];

    public NametagEquipment() {
        for (int i = 0; i < states.length; i++) states[i] = new ItemStackRenderState();
    }

    public int render(LevelRenderContext context, LivingEntity entity, NametagConfig config) {
        Minecraft client = Minecraft.getInstance();
        int count = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            if (enabled(i, config) && !entity.getItemBySlot(SLOTS[i]).isEmpty()) count++;
        }
        if (count == 0) return 0;
        PoseStack matrices = context.poseStack();
        float x = -(count - 1) * 9.0f;
        int submitted = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = entity.getItemBySlot(SLOTS[i]);
            if (!enabled(i, config) || stack.isEmpty()) continue;
            client.getItemModelResolver().updateForLiving(states[i], stack, ItemDisplayContext.GUI, entity);
            if (states[i].isEmpty()) continue;
            matrices.pushPose();
            try {
                matrices.translate(x, -12, 0);
                // GUI item models use upward-positive Y, while label pixels use downward-positive Y.
                matrices.scale(16.0f, -16.0f, 16.0f);
                states[i].submit(matrices, context.submitNodeCollector(), 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
                submitted++;
            } finally {
                matrices.popPose();
            }
            x += 18;
        }
        return submitted;
    }

    private static boolean enabled(int slot, NametagConfig config) {
        return slot == 0 ? config.mainHand : slot == 5 ? config.offHand : config.armor;
    }
}
