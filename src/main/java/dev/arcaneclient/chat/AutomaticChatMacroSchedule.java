package dev.arcaneclient.chat;

/** Monotonic, session-local timer. A delayed tick sends once without replaying missed intervals. */
public final class AutomaticChatMacroSchedule {
    public static final int MACRO_COUNT = 4;
    public static final int MIN_INTERVAL_SECONDS = 1;
    public static final int MAX_INTERVAL_SECONDS = 3600;

    private Object session;
    private int slot;
    private int intervalSeconds;
    private ChatMacroMessage.Outbound message;
    private long startedAt;

    public ChatMacroMessage.Outbound poll(boolean enabled, Object session, int slot, int intervalSeconds,
                                         ChatMacroMessage.Outbound message, long now) {
        if (!enabled || session == null || message == null || slot < 0 || slot >= MACRO_COUNT) {
            reset();
            return null;
        }
        int interval = Math.clamp(intervalSeconds, MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS);
        if (this.session != session || this.slot != slot || this.intervalSeconds != interval
            || !message.equals(this.message)) {
            this.session = session;
            this.slot = slot;
            this.intervalSeconds = interval;
            this.message = message;
            this.startedAt = now;
            return null;
        }
        if (now - this.startedAt < interval * 1_000_000_000L) return null;
        this.startedAt = now;
        return message;
    }

    private void reset() {
        this.session = null;
        this.message = null;
    }
}
