package dev.arcane.loader;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

/** Fabric entrypoint for the same signed Arcane loader used by the desktop UI. */
public final class ArcaneFabricLoader implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader fabric = FabricLoader.getInstance();
        ModContainer loader = fabric.getModContainer("arcaneloader").orElseThrow();
        String currentVersion = loader.getMetadata().getVersion().getFriendlyString();
        Path runningLoader;
        try {
            runningLoader = selectedOuterJar(loader);
        } catch (Exception unsafeOrigin) {
            System.err.println("[ARCLoader] Automatic updates disabled: " + unsafeOrigin.getMessage());
            return;
        }
        Set<Path> selectedOrigins = new HashSet<>();
        selectedOrigins.add(runningLoader);
        fabric.getModContainer("arcaneclient").ifPresent(client ->
            addSelectedPathOrigins(selectedOrigins, client.getOrigin()));
        Path modsDirectory = fabric.getGameDir().resolve("mods").toAbsolutePath().normalize();
        Thread update = new Thread(
            () -> checkForLoaderUpdate(runningLoader, currentVersion, modsDirectory,
                Set.copyOf(selectedOrigins)),
            "Arcane signed update check"
        );
        update.setDaemon(true);
        update.start();
    }

    private static void checkForLoaderUpdate(Path runningLoader, String currentVersion,
                                             Path modsDirectory, Set<Path> selectedOrigins) {
        try {
            LoaderState state = LoaderState.load();
            var redundant = LegacyModCleanup.findRedundant(modsDirectory, selectedOrigins);
            if (!redundant.isEmpty()) {
                new DetachedCleanupScheduler(state.directory()).schedule(redundant);
                System.out.println("[ARCLoader] " + redundant.size()
                    + " redundant legacy Arcane JAR(s) will be moved out of mods after exit.");
            }
            ReleaseManifest release = ArcaneLoader.fetchRelease();
            LoaderUpdateResult result = new OuterLoaderUpdater(state)
                .checkAndStage(release, currentVersion, runningLoader);
            if (result.status() == LoaderUpdateResult.Status.STAGED) {
                System.out.println("[ARCLoader] Signed loader " + result.version()
                    + " is staged outside mods and will replace this loader after Minecraft exits.");
            } else {
                System.out.println("[ARCLoader] Loader " + currentVersion
                    + " is current; mods folder was not changed.");
            }
        } catch (Exception error) {
            System.err.println("[ARCLoader] Signed loader update failed: " + error.getMessage());
        }
    }

    static Path selectedOuterJar(ModContainer container) throws Exception {
        if (container.getOrigin().getKind() != ModOrigin.Kind.PATH) {
            throw new IllegalStateException("selected loader origin is not a filesystem path");
        }
        var paths = container.getOrigin().getPaths();
        if (paths.size() != 1) {
            throw new IllegalStateException("selected loader origin is not one file");
        }
        Path candidate = paths.getFirst().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(candidate)
            || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
            || !candidate.getFileName().toString().toLowerCase().endsWith(".jar")) {
            throw new IllegalStateException("selected loader origin is not a regular JAR");
        }
        return candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    static void addSelectedPathOrigins(Set<Path> selectedOrigins, ModOrigin origin) {
        if (origin.getKind() != ModOrigin.Kind.PATH) return;
        origin.getPaths().forEach(path ->
            selectedOrigins.add(path.toAbsolutePath().normalize()));
    }
}
