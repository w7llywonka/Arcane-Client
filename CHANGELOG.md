# Changelog

## 2.2.0+mc1.21.11

- Replaced broad base suspicion with a growth-only discovery model.
- Set storage, block-entity, machine, placed-block, entity, interaction, and light category caps to zero.
- Added palette-level section skipping so normal scans avoid sections with no growth-relevant block states.
- Added organized crop, farmland support, synchronized stage, mature crop, sapling, vertical growth, imported biome, and temporal harvest heuristics.
- Reduced the packet mixin from fifteen broad hooks to two growth-transition hooks.
- Renamed stash-facing alerts and labels to growth-site alerts while retaining `/arcane stashalerts` as a command alias.
- Removed obsolete depth weighting, generic temporal layout scans, block-entity scans, light sampling, and entity scans.
- Added automated guarantees that storage, light, machines, block entities, and entities cannot produce suspicion.

## 2.1.0+mc1.21.11

- Added a professional smooth Inter UI font under the SIL Open Font License.
- Added soft outlined module cards, modern static toggles, and a live cached FPS/profile readout.
- Added High FPS, Balanced, and Quality profiles; High FPS is the default.
- Capped scan time, snapshot cadence, storage/item targets, tunnel outlines, and chunk markers per profile.
- Paused HUD/3D overlay submission and capped scanning at 0.5 ms/tick while the Click GUI is open.
- Replaced per-frame GUI model/filter allocations and streams with reusable cached data.
- Added a chunk snapshot index and replaced quadratic stash-neighbor checks with bounded spatial lookup.
- Added `/arcane profile` and automated performance-budget tests.

## 2.0.0+mc1.21.11

- Reconstructed the complete client as readable Yarn-named Java source.
- Unified the supplied 1.6 and 1.8 feature sets under the Arcane Client identity.
- Added searchable, draggable, collapsible module panels with nested settings and three persisted themes.
- Added a matching themed radar and current-chunk intelligence HUD.
- Added `/arcane` commands and retained `/dtrace` as a compatibility alias.
- Updated to Fabric Loader 0.19.3, Fabric API 0.141.6+1.21.11, Yarn 1.21.11+build.6, and Java 21.
- Added reproducible Gradle builds, sources JAR generation, CI, documentation, and preserved legacy artifacts with checksums.
