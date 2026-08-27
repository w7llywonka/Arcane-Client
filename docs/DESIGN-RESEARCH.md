# Merge and design research

This document records how Arcane Client 2.0 was assembled and how the 2.2 growth-only scanner was refined and which external references informed the product pass. It separates source provenance from visual inspiration so future contributors can audit the decisions.

## Supplied client comparison

| Area | 1.6 artifact | 1.8 artifact | Arcane Client 2.0 decision |
| --- | --- | --- | --- |
| Chunk evidence scanner | Present | Present and refined | Replace broad suspicion with the 2.2 growth-only palette fast path |
| Chunk overlay/radar/intel | Present | Expanded settings | Keep all views and unify their theme |
| Storage, item, and tunnel ESP | Present | Expanded controls | Keep all renderers, category colors, tracers, and diagnostics |
| Freecam and Auto Totem | Present | Present | Preserve both under the Player panel |
| Packet activity bridge | Present | Refined filtering | Keep the guard but reduce observation to block and section growth transitions |
| Block-entity classification | Basic | Dedicated classifier | Keep it only for independent ESP; remove it from chunk suspicion |
| Transient entity handling | Basic | Dedicated filter | Remove the entity scan and filter from discovery |
| Chat macros | Absent | Four configurable macros | Keep the 1.8 implementation |
| Settings UI | Older fixed layout | More controls | Use a responsive Arcane interface with only Growth patterns and Live growth scanner controls |

The 1.8 artifact is a functional superset, so it is the reconstruction baseline. The 1.6 binary remains useful for provenance and behavioral comparison; both original SHA-256 hashes are recorded in `legacy/README.md`.

## Growth-only algorithm rationale

The 2.2 scanner deliberately separates discovery from visualization. Storage ESP may render storage blocks, but those blocks never enter the evidence model. The same hard boundary applies to block entities, machines, generic placed blocks, entities, sounds, particles, and lighting packets.

Normal discovery asks each `ChunkSection` palette whether it contains any cached growth-relevant state before visiting its 4,096 block positions. Relevant sections aggregate compact 16×16 occupancy grids and fixed-size per-section temporal counters. Deliberate rows, rectangular plots, farmland support, synchronized stages, mature concentrations, sapling layouts, structured sugar-cane/cactus columns, imported-biome clusters, and repeated harvest cycles provide cultivation evidence. Natural growth uses a separate category whose score cap is intentionally below the suspicious threshold.

This reduces both work and false positives: there are no block-entity/entity list walks, light lookups, generic stable-layout hashes, storage-network pair searches, or broad sound/particle hooks in the discovery path.

## External reference review

- [Meteor Client source](https://github.com/MeteorDevelopment/meteor-client) — reference for a mature Fabric client organized around discoverable module categories and centralized configuration. Arcane adopts the clarity of category-based navigation, not Meteor source code.
- [Meteor installation FAQ](https://meteorclient.com/faq/installation) — reference for communicating the Fabric Loader, Fabric API, and Minecraft-version requirements plainly.
- [67 Client source](https://github.com/alx-3/67-Client) and [67 Client site](https://67client.com/) — reference for presenting a broad utility set without hiding key actions several screens deep.
- [Krypton Client features](https://kryptonclient.org/features) — reference for concise feature-first presentation and quick access to frequently toggled tools.
- [Fabric example mod](https://github.com/FabricMC/fabric-example-mod), [Yarn](https://maven.fabricmc.net/net/fabricmc/yarn/), [Fabric Loader](https://maven.fabricmc.net/net/fabricmc/fabric-loader/), and [Fabric API](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/) — authoritative references for the 1.21.11 source layout and pinned build dependencies.
- [CFR](https://github.com/leibnitz27/cfr) and [Tiny Remapper](https://github.com/FabricMC/tiny-remapper) — reconstruction tools used to turn the CC0 input binaries into readable Yarn-named Java before manual cleanup.

No external client source was copied into Arcane Client. The external projects informed information architecture, presentation, and repository ergonomics only.

## Arcane product decisions

- One searchable Click GUI with five panels: Discovery, Visual, Player, Keybinds, and Social.
- Left click toggles a module; right click exposes module-specific configuration.
- Panels can be dragged and collapsed, and the layout adapts from five columns down to one.
- Arcane, Ember, and Verdant palettes are shared by the GUI and HUD and persist in `arcane-client.json`.
- `/arcane` is the primary command; `/dtrace` redirects to it for configuration continuity.
- Original package names, mod ID, resource namespace, config filename, artifact name, and user-visible branding are all replaced.
- The two input binaries and old placeholder icon remain under `legacy/` and are never bundled into production output.
