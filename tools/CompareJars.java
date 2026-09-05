import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline comparison only: reads two archives, never executes or downloads either. */
public final class CompareJars {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java tools/CompareJars.java <rebuilt.jar> <downloaded.jar>");
            System.exit(2);
        }
        Map<String, String> left = contents(Files.readAllBytes(Path.of(args[0])), "", 0);
        Map<String, String> right = contents(Files.readAllBytes(Path.of(args[1])), "", 0);
        TreeSet<String> names = new TreeSet<>(left.keySet());
        names.addAll(right.keySet());
        int differences = 0;
        for (String name : names) {
            if (!java.util.Objects.equals(left.get(name), right.get(name))) {
                System.out.println("DIFFERENT: " + name);
                differences++;
            }
        }
        if (differences != 0) {
            System.err.println(differences + " differing/missing entries; investigate before trusting the download.");
            System.exit(1);
        }
        System.out.println("MATCH: " + names.size() + " file entries, including nested JARs. ZIP container metadata ignored.");
    }

    private static Map<String, String> contents(byte[] archive, String prefix, int depth) throws Exception {
        if (depth > 8) throw new IllegalArgumentException("Excessive nested JAR depth");
        TreeMap<String, String> result = new TreeMap<>();
        TreeSet<String> seen = new TreeSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!seen.add(entry.getName())) throw new IllegalArgumentException("Duplicate ZIP entry: " + prefix + entry.getName());
                if (entry.isDirectory()) continue;
                String name = prefix + entry.getName();
                byte[] bytes = zip.readAllBytes();
                if (entry.getName().endsWith(".jar")) {
                    for (var nested : contents(bytes, name + "!/", depth + 1).entrySet()) {
                        if (result.putIfAbsent(nested.getKey(), nested.getValue()) != null) {
                            throw new IllegalArgumentException("Ambiguous nested entry: " + nested.getKey());
                        }
                    }
                } else {
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    if (result.putIfAbsent(name, digest) != null) throw new IllegalArgumentException("Ambiguous ZIP entry: " + name);
                }
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Empty or invalid JAR: " + prefix);
        return result;
    }
}
