# Arcane Client website

This is the dependency-free product site for Arcane Client 2.2.

## Preview

Serve this directory with any static HTTP server:

~~~powershell
python -m http.server 4173 --directory website
~~~

Then open `http://127.0.0.1:4173`.

## Structure

- `index.html` — semantic content and product feature inventory.
- `styles.css` — responsive visual system, motion, and interface mockups.
- `script.js` — scanner demo, GUI theme preview, navigation, and copy interaction.
- `assets/` — the optimized Arcane emblem, locally bundled interface font, and its OFL license.

The site intentionally has no build step and can be hosted on any static provider.
