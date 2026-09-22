package dev.arcaneclient.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Select focused real-client regressions with -ParcaneGameTests=effects,sus,ui,cosmetics. */
public final class ArcaneClientGameTestSuite implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Map<String, FabricClientGameTest> tests = new LinkedHashMap<>();
        tests.put("nuker", new NukerClientGameTest());
        tests.put("effects", new EffectsClientGameTest());
        tests.put("sus", new SusChunkFinderClientGameTest());
        tests.put("ui", new Dev21UiClientGameTest());
        tests.put("cosmetics", new CosmeticsClientGameTest());
        tests.put("legacy-ui", new DevUiClientGameTest());
        tests.put("camera", new ArcaneCameraClientGameTest());
        tests.put("scanner", new ChunkScannerClientGameTest());
        tests.put("growth", new GrowthFinderClientGameTest());
        tests.put("relog", new RelogClientGameTest());
        String filter = System.getProperty("arcane.gametest.tests", "all");
        for (String name : filter.equals("all") ? tests.keySet() : java.util.Arrays.asList(filter.split(","))) {
            FabricClientGameTest test = tests.get(name.trim());
            if (test == null) throw new IllegalArgumentException("Unknown Arcane client test: " + name);
            dev.arcaneclient.ArcaneClient.LOGGER.info("Starting Arcane client regression: {}", name);
            context.restoreDefaultGameOptions();
            test.runTest(context);
            dev.arcaneclient.ArcaneClient.LOGGER.info("Passed Arcane client regression: {}", name);
        }
    }
}
