package dev.arcaneclient.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class AutomaticChatMacroScheduleTest {
    private static final long SECOND = 1_000_000_000L;
    private final AutomaticChatMacroSchedule schedule = new AutomaticChatMacroSchedule();
    private final Object session = new Object();
    private final ChatMacroMessage.Outbound message = ChatMacroMessage.parse("hello");

    @Test
    void waitsForFullIntervalThenRepeatsAtTheBoundary() {
        assertNull(poll(true, session, 0, 30, message, 0));
        assertNull(poll(true, session, 0, 30, message, 30 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, 30, message, 30 * SECOND));
        assertNull(poll(true, session, 0, 30, message, 30 * SECOND));
        assertNull(poll(true, session, 0, 30, message, 60 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, 30, message, 60 * SECOND));
    }

    @Test
    void delayedTicksDoNotBurstOrReplayMissedIntervals() {
        poll(true, session, 0, 30, message, 0);
        assertEquals(message, poll(true, session, 0, 30, message, 150 * SECOND));
        assertNull(poll(true, session, 0, 30, message, 150 * SECOND + 1));
        assertNull(poll(true, session, 0, 30, message, 180 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, 30, message, 180 * SECOND));
    }

    @Test
    void disablingOrPausingRestartsTheCountdownOnResume() {
        poll(true, session, 0, 30, message, 0);
        assertNull(poll(false, session, 0, 30, message, 40 * SECOND));
        assertNull(poll(true, session, 0, 30, message, 50 * SECOND));
        assertNull(poll(true, session, 0, 30, message, 80 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, 30, message, 80 * SECOND));
    }

    @Test
    void aNewConnectionRestartsEvenWithoutAnIntermediateDisconnectedTick() {
        poll(true, session, 0, 30, message, 0);
        Object reconnected = new Object();
        assertNull(poll(true, reconnected, 0, 30, message, 40 * SECOND));
        assertEquals(message, poll(true, reconnected, 0, 30, message, 70 * SECOND));
    }

    @Test
    void disconnectedTicksClearTheOldCountdown() {
        poll(true, session, 0, 30, message, 0);
        assertNull(poll(true, null, 0, 30, message, 40 * SECOND));
        assertNull(poll(true, session, 0, 30, message, 50 * SECOND));
        assertEquals(message, poll(true, session, 0, 30, message, 80 * SECOND));
    }

    @Test
    void slotAndMessageChangesRestartAndOnlySendTheNewSelection() {
        poll(true, session, 0, 30, message, 0);
        assertNull(poll(true, session, 3, 30, message, 30 * SECOND));
        ChatMacroMessage.Outbound command = ChatMacroMessage.parse("/spawn");
        assertNull(poll(true, session, 3, 30, command, 40 * SECOND));
        assertNull(poll(true, session, 3, 30, command, 70 * SECOND - 1));
        assertEquals(new ChatMacroMessage.Outbound(true, "spawn"), poll(true, session, 3, 30, command, 70 * SECOND));
    }

    @Test
    void intervalChangesRestartTheCountdown() {
        poll(true, session, 0, 30, message, 0);
        assertNull(poll(true, session, 0, 5, message, 29 * SECOND));
        assertNull(poll(true, session, 0, 5, message, 34 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, 5, message, 34 * SECOND));
    }

    @Test
    void emptyMessagesAndInvalidSlotsNeverSend() {
        for (String empty : new String[] { null, "", "  ", "/" }) {
            poll(true, session, 0, 30, message, 0);
            assertNull(poll(true, session, 0, 30, ChatMacroMessage.parse(empty), 30 * SECOND));
            assertNull(poll(true, session, 0, 30, message, 40 * SECOND));
        }
        assertNull(poll(true, session, -1, 30, message, 100 * SECOND));
        assertNull(poll(true, session, 4, 30, message, 100 * SECOND));
    }

    @Test
    void intervalBoundsPreventZeroDelayAndOverflow() {
        assertNull(poll(true, session, 0, Integer.MIN_VALUE, message, 0));
        assertNull(poll(true, session, 0, Integer.MIN_VALUE, message, SECOND - 1));
        assertEquals(message, poll(true, session, 0, Integer.MIN_VALUE, message, SECOND));
        assertNull(poll(true, session, 0, Integer.MAX_VALUE, message, SECOND));
        assertNull(poll(true, session, 0, Integer.MAX_VALUE, message, 3601 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, Integer.MAX_VALUE, message, 3601 * SECOND));
    }

    @Test
    void monotonicCounterWrapStillMeasuresElapsedTime() {
        long start = Long.MAX_VALUE - SECOND;
        assertNull(poll(true, session, 0, 2, message, start));
        assertNull(poll(true, session, 0, 2, message, start + 2 * SECOND - 1));
        assertEquals(message, poll(true, session, 0, 2, message, start + 2 * SECOND));
    }

    private ChatMacroMessage.Outbound poll(boolean enabled, Object connection, int slot, int interval,
                                          ChatMacroMessage.Outbound value, long now) {
        return schedule.poll(enabled, connection, slot, interval, value, now);
    }
}
