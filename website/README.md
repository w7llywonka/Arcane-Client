# Arcane Client website

This is the dependency-free product site for Arcane Client 2.2.

## Preview

Serve this directory with any static HTTP server:

~~~powershell
python -m http.server 4173 --directory website
~~~

Then open `http://127.0.0.1:4173`.

## Structure

- `index.html` — minimal landing page and $5/$10 access options.
- `styles.css` — responsive purple-and-blue layout and motion.
- `script.js` — lightweight scroll reveals.
- `assets/` — the Arcane mark, locally bundled interface font, and its OFL license.

The site intentionally has no build step and can be hosted on any static provider.
