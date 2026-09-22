package dev.arcaneclient.additions.cosmetics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NametagLayoutTest {
    @Test void everyHeadStyleClearsTextAtEverySupportedSizeAndTagScale() {
        for (int style = 0; style < 3; style++) {
            for (int headSize : new int[] { 50, 100, 150 }) {
                for (int tagScale : new int[] { 50, 100, 200 }) {
                    double anchor = NametagLayout.anchorY(2.1, 1.62, 1, true, style, headSize, tagScale, 9);
                    double lowerEdge = anchor - NametagLayout.textDrop(tagScale, 9);
                    double meshTop = NametagLayout.headwearTopY(1.62, 1, style, headSize);
                    assertTrue(lowerEdge - meshTop >= NametagLayout.HEADWEAR_GAP - 1e-9,
                        "Text background must clear every selected headwear mesh");
                }
            }
        }
    }

    @Test void remotePlayersAndDisabledHeadwearKeepTheirOriginalAnchor() {
        assertEquals(2.1, NametagLayout.anchorY(2.1, 1.62, 1, false, 2, 150, 200, 9));
    }

    @Test void largerHeadwearMovesTheSharedTextAndEquipmentAnchorUp() {
        double small = NametagLayout.anchorY(2.1, 1.62, 1, true, 2, 50, 100, 9);
        double large = NametagLayout.anchorY(2.1, 1.62, 1, true, 2, 150, 100, 9);
        assertEquals(0.38, large - small, 1e-9);
    }
}
