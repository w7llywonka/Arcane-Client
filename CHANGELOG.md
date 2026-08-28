# Changelog

## Unreleased

## 2.3.6+mc1.21.11

- Replaced bright perimeter outlines across the Click GUI and HUD with dense smoked-glass surfaces and low-alpha dark edges.
- Added subtle glass highlights, softer hover states, and accent-only focus rings while keeping text and controls readable.
- Applied the glass treatment to preset themes and fully custom RGB palettes.

## 2.3.5+mc1.21.11

- Made both Freecam and Freelook render a synchronized client-only copy of the local player, including skin, pose, health, and equipment.
- Made the visual body explicitly non-targetable, non-attackable, non-interactable, and non-collidable so it cannot produce an invalid entity packet.
- Unified attack, break, use, callback, and outbound packet guards across both detached camera modes while preserving body-origin block mining within the player's real reach.
- Expanded the Minecraft client GameTest to aim at and deliberately attack both visual bodies and detached cameras in Freecam and Freelook while verifying the connection remains open.

## 2.3.4+mc1.21.11

- Fixed Freecam and Freelook rendering from stale interpolated angles by making the rendered camera use the active detached camera's current yaw and pitch.
- Added a client-only stationary player replica for Freecam, preserving the player's skin, pose, and equipment while the camera moves independently.
- Added official Fabric client GameTests covering raw flight input, both mouse-look directions, rendered camera alignment, hotbar visibility, body anchoring, mode restoration, and invalid-entity packet safety.
- Reworked the Click GUI layout into bounded, scrollable category viewports so tall module lists cannot overlap parked windows or clip through partial rows.
- Removed the unused optional Caxton font path and its startup warning; Arcane now consistently uses the bundled scale-matched Sora variants.

## 2.3.3+mc1.21.11

- Made the real local player render while Freecam uses its detached client-only camera entity.
- Added a final ClientConnection packet boundary so entity, block, and item interaction packets cannot escape Freecam and trigger strict-server invalid-entity disconnects.
- Changed Freecam navigation to level WASD flight with independent Space/Shift vertical movement and normalized diagonal speed.

## 2.3.2+mc1.21.11

- Fixed Freecam and Freelook camera instability by keeping both interpolation endpoints synchronized for their non-ticking detached camera entities.
- Preserved normal tick-to-tick position interpolation while eliminating stale activation coordinates and stale mouse angles.

## 2.3.1+mc1.21.11

- Fixed multiplayer Freecam disconnects caused by camera-origin entity attacks reaching strict servers.
- Added direct vanilla attack/break/use cancellation plus a fail-closed outbound entity/block/item interaction packet guard; body-origin directional mining packets remain allowed.

## 2.3.0+mc1.21.11

- Corrected Swing Speed semantics: Arcane now lengthens the hand animation from 7 to 30 ticks, with a slower 12-tick default and migration from the old fast value.
- Added a configurable Info HUD for FPS, coordinates, facing direction, horizontal speed, ping, and biome.
- Added Auto Tool with first-click selection, best-tool scoring, optional one-durability protection, server slot synchronization, and safe restoration.
- Expanded the validated Click GUI catalog from 40 to 42 modules.

## 2.2.1+mc1.21.11

- Fixed Freecam and Freelook mouse control on Minecraft 1.21.11 by routing vanilla look input to the active detached camera while keeping the real player's head rotation unchanged.

- Expanded Arcane to an exact runtime-validated 40-module catalog across Base Finding, ESP, Combat, Render, Utility, and Client.
- Added Player, Mob, Projectile, Crystal, Entity Tracer, and Hole ESP with configurable ranges, names, and colors.
- Added Auto Sprint, Auto Eat, Health Alert, Armor Alert, Hit Sound, Swing Speed, and a Combat HUD alongside Auto Totem.
- Rebuilt Freecam with smooth acceleration/drag, persisted scroll-wheel speed control, and body-origin directional mining constrained to real reach.
- Added anchored third-person Freelook, held Zoom, Fullbright, No Hurt Cam, Clean Capture, Streamer Mode, and sound-notification volume.
- Added exact RGB customization for the Click GUI accent, panel, and text colors.
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
