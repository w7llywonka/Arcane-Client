# Arcane Client website

This is the dependency-free product site for Arcane Client 2.2.

## Preview

Serve this directory with any static HTTP server:

~~~powershell
python -m http.server 4173 --directory website
~~~

Then open `http://127.0.0.1:4173`.

## Structure

- `index.html` — product landing page, scanner proof, feature rows, Hiss Addon, and an explicit plan comparison table.
- `styles.css` — flat responsive visual system, client mockup, and motion.
- `script.js` — session-aware Hiss replay control.
- `assets/arcane-mark.svg` — the three-stroke Arcane mark.

The site has no build step, remote asset requests, gradients, or web-font payload. Replace the `https://discord.com/` fallback with the Arcane server invite when it is available.
