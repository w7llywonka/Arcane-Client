# Arcane Client

[![Build](https://github.com/eiiorejierge/Arcane-Client/actions/workflows/build.yml/badge.svg)](https://github.com/eiiorejierge/Arcane-Client/actions/workflows/build.yml)

Arcane Client is a client-side Fabric utility suite for Minecraft 1.21.11. This 2.2 source release focuses chunk discovery on deliberate growth and cultivation patterns while keeping storage, item, and tunnel visualization as independent tools alongside player utilities, chat macros, and the polished Click GUI.

## Highlights

- **Growth discovery:** palette-skipping loaded-chunk scans, organized crop plots, synchronized stages, mature concentrations, sapling layouts, sugar-cane/cactus columns, imported growth clusters, harvest cycles, sensitivity control, rescans, and per-dimension history.
- **Visual intelligence:** chunk tiles, radar HUD, current-chunk growth intel, independent storage ESP, item ESP with category colors, tunnel ESP, tracers, growth-site labels/alerts, and diagnostics.
- **Player tools:** freecam and Auto Totem.
- **Social tools:** four editable, rebindable chat macros.
- **Arcane Click GUI:** smooth Inter typography, soft outlined module cards, searchable modules, draggable/collapsible panels, nested settings, live FPS/profile status, in-GUI key rebinding, and three persistent themes.
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

## Growth-only scanner

Only `NATURAL_GROWTH` and `CULTIVATION` evidence can affect a chunk score. Storage blocks, block entities, machines, generic placed blocks, entities, sounds, particles, and all light data are ignored by the discovery algorithm. Storage ESP remains an independent visual module and never changes suspicion.

The scanner looks for repeated or deliberately organized growth: crop rows and rectangles, supporting farmland, synchronized stages, mature crop concentrations, sapling layouts, structured sugar-cane/cactus columns, plants outside native biomes, and observed growth/harvest cycles. Natural growth alone is capped below the suspicious threshold; cultivation evidence or repeated activity is required.

Normal scans use Minecraft section palettes to skip every non-empty section that contains no growth-relevant states. Tunnel ESP still performs its separate geometry pass only when that module is enabled.

## Performance profiles

**High FPS** is the default. Click the Performance row in the Player panel or run `/arcane profile` to cycle profiles.

| Profile | Default scan budget | Snapshot cadence | Storage targets | Item targets | Tunnel/chunk targets | Storage fill |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| High FPS | 1.0 ms/tick | 20 ticks | 256 | 128 | 384 | Outline only |
| Balanced | 1.85 ms/tick | 10 ticks | 512 | 256 | 768 | Enabled |
| Quality | 2.5 ms/tick | 5 ticks | 1,024 | 512 | 1,500 | Enabled |

While the Click GUI is open, 3D overlays and the HUD pause and background scanning is capped at 0.5 ms/tick. The interface itself uses cached module/filter models, a cached FPS label, static no-animation toggles, and an OFL-licensed Inter TrueType renderer. Actual FPS remains hardware, shader, resource-pack, and world dependent.

## Default controls

| Action | Key |
| --- | --- |
| Open Click GUI | Right Shift |
| Toggle growth scanner | B |
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
- `/arcane profile` — cycle High FPS, Balanced, and Quality budgets.
- `/arcane here` and `/arcane list [count]` — inspect recorded chunk evidence.
- `/arcane threshold <0-100>` — set the flagged-score threshold.
- `/arcane radius <2-24>` — set the loaded-chunk scan radius.
- `/arcane speed <1-8>` — set scan work per tick.
- `/arcane rescan|clear` — refresh or clear the current server/dimension evidence.
- `/arcane overlay|hud|freecam|esp|itemesp|tunnelesp|autototem|macros|tracers|growthalerts|analysis` — toggle individual tools.

## Product website

The responsive Arcane Client product site lives in [website/](website/). It is a dependency-free static pricing page with explicit Base and Premium feature lists, flat styling, and no JavaScript or web-font payload. See [website/README.md](website/README.md) for local preview instructions.

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
- `src/main/resources/` — Fabric metadata, mixin config, language strings, Inter font, and packaged icon.
- `website/` — responsive product website and local assets.
- `artwork/` — full-resolution Arcane Client emblem source asset.
- `third-party/` — Inter's SIL Open Font License.
- `legacy/artifacts/` — untouched 1.6 and 1.8 input binaries retained for provenance.
- `.github/workflows/build.yml` — reproducible Java 21 build.

## Design and provenance

Version 1.8 was a functional superset of 1.6, so its newer chat macros and expanded visual settings formed the reconstruction base. Version 2.2 replaces its broad block-entity, lighting, and transient-entity suspicion model with the focused growth-only algorithm documented above. The older artifact is retained to make that comparison auditable. The rebuilt UI follows the module-oriented discoverability seen in established Fabric clients while using an original Arcane visual system and implementation; no source was copied from those projects.

Useful references used during the rebuild include [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), [67 Client](https://github.com/alx-3/67-Client), [Krypton Client's feature overview](https://kryptonclient.org/features), and the [official Fabric example mod](https://github.com/FabricMC/fabric-example-mod).

The supplied artifacts declared **CC0-1.0**, and this reconstructed source is provided under the same license. The bundled Inter font remains under **OFL-1.1**; its license is included in `third-party/` and inside release JARs. See [LICENSE](LICENSE), [legacy/README.md](legacy/README.md), and the detailed [merge/design research](docs/DESIGN-RESEARCH.md).
