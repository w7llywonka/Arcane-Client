package dev.arcaneclient.screen;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class ArcaneWelcomeScreen extends Screen {
    public static final int NOTICE_VERSION = 1;
    private static final String OFFICIAL_URL = "https://arcaneclient.shop";
    private static final URI OFFICIAL_URI = URI.create("https://www.arcaneclient.shop");
    private static final Component WARNING = Component.literal(
        "remember that if you did not install this from the official website then you may have "
            + "been ratted! please be careful."
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
        panelWidth = Math.min(430, Math.max(260, width - 32));
        warningLines = font.split(WARNING, panelWidth - 40);
        panelHeight = 134 + warningLines.size() * 12;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(12, (height - panelHeight) / 2);

        int buttonWidth = Math.min(280, panelWidth - 40);
        int buttonX = (width - buttonWidth) / 2;
        int linkY = panelY + 70 + warningLines.size() * 12;
        Component link = Component.literal(OFFICIAL_URL)
            .withStyle(style -> style.withColor(0xB5D78A).withUnderlined(true));
        addRenderableWidget(Button.builder(link, button -> Blaze3D.openUri(OFFICIAL_URI))
            .bounds(buttonX, linkY, buttonWidth, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Continue"), button -> onClose())
            .bounds(buttonX, linkY + 28, buttonWidth, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        parent.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x99000000);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF20C110F);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFFB5D78A);
        context.fill(panelX, panelY + panelHeight - 2, panelX + panelWidth, panelY + panelHeight, 0xFFB5D78A);
        context.fill(panelX, panelY, panelX + 2, panelY + panelHeight, 0xFFB5D78A);
        context.fill(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFB5D78A);

        int centerX = width / 2;
        context.centeredText(font, "welcome to Arcane Client!", centerX, panelY + 18, 0xFFF2F7F3);
        context.centeredText(font, "please enjoy my free client i made :)", centerX, panelY + 36, 0xFFB5D78A);
        int warningY = panelY + 54;
        for (FormattedCharSequence line : warningLines) {
            context.centeredText(font, line, centerX, warningY, 0xFFD2D9D4);
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
}
