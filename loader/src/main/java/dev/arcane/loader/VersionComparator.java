package dev.arcane.loader;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compares the SemVer-shaped versions used for Arcane Loader releases. */
final class VersionComparator {
    private VersionComparator() {
    }

    static int compare(String left, String right) {
        ParsedVersion a = parse(left);
        ParsedVersion b = parse(right);
        int core = compareIdentifiers(a.core(), b.core(), true);
        if (core != 0) return core;
        if (a.preRelease().isEmpty() && b.preRelease().isEmpty()) return 0;
        if (a.preRelease().isEmpty()) return 1;
        if (b.preRelease().isEmpty()) return -1;
        return compareIdentifiers(a.preRelease(), b.preRelease(), false);
    }

    private static ParsedVersion parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Version is missing");
        String withoutBuild = value.split("\\+", 2)[0];
        String[] parts = withoutBuild.split("-", 2);
        return new ParsedVersion(identifiers(parts[0]), parts.length == 2 ? identifiers(parts[1]) : List.of());
    }

    private static List<String> identifiers(String value) {
        List<String> result = new ArrayList<>();
        for (String part : value.split("\\.")) {
            if (part.isBlank()) throw new IllegalArgumentException("Invalid version: " + value);
            result.add(part.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }

    private static int compareIdentifiers(List<String> left, List<String> right, boolean zeroFill) {
        int length = zeroFill ? Math.max(left.size(), right.size()) : Math.min(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            String a = index < left.size() ? left.get(index) : "0";
            String b = index < right.size() ? right.get(index) : "0";
            int compared = compareIdentifier(a, b);
            if (compared != 0) return compared;
        }
        return zeroFill ? 0 : Integer.compare(left.size(), right.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumber = left.chars().allMatch(Character::isDigit);
        boolean rightNumber = right.chars().allMatch(Character::isDigit);
        if (leftNumber && rightNumber) return new BigInteger(left).compareTo(new BigInteger(right));
        if (leftNumber != rightNumber) return leftNumber ? -1 : 1;
        return left.compareTo(right);
    }

    private record ParsedVersion(List<String> core, List<String> preRelease) {
    }
}
