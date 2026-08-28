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
import net.minecraft.client.Mouse;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.junit.jupiter.api.Test;

final class RuntimeHookContractTest {
    @Test
    void exactMinecraftMethodsRequiredByFeatureMixinsExist() throws Exception {
        method(GameRenderer.class, "getFov", Camera.class, float.class, boolean.class);
        method(GameRenderer.class, "tiltViewWhenHurt", MatrixStack.class, float.class);
        method(GameRenderer.class, "getNightVisionStrength", LivingEntity.class, float.class);
        method(LivingEntity.class, "getHandSwingDuration");
        method(Entity.class, "changeLookDirection", double.class, double.class);
        method(Mouse.class, "onMouseScroll", long.class, double.class, double.class);
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
            assertTrue(names.contains("EntityMixin"));
            assertTrue(names.contains("GameRendererMixin"));
            assertTrue(names.contains("LivingEntityMixin"));
            assertTrue(names.contains("MouseMixin"));
            assertTrue(names.contains("KeyboardInputMixin"));
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        assertTrue(method.trySetAccessible());
        return method;
    }
}
