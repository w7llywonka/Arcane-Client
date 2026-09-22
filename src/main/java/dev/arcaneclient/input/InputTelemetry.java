package dev.arcaneclient.input;

/** Measures the first rendered HUD frame after an SDL keyboard or mouse event. */
public final class InputTelemetry {
    private static long eventNanos;
    private static long eventSerial;
    private static long sampledSerial;
    private static double latencyMs;

    private InputTelemetry() { }

    public static void event() {
        eventNanos = System.nanoTime();
        eventSerial++;
    }

    public static void renderedFrame() {
        if (sampledSerial == eventSerial || eventNanos == 0L) return;
        latencyMs = Math.clamp((System.nanoTime() - eventNanos) / 1_000_000.0, 0.0, 999.0);
        sampledSerial = eventSerial;
    }

    public static String label() {
        return eventNanos == 0L ? "--" : String.format(java.util.Locale.ROOT, "%.1f ms", latencyMs);
    }
}
