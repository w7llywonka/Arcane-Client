package dev.arcane.loader;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;

/** Downloads only a newer signed outer loader and stages it outside the mods directory. */
final class OuterLoaderUpdater {
    static final long MAX_LOADER_BYTES = 64L * 1024L * 1024L;

    private final LoaderState state;
    private final PublicKey publicKey;
    private final ArtifactDownloader downloader;
    private final UpdateScheduler scheduler;

    OuterLoaderUpdater(LoaderState state) throws Exception {
        this(state, releasePublicKey(), ArtifactDownloader.http(),
            new DetachedUpdateScheduler(state.directory()));
    }

    OuterLoaderUpdater(LoaderState state, PublicKey publicKey, ArtifactDownloader downloader,
                       UpdateScheduler scheduler) {
        this.state = state;
        this.publicKey = publicKey;
        this.downloader = downloader;
        this.scheduler = scheduler;
    }

    LoaderUpdateResult checkAndStage(ReleaseManifest release, String currentVersion,
                                     Path runningLoader) throws Exception {
        if (!"1.21.11".equals(release.minecraftVersion())) {
            throw new IllegalStateException("Release targets Minecraft " + release.minecraftVersion());
        }
        UpdatePlan plan = UpdatePlanner.plan(state.directory(), runningLoader, currentVersion, release);
        if (!plan.updateAvailable()) {
            // Critical invariant: do not create, copy, delete, or rename anything in mods.
            return new LoaderUpdateResult(LoaderUpdateResult.Status.CURRENT, currentVersion,
                plan.runningLoader());
        }
        if (release.size() > MAX_LOADER_BYTES) throw new SecurityException("Loader release is too large");

        Files.createDirectories(plan.stagedLoader().getParent());
        if (!isVerifiedRelease(plan.stagedLoader(), release)) {
            Path temporary = Files.createTempFile(state.directory(), "arcane-loader-download-", ".jar.tmp");
            try {
                URI uri = ReleaseManifestClient.checkedUri(release.downloadUrl());
                downloader.download(uri, temporary, MAX_LOADER_BYTES);
                verifyRelease(temporary, release);
                moveReplace(temporary, plan.stagedLoader());
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        scheduler.schedule(plan, release);
        return new LoaderUpdateResult(LoaderUpdateResult.Status.STAGED, release.version(),
            plan.stagedLoader());
    }

    private boolean isVerifiedRelease(Path artifact, ReleaseManifest release) {
        if (!Files.isRegularFile(artifact)) return false;
        try {
            verifyRelease(artifact, release);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void verifyRelease(Path artifact, ReleaseManifest release) throws Exception {
        if (Files.size(artifact) != release.size()) {
            throw new SecurityException("Downloaded loader size does not match the release manifest");
        }
        ArtifactVerifier.verify(artifact, release.sha256(), release.signature(), publicKey);
        LoaderArtifactValidator.validate(artifact, release);
    }

    private static PublicKey releasePublicKey() throws Exception {
        try (InputStream input = OuterLoaderUpdater.class.getResourceAsStream("/release-public.pem")) {
            if (input == null) throw new IllegalStateException("Missing release verification key");
            return PemKeys.readPublic(new String(input.readAllBytes(),
                java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    private static void moveReplace(Path source, Path destination) throws Exception {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
