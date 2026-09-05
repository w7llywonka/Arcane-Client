package dev.arcaneclient.scan;

/**
 * Pure scheduling policy shared by the runtime queue and deterministic tests.
 * Lower priority scores are processed first.
 */
public final class ScanScheduling {
    public static final int SLICES_PER_JOB_TURN = 4;
    private static final int MAX_ACTIVE_JOBS = 8;
    private static final int FLIGHT_LOOKAHEAD_CHUNKS = 4;
    private static final double DIRECTION_DEADBAND = 0.025;
    private static final long BACKGROUND_PRIORITY = 1L << 54;

    private ScanScheduling() {
    }

    public static int activeWindow(int intensity) {
        return Math.clamp(intensity, 2, MAX_ACTIVE_JOBS);
    }

    public static int direction(double velocityComponent) {
        if (Math.abs(velocityComponent) < DIRECTION_DEADBAND) {
            return 0;
        }
        return velocityComponent > 0.0 ? 1 : -1;
    }

    public static long priorityScore(
        boolean frontier,
        int chunkX,
        int chunkZ,
        int centerX,
        int centerZ,
        int directionX,
        int directionZ
    ) {
        long deltaX = (long)chunkX - centerX;
        long deltaZ = (long)chunkZ - centerZ;
        long targetX = (long)centerX + (long)directionX * FLIGHT_LOOKAHEAD_CHUNKS;
        long targetZ = (long)centerZ + (long)directionZ * FLIGHT_LOOKAHEAD_CHUNKS;
        long targetDeltaX = (long)chunkX - targetX;
        long targetDeltaZ = (long)chunkZ - targetZ;
        long predictedDistance = targetDeltaX * targetDeltaX + targetDeltaZ * targetDeltaZ;
        long centerDistance = deltaX * deltaX + deltaZ * deltaZ;
        long forward = deltaX * directionX + deltaZ * directionZ;
        long behindPenalty = Math.max(0L, -forward);
        long score = predictedDistance * 4_096L + centerDistance * 16L + behindPenalty * 256L;
        return frontier ? score : BACKGROUND_PRIORITY + score;
    }

    public static int neighborhoodCoordinateCount(int radius) {
        int diameter = Math.max(0, radius) * 2 + 1;
        return diameter * diameter;
    }
}
