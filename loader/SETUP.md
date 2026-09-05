# ARCLoader

ARCLoader is one Fabric mod JAR. Arcane Client is a Fabric nested JAR inside it, so it loads on the first launch without extracting another mod into `mods`. A normal instance contains exactly one Arcane file: `ARCLoader.jar`.

Version 1.2.3 never downloads or installs `arcane-client-*.jar`. When its signed manifest reports the same or an older loader version, it performs no write in the mods directory. When a newer loader exists, it downloads and verifies the **outer loader JAR** into the loader state directory. At Minecraft shutdown, a small detached Java helper waits for the JVM to exit, verifies the staged JAR again, backs up the old outer loader outside the instance, and atomically replaces that same selected loader path. This avoids Windows JAR locks and never adds a second active Arcane mod.

During the update check, redundant unselected Arcane Client/Loader JARs left by older builds are identified by their root `fabric.mod.json` IDs and scheduled to be moved into the state backup directory after Minecraft exits. The selected loader and any selected external Arcane Client origin are excluded.

This updater does not contain a license or device-lock system.

## User flow

1. Install Java 21 and Fabric Loader for Minecraft 1.21.11.
2. Import `ARCLoader.jar` into the Modrinth/Prism instance, or place that one JAR in its `mods` folder.
3. Launch the instance. Fabric loads the bundled Arcane Client immediately.
4. Future signed loader updates are staged outside `mods` and replace the same JAR after Minecraft closes. The next launch runs the new bundled client.

The same distribution remains directly executable. Running it copies the outer loader into the selected mods directory; it never extracts the nested client:

```text
java -jar ARCLoader.jar
java -jar ARCLoader.jar --install "path/to/instance/mods"
```

## Build

Use Java 21 and the wrapper from the repository root:

```text
./gradlew build
./gradlew -p loader build
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`. The distribution is `loader/build/libs/ARCLoader.jar`. `verifyDistributionJar` confirms its fixed public filename, root Fabric loader metadata, and exactly one nested `arcaneclient` JAR.

`build/libs/arcane-client-2.9.2+mc1.21.11.jar` from the root client build is the bundled client. No prebuilt client JAR is committed for this build. When the client changes, rebuild the outer loader and publish a new outer loader version. Never use the client JAR as the schema-2 loader update artifact.

## Signed release manifest

The loader accepts schema 2 only. The live `/updates/loader-release.json` must describe the complete outer loader JAR:

```json
{
  "schemaVersion": 2,
  "artifactType": "arcane-loader",
  "version": "1.2.3+mc1.21.11",
  "minecraftVersion": "1.21.11",
  "downloadUrl": "https://www.arcaneclient.shop/updates/ARCLoader.jar",
  "sha256": "64 lowercase hexadecimal characters",
  "signature": "Base64 Ed25519 signature of the exact outer JAR bytes",
  "size": 1234567
}
```

The root `fabric.mod.json` inside the signed artifact must have `id: arcaneloader`, a version exactly matching the manifest, and exactly one nested JAR whose root Fabric id is `arcaneclient`. A legacy schema-1 client manifest is intentionally rejected.

The public verification key is `loader/src/main/resources/release-public.pem`. The matching private key is deliberately not included. Local builds do not require it; only the release maintainer can sign updates accepted by the official loader.

Publish an update as follows:

1. Build the client from the repository root and update the loader's configured client name/version when needed.
2. Bump the outer loader version and build it.
3. Sign the **built outer loader** locally:

   ```text
   ./gradlew -p loader signRelease --args="/secure/path/release-private.pem build/libs/ARCLoader.jar"
   ```

4. Upload that exact outer JAR and update schema-2 `loader-release.json` with its exact size, SHA-256, and signature.
5. Verify the deployed JSON/JAR pair before making the website download public.

Never commit or upload the private key. Back it up encrypted and offline. If it leaks, rotate the embedded public key and distribute a new loader manually.
