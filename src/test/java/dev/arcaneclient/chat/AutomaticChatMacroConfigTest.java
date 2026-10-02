package dev.arcaneclient.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.additions.configlibrary.ConfigLibrary;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

final class AutomaticChatMacroConfigTest {
    private static final Gson GSON = new Gson();

    @Test
    void legacyConfigurationsKeepAutomaticSendingOff() {
        ArcaneConfig config = GSON.fromJson("{\"chatMacro1\":\"hello\"}", ArcaneConfig.class);
        assertFalse(config.automaticChatMacros);
        assertEquals(0, config.automaticChatMacroSlot);
        assertEquals(30, config.automaticChatMacroIntervalSeconds);
        assertEquals("hello", config.chatMacro(0));
    }

    @Test
    void chosenMacroAndExactIntervalSurviveReload() throws ReflectiveOperationException {
        ArcaneConfig config = new ArcaneConfig();
        config.automaticChatMacros = true;
        config.automaticChatMacroSlot = 2;
        config.automaticChatMacroIntervalSeconds = 137;
        config.setChatMacro(2, "/spawn");
        ArcaneConfig reloaded = GSON.fromJson(GSON.toJson(config), ArcaneConfig.class);
        clamp(reloaded);
        assertTrue(reloaded.automaticChatMacros);
        assertEquals(2, reloaded.automaticChatMacroSlot);
        assertEquals(137, reloaded.automaticChatMacroIntervalSeconds);
        assertEquals("/spawn", reloaded.chatMacro(2));
    }

    @Test
    void corruptValuesAreClampedOnLoad() throws ReflectiveOperationException {
        ArcaneConfig config = new ArcaneConfig();
        config.automaticChatMacroSlot = -99;
        config.automaticChatMacroIntervalSeconds = 0;
        clamp(config);
        assertEquals(0, config.automaticChatMacroSlot);
        assertEquals(1, config.automaticChatMacroIntervalSeconds);
        config.automaticChatMacroSlot = 99;
        config.automaticChatMacroIntervalSeconds = Integer.MAX_VALUE;
        clamp(config);
        assertEquals(3, config.automaticChatMacroSlot);
        assertEquals(3600, config.automaticChatMacroIntervalSeconds);
    }

    @Test
    void intervalEditorAcceptsExactSecondsAndPreservesValueForInvalidText() {
        ArcaneConfig config = new ArcaneConfig();
        assertTrue(config.setAutomaticChatMacroInterval(" 137 "));
        assertEquals(137, config.automaticChatMacroIntervalSeconds);
        for (String invalid : new String[] { "", "abc", "1.5", "9999999999" }) {
            assertFalse(config.setAutomaticChatMacroInterval(invalid));
            assertEquals(137, config.automaticChatMacroIntervalSeconds);
        }
        assertTrue(config.setAutomaticChatMacroInterval("0"));
        assertEquals(1, config.automaticChatMacroIntervalSeconds);
        assertTrue(config.setAutomaticChatMacroInterval("5000"));
        assertEquals(3600, config.automaticChatMacroIntervalSeconds);
    }

    @Test
    void loadingProfilesDisablesAutomaticSendingWithoutLosingTheChosenInterval() throws ReflectiveOperationException {
        JsonObject profile = GSON.fromJson("{\"chatMacros\":true,\"automaticChatMacros\":true,"
            + "\"automaticChatMacroSlot\":2,\"automaticChatMacroIntervalSeconds\":137}", JsonObject.class);
        Method disable = ConfigLibrary.class.getDeclaredMethod("disableAutomation", JsonObject.class, String.class);
        disable.setAccessible(true);
        disable.invoke(null, profile, "");
        assertFalse(profile.get("chatMacros").getAsBoolean());
        assertFalse(profile.get("automaticChatMacros").getAsBoolean());
        assertEquals(2, profile.get("automaticChatMacroSlot").getAsInt());
        assertEquals(137, profile.get("automaticChatMacroIntervalSeconds").getAsInt());
    }

    private static void clamp(ArcaneConfig config) throws ReflectiveOperationException {
        Method method = ArcaneConfig.class.getDeclaredMethod("clamp");
        method.setAccessible(true);
        method.invoke(config);
    }
}
