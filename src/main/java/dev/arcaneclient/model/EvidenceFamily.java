package dev.arcaneclient.model;

import java.util.Locale;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Independent evidence families used to prevent one pattern from inflating several score categories. */
@Environment(EnvType.CLIENT)
public enum EvidenceFamily {
    GROWTH(180),
    FARM_GEOMETRY(220),
    HARVEST(240),
    AUTOMATION(220),
    MANAGED_HABITAT(220),
    AMETHYST_ACTIVITY(220),
    ACCESS_TRAIL(200),
    PLAYER_PLACEMENT(200),
    LIGHTING(140),
    EXCAVATION(180),
    INFRASTRUCTURE(180),
    OTHER(160);

    private final int rawStrengthCap;

    EvidenceFamily(int rawStrengthCap) {
        this.rawStrengthCap = rawStrengthCap;
    }

    public int rawStrengthCap() {
        return this.rawStrengthCap;
    }

    public static EvidenceFamily infer(SignalCategory category, String reason) {
        Objects.requireNonNull(category, "category");
        String normalized = Objects.requireNonNull(reason, "reason").toLowerCase(Locale.ROOT);
        if (normalized.contains("amethyst") && !normalized.contains("stripped") && !normalized.contains("shell")) {
            return AMETHYST_ACTIVITY;
        }
        if (containsAny(normalized, "cobbled-deepslate access", "descending cobbled")) {
            return ACCESS_TRAIL;
        }
        if (containsAny(normalized, "harvest", "crop reset", "plant removed", "berries removed")) {
            return HARVEST;
        }
        if (containsAny(normalized, "aligned crop", "rectangular crop", "farmland", "farm column", "farm geometry",
            "cultivated", "crop row", "synchronized crop", "dripstone above")) {
            return FARM_GEOMETRY;
        }
        if (containsAny(normalized, "state change", "automation", "redstone", "block event", "piston", "note block",
            "functional block", "powered cadence")) {
            return AUTOMATION;
        }
        if (category == SignalCategory.ENTITY || containsAny(normalized, "tamed", "leashed", "equipped", "animal",
            "villager", "managed habitat", "custom-named entit", "player-created entit")) {
            return MANAGED_HABITAT;
        }
        if (category == SignalCategory.LIGHT_LEAK || normalized.contains("light")) {
            return LIGHTING;
        }
        if (containsAny(normalized, "deepslate", "geode", "excavat", "tunnel")) {
            return EXCAVATION;
        }
        if (category == SignalCategory.PLACED_BLOCK || containsAny(normalized, "placed fingerprint", "persistent leaves")) {
            return PLAYER_PLACEMENT;
        }
        if (category == SignalCategory.NATURAL_GROWTH || containsAny(normalized, "growth", "grew", "matured")) {
            return GROWTH;
        }
        if (category == SignalCategory.INFRASTRUCTURE || category == SignalCategory.BLOCK_ENTITY) {
            return INFRASTRUCTURE;
        }
        return OTHER;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
