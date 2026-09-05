# Security and release verification

## Scope

This repository provides inspectable source, not an independent security certification. A valid release signature identifies the holder of the release signing key; it does not establish that code is harmless. Source-to-binary comparison and human review are separate checks.

Public release: client **2.9.2+mc1.21.11**, loader **1.2.3+mc1.21.11**. The gameplay implementation comes from DEV 10; public branding replaces the DEV label. The private DEV bootstrap, pairing/session state, administrator API and bot credentials are not in the shipped public loader.

## Behavior worth reviewing

| Area | Source | Behavior |
| --- | --- | --- |
| Update requests | `loader/src/main/java/dev/arcane/loader/ArcaneLoader.java`, `ReleaseManifestClient.java`, `ArtifactDownloader.java` | Fetches HTTPS release metadata from www.arcaneclient.shop, falling back to arcane-client-puce.vercel.app; downloads the artifact URL supplied by that manifest. HTTP redirects are followed. An optional JVM manifest-URL override exists for testing. |
| Integrity | `ArtifactVerifier.java`, `LoaderArtifactValidator.java`, `OuterLoaderUpdater.java` | Checks SHA-256, Ed25519 signature, size, outer Fabric metadata and nested-client layout before staging an update. |
| Local loader state | `LoaderState.java`, `UpdatePlanner.java` | Uses Windows APPDATA/Arcane Loader or the user's .arcane-loader directory for state, staged files, backups and helper files. |
| Separate process | `DetachedUpdateScheduler.java`, `DetachedUpdater.java` | Starts a small Java helper during Minecraft shutdown. It waits for the game process to exit, verifies the update again, backs up and replaces the selected loader file. This is executable update behavior; inspect it rather than assuming all process launches are malicious or harmless. |
| Old duplicate mods | `LegacyModCleanup.java`, `DetachedCleanupScheduler.java` | Identifies redundant Arcane JARs and schedules recoverable movement out of mods after exit; selected mod origins are preserved. |
| Manual installer | `SingleFileInstaller.java` | Installs the running outer loader in the selected mods directory. |
| Client files | `ArcaneConfig.java`, `config/AtomicConfigFile.java`, `render/WaypointStore.java` | Saves client configuration and waypoint data locally. Config and waypoint files may contain private preferences, server identities or coordinates; do not upload your instance folder. |
| Minecraft connections | `utility/RelogController.java` and ordinary module/network hooks | Uses the existing Minecraft session and vanilla disconnect/connect APIs. Relog does not send a /rejoin command or require a companion mod. |
| Website link | `screen/ArcaneWelcomeScreen.java` | Opens the official website through the welcome-screen link. |

The loader can install future signed executable code. If you want to review and pin a version without the loader's automatic-update path, build and install the standalone client JAR instead. Do not install it alongside ARCLoader.

## Release fingerprints

Official public artifacts published on 2026-09-05:

| Artifact | SHA-256 |
| --- | --- |
| Arcane Client 2.9.2 | `3d16ad989e7ab7c3114f4c4e505c11fd4e5af4a70c5556917c7a0f34a0bb10ef` |
| ARCLoader 1.2.3 | `392b7cc15fe047057ecf00c24307e25766ef2e6b4da112b6bed9c237c9b75b6a` |

The public verification key is committed in `loader/src/main/resources/release-public.pem`. The private key is intentionally absent. These fingerprints describe that specific release, not future updates.

## Verify your build

1. Check out the source commit you intend to review.
2. Review build scripts and dependencies as well as Java code.
3. Run the client build, then the loader build as described in the root README.
4. Download the official artifact separately.
5. Run `java tools/CompareJars.java <rebuilt.jar> <downloaded.jar>`.

The comparison checks names and bytes for every non-directory entry, including nested JAR contents. ZIP timestamps, compression and archive comments are ignored; class files, resources and manifests are not ignored. A mismatch is not automatically malware: investigate toolchain/dependency differences and the exact source revision. A match is useful source-to-binary evidence, not a security audit.

Builds resolve external Maven/Gradle dependencies, including a Loom snapshot declaration. Rebuilding at another time or with a different compiler may yield differences. Do not claim guaranteed byte-for-byte reproducibility based solely on the build succeeding.

## Tested and not tested

During this source publication on 2026-09-05, both artifacts were rebuilt from a fresh repository checkout with the updated source. The offline comparison matched all **281 client entries** and **312 loader entries**, recursively including the nested client, against the published 2.9.2/1.2.3 artifacts. Only ZIP container metadata is excluded from that comparison. This check does not certify the behavior of those matching files as safe.

The public release passed 157 client unit tests, four Fabric client integration suites, public loader tests and distribution validation. Integration coverage includes camera interactions, UI, grounded tiles, item nametags, scanning, and Relog against a local dedicated server with no Rejoin mod.

This does not certify authenticated public-server/proxy behavior, every module combination, all graphics drivers, shaders or third-party mods. The published update artifacts were downloaded and their signatures and hashes verified during release preparation.

## Reporting concerns

Report non-sensitive findings in the repository's issues. Never post Discord tokens, Minecraft authentication arguments, signing keys or session files. If a secret is exposed, revoke/rotate it; removing it from the latest commit alone does not remove it from Git history.
