package dev.arcaneclient.additions.susfinder;

import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class SusChunkFinderModules {
    private SusChunkFinderModules() { }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        var c = config.susFinder;
        return List.of(GuiModule.toggle("Sus Chunk Finder",
            "Scores growth density in received chunks at every height. Natural terrain can qualify; light hints are inferred, never confirmed hidden blocks.",
            () -> c.enabled, value -> c.enabled = value)
            .with(new GuiSetting.Slider("Score threshold", () -> c.threshold, value -> c.threshold = value, 1, 100, "/100"))
            .with(new GuiSetting.Info("Sensitivity", () -> "Lower threshold = more leads"))
            .with(new GuiSetting.Toggle("Amethyst", () -> c.amethyst, value -> c.amethyst = value))
            .with(new GuiSetting.Toggle("Kelp", () -> c.kelp, value -> c.kelp = value))
            .with(new GuiSetting.Toggle("Bamboo", () -> c.bamboo, value -> c.bamboo = value))
            .with(new GuiSetting.Toggle("Berries", () -> c.berries, value -> c.berries = value))
            .with(new GuiSetting.Toggle("Vines", () -> c.vines, value -> c.vines = value))
            .with(new GuiSetting.Toggle("Dripstone", () -> c.dripstone, value -> c.dripstone = value))
            .with(new GuiSetting.Toggle("Inferred amethyst light", () -> c.inferredAmethystLight, value -> c.inferredAmethystLight = value))
            .with(new GuiSetting.Info("Light hints", () -> "Received light 5 + geode shell"))
            .with(new GuiSetting.Info("Initial light data", () -> "Reload chunks after enabling hints"))
            .with(new GuiSetting.Info("Inference", () -> "Weak hint; not hidden growth proof"))
            .with(new GuiSetting.Slider("Scan range", () -> c.scanRange, value -> c.scanRange = value, 32, 512, "m"))
            .with(new GuiSetting.Slider("Scan budget", () -> c.scanBudget, value -> c.scanBudget = value, 512, 16384, " blocks/t"))
            .with(new GuiSetting.Slider("Merge radius", () -> c.mergeRadius, value -> c.mergeRadius = value, 0, 128, "m"))
            .with(new GuiSetting.Toggle("Ground highlights", () -> c.highlights, value -> c.highlights = value))
            .with(new GuiSetting.Toggle("Zone center markers", () -> c.markers, value -> c.markers = value))
            .with(new GuiSetting.Toggle("Zone radar", () -> c.radar, value -> c.radar = value))
            .with(new GuiSetting.Slider("Fill opacity", () -> c.fillOpacity, value -> c.fillOpacity = value, 0, 160, "/255"))
            .with(new GuiSetting.Slider("Outline opacity", () -> c.outlineOpacity, value -> c.outlineOpacity = value, 0, 255, "/255"))
            .with(new GuiSetting.Swatch("Observed color", () -> c.color, value -> c.color = value))
            .with(new GuiSetting.Swatch("Inferred color", () -> c.inferredColor, value -> c.inferredColor = value))
            .with(new GuiSetting.Info("Scan queue", () -> SusChunkFinderController.instance() == null ? "0" : Integer.toString(SusChunkFinderController.instance().pendingChunks())))
            .with(new GuiSetting.Info("Sus zones", () -> SusChunkFinderController.instance() == null ? "0" : Integer.toString(SusChunkFinderController.instance().zones().size())))
            .build());
    }
}
