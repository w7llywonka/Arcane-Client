package dev.arcaneclient.screen;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import dev.arcaneclient.ArcaneClient;
import java.net.URI;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class ArcaneWelcomeScreen extends Screen {
    public static final int NOTICE_VERSION = 1;
    private static final String OFFICIAL_URL = "https://arcaneclient.shop";
    private static final URI OFFICIAL_URI = URI.create("https://www.arcaneclient.shop");
    private static final Component WARNING = Component.literal(
        "For your safety, install Arcane Client only from the official website. "
            + "Copies from other sources may contain malicious software."
    );

    private final Screen parent;
    private List<FormattedCharSequence> warningLines = List.of();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public ArcaneWelcomeScreen(Screen parent) {
        super(Component.literal("Welcome to Arcane Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ArcaneFont.invalidate();
        panelWidth = Math.min(430, Math.max(260, width - 32));
        warningLines = font.split(ArcaneFont.text(WARNING.getString()), panelWidth - 56);
        panelHeight = 150 + warningLines.size() * 12;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(12, (height - panelHeight) / 2);

        int buttonWidth = Math.min(280, panelWidth - 40);
        int buttonX = (width - buttonWidth) / 2;
        int linkY = panelY + 80 + warningLines.size() * 12;
        addRenderableWidget(new WelcomeButton(buttonX, linkY, buttonWidth, OFFICIAL_URL,
            false, () -> Blaze3D.openUri(OFFICIAL_URI)));
        addRenderableWidget(new WelcomeButton(buttonX, linkY + 30, buttonWidth, "Continue",
            true, this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        parent.extractRenderState(context, mouseX, mouseY, delta);
        ClickGuiColors colors = colors();
        context.fill(0, 0, width, height, 0x99000000);
        RoundedGui.fill(context, panelX, panelY + 3, panelWidth, panelHeight, 10, 0x30000000);
        RoundedGui.fill(context, panelX, panelY, panelWidth, panelHeight, 10, colors.window());
        RoundedGui.outlineOnly(context, panelX, panelY, panelWidth, panelHeight, 10, colors.outline());

        int centerX = width / 2;
        centeredLabel(context, "Welcome to Arcane Client", centerX, panelY + 20, colors.text());
        centeredLabel(context, "Configure your modules in the Click GUI.", centerX, panelY + 38, colors.muted());
        RoundedGui.fill(context, panelX + 18, panelY + 56, panelWidth - 36,
            warningLines.size() * 12 + 18, 6, colors.nest());
        int warningY = panelY + 65;
        for (FormattedCharSequence line : warningLines) {
            context.text(font, line, centerX - font.width(line) / 2, warningY, colors.muted(), false);
            warningY += 12;
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private ClickGuiColors colors() {
        return ClickGuiColors.display(ArcaneClient.config());
    }

    private void centeredLabel(GuiGraphicsExtractor context, String value, int centerX, int top, int color) {
        context.text(font, ArcaneFont.text(value), centerX - ArcaneFont.width(font, value) / 2, top, color, false);
    }

    private final class WelcomeButton extends AbstractWidget {
        private final boolean primary;
        private final Runnable action;

        private WelcomeButton(int x, int y, int width, String label, boolean primary, Runnable action) {
            super(x, y, width, 24, ArcaneFont.text(label));
            this.primary = primary;
            this.action = action;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            ClickGuiColors colors = colors();
            boolean highlighted = isHovered() || isFocused();
            int background = primary ? (highlighted ? colors.activeHover() : colors.active())
                : highlighted ? colors.hover() : colors.nest();
            RoundedGui.fill(context, getX(), getY(), getWidth(), getHeight(), 6, background);
            RoundedGui.outlineOnly(context, getX(), getY(), getWidth(), getHeight(), 6,
                isFocused() ? colors.accentBright() : primary ? colors.accentDim() : colors.outlineSoft());
            String label = getMessage().getString();
            centeredLabel(context, label, getX() + getWidth() / 2, getY() + 8,
                primary ? colors.text() : colors.muted());
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            action.run();
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (active && isFocused() && (input.key() == InputConstants.KEY_RETURN
                || input.key() == InputConstants.KEY_NUMPADENTER || input.key() == InputConstants.KEY_SPACE)) {
                playDownSound(minecraft.getSoundManager());
                action.run();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }
}
