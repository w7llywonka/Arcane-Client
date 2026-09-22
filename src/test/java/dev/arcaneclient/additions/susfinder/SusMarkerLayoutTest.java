package dev.arcaneclient.additions.susfinder;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SusMarkerLayoutTest {
    @Test void scaleTracksDistanceForReadableLabelsAtTypicalFlightHeights() {
        assertEquals(0.12f, SusMarkerLayout.scale(24), 0.0001f);
        assertEquals(0.48f, SusMarkerLayout.scale(96), 0.0001f);
        assertEquals(SusMarkerLayout.scale(24) / 24, SusMarkerLayout.scale(96) / 96, 0.0001f);
    }

    @Test void scaleHasFiniteNearAndFarBounds() {
        assertEquals(0.025f, SusMarkerLayout.scale(0));
        assertEquals(0.025f, SusMarkerLayout.scale(-10));
        assertEquals(0.025f, SusMarkerLayout.scale(Double.NaN));
        assertEquals(1.5f, SusMarkerLayout.scale(512));
        assertEquals(1.5f, SusMarkerLayout.scale(Double.POSITIVE_INFINITY));
    }

    @Test void compactLabelsKeepScoresAndExplicitInference() {
        assertEquals("SUS · 14/100", SusMarkerLayout.title(14));
        assertEquals("1 chunk · growth", SusMarkerLayout.detail(1, false));
        assertEquals("2 chunks · inferred light", SusMarkerLayout.detail(2, true));
    }
}
