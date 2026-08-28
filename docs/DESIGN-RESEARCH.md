# Merge and design research

This document records how Arcane Client 2.0 was assembled and which external references informed the product pass. It separates source provenance from visual inspiration so future contributors can audit the decisions.

## Supplied client comparison

| Area | 1.6 artifact | 1.8 artifact | Arcane Client 2.0 decision |
| --- | --- | --- | --- |
| Chunk evidence scanner | Present | Present and refined | Keep the newer engine and full-height incremental scan behavior |
| Chunk overlay/radar/intel | Present | Expanded settings | Keep all views and unify their theme |
| Storage, item, and tunnel ESP | Present | Expanded controls | Keep all renderers, category colors, tracers, and diagnostics |
| Freecam and Auto Totem | Present | Present | Preserve both under the Player panel |
| Packet activity bridge | Present | Refined filtering | Keep the newer bridge and observer guard |
| Block-entity classification | Basic | Dedicated classifier | Use the 1.8 classifier |
| Transient entity handling | Basic | Dedicated filter | Use the 1.8 filter |
| Chat macros | Absent | Four configurable macros | Keep the 1.8 implementation |
| Settings UI | Older fixed layout | More controls | Replace with a responsive Arcane interface while retaining all controls |

The 1.8 artifact is a functional superset, so it is the reconstruction baseline. The 1.6 binary remains useful for provenance and behavioral comparison; both original SHA-256 hashes are recorded in `legacy/README.md`.

## External reference review

- [Meteor Client source](https://github.com/MeteorDevelopment/meteor-client) — reference for a mature Fabric client organized around discoverable module categories and centralized configuration. Arcane adopts the clarity of category-based navigation, not Meteor source code.
- [Meteor installation FAQ](https://meteorclient.com/faq/installation) — reference for communicating the Fabric Loader, Fabric API, and Minecraft-version requirements plainly.
- [67 Client source](https://github.com/alx-3/67-Client) and [67 Client site](https://67client.com/) — reference for presenting a broad utility set without hiding key actions several screens deep.
- [Krypton Client features](https://kryptonclient.org/features) — reference for concise feature-first presentation, quick access to frequently toggled tools, and the module-menu interaction its documentation describes: open the menu with Right Shift, pick a category, left click to toggle a module, right click to expand its settings, and bind any module to a key. Arcane adopts that interaction model and information architecture; no Krypton source or asset is used.
- [Fabric example mod](https://github.com/FabricMC/fabric-example-mod), [Yarn](https://maven.fabricmc.net/net/fabricmc/yarn/), [Fabric Loader](https://maven.fabricmc.net/net/fabricmc/fabric-loader/), and [Fabric API](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/) — authoritative references for the 1.21.11 source layout and pinned build dependencies.
- [CFR](https://github.com/leibnitz27/cfr) and [Tiny Remapper](https://github.com/FabricMC/tiny-remapper) — reconstruction tools used to turn the CC0 input binaries into readable Yarn-named Java before manual cleanup.

No external client source was copied into Arcane Client. The external projects informed information architecture, presentation, and repository ergonomics only.

## Arcane product decisions

- One searchable Click GUI with a window per category: Base Finding, ESP, Render, Utility, and Client. Base finding leads because that is what the client is for.
- Left click toggles a module, right click expands its settings, middle click rebinds it. Every setting a module owns — including its key — lives inside that module rather than in a separate panel.
- Each module carries a one-line description, shown inline while its settings are open and as a hover tooltip while they are not.
- Settings are a typed model (checkbox, slider, colour swatch, value cycler, key bind, live counter), so the window layout and the click hit-testing walk exactly the same geometry.
- Windows can be dragged and collapsed; the row of windows reflows from five columns down to one, and anything that will not fit is parked as a collapsed title above the status bar.
- Verdant, Arcane, and Ember palettes are shared by the GUI and HUD and persist in `arcane-client.json`. Verdant is the default and matches the product site.
- `/arcane` is the primary command; `/dtrace` redirects to it for configuration continuity.
- Original package names, mod ID, resource namespace, config filename, artifact name, and user-visible branding are all replaced.
- The two input binaries and old placeholder icon remain under `legacy/` and are never bundled into production output.
