package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.screen.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;

/** Verify the actual controller sends only the chosen macro through a real local connection. */
public final class ChatMacrosClientGameTest implements FabricClientGameTest {
    private record Received(String text, long at) { }

    @Override public void runTest(ClientGameTestContext context) {
        List<Received> messages = new CopyOnWriteArrayList<>();
        List<String> commands = new CopyOnWriteArrayList<>();
        long[] enabledAt = new long[1];
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (message.signedContent().startsWith("arcane-macro-test-")) {
                messages.add(new Received(message.signedContent(), System.nanoTime()));
            }
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
            if (message.signedContent().equals("arcane-macro-command-test")) commands.add(message.signedContent());
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.levelRenderer.hasRenderedAllSections(), 5000);
            context.getInput().resizeWindow(1200, 800);
            context.runOnClient(client -> {
                var config = ArcaneClient.config();
                config.chatMacros = true;
                config.automaticChatMacros = false;
                config.automaticChatMacroSlot = 0;
                config.setChatMacro(0, "arcane-macro-test-first");
                config.setChatMacro(1, "arcane-macro-test-wrong");
                config.setChatMacro(2, "arcane-macro-test-third");
                config.setChatMacro(3, "/me arcane-macro-command-test");
                config.uiPanelLayout.clear();
                config.uiLayoutCustomized = false;
                config.uiShowEnabledOnly = config.uiShowFavoritesOnly = false;
                client.gui.setScreen(new ArcaneSettingsScreen(null));
                var screen = (ArcaneSettingsScreen) client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                GuiModule social = categories.stream().flatMap(c -> c.modules().stream())
                    .filter(m -> m.name().equals("Social")).findFirst().orElseThrow();
                screen.toggleModuleSettings(social);
                GuiSetting.Cycle interval = setting(social, "Interval", GuiSetting.Cycle.class);
                interval.next();
                require(client.gui.screen() instanceof TextSettingsScreen, "Interval opens the exact-value editor");
                List<EditBox> inputs = field(client.gui.screen(), "inputs");
                inputs.getFirst().setValue("1");
            });
            context.waitTicks(3);
            context.takeScreenshot("automatic-chat-macro-interval");
            context.runOnClient(client -> {
                var form = client.gui.screen();
                var save = form.children().stream().filter(child -> child instanceof AbstractWidget widget
                    && widget.getMessage().getString().equals("Save")).map(AbstractWidget.class::cast)
                    .findFirst().orElseThrow();
                form.mouseClicked(new MouseButtonEvent(save.getX() + save.getWidth() / 2.0,
                    save.getY() + save.getHeight() / 2.0, new MouseButtonInfo(SDL_BUTTON_LEFT, 0)), false);
            });
            context.runOnClient(client -> {
                require(ArcaneClient.config().automaticChatMacroIntervalSeconds == 1, "editor saves exact seconds");
                var screen = (ArcaneSettingsScreen) client.gui.screen();
                List<GuiCategory> categories = field(screen, "categories");
                GuiModule social = categories.stream().flatMap(c -> c.modules().stream())
                    .filter(m -> m.name().equals("Social")).findFirst().orElseThrow();
                GuiSetting.Cycle selector = setting(social, "Auto macro", GuiSetting.Cycle.class);
                selector.next();
                selector.next();
                require(ArcaneClient.config().automaticChatMacroSlot == 2 && selector.value().equals("Macro 3"),
                    "selector chooses the third macro");
                enabledAt[0] = System.nanoTime();
                setting(social, "Automatic", GuiSetting.Toggle.class).toggle();
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                List<GuiCategory> categories = field(client.gui.screen(), "categories");
                categories.stream().filter(c -> c.name().equals("UTILITY")).findFirst().orElseThrow().scrollBy(45);
            });
            context.waitTicks(3);
            context.takeScreenshot("automatic-chat-macros");
            context.waitFor(client -> messages.size() >= 2, 5000);
            require(messages.stream().allMatch(m -> m.text().equals("arcane-macro-test-third")), "only selected macro reaches server");
            // Packet arrival includes server tick jitter; unit tests cover the exact timer boundary.
            require(messages.get(1).at() - messages.get(0).at() >= 900_000_000L, "server receives repeat at interval");
            require(messages.get(0).at() - enabledAt[0] >= 1_000_000_000L, "first message waits for interval");

            context.runOnClient(client -> ArcaneClient.config().automaticChatMacros = false);
            context.waitTicks(5);
            int stoppedCount = messages.size();
            waitSeconds(context, 1.3);
            require(messages.size() == stoppedCount, "disabling stops sends");

            context.runOnClient(client -> {
                ArcaneClient.config().automaticChatMacroSlot = 0;
                ArcaneClient.config().automaticChatMacros = true;
            });
            context.waitFor(client -> messages.size() > stoppedCount, 5000);
            require(messages.get(stoppedCount).text().equals("arcane-macro-test-first"), "changing slot sends new selection");
            context.runOnClient(client -> ArcaneClient.config().chatMacros = false);
            context.waitTicks(5);
            int masterOffCount = messages.size();
            waitSeconds(context, 1.3);
            require(messages.size() == masterOffCount, "master toggle stops automation");

            context.runOnClient(client -> {
                ArcaneClient.config().chatMacros = true;
                ArcaneClient.config().automaticChatMacroSlot = 3;
            });
            context.waitFor(client -> !commands.isEmpty(), 5000);
            context.runOnClient(client -> {
                ArcaneClient.config().setChatMacro(3, "");
                client.gui.setScreen(null);
            });
            context.waitTicks(5);
            int commandCount = commands.size();
            waitSeconds(context, 1.3);
            require(commands.size() == commandCount && messages.size() == masterOffCount, "empty selection sends nothing");
            ArcaneClient.LOGGER.info("[QA] Automatic chat macros passed: exact interval editor, selected slot, repeated chat, both toggles, commands and empty slot");
        } finally {
            context.runOnClient(client -> ArcaneClient.config().automaticChatMacros = false);
        }
    }

    private static void waitSeconds(ClientGameTestContext context, double seconds) {
        long start = System.nanoTime();
        context.waitFor(client -> System.nanoTime() - start >= seconds * 1_000_000_000L, 5000);
    }

    private static <T extends GuiSetting> T setting(GuiModule module, String label, Class<T> type) {
        return module.settings().stream().filter(s -> s.label().equals(label) && type.isInstance(s))
            .map(type::cast).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Chat macros: " + message);
    }
}
