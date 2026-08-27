# Arcane Client

[![Build](https://github.com/eiiorejierge/Arcane-Client/actions/workflows/build.yml/badge.svg)](https://github.com/eiiorejierge/Arcane-Client/actions/workflows/build.yml)

Arcane Client is a client-side Fabric utility suite for Minecraft 1.21.11. This 2.0 source release combines the strongest parts of the archived 1.6 and 1.8 builds: evidence-based chunk discovery, the newer packet and entity filters, storage/item/tunnel visualization, player utilities, chat macros, and a redesigned Click GUI.

## Highlights

- **Discovery engine:** incremental loaded-chunk scanning, evidence scoring, sensitivity control, deep-Y focus, configurable farm/machine/player-block/light/entity signals, packet activity, rescans, and per-dimension history.
- **Visual intelligence:** chunk tiles, radar HUD, current-chunk intel, storage ESP, item ESP with category colors, tunnel ESP, tracers, stash labels/alerts, and diagnostics.
- **Player tools:** freecam and Auto Totem.
- **Social tools:** four editable, rebindable chat macros.
- **Arcane Click GUI:** searchable modules, draggable and collapsible panels, nested settings, live active counts, in-GUI key rebinding, and Arcane/Ember/Verdant themes that persist in configuration.
- **Clean source:** readable Yarn-named Java, typed collections, warning-free compilation, client-only mixins, Gradle wrapper, sources JAR, and GitHub Actions CI.

Use Arcane Client only where the server rules and applicable terms permit it.

## Requirements

- Minecraft **1.21.11**
- Java **21**
- Fabric Loader **0.19.3 or newer**
- Fabric API **0.141.6+1.21.11 or newer compatible release**

## Install

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API and the Arcane Client JAR in the Minecraft `mods` folder.
3. Start the Fabric profile.
4. Press **Right Shift** to open the Arcane Click GUI.

Configuration is saved to `config/arcane-client.json`.

## Default controls

| Action | Key |
| --- | --- |
| Open Click GUI | Right Shift |
| Toggle base scanner | B |
| Toggle chunk tiles | N |
| Toggle freecam | F6 |
| Toggle storage ESP | F7 |
| Toggle Auto Totem | F8 |
| Toggle tunnel ESP | F9 |
| Toggle item ESP | F10 |
| Chat macros 1–4 | Unbound |

Bindings can be changed in the Click GUI or Minecraft's keybind settings.

## Commands

The primary command is `/arcane`; `/dtrace` remains as a compatibility alias for existing users.

- `/arcane` — show current status.
- `/arcane on|off|settings` — control the scanner or open the GUI.
- `/arcane here` and `/arcane list [count]` — inspect recorded chunk evidence.
- `/arcane threshold <0-100>` — set the flagged-score threshold.
- `/arcane radius <2-24>` — set the loaded-chunk scan radius.
- `/arcane speed <1-8>` — set scan work per tick.
- `/arcane rescan|clear` — refresh or clear the current server/dimension evidence.
- `/arcane overlay|hud|freecam|esp|itemesp|tunnelesp|autototem|macros|tracers|stashalerts|analysis` — toggle individual tools.

## Build from source

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

The remapped mod and sources JARs are written to `build/libs/`.

## Project layout

- `src/main/java/dev/arcaneclient/` — client logic, UI, rendering, scanners, commands, and mixins.
- src/main/resources/ — Fabric metadata, mixin config, language strings, and packaged icon.
- rtwork/ — full-resolution Arcane Client emblem source asset.
- `legacy/artifacts/` — untouched 1.6 and 1.8 input binaries retained for provenance.
- `.github/workflows/build.yml` — reproducible Java 21 build.

## Design and provenance

Version 1.8 was a functional superset of 1.6, so its newer chat macro, block-entity classification, transient-entity filtering, and expanded visual settings form the base. The older artifact is retained to make that comparison auditable. The rebuilt UI follows the module-oriented discoverability seen in established Fabric clients while using an original Arcane visual system and implementation; no source was copied from those projects.

Useful references used during the rebuild include [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), [67 Client](https://github.com/alx-3/67-Client), [Krypton Client's feature overview](https://kryptonclient.org/features), and the [official Fabric example mod](https://github.com/FabricMC/fabric-example-mod).

The supplied artifacts declared **CC0-1.0**, and this reconstructed source is provided under the same license. See [LICENSE](LICENSE), [legacy/README.md](legacy/README.md), and the detailed [merge/design research](docs/DESIGN-RESEARCH.md).
