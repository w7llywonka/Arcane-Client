package dev.arcaneclient.additions.configlibrary;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.config.AtomicConfigFile;
import dev.arcaneclient.screen.ConfigLibraryScreen;
import dev.arcaneclient.screen.GuiModule;
import dev.arcaneclient.screen.GuiSetting;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Local, review-before-apply profiles. There is deliberately no cloud or community endpoint. */
public final class ConfigLibrary {
    public static final int MAX_BYTES = 256 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BACKUP = "last-apply-backup";
    private static final Set<String> ENVELOPE = Set.of("format", "version", "name", "includeUi", "settings");
    private static final Set<String> PERSONAL = Set.of("social", "mediaHud", "preview", "presence", "configLibrary",
        "chatMacro1", "chatMacro2", "chatMacro3", "chatMacro4", "streamerMode", "cleanCapture",
        "welcomeNoticeVersion", "configVersion", "devUiRevision", "intelAdditions.espSettingsVersion",
        "intelAdditions.range", "intelAdditions.tracers");
    private static final Set<String> AUTOMATION = Set.of("autoTotem", "autoSprint", "autoEat", "autoArmor",
        "smartWeapon", "triggerBot", "hotbarRefill", "spearSwitch", "maceSwitch", "safetyDisconnect", "autoTool",
        "elytraAssist", "autoRespawn", "antiAfk", "autoWalk", "autoJump", "autoSneak", "inventoryMove",
        "safeWalk", "parkourAssist", "swimAssist", "vehicleCruise", "autoFish", "chatMacros", "coordinateClipboard",
        "freecamMining", "combatAdditions.aimAssist", "combatAdditions.autoClicker", "combatAdditions.autoCrystal",
        "combatAdditions.shieldBreaker", "combatAdditions.maceBomber", "combatAdditions.anchorMacro",
        "combatAdditions.doubleAnchor", "combatAdditions.clickerIgnoreCooldown", "utilityAdditions.hoverTotem",
        "utilityAdditions.inventoryTotem", "utilityAdditions.fastUse", "social.autoTpa", "social.sendRequests",
        "dispenser.enabled");
    private static Consumer<Minecraft> applyHook = client -> { };

    public record Profile(String name) { }
    public record Change(String field, String before, String after) { }
    public static final class Review {
        private final JsonObject baseline;
        private final ArcaneConfig candidate;
        private final List<Change> changes;
        private final String name;
        private Review(String name, JsonObject baseline, ArcaneConfig candidate, List<Change> changes) {
            this.name = name; this.baseline = baseline; this.candidate = candidate; this.changes = List.copyOf(changes);
        }
        public String name() { return name; }
        public List<Change> changes() { return changes; }
    }

    private ConfigLibrary() { }

    public static void registerApplyHook(Consumer<Minecraft> hook) {
        applyHook = Objects.requireNonNull(hook);
    }

    public static List<GuiModule> modules(ArcaneConfig config, Minecraft client) {
        return List.of(GuiModule.value("Config Library", "Save local presets, review changes before loading, and share JSON files without personal data. Automation stays off after loading.", () -> "Local")
            .with(new GuiSetting.Cycle("Open library", () -> "Open", () -> client.gui.setScreen(new ConfigLibraryScreen(client.gui.screen(), config))))
            .with(new GuiSetting.Info("Storage", () -> "config/arcane/profiles"))
            .with(new GuiSetting.Info("Sharing", () -> "Local JSON; no account or upload"))
            .build());
    }

    public static Path directory() throws IOException {
        Path root = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
        Files.createDirectories(root);
        root = root.toRealPath();
        return childDirectory(childDirectory(root, "arcane"), "profiles");
    }

    private static Path childDirectory(Path parent, String name) throws IOException {
        Path child = parent.resolve(name);
        if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(child);
        if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
            || !child.toRealPath().equals(child)) throw new IOException("Profile folders must be real local directories.");
        return child;
    }

    public static Path importsDirectory() throws IOException { return childDirectory(directory(), "imports"); }
    private static Path exportsDirectory() throws IOException { return childDirectory(directory(), "exports"); }

    public static List<Profile> profiles(boolean imports) throws IOException {
        Path folder = imports ? importsDirectory() : directory();
        ArrayList<Profile> result = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.json")) {
            int examined = 0;
            for (Path file : files) {
                if (++examined > 512) throw new IOException("Too many JSON files in this folder (maximum 512).");
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - 5);
                if (name.equalsIgnoreCase(BACKUP) || !safeName(name)) continue;
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) result.add(new Profile(name));
            }
        }
        result.sort(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static boolean safeName(String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,47}") && name.equals(name.strip())
            && !name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");
    }

    public static String normalizeName(String input) throws IOException {
        String name = input == null ? "" : input.strip();
        if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
        if (!safeName(name) || name.equalsIgnoreCase(BACKUP)) {
            throw new IOException("Use 1–48 letters, numbers, spaces, hyphens or underscores; no paths.");
        }
        return name;
    }

    private static Path file(Path folder, String name, boolean backup) throws IOException {
        String clean = backup ? BACKUP : normalizeName(name);
        Path path = folder.resolve(clean + ".json").normalize();
        if (!path.getParent().equals(folder)) throw new IOException("Profile path is outside its folder.");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
            && (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().getParent().equals(folder))) throw new IOException("Profile links and special files are not allowed.");
        return path;
    }

    public static void save(String name, ArcaneConfig config, boolean includeUi) throws IOException {
        String clean = normalizeName(name);
        writeNew(file(directory(), clean, false), envelope(clean, capture(config, includeUi), includeUi));
    }

    public static void rename(String oldName, String newName) throws IOException {
        String clean = normalizeName(newName);
        Path previous = file(directory(), oldName, false);
        JsonObject contents = read(previous);
        contents.addProperty("name", clean);
        Path destination = file(directory(), clean, false);
        if (previous.equals(destination)) return;
        writeNew(destination, contents);
        try { Files.delete(previous); }
        catch (IOException failure) { throw new IOException("New name saved, but the old file could not be removed.", failure); }
    }

    public static void delete(String name) throws IOException {
        Files.delete(file(directory(), name, false));
    }

    public static String export(String name, boolean includeUi) throws IOException {
        JsonObject contents = read(file(directory(), name, false));
        JsonObject settings = contents.getAsJsonObject("settings").deepCopy();
        removeExcluded(settings, "", includeUi);
        contents.add("settings", settings);
        contents.addProperty("includeUi", includeUi);
        String exportName = normalizeName(name);
        Path folder = exportsDirectory();
        for (int i = 0; i < 1000; i++) {
            String suffix = i == 0 ? "-share" : "-share-" + i;
            String candidate = exportName.substring(0, Math.min(exportName.length(), 48 - suffix.length())) + suffix;
            Path target = file(folder, candidate, false);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
            contents.addProperty("name", candidate);
            writeNew(target, contents);
            return "exports/" + candidate + ".json";
        }
        throw new IOException("Too many exports with this name.");
    }

    public static String importFile(String incoming, String name) throws IOException {
        String clean = normalizeName(name);
        JsonObject contents = read(file(importsDirectory(), incoming, false));
        contents.addProperty("name", clean);
        writeNew(file(directory(), clean, false), contents);
        return clean;
    }

    public static boolean hasBackup() {
        try { return Files.isRegularFile(file(directory(), BACKUP, true), LinkOption.NOFOLLOW_LINKS); }
        catch (IOException failure) { return false; }
    }

    public static Review review(String name, ArcaneConfig config, boolean includeUi) throws IOException {
        return reviewDocument(name, read(file(directory(), name, false)), config, includeUi);
    }

    public static Review reviewBackup(ArcaneConfig config) throws IOException {
        return reviewDocument("Revert last apply", read(file(directory(), BACKUP, true)), config, true);
    }

    private static Review reviewDocument(String name, JsonObject document, ArcaneConfig config, boolean includeUi) throws IOException {
        JsonObject baseline = GSON.toJsonTree(config).getAsJsonObject();
        JsonObject merged = baseline.deepCopy();
        JsonObject incoming = document.getAsJsonObject("settings").deepCopy();
        removeExcluded(incoming, "", includeUi);
        merge(merged, incoming);
        disableAutomation(merged, "");
        ArcaneConfig candidate;
        try {
            candidate = GSON.fromJson(merged, ArcaneConfig.class);
            candidate.sanitize();
        } catch (RuntimeException invalid) { throw new IOException("This profile contains unsupported setting values.", invalid); }
        // Preserve current migration state; profiles never trigger legacy range/consent migrations.
        JsonObject before = capture(config, true);
        JsonObject after = capture(candidate, true);
        ArrayList<Change> changes = new ArrayList<>();
        changes(before, after, "", changes);
        // Social settings are intentionally omitted from shareable documents, but disabling
        // any existing request automation must still be visible in the review.
        if (config.social.autoTpa != candidate.social.autoTpa) changes.add(new Change("social.autoTpa", "ON", "OFF"));
        if (config.social.sendRequests != candidate.social.sendRequests) changes.add(new Change("social.sendRequests", "ON", "OFF"));
        return new Review(name, baseline, candidate, changes);
    }

    public static void apply(Review review, ArcaneConfig config, Minecraft client) throws IOException {
        if (!GSON.toJsonTree(config).equals(review.baseline)) throw new IOException("Settings changed during review. Open the review again.");
        JsonObject backup = envelope("Before last apply", capture(config, true), true);
        Path backupFile = file(directory(), BACKUP, true);
        writeBackup(backupFile, backup);
        Path live = FabricLoader.getInstance().getConfigDir().resolve("arcane-client.json");
        AtomicConfigFile.write(live, GSON.toJson(review.candidate));
        try {
            copyInto(config, review.candidate);
            applyHook.accept(client);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            ArcaneConfig original = GSON.fromJson(review.baseline, ArcaneConfig.class);
            try { copyInto(config, original); applyHook.accept(client); }
            catch (ReflectiveOperationException | RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            AtomicConfigFile.write(live, GSON.toJson(original));
            throw new IOException("Could not apply this profile; previous settings were restored.", failure);
        }
    }

    private static void copyInto(Object destination, Object source) throws ReflectiveOperationException {
        for (Field field : fields(source.getClass()).values()) {
            Object value = field.get(source), old = field.get(destination);
            if (value != null && old != null && configObject(field.getType())) copyInto(old, value);
            else field.set(destination, value);
        }
    }

    private static boolean configObject(Class<?> type) {
        return type.getName().startsWith("dev.arcaneclient.") && !type.isRecord() && !type.isEnum();
    }

    private static JsonObject capture(ArcaneConfig config, boolean includeUi) {
        JsonObject value = GSON.toJsonTree(config).getAsJsonObject();
        removeExcluded(value, "", includeUi);
        return value;
    }

    private static boolean excluded(String path, boolean includeUi) {
        if (PERSONAL.contains(path)) return true;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(credential|password|secret|token|apikey|consent|presence|discord|trusted|recipient|endpoint|serveraddress|waypointdata|coordinatehistory).*")) return true;
        if (!includeUi && (path.startsWith("ui") || path.equals("customUiColors") || path.equals("hudScalePercent") || path.equals("hudAnchor"))) return true;
        return false;
    }

    private static void removeExcluded(JsonObject object, String prefix, boolean includeUi) {
        for (String key : new ArrayList<>(object.keySet())) {
            String path = prefix + key;
            if (excluded(path, includeUi)) object.remove(key);
            else if (object.get(key).isJsonObject()) removeExcluded(object.getAsJsonObject(key), path + ".", includeUi);
        }
    }

    private static void disableAutomation(JsonObject object, String prefix) {
        for (var entry : object.entrySet()) {
            String path = prefix + entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) disableAutomation(value.getAsJsonObject(), path + ".");
            else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && AUTOMATION.contains(path)) {
                entry.setValue(new JsonPrimitive(false));
            }
        }
    }

    private static void merge(JsonObject destination, JsonObject source) {
        for (var entry : source.entrySet()) {
            if (entry.getValue().isJsonObject() && destination.has(entry.getKey()) && destination.get(entry.getKey()).isJsonObject()
                && !entry.getKey().equals("uiPanelLayout")) merge(destination.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            else destination.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private static void changes(JsonObject before, JsonObject after, String prefix, List<Change> changes) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String path = prefix + key;
            JsonElement old = before.get(key), value = after.get(key);
            if (Objects.equals(old, value)) continue;
            if (old != null && value != null && old.isJsonObject() && value.isJsonObject()) changes(old.getAsJsonObject(), value.getAsJsonObject(), path + ".", changes);
            else if (old != null && value != null && old.isJsonArray() && value.isJsonArray()) {
                Set<JsonElement> removed = new LinkedHashSet<>(old.getAsJsonArray().asList());
                Set<JsonElement> added = new LinkedHashSet<>(value.getAsJsonArray().asList());
                removed.removeAll(value.getAsJsonArray().asList());
                added.removeAll(old.getAsJsonArray().asList());
                changes.add(new Change(path, selectionChange("removed", removed),
                    removed.isEmpty() && added.isEmpty() ? "Order changed" : selectionChange("added", added)));
            }
            else changes.add(new Change(path, display(old), display(value)));
        }
    }

    private static String selectionChange(String action, Set<JsonElement> values) {
        StringJoiner sample = new StringJoiner(", ");
        values.stream().limit(2).forEach(value -> sample.add(value.getAsString()));
        return values.size() + " " + action + (values.isEmpty() ? "" : ": " + sample + (values.size() > 2 ? ", ..." : ""));
    }

    private static String display(JsonElement value) {
        if (value == null || value.isJsonNull()) return "unset";
        if (value.isJsonArray()) {
            JsonArray values = value.getAsJsonArray();
            StringJoiner sample = new StringJoiner(", ");
            for (int i = 0; i < Math.min(2, values.size()); i++) sample.add(values.get(i).getAsString());
            return values.size() + " entries" + (values.isEmpty() ? "" : ": " + sample + (values.size() > 2 ? ", ..." : ""));
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("x") && object.has("y")) return "Panel at " + object.get("x") + ", " + object.get("y");
            return object.size() + " settings";
        }
        if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean() ? "ON" : "OFF";
        String text = value.getAsString();
        return text.length() > 64 ? text.substring(0, 61) + "..." : text;
    }

    private static JsonObject envelope(String name, JsonObject settings, boolean includeUi) {
        JsonObject document = new JsonObject();
        document.addProperty("format", "arcane-profile"); document.addProperty("version", 1);
        document.addProperty("name", name); document.addProperty("includeUi", includeUi); document.add("settings", settings);
        return document;
    }

    private static byte[] bytes(JsonObject contents) throws IOException {
        byte[] value = GSON.toJson(contents).getBytes(StandardCharsets.UTF_8);
        if (value.length > MAX_BYTES) throw new IOException("Profile is larger than 256 KiB.");
        return value;
    }

    private static void writeNew(Path path, JsonObject contents) throws IOException {
        byte[] value = bytes(contents);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("That name already exists. Choose a new name.");
        Path temporary = Files.createTempFile(path.getParent(), "arcane-profile-", ".tmp");
        try {
            Files.write(temporary, value, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            Files.move(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void writeBackup(Path path, JsonObject contents) throws IOException {
        bytes(contents);
        AtomicConfigFile.write(path, GSON.toJson(contents));
    }

    private static JsonObject read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Profile is larger than 256 KiB.");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Profile is larger than 256 KiB.");
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setLenient(false);
            JsonElement parsed = readValue(reader, 0, new int[]{0});
            if (reader.peek() != JsonToken.END_DOCUMENT || !parsed.isJsonObject()) throw new IOException("Expected one JSON profile object.");
            JsonObject document = parsed.getAsJsonObject();
            if (!document.keySet().equals(ENVELOPE)) throw new IOException("Unknown or missing profile header fields.");
            if (!string(document.get("format")) || !document.get("format").getAsString().equals("arcane-profile")) throw new IOException("This is not an Arcane profile.");
            validate(document.get("version"), int.class, "version");
            if (document.get("version").getAsInt() != 1) throw new IOException("Unsupported profile version.");
            if (!string(document.get("name")) || !safeName(document.get("name").getAsString())) throw new IOException("Invalid profile name.");
            validate(document.get("includeUi"), boolean.class, "includeUi");
            validate(document.get("settings"), ArcaneConfig.class, "");
            return document;
        } catch (IllegalStateException | IllegalArgumentException invalid) { throw new IOException("Invalid JSON profile.", invalid); }
    }

    private static JsonElement readValue(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 12 || ++nodes[0] > 24000) throw new IOException("Profile JSON is too complex.");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.length() > 96 || object.has(name)) throw new IOException("Duplicate or oversized JSON key.");
                    object.add(name, readValue(reader, depth + 1, nodes));
                }
                reader.endObject(); return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= 8192) throw new IOException("Profile list is too large.");
                    array.add(readValue(reader, depth + 1, nodes));
                }
                reader.endArray(); return array;
            }
            case STRING -> { return new JsonPrimitive(reader.nextString()); }
            case BOOLEAN -> { return new JsonPrimitive(reader.nextBoolean()); }
            case NUMBER -> {
                String value = reader.nextString();
                if (value.length() > 32) throw new IOException("Profile number is too large.");
                return new JsonPrimitive(new BigDecimal(value));
            }
            case NULL -> { reader.nextNull(); return JsonNull.INSTANCE; }
            default -> throw new IOException("Invalid JSON value.");
        }
    }

    private static boolean string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static Map<String, Field> fields(Class<?> type) {
        LinkedHashMap<String, Field> result = new LinkedHashMap<>();
        for (Field field : type.getFields()) if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) result.put(field.getName(), field);
        return result;
    }

    private static void validate(JsonElement value, Type type, String path) throws IOException {
        if (value == null || value.isJsonNull()) throw new IOException("Null is not allowed: " + path);
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if (Collection.class.isAssignableFrom(raw)) {
                if (!value.isJsonArray()) throw new IOException("Expected a list: " + path);
                if (value.getAsJsonArray().size() > (path.equals("uiFavorites") ? 128 : 8192)) throw new IOException("Too many entries: " + path);
                for (JsonElement entry : value.getAsJsonArray()) validate(entry, arguments[0], path + "[]");
                return;
            }
            if (Map.class.isAssignableFrom(raw)) {
                if (!value.isJsonObject() || value.getAsJsonObject().size() > 64) throw new IOException("Invalid panel layout.");
                for (var entry : value.getAsJsonObject().entrySet()) {
                    if (entry.getKey().isBlank() || entry.getKey().chars().anyMatch(Character::isISOControl)) throw new IOException("Invalid panel name.");
                    validate(entry.getValue(), arguments[1], path + "." + entry.getKey());
                }
                return;
            }
        }
        if (!(type instanceof Class<?> raw)) throw new IOException("Unsupported setting type: " + path);
        if (raw == boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IOException("Expected true or false: " + path);
        } else if (raw == int.class) {
            try {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
                int number = value.getAsBigDecimal().intValueExact();
                if (path.startsWith("uiPanelLayout.")) {
                    String component = path.substring(path.lastIndexOf('.') + 1);
                    int minimum = component.equals("x") || component.equals("y") ? -16384 : 0;
                    int maximum = component.equals("order") ? 128 : component.equals("scroll") ? 100000 : 16384;
                    if (number < minimum || number > maximum) throw new ArithmeticException();
                }
            } catch (ArithmeticException invalid) { throw new IOException("Expected a whole 32-bit number: " + path); }
        } else if (raw == String.class) {
            if (!string(value) || value.getAsString().length() > 256 || value.getAsString().chars().anyMatch(Character::isISOControl)) throw new IOException("Invalid text: " + path);
            if ((path.equals("itemEspItems[]") || path.equals("intelAdditions.blocks[]"))
                && !value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) throw new IOException("Invalid registry ID: " + path);
        } else {
            if (!value.isJsonObject()) throw new IOException("Expected settings object: " + path);
            Map<String, Type> known = new LinkedHashMap<>();
            if (raw.isRecord()) for (RecordComponent component : raw.getRecordComponents()) known.put(component.getName(), component.getGenericType());
            else for (var entry : fields(raw).entrySet()) known.put(entry.getKey(), entry.getValue().getGenericType());
            for (var entry : value.getAsJsonObject().entrySet()) {
                String child = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                if (!known.containsKey(entry.getKey())) throw new IOException("Unknown setting: " + child);
                if (excluded(child, true)) throw new IOException("Personal or migration settings cannot be imported: " + child);
                validate(entry.getValue(), known.get(entry.getKey()), child);
            }
            if (raw.isRecord() && !value.getAsJsonObject().keySet().equals(known.keySet())) throw new IOException("Incomplete panel layout.");
        }
    }
}
