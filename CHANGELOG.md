# Changelog

## Unreleased

- Reworked the Click GUI around transparent top controls, collision-safe labels, tighter collapsed windows, and six first-class evidence-channel modules.
- Made the nearby-chunk radar size its cells to the active font so two-digit scores cannot overlap.

- Fixed the Sora resource path and added a vanilla glyph fallback; replaced integer scanline corners with linearly filtered high-density rounded masks so edges remain smooth at scaled GUI resolutions.

- Rebuilt the Click GUI as a base-finding module menu: one draggable window per category, left click to toggle, right click for nested settings, middle click to rebind.
- Added per-module descriptions, shown inline when a module is open and as a hover tooltip when it is not.
- Added a typed setting model behind the GUI: checkboxes, sliders, colour swatches, value cyclers, key binds, and live counters.
- Exposed scan radius, scan speed, and rescan delay as Chunk Finder sliders; they were previously command-only.
- Regrouped modules into Base Finding, ESP, Render, Utility, and Client, and folded the standalone keybind panel into each module's own bind row.
- Added optional [Caxton](https://modrinth.com/mod/caxton) support: when it is installed, the interface renders Sora as multi-channel signed distance fields, which stay crisp at any size.
- Fixed Arcane silently losing its font when Caxton is installed. Interface text is now selected by a style on the text instead of a private text renderer, which Caxton casts unconditionally to Minecraft's own implementation.
- Fixed blocky Click GUI and HUD text: the font is now baked at six resolutions and the one matching the player's GUI scale is selected, so glyphs map one texel per physical pixel instead of being resampled by an unfiltered glyph atlas.
- Refined the renderer with smaller rounded module cards, compact HUD panels, a neutral graphite overlay, and green-free Arcane, Frost, and Rose accents shared by the GUI and HUD.

## 2.2.0+mc1.21.11

- Restored the evidence-based scanner reconstructed from the supplied 1.8 and 1.6 clients, including configurable cultivation, placed-block, machine, block-entity, light, live-activity, and entity signals.
- Restored deep-Y weighting, temporal layout comparison, stash clustering, block-entity classification, transient-entity filtering, and the broader packet observation bridge.
- Refined the Click GUI and HUD with smaller rounded module cards, a neutral graphite overlay, green-free accent themes, and an OFL-licensed Sora font.
- Added the Hiss Addon website intro: a lightweight snake slither shown once per session with an optional replay.
- Rebuilt the product site with a Krypton-inspired dark forest/mint visual system, optimized motion, setup bento, interface samples, explicit plan cards, Hiss showcase, and FAQ.
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
