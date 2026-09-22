package dev.arcaneclient.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.arcaneclient.ArcaneClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;

/** Small, server-and-dimension-scoped persistent waypoint store. */
@Environment(EnvType.CLIENT)
public final class WaypointStore {
    private static final int MAX_WAYPOINTS = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Waypoint> waypoints;
    private static Scope cachedScope;
    private static List<Waypoint> cachedCurrent = List.of();

    private WaypointStore() {
    }

    public static synchronized Waypoint add(Minecraft client, String requestedName, BlockPos pos) {
        ensureLoaded();
        Scope scope = scope(client);
        String name = cleanName(requestedName);
        waypoints.removeIf(point -> point.sameScope(scope) && point.name.equalsIgnoreCase(name));
        Waypoint added = new Waypoint(scope.server, scope.dimension, name, pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
        waypoints.add(added);
        while (waypoints.size() > MAX_WAYPOINTS) {
            Waypoint oldest = waypoints.stream().min(Comparator.comparingLong(Waypoint::createdMillis)).orElse(waypoints.getFirst());
            waypoints.remove(oldest);
        }
        invalidateCurrent();
        save();
        return added;
    }

    public static synchronized boolean remove(Minecraft client, String requestedName) {
        ensureLoaded();
        Scope scope = scope(client);
        String name = cleanName(requestedName);
        boolean removed = waypoints.removeIf(point -> point.sameScope(scope) && point.name.equalsIgnoreCase(name));
        if (removed) {
            invalidateCurrent();
            save();
        }
        return removed;
    }

    public static synchronized List<Waypoint> current(Minecraft client) {
        ensureLoaded();
        Scope scope = scope(client);
        if (!scope.equals(cachedScope)) {
            cachedScope = scope;
            cachedCurrent = waypoints.stream().filter(point -> point.sameScope(scope))
                .sorted(Comparator.comparingLong(Waypoint::createdMillis)).toList();
        }
        return cachedCurrent;
    }

    public static String cleanName(String value) {
        String cleaned = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) cleaned = "Waypoint";
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static void ensureLoaded() {
        if (waypoints != null) return;
        Path path = storagePath();
        if (!Files.isRegularFile(path)) {
            waypoints = new ArrayList<>();
            return;
        }
        try {
            WaypointFile file = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), WaypointFile.class);
            waypoints = new ArrayList<>(file == null || file.waypoints == null ? List.of() : file.waypoints);
            waypoints.removeIf(point -> point == null || point.name == null || point.server == null || point.dimension == null);
        } catch (IOException | JsonSyntaxException exception) {
            ArcaneClient.LOGGER.warn("Could not read Arcane waypoints; starting with an empty store", exception);
            waypoints = new ArrayList<>();
        }
    }

    private static void save() {
        try {
            Path path = storagePath();
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(new WaypointFile(1, waypoints)), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            ArcaneClient.LOGGER.warn("Could not save Arcane waypoints", exception);
        }
    }

    private static void invalidateCurrent() {
        cachedScope = null;
        cachedCurrent = List.of();
    }

    private static Path storagePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("arcaneclient-waypoints.json");
    }

    private static Scope scope(Minecraft client) {
        String server;
        if (client.getCurrentServer() != null) {
            server = client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        } else {
            IntegratedServer integratedServer = client.getSingleplayerServer();
            server = integratedServer == null
                ? "singleplayer:unknown"
                : singleplayerIdentity(
                    integratedServer.getWorldData().getLevelName(),
                    integratedServer.getWorldPath(LevelResource.ROOT)
                );
        }
        String dimension = client.level == null ? "unknown" : client.level.dimension().identifier().toString();
        return new Scope(server, dimension);
    }

    static String singleplayerIdentity(String levelName, Path saveRoot) {
        String level = levelName == null || levelName.isBlank()
            ? "unknown"
            : levelName.strip().toLowerCase(Locale.ROOT);
        Path normalizedRoot = saveRoot == null ? null : saveRoot.toAbsolutePath().normalize();
        Path folder = normalizedRoot == null ? null : normalizedRoot.getFileName();
        String save = folder == null ? "unknown" : folder.toString().toLowerCase(Locale.ROOT);
        return "singleplayer:" + level + "@" + save;
    }

    public record Waypoint(String server, String dimension, String name, int x, int y, int z, long createdMillis) {
        private boolean sameScope(Scope scope) {
            return server.equals(scope.server) && dimension.equals(scope.dimension);
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private record Scope(String server, String dimension) {
    }

    private record WaypointFile(int version, List<Waypoint> waypoints) {
    }
}
