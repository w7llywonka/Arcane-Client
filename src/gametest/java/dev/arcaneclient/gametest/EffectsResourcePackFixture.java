package dev.arcaneclient.gametest;

import dev.arcaneclient.ArcaneClient;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

/** Test-only selection of a staged, unmodified ZIP; never edits or repairs the supplied pack. */
final class EffectsResourcePackFixture {
    record Selection(Set<RepositorySource> providers, List<String> enabled) { }

    private EffectsResourcePackFixture() { }

    static Selection activate(ClientGameTestContext context) {
        String filename = System.getProperty("arcane.qa.resourcePack", "").strip();
        if (filename.isEmpty()) return null;
        if (!filename.matches("[^\\\\/:]+\\.zip")) throw new AssertionError("QA resource pack must be a staged ZIP filename, not a path.");
        Selection[] previous = new Selection[1];
        CompletableFuture<?>[] reload = new CompletableFuture<?>[1];
        context.runOnClient(client -> {
            Path root = client.getResourcePackDirectory().toAbsolutePath().normalize();
            Path pack = root.resolve(filename).normalize();
            require(pack.getParent().equals(root) && Files.isRegularFile(pack, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(pack), "QA pack is not a staged regular ZIP: " + filename);
            PackRepository manager = client.getResourcePackRepository();
            previous[0] = new Selection(providers(manager), List.copyOf(manager.getSelectedIds()));
            manager.reload();
            String id = "file/" + filename;
            if (!manager.isAvailable(id)) {
                // Some legacy packs have invalid modern pack.mcmeta. Use an in-memory,
                // explicitly UNKNOWN test profile to read their original ZIP assets.
                // This is not present in the shipping mod and does not alter the ZIP.
                var info = new PackLocationInfo(id, Component.literal(filename + " [QA legacy metadata]"), PackSource.DEFAULT, Optional.empty());
                var metadata = new Pack.Metadata(Component.literal("Unmodified legacy ZIP under test"),
                    PackCompatibility.UNKNOWN, FeatureFlagSet.of(), List.of());
                var profile = new Pack(info, new FilePackResources.FileResourcesSupplier(pack), metadata,
                    new PackSelectionConfig(false, Pack.Position.TOP, false));
                Set<RepositorySource> withFixture = new LinkedHashSet<>(previous[0].providers());
                withFixture.add(consumer -> consumer.accept(profile));
                setProviders(manager, withFixture);
                manager.reload();
                ArcaneClient.LOGGER.info("Effects QA reads {} with an in-memory compatibility override; source ZIP is unchanged", filename);
            }
            require(manager.isAvailable(id), "QA resource pack profile could not be selected: " + filename);
            List<String> enabled = new ArrayList<>(previous[0].enabled());
            if (!enabled.contains(id)) enabled.add(id);
            manager.setSelected(enabled);
            reload[0] = client.reloadResourcePacks();
        });
        awaitReload(context, reload[0]);
        context.runOnClient(client -> require(client.getResourcePackRepository().getSelectedIds().contains("file/" + filename),
            "QA pack selection must survive the actual resource reload"));
        return previous[0];
    }

    static void restore(ClientGameTestContext context, Selection selection) {
        if (selection == null) return;
        CompletableFuture<?>[] reload = new CompletableFuture<?>[1];
        context.runOnClient(client -> {
            PackRepository manager = client.getResourcePackRepository();
            setProviders(manager, selection.providers());
            manager.reload();
            manager.setSelected(selection.enabled());
            reload[0] = client.reloadResourcePacks();
        });
        awaitReload(context, reload[0]);
    }

    private static void awaitReload(ClientGameTestContext context, CompletableFuture<?> reload) {
        require(reload != null, "QA resource reload did not start");
        context.waitFor(client -> reload.isDone() && client.getOverlay() == null, 600);
        reload.join();
    }

    @SuppressWarnings("unchecked")
    private static Set<RepositorySource> providers(PackRepository manager) {
        try {
            Field field = PackRepository.class.getDeclaredField("providers");
            field.setAccessible(true);
            return (Set<RepositorySource>)field.get(manager);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read the isolated QA pack providers", failure);
        }
    }

    private static void setProviders(PackRepository manager, Set<RepositorySource> providers) {
        try {
            Field field = PackRepository.class.getDeclaredField("providers");
            field.setAccessible(true);
            field.set(manager, Set.copyOf(providers));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot update the isolated QA pack providers", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
