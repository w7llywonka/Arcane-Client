# Legacy artifacts

These are the two original client binaries that were supplied in the repository. They are retained unchanged for provenance and regression comparison; they are not included inside the Arcane Client build.

| Artifact | SHA-256 |
| --- | --- |
| `donuttrace-1.6.0+mc1.21.11.jar` | `803fdf4417e64a4a792c67c47fd188029b6cac33962b4a5298d9e1ed1ac8c941` |
| `donuttrace-1.8.0+mc1.21.11.jar` | `f643fb8c6e10813e1a02a6df689ed175961be9cfdc1e76d7aa3a0c81ad8ce5cb` |

The 1.8 binary contains all 1.6 functionality plus chat macros, improved block-entity ESP classification, transient-entity filtering, and expanded settings. Arcane Client 2.0 reconstructs the newer implementation as maintainable Yarn-named source and preserves the best compatible behavior from both.

Both supplied binaries declare the CC0-1.0 license in their Fabric metadata.
