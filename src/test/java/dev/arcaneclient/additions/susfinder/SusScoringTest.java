package dev.arcaneclient.additions.susfinder;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SusScoringTest {
    @Test void familyWeightsDampenDensityAndCapIndependently() {
        assertEquals(7, SusScoring.contribution(SusScoring.Family.AMETHYST, 1));
        assertEquals(14, SusScoring.contribution(SusScoring.Family.AMETHYST, 4));
        assertEquals(70, SusScoring.contribution(SusScoring.Family.AMETHYST, 1000000));
        assertEquals(45, SusScoring.contribution(SusScoring.Family.VINES, 1000000));
        assertEquals(0, SusScoring.contribution(SusScoring.Family.KELP, -1));
    }

    @Test void inferenceIsOptInWeakerAndDoesNotInventObservedAmethyst() {
        var config = new SusChunkFinderConfig();
        assertEquals(0, SusScoring.score(Map.of(), 99, config).value());
        config.inferredAmethystLight = true;
        var score = SusScoring.score(Map.of(), 99, config);
        assertEquals(18, score.value());
        assertTrue(score.inferred());
        assertTrue(score.contributions().isEmpty());
        assertTrue(score.value() < config.threshold);
        config.amethyst = false;
        assertEquals(0, SusScoring.score(Map.of(SusScoring.Family.AMETHYST, 500), 99, config).value());
    }

    @Test void disabledFamiliesAndEmptyStorageOnlyCountsCannotContribute() {
        var config = new SusChunkFinderConfig();
        config.kelp = false;
        assertEquals(0, SusScoring.score(Map.of(SusScoring.Family.KELP, 5000), 0, config).value());
        assertEquals(0, SusScoring.score(Map.of(), 0, config).value());
        var counts = new EnumMap<SusScoring.Family, Integer>(SusScoring.Family.class);
        for (var family : SusScoring.Family.values()) counts.put(family, 5000);
        assertEquals(100, SusScoring.score(counts, 0, config).value());
    }

    @Test void sanitizeClampsUntrustedConfiguration() {
        var config = new SusChunkFinderConfig();
        config.threshold = -8; config.scanRange = 2000000; config.scanBudget = Integer.MAX_VALUE;
        config.fillOpacity = 255; config.outlineOpacity = -10; config.mergeRadius = -1;
        config.sanitize();
        assertEquals(1, config.threshold); assertEquals(512, config.scanRange);
        assertEquals(16384, config.scanBudget); assertEquals(160, config.fillOpacity);
        assertEquals(0, config.outlineOpacity); assertEquals(0, config.mergeRadius);
    }
}
