package dev.arcaneclient.additions.intel;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcaneclient.ArcaneClient;
import dev.arcaneclient.ArcaneConfig;
import dev.arcaneclient.render.ArcaneVisibility;
import dev.arcaneclient.render.Render263;
import dev.arcaneclient.screen.*;
import java.util.*;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Bounded, received-data-only overlays. Nothing here contributes suspicious-chunk evidence. */
public final class IntelAdditions {
    /** Separate caps keep dense Block ESP selections from crowding out block entities. */
    private static final int MAX_TARGETS = 1536;
    private static final int MAX_PER_CHUNK = 128;
    private static final int MAX_RENDERED = 600;
    private static final int SCAN_BUDGET = 8192;
    private static final Pattern STAFF_RANK = Pattern.compile("(?i)\\b(owner|admin|administrator|moderator|mod|staff|helper)\\b");
    private record Target(BlockPos pos, boolean blockEntity, String label) { }
    private record Candidate(Target target, double distance) { }
    private static final Comparator<Candidate> NEAREST = Comparator.comparingDouble(Candidate::distance)
        .thenComparingLong(candidate -> candidate.target.pos.asLong());
    private static final Map<Long, List<Target>> targets = new LinkedHashMap<>();
    private static final ArrayDeque<LevelChunk> queue = new ArrayDeque<>();
    private static final Set<Long> queued = new HashSet<>();
    private static final Map<Long, Integer> scannedAt = new HashMap<>();
    private static final Set<Block> selected = new HashSet<>();
    private static List<Target> renderTargets = List.of();
    private static Vec3 queuedFrom;
    private static List<String> staff = List.of(), active = List.of();
    private static List<GuiCategory> catalog;
    private static ClientLevel world;
    private static Scan scan;
    private static int signature, tick, weather = -1;
    private static final int[] map = new int[32 * 32];
    private static int mapCursor, mapX = Integer.MIN_VALUE, mapZ;

    private IntelAdditions() { }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(IntelAdditions::renderWorld);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ArcaneClient.id("additional_intel"), (draw, counter) -> renderHud(draw));
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (world == level && scanEnabled()) enqueue(chunk, cameraPosition(Minecraft.getInstance()));
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            long key = chunk.getPos().pack();
            targets.remove(key);
            scannedAt.remove(key);
            queue.removeIf(c -> c.getPos().pack() == key);
            queued.remove(key);
            if (scan != null && scan.chunk == chunk) scan = null;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            ClientCommands.literal("arcane").then(ClientCommands.literal("blocks")
                .executes(ctx -> { ctx.getSource().sendFeedback(Component.literal("Block ESP: " + String.join(", ", settings().blocks))); return 1; })
                .then(ClientCommands.literal("clear").executes(ctx -> {
                    settings().blocks.clear(); ArcaneClient.config().save(); return 1;
                }))
                .then(blockCommand("add", true)).then(blockCommand("remove", false)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> blockCommand(String name, boolean add) {
        return ClientCommands.literal(name).then(ClientCommands.argument("block", StringArgumentType.word())
            .suggests((ctx, builder) -> {
                String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                BuiltInRegistries.BLOCK.stream().filter(block -> !block.defaultBlockState().isAir())
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString()).filter(s -> s.startsWith(prefix)).limit(100).forEach(builder::suggest);
                return builder.buildFuture();
            }).executes(ctx -> {
                Identifier id = Identifier.tryParse(StringArgumentType.getString(ctx, "block"));
                if (id == null || !BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK.getValue(id).defaultBlockState().isAir()) {
                    ctx.getSource().sendError(Component.literal("Choose a registered, non-air block.")); return 0;
                }
                List<String> blocks = settings().blocks;
                if (add && !blocks.contains(id.toString())) {
                    blocks.add(id.toString());
                } else if (!add) blocks.remove(id.toString());
                ArcaneClient.config().save();
                ctx.getSource().sendFeedback(Component.literal((add ? "Added " : "Removed ") + id + " from Block ESP.")); return 1;
            }));
    }

    private static IntelAdditionsConfig settings() { return ArcaneClient.config().intelAdditions; }
    private static boolean scanEnabled() {
        if (ArcaneClient.config() == null) return false;
        var c = settings(); return c.blockEsp || c.blockEntityEsp || c.spawnerNametags;
    }

    public static void reset(Minecraft client) {
        world = null; scan = null; signature = 0; tick = 0; weather = -1;
        targets.clear(); queue.clear(); queued.clear(); scannedAt.clear(); selected.clear();
        renderTargets = List.of(); queuedFrom = null;
        staff = List.of(); active = List.of(); catalog = null;
        Arrays.fill(map, 0); mapCursor = 0; mapX = Integer.MIN_VALUE;
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) { if (world != null) reset(client); return; }
        if (world != client.level) { reset(client); world = client.level; }
        var c = settings(); tick++;
        int next = Objects.hash(c.blockEsp, c.blockAll, c.blockEntityEsp, c.spawnerNametags,
            c.blockEntityDeepOnly, c.blockRange, c.blockEntityRange, c.blocks);
        if (next != signature) {
            targets.clear(); queue.clear(); queued.clear(); scannedAt.clear(); scan = null; selected.clear(); signature = next;
            renderTargets = List.of(); queuedFrom = null;
            for (String name : c.blocks) {
                Identifier id = Identifier.tryParse(name);
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.BLOCK.getValue(id).defaultBlockState().isAir()) {
                    selected.add(BuiltInRegistries.BLOCK.getValue(id));
                }
            }
            if (scanEnabled()) queueNearby(client);
        }
        if (scanEnabled()) {
            Vec3 camera = cameraPosition(client);
            if (queuedFrom == null || queuedFrom.distanceToSqr(camera) >= 64 || tick % 20 == 0) queueNearby(client);
            if (scan != null && scan.origin.distanceToSqr(camera) > 256) {
                LevelChunk previous = scan.chunk;
                queued.remove(previous.getPos().pack()); scan = null;
                enqueue(previous, camera);
                prioritizeQueue(camera);
            }
            int work = SCAN_BUDGET, jobs = 0;
            while (work > 0 && jobs < 4) {
                if (scan == null) {
                    LevelChunk chunk = queue.poll();
                    if (chunk == null) break;
                    if (!nearby(chunk, camera) || world.getChunkSource().getChunk(chunk.getPos().x(), chunk.getPos().z(), false) != chunk) {
                        queued.remove(chunk.getPos().pack()); continue;
                    }
                    scan = new Scan(chunk, c, camera);
                }
                work -= Math.max(1, scan.step(work));
                // Publish discovery progress immediately, but keep an existing chunk's
                // stable nearest slots until its replacement scan has completed.
                if (scan.done() || !scan.refresh) targets.put(scan.chunk.getPos().pack(), scan.results());
                if (!scan.done()) break;
                scannedAt.put(scan.chunk.getPos().pack(), tick);
                queued.remove(scan.chunk.getPos().pack()); scan = null; jobs++;
            }
            trim(client);
        }
        if (c.regionMap) updateMap(client);
        if (tick % 20 == 0) {
            updateWeather(client, c);
            staff = c.staffList ? readStaff(client) : List.of();
            if (c.activeModules) {
                if (catalog == null) catalog = ModuleCatalog.build(ArcaneClient.config(), client);
                active = catalog.stream().flatMap(category -> category.modules().stream()).filter(GuiModule::enabled)
                    .map(GuiModule::name).filter(name -> !name.equals("Active Modules")).limit(16).toList();
            } else active = List.of();
        }
    }

    private static Vec3 cameraPosition(Minecraft client) {
        return client.gameRenderer.mainCamera().position();
    }

    private static int scanRange() {
        var c = settings();
        return Math.max(c.blockEsp ? c.blockRange : 0, c.blockEntityEsp || c.spawnerNametags ? c.blockEntityRange : 0);
    }

    private static double chunkDistance(LevelChunk chunk, Vec3 origin) {
        double dx = Math.max(Math.max(chunk.getPos().getMinBlockX() - origin.x, 0), origin.x - (chunk.getPos().getMinBlockX() + 16));
        double dz = Math.max(Math.max(chunk.getPos().getMinBlockZ() - origin.z, 0), origin.z - (chunk.getPos().getMinBlockZ() + 16));
        return dx * dx + dz * dz;
    }

    private static boolean nearby(LevelChunk chunk, Vec3 origin) {
        return chunkDistance(chunk, origin) <= (double) scanRange() * scanRange();
    }

    private static double distance(BlockPos pos, Vec3 origin) {
        double dx = pos.getX() + 0.5 - origin.x, dy = pos.getY() + 0.5 - origin.y, dz = pos.getZ() + 0.5 - origin.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void enqueue(LevelChunk chunk, Vec3 origin) {
        if (nearby(chunk, origin) && queued.size() < 625 && queued.add(chunk.getPos().pack())) {
            if (!queue.isEmpty() && chunkDistance(chunk, origin) < chunkDistance(queue.peek(), origin)) queue.addFirst(chunk);
            else queue.addLast(chunk);
        }
    }

    private static void prioritizeQueue(Vec3 origin) {
        ArrayList<LevelChunk> pending = new ArrayList<>(queue);
        pending.removeIf(chunk -> !nearby(chunk, origin)
            || world.getChunkSource().getChunk(chunk.getPos().x(), chunk.getPos().z(), false) != chunk);
        // Finish unseen and older chunks before scheduling refreshes. Re-adding nearby
        // chunks every second must not permanently starve the rest of the loaded radius.
        pending.sort(Comparator.comparingInt((LevelChunk chunk) -> scannedAt.getOrDefault(chunk.getPos().pack(), Integer.MIN_VALUE))
            .thenComparingDouble(chunk -> chunkDistance(chunk, origin))
            .thenComparingLong(chunk -> chunk.getPos().pack()));
        queue.clear(); queue.addAll(pending);
        queued.clear();
        for (LevelChunk chunk : queue) queued.add(chunk.getPos().pack());
        if (scan != null) queued.add(scan.chunk.getPos().pack());
    }

    private static void queueNearby(Minecraft client) {
        Vec3 origin = cameraPosition(client);
        scannedAt.keySet().removeIf(key -> {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            LevelChunk chunk = world.getChunkSource().getChunk(pos.x(), pos.z(), false);
            return chunk == null || !nearby(chunk, origin);
        });
        prioritizeQueue(origin);
        int centerX = Mth.floor(origin.x) >> 4, centerZ = Mth.floor(origin.z) >> 4;
        int radius = Math.min(12, (scanRange() + 15) / 16);
        for (int ring = 0; ring <= radius; ring++) for (int z = -ring; z <= ring; z++) for (int x = -ring; x <= ring; x++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
            LevelChunk chunk = world.getChunkSource().getChunk(centerX + x, centerZ + z, false);
            if (chunk != null) enqueue(chunk, origin);
        }
        prioritizeQueue(origin);
        queuedFrom = origin;
    }

    private static void trim(Minecraft client) {
        Vec3 origin = cameraPosition(client);
        var c = settings();
        ArrayList<Candidate> nearest = new ArrayList<>();
        for (var entry : targets.entrySet()) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()));
            if (world.getChunkSource().getChunk(pos.x(), pos.z(), false) == null) continue;
            for (Target target : entry.getValue()) {
                int range = target.blockEntity ? c.blockEntityRange : c.blockRange;
                double distance = distance(target.pos, origin);
                if (distance <= (double) range * range) nearest.add(new Candidate(target, distance));
            }
        }
        nearest.sort(NEAREST);
        targets.clear();
        ArrayList<Target> ordered = new ArrayList<>();
        int blocks = 0, entities = 0;
        for (Candidate candidate : nearest) {
            Target target = candidate.target;
            if (target.blockEntity ? entities >= MAX_TARGETS : blocks >= MAX_TARGETS) continue;
            if (target.blockEntity) entities++; else blocks++;
            ordered.add(target);
            targets.computeIfAbsent(ChunkPos.pack(target.pos.getX() >> 4, target.pos.getZ() >> 4), key -> new ArrayList<>()).add(target);
        }
        renderTargets = List.copyOf(ordered);
    }

    private static boolean matches(BlockState state, boolean all) {
        return !state.isAir() && (all || selected.contains(state.getBlock()));
    }

    private static final class Scan {
        final LevelChunk chunk;
        final Vec3 origin;
        final IntelAdditionsConfig config;
        final PriorityQueue<Candidate> blockMatches = new PriorityQueue<>(NEAREST.reversed());
        final PriorityQueue<Candidate> entityMatches = new PriorityQueue<>(NEAREST.reversed());
        final Iterator<net.minecraft.world.level.block.entity.BlockEntity> entities;
        final List<Integer> sections = new ArrayList<>();
        final boolean blocks, all, refresh;
        int section, index;

        Scan(LevelChunk chunk, IntelAdditionsConfig c, Vec3 origin) {
            this.chunk = chunk; this.config = c; this.origin = origin; blocks = c.blockEsp; all = c.blockAll;
            refresh = targets.containsKey(chunk.getPos().pack());
            entities = (c.blockEntityEsp || c.spawnerNametags
                ? new ArrayList<>(chunk.getBlockEntities().values()) : List.<net.minecraft.world.level.block.entity.BlockEntity>of()).iterator();
            if (blocks) {
                for (int i = 0; i < chunk.getSections().length; i++) sections.add(i);
                sections.sort(Comparator.comparingDouble(this::sectionDistance).thenComparingInt(Integer::intValue));
            }
        }

        private double sectionDistance(int sectionIndex) {
            int bottom = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            double dy = Math.max(Math.max(bottom - origin.y, 0), origin.y - (bottom + 16));
            return chunkDistance(chunk, origin) + dy * dy;
        }

        private static void retain(PriorityQueue<Candidate> matches, Candidate candidate) {
            if (matches.size() < MAX_PER_CHUNK) matches.add(candidate);
            else if (NEAREST.compare(candidate, matches.peek()) < 0) {
                matches.poll(); matches.add(candidate);
            }
        }

        int step(int budget) {
            int visited = 0;
            while (entities.hasNext() && visited < budget) {
                var be = entities.next(); visited++;
                if (be.isRemoved()) continue;
                double distance = distance(be.getBlockPos(), origin);
                if (distance > (double) config.blockEntityRange * config.blockEntityRange) continue;
                boolean spawner = be instanceof SpawnerBlockEntity || be.getBlockState().is(Blocks.TRIAL_SPAWNER);
                boolean highlight = config.blockEntityEsp && (!config.blockEntityDeepOnly || be.getBlockPos().getY() < 0);
                if (!highlight && !(spawner && config.spawnerNametags)) continue;
                if (entityMatches.size() == MAX_PER_CHUNK && distance > entityMatches.peek().distance) continue;
                String label = null;
                if (spawner && config.spawnerNametags) {
                    label = be.getBlockState().is(Blocks.TRIAL_SPAWNER) ? "Trial spawner" : "Spawner";
                    if (be instanceof SpawnerBlockEntity mob) {
                        var entity = mob.getSpawner().getOrCreateDisplayEntity(world, be.getBlockPos());
                        if (entity != null) label = entity.getType().getDescription().getString() + " spawner";
                    }
                }
                retain(entityMatches, new Candidate(new Target(be.getBlockPos().immutable(), true, label), distance));
            }
            while (section < sections.size() && visited < budget) {
                int sectionIndex = sections.get(section);
                var current = chunk.getSections()[sectionIndex];
                if (index == 0) {
                    visited++;
                    double lowerBound = sectionDistance(sectionIndex);
                    // Sections are ordered by their nearest possible distance. A full heap can
                    // discard farther sections, but never terminate merely after 128 low-Y hits.
                    if (lowerBound > (double) config.blockRange * config.blockRange
                        || blockMatches.size() == MAX_PER_CHUNK && lowerBound > blockMatches.peek().distance) {
                        section = sections.size(); break;
                    }
                    if (current.hasOnlyAir() || !all && !current.maybeHas(state -> matches(state, false))) {
                        section++; continue;
                    }
                    if (visited >= budget) break;
                }
                int x = index & 15, z = index >> 4 & 15, y = index >> 8;
                if (matches(current.getBlockState(x, y, z), all)) {
                    int bx = chunk.getPos().getMinBlockX() + x;
                    int by = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex)) + y;
                    int bz = chunk.getPos().getMinBlockZ() + z;
                    double dx = bx + 0.5 - origin.x, dy = by + 0.5 - origin.y, dz = bz + 0.5 - origin.z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance <= (double) config.blockRange * config.blockRange
                        && (blockMatches.size() < MAX_PER_CHUNK || distance <= blockMatches.peek().distance)) {
                        retain(blockMatches, new Candidate(new Target(new BlockPos(bx, by, bz), false, null), distance));
                    }
                }
                index++; visited++;
                if (index == 4096) { index = 0; section++; }
            }
            return visited;
        }

        List<Target> results() {
            ArrayList<Target> result = new ArrayList<>(blockMatches.size() + entityMatches.size());
            for (Candidate candidate : blockMatches) result.add(candidate.target);
            for (Candidate candidate : entityMatches) result.add(candidate.target);
            return result;
        }

        boolean done() { return !entities.hasNext() && section >= sections.size(); }
    }

    private static void renderWorld(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level != world || context.poseStack() == null
            || context.levelState() == null || context.levelState().cameraRenderState == null
            || ArcaneVisibility.overlaysHidden() || !scanEnabled()) return;
        var c = settings(); var camera = Render263.camera(context);
        var mainCamera = client.gameRenderer.mainCamera();
        Vec3 tracerStart = camera.add(Vec3.directionFromRotation(mainCamera.xRot(), mainCamera.yRot()).scale(0.08));
        Render263.Batch batch = Render263.batch(context);
        int blocks = 0, entities = 0;
        List<Target> labelTargets = new ArrayList<>(48);
        for (Target target : renderTargets) {
            if (target.blockEntity ? entities >= MAX_RENDERED : blocks >= MAX_RENDERED) continue;
            BlockPos p = target.pos;
            int range = target.blockEntity ? c.blockEntityRange : c.blockRange;
            if (distance(p, camera) > (double) range * range) continue;
            LevelChunk received = world.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, false);
            if (received == null) continue;
            if (target.blockEntity) {
                var blockEntity = received.getBlockEntities().get(p);
                if (blockEntity == null || blockEntity.isRemoved()) continue;
            } else if (!matches(received.getBlockState(p), c.blockAll)) continue;
            boolean outline = target.blockEntity ? c.blockEntityEsp && (!c.blockEntityDeepOnly || p.getY() < 0) : c.blockEsp;
            int color = target.blockEntity ? c.entityColor : c.blockColor;
            if (outline) {
                batch.outline(Shapes.block(), p.getX(), p.getY(), p.getZ(), color, 1.5f, true);
                if (target.blockEntity ? c.blockEntityTracers : c.blockTracers) {
                    Vec3 end = Vec3.atCenterOf(p);
                    batch.line(tracerStart.x, tracerStart.y, tracerStart.z,
                        end.x, end.y, end.z, color, 1.2f, true);
                }
            }
            BlockState state = received.getBlockState(p);
            if (c.spawnerNametags && target.label != null && (state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER))
                && labelTargets.size() < 48) labelTargets.add(target);
            if (target.blockEntity) entities++; else blocks++;
        }
        batch.submit();
        // Text can switch the shared immediate buffer and invalidate `lines`.
        // Complete all outlines and tracers before requesting any text layers.
        for (Target target : labelTargets) {
            int color = target.blockEntity ? c.entityColor : c.blockColor;
            Render263.text(context, ArcaneFont.renderer(client),
                target.label + " · " + Math.round(Math.sqrt(target.pos.distToCenterSqr(camera))) + "m",
                Vec3.atCenterOf(target.pos).add(0.0, 0.75, 0.0), 0.025f, color, true);
        }
    }

    private static void updateWeather(Minecraft client, IntelAdditionsConfig c) {
        int now = world.isThundering() ? 2 : world.isRaining() ? 1 : 0;
        if (c.weatherNotifier && weather >= 0 && now != weather) client.player.sendOverlayMessage(Component.literal("Weather · " + new String[]{"Clear", "Rain", "Thunder"}[now]));
        weather = now;
    }
    private static List<String> readStaff(Minecraft client) {
        if (client.getConnection() == null) return List.of();
        ArrayList<String> names = new ArrayList<>();
        for (var entry : client.getConnection().getOnlinePlayers()) {
            var team = entry.getTeam();
            String prefix = team == null ? "" : team.getPlayerPrefix().getString();
            if (!STAFF_RANK.matcher(prefix).find()) continue;
            names.add(ArcaneClient.config().streamerMode ? "Player" : entry.getProfile().name());
            if (names.size() == 8) break;
        }
        return List.copyOf(names);
    }
    private static void updateMap(Minecraft client) {
        int x = Math.floorDiv(client.player.getBlockX(), 16) * 16 - 64, z = Math.floorDiv(client.player.getBlockZ(), 16) * 16 - 64;
        if (mapX != x || mapZ != z) { Arrays.fill(map, 0); mapX = x; mapZ = z; mapCursor = 0; }
        for (int work = 0; work < 64; work++) {
            int index = mapCursor++ & 1023, bx = mapX + (index & 31) * 4, bz = mapZ + (index >> 5) * 4;
            LevelChunk chunk = world.getChunkSource().getChunk(bx >> 4, bz >> 4, false);
            if (chunk == null) { map[index] = 0; continue; }
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15);
            BlockPos pos = new BlockPos(bx, y, bz);
            map[index] = 0xFF000000 | chunk.getBlockState(pos).getMapColor(world, pos).col;
        }
    }
    private static void renderHud(GuiGraphicsExtractor draw) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level != world || ArcaneVisibility.overlaysHidden() || ArcaneSettingsScreen.isOpen(client)) return;
        var c = settings(); Font font = ArcaneFont.renderer(client);
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        if (c.regionMap && !ArcaneClient.config().streamerMode) {
            int size = c.mapSize, x = 8, y = Math.max(8, height - size - 30);
            RoundedGui.fill(draw, x - 3, y - 3, size + 6, size + 6, 5, 0xEB111118);
            draw.enableScissor(x, y, x + size, y + size);
            for (int i = 0; i < map.length; i++) if (map[i] != 0) {
                int px = i & 31, pz = i >> 5;
                draw.fill(x + px * size / 32, y + pz * size / 32, x + (px + 1) * size / 32, y + (pz + 1) * size / 32, map[i]);
            }
            int cx = x + (client.player.getBlockX() - mapX) * size / 128;
            int cy = y + (client.player.getBlockZ() - mapZ) * size / 128;
            draw.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
            draw.disableScissor(); draw.text(font, ArcaneFont.text("N"), x + size / 2, y + 2, 0xFFFFFFFF, true);
        }
        int lineY = 10;
        if (c.staffList) {
            lineY = rightLine(draw, font, width, lineY, "Ranked online · " + staff.size(), 0xFF72CFC6);
            for (String name : staff) lineY = rightLine(draw, font, width, lineY, name, 0xFFF0EBF6);
            lineY += 8;
        }
        if (c.activeModules) for (String name : active) lineY = rightLine(draw, font, width, lineY, name, 0xFFDF8CBF);
        if (c.keystrokes) {
            String[] labels = {client.options.keyUp.getTranslatedKeyMessage().getString(), client.options.keyLeft.getTranslatedKeyMessage().getString(), client.options.keyDown.getTranslatedKeyMessage().getString(), client.options.keyRight.getTranslatedKeyMessage().getString()};
            boolean[] pressed = {client.options.keyUp.isDown(), client.options.keyLeft.isDown(), client.options.keyDown.isDown(), client.options.keyRight.isDown()};
            for (int i = 0; i < 4; i++) {
                int x = width - 68 + (i == 0 ? 20 : (i - 1) * 20), y = height - 72 + (i == 0 ? 0 : 20);
                RoundedGui.fill(draw, x, y, 18, 18, 4, pressed[i] ? 0xDA903C69 : 0xCE16121C);
                String label = labels[i]; if (label.length() > 2) label = label.substring(0, 2);
                draw.text(font, ArcaneFont.text(label), x + (18 - ArcaneFont.width(font, label)) / 2, y + 5, 0xFFFFFFFF, false);
            }
        }
    }
    private static int rightLine(GuiGraphicsExtractor draw, Font font, int width, int y, String text, int color) {
        draw.text(font, ArcaneFont.text(text), width - 10 - ArcaneFont.width(font, text), y, color, true); return y + 11;
    }

    public static List<GuiModule> espModules(ArcaneConfig config, Minecraft client) {
        var c = config.intelAdditions;
        return List.of(
            GuiModule.toggle("Block ESP", "Highlights any chosen non-air block, or all non-air blocks received from the server. Keeps nearest matches; never changes Chunk Finder scores.", () -> c.blockEsp, v -> c.blockEsp = v)
                .with(new GuiSetting.Cycle("Choose blocks", () -> Integer.toString(c.blocks.size()),
                    () -> client.gui.setScreen(new BlockEspPickerScreen(client.gui.screen(), config))))
                .with(new GuiSetting.Cycle("Add looked-at block", () -> "Add", () -> {
                    if (client.level != null && client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                        LevelChunk chunk = client.level.getChunkSource().getChunk(hit.getBlockPos().getX() >> 4, hit.getBlockPos().getZ() >> 4, false);
                        if (chunk != null) addBlock(config, chunk.getBlockState(hit.getBlockPos()).getBlock());
                    }
                }))
                .with(new GuiSetting.Cycle("Add held block", () -> "Add", () -> {
                    if (client.player != null && client.player.getMainHandItem().getItem() instanceof BlockItem blockItem) {
                        addBlock(config, blockItem.getBlock());
                    }
                }))
                .with(new GuiSetting.Cycle("Add 26.3 palette", () -> "Add", () -> add263Palette(config)))
                .with(new GuiSetting.Toggle("All non-air blocks", () -> c.blockAll, v -> c.blockAll = v))
                .with(new GuiSetting.Swatch("Block color", () -> c.blockColor, v -> c.blockColor = v))
                .with(new GuiSetting.Slider("Range", () -> c.blockRange, v -> c.blockRange = v, 16, 192, "m"))
                .with(new GuiSetting.Toggle("Tracers", () -> c.blockTracers, v -> c.blockTracers = v))
                .with(new GuiSetting.Info("Nearest per chunk", () -> Integer.toString(MAX_PER_CHUNK)))
                .with(new GuiSetting.Info("Draw / cache cap", () -> MAX_RENDERED + " / " + MAX_TARGETS))
                .with(new GuiSetting.Info("Scan budget / tick", () -> Integer.toString(SCAN_BUDGET))).build(),
            GuiModule.toggle("Block Entity ESP", "Received block entities only. This never changes Chunk Finder scores.", () -> c.blockEntityEsp, v -> c.blockEntityEsp = v)
                .with(new GuiSetting.Toggle("Below Y 0 only", () -> c.blockEntityDeepOnly, v -> c.blockEntityDeepOnly = v))
                .with(new GuiSetting.Toggle("Tracers", () -> c.blockEntityTracers, v -> c.blockEntityTracers = v))
                .with(new GuiSetting.Slider("Range", () -> c.blockEntityRange, v -> c.blockEntityRange = v, 16, 192, "m"))
                .with(new GuiSetting.Swatch("Color", () -> c.entityColor, v -> c.entityColor = v))
                .with(new GuiSetting.Info("Nearest per chunk", () -> Integer.toString(MAX_PER_CHUNK)))
                .with(new GuiSetting.Info("Draw / cache cap", () -> MAX_RENDERED + " / " + MAX_TARGETS)).build(),
            GuiModule.toggle("Spawner Nametags", "Labels loaded spawners with the mob type sent by the server and their distance.", () -> c.spawnerNametags, v -> c.spawnerNametags = v).build());
    }

    private static void addBlock(ArcaneConfig config, Block block) {
        if (block.defaultBlockState().isAir()) return;
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (!config.intelAdditions.blocks.contains(id)) config.intelAdditions.blocks.add(id);
        config.save();
    }

    private static void add263Palette(ArcaneConfig config) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().isAir()) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (path.contains("poplar") || path.contains("shelf_mushroom") || path.equals("red_shrub")
                || path.endsWith("_wool_slab") || path.endsWith("_wool_stairs")
                || path.endsWith("_concrete_slab") || path.endsWith("_concrete_stairs")) {
                if (!config.intelAdditions.blocks.contains(id)) config.intelAdditions.blocks.add(id);
            }
        }
        config.save();
    }
    public static List<GuiModule> renderModules(ArcaneConfig config, Minecraft client) {
        var c = config.intelAdditions;
        return List.of(
            GuiModule.toggle("Region Map", "North-up terrain minimap from loaded surface columns; hidden in Streamer Mode.", () -> c.regionMap, v -> c.regionMap = v)
                .with(new GuiSetting.Slider("Size", () -> c.mapSize, v -> c.mapSize = v, 64, 160, "px")).build(),
            GuiModule.toggle("Skin Protect", "Replaces player skins locally with the default skin. Does not change what other players see.", () -> c.skinProtect, v -> c.skinProtect = v).build(),
            GuiModule.toggle("Keystrokes", "Compact movement-key display using your actual keybinds.", () -> c.keystrokes, v -> c.keystrokes = v).build(),
            GuiModule.toggle("Active Modules", "Optional list of enabled modules, capped at sixteen lines.", () -> c.activeModules, v -> c.activeModules = v).build());
    }
    public static List<GuiModule> utilityModules(ArcaneConfig config, Minecraft client) {
        var c = config.intelAdditions;
        return List.of(
            GuiModule.toggle("Staff List", "Shows online players with visible staff rank prefixes; cannot detect hidden staff.", () -> c.staffList, v -> c.staffList = v).build(),
            GuiModule.toggle("Weather Notifier", "One actionbar message when the received weather changes; no permanent HUD.", () -> c.weatherNotifier, v -> c.weatherNotifier = v).build());
    }
}
