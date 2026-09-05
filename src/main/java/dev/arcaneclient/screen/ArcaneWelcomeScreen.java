package dev.arcaneclient.screen;

import java.net.URI;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class ArcaneWelcomeScreen extends Screen {
    public static final int NOTICE_VERSION = 1;
    private static final String OFFICIAL_URL = "https://arcaneclient.shop";
    private static final URI OFFICIAL_URI = URI.create("https://www.arcaneclient.shop");
    private static final Text WARNING = Text.literal(
        "remember that if you did not install this from the official website then you may have "
            + "been ratted! please be careful."
    );

    private final Screen parent;
    private List<OrderedText> warningLines = List.of();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public ArcaneWelcomeScreen(Screen parent) {
        super(Text.literal("Welcome to Arcane Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(430, Math.max(260, width - 32));
        warningLines = textRenderer.wrapLines(WARNING, panelWidth - 40);
        panelHeight = 134 + warningLines.size() * 12;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(12, (height - panelHeight) / 2);

        int buttonWidth = Math.min(280, panelWidth - 40);
        int buttonX = (width - buttonWidth) / 2;
        int linkY = panelY + 70 + warningLines.size() * 12;
        Text link = Text.literal(OFFICIAL_URL)
            .styled(style -> style.withColor(0xB5D78A).withUnderline(true));
        addDrawableChild(ButtonWidget.builder(link, button -> Util.getOperatingSystem().open(OFFICIAL_URI))
            .dimensions(buttonX, linkY, buttonWidth, 20)
            .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Continue"), button -> close())
            .dimensions(buttonX, linkY + 28, buttonWidth, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        parent.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x99000000);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF20C110F);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFFB5D78A);
        context.fill(panelX, panelY + panelHeight - 2, panelX + panelWidth, panelY + panelHeight, 0xFFB5D78A);
        context.fill(panelX, panelY, panelX + 2, panelY + panelHeight, 0xFFB5D78A);
        context.fill(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFB5D78A);

        int centerX = width / 2;
        context.drawCenteredTextWithShadow(textRenderer, "welcome to Arcane Client!", centerX, panelY + 18, 0xFFF2F7F3);
        context.drawCenteredTextWithShadow(textRenderer, "please enjoy my free client i made :)", centerX, panelY + 36, 0xFFB5D78A);
        int warningY = panelY + 54;
        for (OrderedText line : warningLines) {
            context.drawCenteredTextWithShadow(textRenderer, line, centerX, warningY, 0xFFD2D9D4);
            warningY += 12;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
