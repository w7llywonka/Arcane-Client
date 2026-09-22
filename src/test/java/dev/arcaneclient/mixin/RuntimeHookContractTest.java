package dev.arcaneclient.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RuntimeHookContractTest {
    @Test
    void exactMinecraftMethodsRequiredByFeatureMixinsExist() throws Exception {
        method("net.minecraft.client.Camera", "getFov");
        method("net.minecraft.client.Camera", "calculateFov");
        method("net.minecraft.client.renderer.GameRenderer", "bobHurt");
        method("net.minecraft.client.renderer.GameRenderer", "nightVisionScale");
        method("net.minecraft.client.renderer.GameRenderer", "render");
        method("net.minecraft.client.renderer.GameRenderer", "extract");
        method("net.minecraft.client.gui.render.GuiRenderer", "endFrame");
        method("net.minecraft.client.renderer.LightmapRenderStateExtractor", "extract");
        method("net.minecraft.world.entity.LivingEntity", "getModifiedSwingDuration");
        method("net.minecraft.world.entity.Entity", "turn");
        method("net.minecraft.client.Minecraft", "startAttack");
        method("net.minecraft.client.Minecraft", "continueAttack");
        method("net.minecraft.client.Minecraft", "startUseItem");
        method("net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl", "send");
        method("net.minecraft.client.multiplayer.MultiPlayerGameMode", "startDestroyBlock");
        method("net.minecraft.client.multiplayer.MultiPlayerGameMode", "continueDestroyBlock");
        method("net.minecraft.client.multiplayer.MultiPlayerGameMode", "attack");
        method("net.minecraft.client.multiplayer.MultiPlayerGameMode", "useItem");
        method("net.minecraft.network.Connection", "send");
        method("net.minecraft.client.MouseHandler", "onScroll");
        method("net.minecraft.client.MouseHandler", "onButton");
        method("net.minecraft.client.KeyboardHandler", "keyPress");
        method("net.minecraft.client.gui.Hud", "extractCrosshair");
        method("net.minecraft.client.renderer.ScreenEffectRenderer", "submitFire");
        method("net.minecraft.client.renderer.ScreenEffectRenderer", "submitWater");
        method("net.minecraft.client.renderer.ScreenEffectRenderer", "submitBlockSprite");
        method("net.minecraft.client.renderer.WeatherEffectRenderer", "render");
        method("net.minecraft.client.renderer.WeatherEffectRenderer", "renderOit");
        method("net.minecraft.client.renderer.culling.Frustum", "isVisible");
        method("net.minecraft.client.renderer.culling.Frustum", "cubeInFrustum");
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

    @Test
    void everyDeclaredMixinHasACompiledClass() throws Exception {
        for (String resource : new String[] {"/arcaneclient.client.mixins.json", "/arcaneclient.additions.mixins.json"}) {
            try (InputStreamReader reader = new InputStreamReader(
                RuntimeHookContractTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                String prefix = root.get("package").getAsString().replace('.', '/') + "/";
                for (var entry : root.getAsJsonArray("client")) {
                    String path = "/" + prefix + entry.getAsString().replace('.', '/') + ".class";
                    assertTrue(RuntimeHookContractTest.class.getResource(path) != null, "Missing mixin: " + path);
                }
            }
        }
    }

    private static Method method(String ownerName, String name) throws Exception {
        Class<?> owner = Class.forName(ownerName, false, RuntimeHookContractTest.class.getClassLoader());
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                assertTrue(method.trySetAccessible());
                return method;
            }
        }
        throw new NoSuchMethodException(ownerName + "." + name);
    }
}
