# Arcane Client

Source for the public **Arcane Client 2.9.2** release and **ARCLoader 1.2.3**, for Minecraft 1.21.11.

Official downloads: [arcaneclient.shop](https://arcaneclient.shop).

## What is included

- Growth-based loaded-chunk scanning, grounded chunk tiles, radar and evidence scoring.
- Camera controls, Fullbright, ESP and dropped-item nametags.
- Combat, movement and utility modules, including built-in Relog: disconnect, wait two seconds, reconnect without another mod.
- The draggable category interface, fonts and their licenses.
- Public loader source, including signed updates, installation and the after-exit replacement helper.
- Unit tests and Minecraft client integration tests.

The source of the shipped client is under `src/`; the public loader is under `loader/`. The private DEV loader and Discord administrator service are not part of this release. Existing `legacy/`, `website/` and older documents are historical material, not the current client build inputs. This update does not deploy or refresh the website source.

## Build from source

Install Java 21. From the repository root:

```powershell
.\gradlew.bat build
.\gradlew.bat -p loader build
```

On Linux/macOS, use `./gradlew` instead of `.\gradlew.bat`.

Outputs:

- `build/libs/arcane-client-2.9.2+mc1.21.11.jar`
- `loader/build/libs/ARCLoader.jar`

The loader bundles the client you just compiled. No prebuilt client binary or private signing key is required. Do not install both outputs together: choose the standalone client **or** ARCLoader, alongside Fabric API.

Runtime requirements: Minecraft 1.21.11, Java 21, Fabric Loader 0.19.3+, compatible Fabric API 0.141.6+1.21.11. Right Shift opens the module interface.

## Inspect and verify

Read [the security and release-verification notes](docs/SECURITY-AND-VERIFICATION.md). They explain the loader's network requests, file writes, helper process, trust model and release hashes.

Publishing source is not a malware audit or a guarantee that a download matches it. Review the code, build it yourself, and compare the compiled contents with your download:

```powershell
java tools/CompareJars.java loader/build/libs/ARCLoader.jar path/to/downloaded/ARCLoader.jar
```

This compares every file's bytes, recursively inside nested JARs, ignoring only ZIP container metadata. Differences cause a failing exit code.

## Tests

```powershell
.\gradlew.bat test
.\gradlew.bat runClientGameTest
.\gradlew.bat -p loader test verifyDistributionJar
```

Minecraft integration tests launch a test client and create isolated worlds. Relog tests start an offline test server bound to **127.0.0.1:25586**, not your public server. Linux CI uses Xvfb. The test build accepts Minecraft's EULA; review that setting before running.

Tests cover representative behavior, not every module combination, shader or server policy. Server-hidden blocks cannot be guaranteed discoverable. Use the client only where server rules and applicable terms permit it.
