# Arcane Client

[![Build](https://github.com/eiiorejierge/Arcane-Client/actions/workflows/build.yml/badge.svg)](https://github.com/eiiorejierge/Arcane-Client/actions/workflows/build.yml)

Arcane Client is a client-side Fabric utility suite for Minecraft 1.21.11. This 2.2 source release combines the strongest parts of the archived 1.6 and 1.8 builds: evidence-based chunk discovery, the newer packet and entity filters, storage/item/tunnel visualization, player utilities, chat macros, and a redesigned Click GUI.

## Highlights

- **Discovery engine:** incremental loaded-chunk scanning, evidence scoring, sensitivity control, deep-Y focus, configurable farm/machine/player-block/light/entity signals, packet activity, rescans, and per-dimension history.
- **Visual intelligence:** chunk tiles, radar HUD, current-chunk intel, storage ESP, item ESP with category colors, tunnel ESP, tracers, stash labels/alerts, and diagnostics.
- **Player tools:** freecam and Auto Totem.
- **Social tools:** four editable, rebindable chat macros.
- **Arcane Click GUI:** a base-finding module menu in the Krypton mould — one draggable window per category, left click to toggle, right click for nested settings, middle click to rebind, per-module descriptions, sliders, colour swatches, search, live FPS/profile status, and three persistent themes shared with the HUD.
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

## Click GUI

Press **Right Shift** to open the module menu. Each category is its own window: drag a title bar to move it, click the title bar to collapse it.

| Input | Action |
| --- | --- |
| Left click a module | Toggle it on or off |
| Right click a module | Open or close its settings |
| Middle click a module | Rebind its key |
| Drag a title bar | Move that category window |
| Click a title bar | Collapse or expand that category |

Modules are grouped for base finding first:

| Category | Modules |
| --- | --- |
| Base Finding | Chunk Finder, Growth Signals, Build Traces, Machine Signals, Live Changes, Light Signals, Entity Signals, Stash Finder, Chunk Intel |
| ESP | Storage ESP, Item ESP, Tunnel ESP, Chunk Tiles, ESP Debug |
| Render | Base Radar, Freecam |
| Utility | Auto Totem, Chat Macros |
| Client | Performance, Interface |

Chunk Finder carries the scan budget and sensitivity controls. The six evidence channels are first-class modules beside it, so growth, build, machine, live-change, light, and entity evidence can be switched independently without opening a nested settings list. Search filters every category by module name, description, or setting name.

## Text sharpness

Minecraft bakes a TrueType glyph once, at `size x oversample` pixels, and samples the glyph atlas with `FilterMode.NEAREST` — there is no filtering anywhere in that path. A glyph is then drawn at `size x guiScale` physical pixels. Unless `oversample` matches the GUI scale, every glyph is an antialiased bitmap resampled with no interpolation, which is what makes custom fonts look blocky at GUI scale 3 and above.

Arcane picks its font per frame, from a style on the text rather than through a private text renderer:

| Situation | Font used | Result |
| --- | --- | --- |
| [Caxton](https://modrinth.com/mod/caxton) installed | `ui_caxton`, Sora as multi-channel signed distance fields | Crisp at any size |
| Otherwise | `ui_x1` to `ui_x6`, Sora baked at the matching oversample | One texel per physical pixel |

Caxton is optional and listed under `suggests`. Arcane checks that its font actually resolved before using it, so a missing native library falls back to the bundled variants rather than rendering blank. Note that Caxton is [incompatible with Iris Shaders](https://gitlab.com/Kyarei/caxton#incompatible); without it the bundled variants already give sharp text at every GUI scale, so install it only if you want every font in the game smooth.

All variants load Sora from Minecraft's required `assets/arcaneclient/font/` path and fall back to `minecraft:default` for glyphs Sora does not provide. Their metrics remain identical across GUI scales.

## Performance profiles

**High FPS** is the default. Cycle it from the Performance module in the Client window or run `/arcane profile`.

| Profile | Default scan budget | Snapshot cadence | Storage targets | Item targets | Tunnel/chunk targets | Storage fill |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| High FPS | 1.0 ms/tick | 20 ticks | 256 | 128 | 384 | Outline only |
| Balanced | 1.85 ms/tick | 10 ticks | 512 | 256 | 768 | Enabled |
| Quality | 2.5 ms/tick | 5 ticks | 1,024 | 512 | 1,500 | Enabled |

While the Click GUI is open, 3D overlays and the HUD pause and background scanning is capped at 0.5 ms/tick. The interface itself uses cached module/filter models, a cached FPS label, static no-animation toggles, and an OFL-licensed Sora TrueType renderer. Actual FPS remains hardware, shader, resource-pack, and world dependent.

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
- `/arcane profile` — cycle High FPS, Balanced, and Quality budgets.
- `/arcane here` and `/arcane list [count]` — inspect recorded chunk evidence.
- `/arcane threshold <0-100>` — set the flagged-score threshold.
- `/arcane radius <2-24>` — set the loaded-chunk scan radius.
- `/arcane speed <1-8>` — set scan work per tick.
- `/arcane rescan|clear` — refresh or clear the current server/dimension evidence.
- `/arcane overlay|hud|freecam|esp|itemesp|tunnelesp|autototem|macros|tracers|stashalerts|analysis` — toggle individual tools.

## Product website

The responsive Arcane Client product site lives in [website/](website/). It is a build-free static page with a Krypton-inspired dark forest/mint visual system, centered hero, setup bento, interface samples, explicit Base/Premium plans, a session-aware Hiss intro, and an FAQ. See [website/README.md](website/README.md) for local preview instructions.

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
- `src/main/resources/` — Fabric metadata, mixin config, language strings, Sora font, and packaged icon.
- `website/` — responsive product website and local assets.
- `artwork/` — full-resolution Arcane Client emblem source asset.
- `third-party/` — Sora's SIL Open Font License.
- `legacy/artifacts/` — untouched 1.6 and 1.8 input binaries retained for provenance.
- `.github/workflows/build.yml` — reproducible Java 21 build.

## Design and provenance

Version 1.8 was a functional superset of 1.6, so its newer chat macro, block-entity classification, transient-entity filtering, and expanded visual settings form the base. The older artifact is retained to make that comparison auditable. The rebuilt UI follows the category-window module menu that established Fabric clients share — Krypton's in particular, where a right click on a module opens its settings — while using an original Arcane visual system and implementation. No source or asset was copied from those projects; Arcane uses its own neutral graphite foundation with violet, blue, and rose accents.

Useful references used during the rebuild include [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), [67 Client](https://github.com/alx-3/67-Client), [Krypton Client's feature overview](https://kryptonclient.org/features), and the [official Fabric example mod](https://github.com/FabricMC/fabric-example-mod).

The supplied artifacts declared **CC0-1.0**, and this reconstructed source is provided under the same license. The bundled Sora font remains under **OFL-1.1**; its license is included in `third-party/` and inside release JARs. See [LICENSE](LICENSE), [legacy/README.md](legacy/README.md), and the detailed [merge/design research](docs/DESIGN-RESEARCH.md).
