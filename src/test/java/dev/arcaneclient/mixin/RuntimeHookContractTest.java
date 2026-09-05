package dev.arcaneclient.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import io.netty.channel.ChannelFutureListener;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

final class RuntimeHookContractTest {
    @Test
    void exactMinecraftMethodsRequiredByFeatureMixinsExist() throws Exception {
        method(GameRenderer.class, "getFov", Camera.class, float.class, boolean.class);
        method(GameRenderer.class, "tiltViewWhenHurt", MatrixStack.class, float.class);
        method(GameRenderer.class, "getNightVisionStrength", LivingEntity.class, float.class);
        method(LightmapTextureManager.class, "update", float.class);
        method(LivingEntity.class, "getHandSwingDuration");
        method(Entity.class, "changeLookDirection", double.class, double.class);
        method(Entity.class, "resetPosition");
        method(Entity.class, "updateLastAngles");
        method(MinecraftClient.class, "doAttack");
        method(MinecraftClient.class, "handleBlockBreaking", boolean.class);
        method(MinecraftClient.class, "doItemUse");
        method(ClientCommonNetworkHandler.class, "sendPacket", Packet.class);
        method(ClientPlayNetworkHandler.class, "sendChatCommand", String.class);
        method(ClientPlayerInteractionManager.class, "attackBlock", BlockPos.class, Direction.class);
        method(ClientPlayerInteractionManager.class, "updateBlockBreakingProgress", BlockPos.class, Direction.class);
        method(ClientPlayerInteractionManager.class, "attackEntity", PlayerEntity.class, Entity.class);
        method(ClientPlayerInteractionManager.class, "interactItem", PlayerEntity.class, Hand.class);
        method(ClientConnection.class, "send", Packet.class, ChannelFutureListener.class, boolean.class);
        method(Mouse.class, "onMouseScroll", long.class, double.class, double.class);
        method(Mouse.class, "onMouseButton", long.class, MouseInput.class, int.class);
        method(Keyboard.class, "onKey", long.class, int.class, KeyInput.class);
        method(InGameHud.class, "renderCrosshair", DrawContext.class, RenderTickCounter.class);
        method(InGameOverlayRenderer.class, "renderFireOverlay", MatrixStack.class, VertexConsumerProvider.class, Sprite.class);
        method(InGameOverlayRenderer.class, "renderUnderwaterOverlay", MinecraftClient.class, MatrixStack.class, VertexConsumerProvider.class);
        method(InGameOverlayRenderer.class, "renderInWallOverlay", Sprite.class, MatrixStack.class, VertexConsumerProvider.class);
        method(WorldRenderer.class, "renderWeather", FrameGraphBuilder.class, GpuBufferSlice.class);
        method(Frustum.class, "isVisible", Box.class);
        method(Frustum.class, "intersectAab", BlockBox.class);
    }

    @Test
    void everyNewRuntimeMixinIsDeclared() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            RuntimeHookContractTest.class.getResourceAsStream("/arcaneclient.client.mixins.json"),
            StandardCharsets.UTF_8
        )) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray client = root.getAsJsonArray("client");
            Set<String> names = new HashSet<>();
            client.forEach(element -> names.add(element.getAsString()));
            assertTrue(names.contains("CameraMixin"));
            assertTrue(names.contains("ClientCommonNetworkHandlerMixin"));
            assertTrue(names.contains("ClientConnectionMixin"));
            assertTrue(names.contains("ClientPlayerInteractionManagerMixin"));
            assertTrue(names.contains("EntityMixin"));
            assertTrue(names.contains("FrustumMixin"));
            assertTrue(names.contains("GameRendererMixin"));
            assertTrue(names.contains("LivingEntityMixin"));
            assertTrue(names.contains("LightmapTextureManagerMixin"));
            assertTrue(names.contains("MouseMixin"));
            assertTrue(names.contains("MinecraftClientMixin"));
            assertTrue(names.contains("KeyboardInputMixin"));
            assertTrue(names.contains("KeyboardMixin"));
            assertTrue(names.contains("KeyBindingAccessor"));
            assertTrue(names.contains("InGameHudMixin"));
            assertTrue(names.contains("InGameOverlayRendererMixin"));
            assertTrue(names.contains("WorldRendererMixin"));
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        assertTrue(method.trySetAccessible());
        return method;
    }
}
