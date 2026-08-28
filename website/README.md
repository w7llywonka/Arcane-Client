# Arcane Client website

This is the build-free product site for Arcane Client 2.3.6.

## Preview

Serve this directory with any static HTTP server:

~~~powershell
python -m http.server 4173 --directory website
~~~

Then open `http://127.0.0.1:4173`.

## Structure

- `index.html` — centered hero, benefit cards, setup bento, interface samples, explicit Base/Premium plans, FAQ, and footer.
- `styles.css` — Krypton-inspired dark forest/mint visual system, responsive layouts, pills, cards, and lightweight motion.
- `script.js` — fixed-nav state, mobile menu, scroll reveals, FAQ behavior, and the code-gated 2.3.6 release download.
- `assets/arcane-mark.svg` — the original three-stroke Arcane mark.

The implementation is original Arcane code and content; it does not copy Krypton assets or source. Sora is loaded from Google Fonts with a system-font fallback. Discord calls to action use the official invite: `https://discord.gg/DvfZW4Fk5`.

## Download gate

The Base download opens a native dialog and compares the submitted access code by SHA-256 before navigating to the GitHub Release asset. This is a lightweight access gate for a public static site, not secure licensing; the release URL and browser logic remain inspectable in a public repository.