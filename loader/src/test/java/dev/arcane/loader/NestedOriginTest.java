package dev.arcane.loader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.junit.jupiter.api.Test;

final class NestedOriginTest {
    @Test
    void nestedClientOriginNeverRequestsFilesystemPaths() {
        Set<Path> selected = new HashSet<>();
        ModOrigin nested = new StubOrigin(ModOrigin.Kind.NESTED, List.of());
        assertDoesNotThrow(() -> ArcaneFabricLoader.addSelectedPathOrigins(selected, nested));
        assertTrue(selected.isEmpty());
    }

    @Test
    void pathClientOriginIsProtectedFromCleanup() {
        Set<Path> selected = new HashSet<>();
        Path client = Path.of("mods", "arcane-client.jar").toAbsolutePath().normalize();
        ArcaneFabricLoader.addSelectedPathOrigins(selected,
            new StubOrigin(ModOrigin.Kind.PATH, List.of(client)));
        assertTrue(selected.contains(client));
    }

    private record StubOrigin(Kind kind, List<Path> paths) implements ModOrigin {
        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public List<Path> getPaths() {
            if (kind == Kind.NESTED) throw new AssertionError("getPaths must not be used for NESTED origins");
            return paths;
        }

        @Override
        public String getParentModId() {
            return kind == Kind.NESTED ? "arcaneloader" : null;
        }

        @Override
        public String getParentSubLocation() {
            return kind == Kind.NESTED ? "META-INF/jars/arcane-client.jar" : null;
        }
    }
}
