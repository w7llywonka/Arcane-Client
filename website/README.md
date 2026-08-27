# Arcane Client website

This is the dependency-free product site for Arcane Client 2.2.

## Preview

Serve this directory with any static HTTP server:

~~~powershell
python -m http.server 4173 --directory website
~~~

Then open `http://127.0.0.1:4173`.

## Structure

- `index.html` — minimal landing page and explicit $5/$10 feature lists.
- `styles.css` — flat responsive layout and lightweight CSS motion.
- `assets/arcane-mark.svg` — the flat Arcane mark.

The site has no build step, JavaScript, external requests, or web-font payload.
