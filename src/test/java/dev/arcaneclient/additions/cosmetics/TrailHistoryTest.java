package dev.arcaneclient.additions.cosmetics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TrailHistoryTest {
    @Test void longSessionsStayBoundedAndKeepSamplesInOrder() {
        TrailHistory history = new TrailHistory();
        for (int tick = 0; tick < 10_000; tick++) history.sample(tick * 0.2, 64, 0, 60);
        assertEquals(60, history.size());
        assertTrue(history.size() <= TrailHistory.CAPACITY);
        assertEquals(1999.8, history.x(history.size() - 1), 0.00001);
        for (int point = 1; point < history.size(); point++) assertTrue(history.x(point) > history.x(point - 1));
    }

    @Test void teleportBreaksTheTrailAndResetDropsEverySample() {
        TrailHistory history = new TrailHistory();
        history.sample(0, 64, 0, 24);
        history.sample(1, 64, 0, 24);
        history.sample(100, 70, 100, 24);
        assertEquals(1, history.size());
        assertEquals(100, history.x(0));
        history.clear();
        assertEquals(0, history.size());
    }

    @Test void stationaryPlayersDoNotAccumulateGeometryAndShorterLifetimesApplyImmediately() {
        TrailHistory history = new TrailHistory();
        for (int tick = 0; tick < 60; tick++) history.sample(tick * 0.2, 64, 0, 60);
        history.sample(11.8, 64, 0, 6);
        assertTrue(history.size() <= 6);
        for (int tick = 0; tick < 30; tick++) history.sample(11.8, 64, 0, 6);
        assertEquals(1, history.size());
        assertTrue(history.opacity(0, 6, 0.5f) >= 0);
        assertTrue(history.opacity(0, 6, 0.5f) <= 1);
    }
}
