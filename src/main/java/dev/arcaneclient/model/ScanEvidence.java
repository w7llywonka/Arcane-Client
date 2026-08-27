package dev.arcaneclient.model;

import dev.arcaneclient.model.BlockPosition;
import dev.arcaneclient.model.SignalCategory;
import java.util.Comparator;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public record ScanEvidence(SignalCategory category, BlockPosition position, String reason, int strength, Kind kind, long observedAtTick, int decayTicks) {
    static final Comparator<ScanEvidence> ORDER = Comparator.<ScanEvidence>comparingInt(evidence -> evidence.category().ordinal()).thenComparing(ScanEvidence::reason).thenComparingInt(evidence -> evidence.position().x()).thenComparingInt(evidence -> evidence.position().y()).thenComparingInt(evidence -> evidence.position().z()).thenComparingInt(evidence -> evidence.kind().ordinal()).thenComparingInt(ScanEvidence::strength).thenComparingLong(ScanEvidence::observedAtTick).thenComparingInt(ScanEvidence::decayTicks);

    public ScanEvidence {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(kind, "kind");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (strength <= 0) {
            throw new IllegalArgumentException("strength must be positive");
        }
        if (kind == Kind.STATIC) {
            observedAtTick = 0L;
            decayTicks = 0;
        } else if (decayTicks <= 0) {
            throw new IllegalArgumentException("live evidence needs a positive decayTicks value");
        }
    }

    public static ScanEvidence staticSignal(SignalCategory category, BlockPosition position, String reason, int strength) {
        return new ScanEvidence(category, position, reason, strength, Kind.STATIC, 0L, 0);
    }

    public static ScanEvidence liveSignal(SignalCategory category, BlockPosition position, String reason, int strength, long observedAtTick, int decayTicks) {
        return new ScanEvidence(category, position, reason, strength, Kind.LIVE, observedAtTick, decayTicks);
    }

    public boolean isLive() {
        return this.kind == Kind.LIVE;
    }

    public int strengthAt(long tick) {
        long age;
        if (!this.isLive() || tick <= this.observedAtTick) {
            return this.strength;
        }
        try {
            age = Math.subtractExact(tick, this.observedAtTick);
        }
        catch (ArithmeticException ignored) {
            return 0;
        }
        if (age >= (long)this.decayTicks) {
            return 0;
        }
        long remainingTicks = (long)this.decayTicks - age;
        return (int)((long)this.strength * remainingTicks / (long)this.decayTicks);
    }

    EvidenceKey key() {
        return new EvidenceKey(this.category, this.position, this.reason);
    }

    ScanEvidence mergeDuplicate(ScanEvidence other) {
        if (!this.key().equals(other.key())) {
            throw new IllegalArgumentException("only matching evidence keys can be merged");
        }
        if (this.isLive() != other.isLive()) {
            return this.isLive() ? other : this;
        }
        if (!this.isLive()) {
            return ScanEvidence.staticSignal(this.category, this.position, this.reason, Math.max(this.strength, other.strength));
        }
        if (this.observedAtTick != other.observedAtTick) {
            return this.observedAtTick > other.observedAtTick ? this : other;
        }
        if (this.strength != other.strength) {
            return this.strength > other.strength ? this : other;
        }
        return this.decayTicks >= other.decayTicks ? this : other;
    }

    @Environment(value=EnvType.CLIENT)
    public static enum Kind {
        STATIC,
        LIVE;

    }

    @Environment(value=EnvType.CLIENT)
    record EvidenceKey(SignalCategory category, BlockPosition position, String reason) {
    }
}
