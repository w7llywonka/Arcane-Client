package dev.arcaneclient.additions.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** One owned, opt-in SMTC reader process. No network, playback controls or disk metadata. */
final class SpotifyBridge {
    private static final String SCRIPT = "/assets/arcaneclient/media/spotify-smtc.ps1";
    private static final int MAX_LINE = 16_384;
    private static final long RESPONSE_TIMEOUT = TimeUnit.SECONDS.toNanos(10);
    private static final long MAX_DURATION_MS = TimeUnit.DAYS.toMillis(7);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private final Object signal = new Object();
    private volatile boolean wanted;
    private volatile boolean shutdown;
    private volatile long generation;
    private volatile Process ownedProcess;
    private volatile Snapshot snapshot = Snapshot.empty("Disabled");
    private Thread supervisor;
    private boolean shutdownHookRegistered;

    void registerShutdownHook() {
        synchronized (signal) {
            if (shutdownHookRegistered) return;
            shutdownHookRegistered = true;
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdown();
                // The JVM can finish before daemon threads; terminate only our
                // captured child process on this dedicated shutdown thread.
                terminate(ownedProcess);
                Thread worker = supervisor;
                if (worker != null) {
                    try { worker.join(1_500); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
                terminate(ownedProcess);
            }, "Arcane-Spotify-shutdown"));
        } catch (IllegalStateException | SecurityException ignored) {
            // Normal client shutdown still signals the owned supervisor.
        }
    }

    void setEnabled(boolean enabled) {
        synchronized (signal) {
            boolean next = enabled && WINDOWS && !shutdown;
            if (wanted == next) {
                if (enabled && !WINDOWS) snapshot = Snapshot.empty("Windows 10/11 only");
                return;
            }
            wanted = next;
            generation++;
            snapshot = Snapshot.empty(next ? "Connecting to Windows media" : "Disabled");
            if (next && supervisor == null) {
                supervisor = new Thread(this::supervise, "Arcane-Spotify-supervisor");
                supervisor.setDaemon(true);
                supervisor.start();
            }
            signal.notifyAll();
            if (supervisor != null) supervisor.interrupt();
        }
    }

    void shutdown() {
        synchronized (signal) {
            shutdown = true;
            wanted = false;
            generation++;
            snapshot = Snapshot.empty("Disabled");
            signal.notifyAll();
            if (supervisor != null) supervisor.interrupt();
        }
    }

    Snapshot snapshot() { return snapshot; }
    String status() { return snapshot.status; }
    static boolean supported() { return WINDOWS; }

    private boolean requested(long token) {
        return wanted && !shutdown && generation == token;
    }

    private void publish(long token, Snapshot next) {
        synchronized (signal) {
            if (requested(token)) snapshot = next;
        }
    }

    private void supervise() {
        int failures = 0;
        long previousGeneration = -1;
        while (!shutdown) {
            long token;
            synchronized (signal) {
                while (!wanted && !shutdown) {
                    try { signal.wait(); }
                    catch (InterruptedException ignored) { }
                }
                if (shutdown) return;
                token = generation;
            }
            // Clear wakeup interrupts before process supervision and waits.
            Thread.interrupted();
            if (token != previousGeneration) failures = 0;
            previousGeneration = token;
            Attempt attempt = new Attempt();
            try {
                runProcess(token, attempt);
            } catch (IOException | RuntimeException failure) {
                attempt.problem = "Local media bridge unavailable";
            }
            if (!requested(token)) continue;
            if (attempt.permanent) {
                publish(token, Snapshot.empty(attempt.problem));
                awaitChange(token, Long.MAX_VALUE);
                continue;
            }
            failures = attempt.receivedTrackOrIdle ? 1 : Math.min(5, failures + 1);
            int delay = Math.min(60, 5 << (failures - 1));
            publish(token, Snapshot.empty(attempt.problem + " · retry " + delay + "s"));
            awaitChange(token, TimeUnit.SECONDS.toNanos(delay));
        }
    }

    private void awaitChange(long token, long delayNanos) {
        long start = System.nanoTime();
        synchronized (signal) {
            while (requested(token)) {
                try {
                    if (delayNanos == Long.MAX_VALUE) signal.wait();
                    else {
                        long remaining = delayNanos - (System.nanoTime() - start);
                        if (remaining <= 0) return;
                        TimeUnit.NANOSECONDS.timedWait(signal, remaining);
                    }
                } catch (InterruptedException ignored) { }
            }
        }
    }

    private void runProcess(long token, Attempt attempt) throws IOException {
        String windowsDirectory = System.getenv("SystemRoot");
        if (windowsDirectory == null || windowsDirectory.isBlank()) windowsDirectory = "C:\\Windows";
        Path executable = Path.of(windowsDirectory, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        if (!Files.isRegularFile(executable)) {
            attempt.permanent = true;
            attempt.problem = "Windows PowerShell unavailable";
            return;
        }
        String encoded;
        try (InputStream stream = SpotifyBridge.class.getResourceAsStream(SCRIPT)) {
            if (stream == null) {
                attempt.permanent = true;
                attempt.problem = "Media bridge resource missing";
                return;
            }
            byte[] bytes = stream.readNBytes(12_001);
            if (bytes.length > 12_000) throw new IOException("Media bridge exceeds size limit");
            String script = new String(bytes, StandardCharsets.UTF_8);
            encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        }
        if (!requested(token)) return;
        Process process = new ProcessBuilder(executable.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
            "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        ownedProcess = process;
        Thread reader = null;
        try {
            process.getOutputStream().close();
            if (!requested(token)) return;
            attempt.lastResultNanos = System.nanoTime();
            publish(token, Snapshot.empty("Connecting to Windows media"));
            reader = new Thread(() -> readResults(process, token, attempt), "Arcane-Spotify-reader");
            reader.setDaemon(true);
            reader.start();
            while (requested(token)) {
                if (attempt.fatal) break;
                if (System.nanoTime() - attempt.lastResultNanos >= RESPONSE_TIMEOUT) {
                    attempt.problem = "Windows media timed out";
                    break;
                }
                try {
                    if (process.waitFor(500, TimeUnit.MILLISECONDS)) break;
                } catch (InterruptedException ignored) { }
            }
        } finally {
            terminate(process);
            if (reader != null) {
                try { reader.join(500); }
                catch (InterruptedException ignored) { }
            }
            if (ownedProcess == process) ownedProcess = null;
        }
    }

    private void readResults(Process process, long token, Attempt attempt) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), 4_096)) {
            while (requested(token)) {
                String line = boundedLine(reader);
                if (line == null) break;
                if (line.isBlank()) continue;
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                String state = text(json, "status", 32);
                if (state.equals("error")) {
                    String reason = text(json, "reason", 40);
                    attempt.permanent = reason.equals("api_unavailable") || reason.equals("access_denied");
                    attempt.problem = reason.equals("api_unavailable") ? "Windows media API unavailable"
                        : reason.equals("access_denied") ? "Windows media access denied"
                        : reason.equals("timed_out") ? "Windows media timed out" : "Windows media read failed";
                    attempt.fatal = true;
                    return;
                }
                if (!state.equals("ok") && !state.equals("no_session")) throw new IOException("Unexpected bridge status");
                long received = System.nanoTime();
                attempt.lastResultNanos = received;
                attempt.receivedTrackOrIdle = true;
                if (state.equals("no_session")) {
                    publish(token, Snapshot.empty("No Spotify media session"));
                    continue;
                }
                String title = text(json, "title", 256);
                String artist = text(json, "artist", 192);
                String playback = text(json, "playback", 24);
                boolean playing = playback.equals("Playing");
                boolean paused = playback.equals("Paused");
                long duration = milliseconds(json, "duration");
                long position = milliseconds(json, "position");
                if (duration > 0) position = Math.min(position, duration);
                String status = playing ? "Spotify playing" : paused ? "Spotify paused" : "Spotify idle";
                publish(token, new Snapshot(title, artist, playing, paused, position, duration, received, status));
            }
        } catch (IOException | RuntimeException failure) {
            if (requested(token)) {
                attempt.problem = "Local media read interrupted";
                attempt.fatal = true;
            }
        }
    }

    private static String boundedLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder(512);
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') return line.toString();
            if (value == '\r') continue;
            if (line.length() >= MAX_LINE) throw new IOException("Media output exceeded line limit");
            line.append((char)value);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static String text(JsonObject json, String key, int maximum) {
        if (!json.has(key) || json.get(key).isJsonNull()) return "";
        String raw = json.get(key).getAsString();
        StringBuilder clean = new StringBuilder(Math.min(raw.length(), maximum));
        raw.codePoints().filter(value -> !Character.isISOControl(value) && value != 0x00A7)
            .limit(maximum).forEach(clean::appendCodePoint);
        return clean.toString().strip();
    }

    private static long milliseconds(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return 0;
        double value = json.get(key).getAsDouble();
        return Double.isFinite(value) ? (long)Math.clamp(value, 0, MAX_DURATION_MS) : 0;
    }

    private static void terminate(Process process) {
        if (process == null) return;
        // This method is only called by the daemon supervisor or JVM shutdown
        // thread. No process search, descendant killing or GUI-thread waits.
        if (process.isAlive()) process.destroyForcibly();
        try { process.waitFor(1, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { }
    }

    private static final class Attempt {
        volatile long lastResultNanos;
        volatile boolean receivedTrackOrIdle;
        volatile boolean permanent;
        volatile boolean fatal;
        volatile String problem = "Local media bridge stopped";
    }

    record Snapshot(String title, String artist, boolean playing, boolean paused, long positionMs,
                    long durationMs, long receivedNanos, String status) {
        static Snapshot empty(String status) { return new Snapshot("", "", false, false, 0, 0, 0, status); }
        boolean displayable() {
            return !title.isBlank() && (playing || paused) && receivedNanos != 0
                && System.nanoTime() - receivedNanos < TimeUnit.SECONDS.toNanos(8);
        }
        long currentPositionMs() {
            long elapsed = playing ? Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - receivedNanos)) : 0;
            long result = positionMs + elapsed;
            return durationMs > 0 ? Math.min(result, durationMs) : result;
        }
    }
}
