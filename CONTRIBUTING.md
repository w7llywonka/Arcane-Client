# Contributing

Arcane Client targets Minecraft 1.21.11, Fabric Loader, Fabric API, and Java 21.

1. Create a focused branch.
2. Run `./gradlew clean build --no-daemon` (or `.\gradlew.bat clean build --no-daemon` on Windows).
3. Keep client-only behavior in the `main` client source set and use Yarn names in mixins.
4. Update documentation when controls, commands, dependencies, or configuration change.
5. Do not commit `.gradle/`, `build/`, `run/`, IDE files, or Minecraft account data.

Changes should preserve saved configuration compatibility where practical. New modules belong in the searchable Click GUI and should expose a clear enabled state and keybind when appropriate.
