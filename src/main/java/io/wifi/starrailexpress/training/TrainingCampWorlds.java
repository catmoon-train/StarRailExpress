package io.wifi.starrailexpress.training;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 八个数据包维度的服务器线程建图器，不创建 ServerLevel，也不向打开的存档写 region。
 * 调用方只需在一个常量注册的服务端 tick 事件中调用 tick；prepare 不代表已经可进入。
 */
public final class TrainingCampWorlds implements AutoCloseable {
    public static final int MAX_WRITES_PER_TICK = 2048;
    public static final long MAX_TICK_NANOS = 4_000_000L;
    private static final int QUANTUM = 64;
    private static final TicketType<ChunkPos> BUILD_TICKET = TicketType.create(
            "sre_training_build", Comparator.comparingLong(ChunkPos::toLong));
    private static final String TEMPLATE = "/data/starrailexpress/training/world-template.zip";
    private static final LinkOption[] NO_LINKS = {LinkOption.NOFOLLOW_LINKS};
    private static Template cachedTemplate;
    private final MinecraftServer server;
    private final Path root;
    private final Task[] tasks = new Task[TrainingCampPool.CAPACITY];
    private final boolean[] ready = new boolean[TrainingCampPool.CAPACITY];
    private Template template;
    private BlockState[] states;
    private int nextSlot;
    private boolean closed;

    public TrainingCampWorlds(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().normalize();
    }

    public static ResourceKey<Level> dimension(int slot) {
        checkSlot(slot);
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("starrailexpress", "training_" + slot));
    }

    /** 只在首次需要时解析一次内置 ZIP；不复制 level.dat、存档身份或任何 region 文件。 */
    public void ensureMapConfiguration() {
        requireOpen();
        try {
            if (template == null) template = template();
            ensureConfiguration(root, template);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot install training map configuration", e);
        }
    }

    /** 创建或恢复该席位唯一的建图任务；固定维度未加载时明确失败。 */
    public boolean prepare(int slot) {
        requireOpen();
        checkSlot(slot);
        ServerLevel level = server.getLevel(dimension(slot));
        if (level == null) throw new IllegalStateException("Missing datapack dimension: " + dimension(slot).location());
        // 不改可能由多个维度共享的 GameRules。
        level.getChunkSource().setSpawnSettings(false, false);
        if (ready[slot]) return true;
        if (tasks[slot] == null) {
            ensureMapConfiguration();
            resolveStates();
            tasks[slot] = new Task(slot, level);
        }
        Task task = tasks[slot];
        if (task.failure != null) throw task.failure;
        task.active = true;
        return false;
    }

    /** 总预算而非每维度预算；64 个单元一轮，等待区块时让其他席位继续。 */
    public void tick() {
        requireOpen();
        long deadline = System.nanoTime() + MAX_TICK_NANOS;
        // 难度变更会重设所有维度的刷怪开关；固定八维度每 tick 恢复，查询不会加载区块。
        for (int slot = 0; slot < tasks.length; slot++) {
            ServerLevel level = server.getLevel(dimension(slot));
            if (level != null) level.getChunkSource().setSpawnSettings(false, false);
        }
        int remaining = MAX_WRITES_PER_TICK;
        int idle = 0;
        while (remaining > 0 && System.nanoTime() < deadline && idle < tasks.length) {
            int slot = nextSlot;
            nextSlot = (nextSlot + 1) % tasks.length;
            Task task = tasks[slot];
            if (task == null || !task.active || ready[slot] || task.failure != null) { idle++; continue; }
            try {
                int used = task.advance(Math.min(QUANTUM, remaining), deadline);
                remaining -= used;
                idle = used == 0 ? idle + 1 : 0;
            } catch (IOException | RuntimeException e) {
                task.failure = new IllegalStateException("Training camp " + slot + " preparation failed", e);
                task.active = false;
                task.unticket();
                idle++;
            }
        }
    }

    public boolean isReady(int slot) {
        requireThread();
        checkSlot(slot);
        return !closed && ready[slot];
    }

    public int progress(int slot) {
        requireThread();
        checkSlot(slot);
        if (closed) return 0;
        if (ready[slot]) return 100;
        Task task = tasks[slot];
        if (task == null) return 0;
        long done = task.verifying ? task.chunkIndex : task.completed;
        long total = task.verifying ? template.chunks.size() : template.cells;
        return (int) Math.min(99, done * 100 / Math.max(1, total));
    }

    /**
     * 暂停未完成任务并立即撤掉构建 ticket，下一租约从同一游标继续。
     * 已完成场地在本服务端生命周期内永不重建；没有异步写回调会覆盖后来的使用者。
     */
    public void release(int slot) {
        requireThread();
        checkSlot(slot);
        Task task = tasks[slot];
        if (task != null) { task.active = false; task.unticket(); }
    }

    @Override
    public void close() {
        requireThread();
        if (closed) return;
        for (int slot = 0; slot < tasks.length; slot++) release(slot);
        closed = true;
        java.util.Arrays.fill(tasks, null);
    }

    private void requireThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Training camps must run on the server thread");
    }

    private void requireOpen() {
        requireThread();
        if (closed) throw new IllegalStateException("Training camp worlds are closed");
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= TrainingCampPool.CAPACITY) throw new IllegalArgumentException("Invalid camp slot: " + slot);
    }

    private void resolveStates() {
        if (states != null) return;
        var blocks = server.registryAccess().lookup(Registries.BLOCK).orElseThrow();
        BlockState[] resolved = new BlockState[template.palette.size()];
        for (int i = 0; i < resolved.length; i++) {
            CompoundTag tag = template.palette.get(i);
            ResourceLocation name = ResourceLocation.parse(tag.getString("Name"));
            // 原版 readBlockState 会把未知方块/属性回退为空气/默认值，这里必须显式拒绝。
            blocks.getOrThrow(ResourceKey.create(Registries.BLOCK, name));
            BlockState state = NbtUtils.readBlockState(blocks, tag);
            CompoundTag properties = tag.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                var property = state.getBlock().getStateDefinition().getProperty(key);
                if (property == null || property.getValue(properties.getString(key)).isEmpty())
                    throw new IllegalStateException("Invalid training block state: " + tag);
            }
            resolved[i] = state;
        }
        states = resolved;
    }

    private final class Task {
        final int slot;
        final ServerLevel level;
        boolean verifying;
        boolean active;
        int chunkIndex;
        int sectionIndex;
        int cellIndex;
        int entityIndex;
        long completed;
        ChunkPos ticket;
        IllegalStateException failure;

        Task(int slot, ServerLevel level) {
            this.slot = slot;
            this.level = level;
            this.verifying = markerMatches(root, slot, template);
        }

        int advance(int allowance, long deadline) throws IOException {
            // 即使调用方传错 level，也绝不允许写到普通世界。
            if (!level.dimension().equals(dimension(slot))) throw new IllegalStateException("Not an exact training dimension");
            if (chunkIndex == template.chunks.size()) {
                if (!verifying) writeMarker(root, slot, template);
                ready[slot] = true;
                active = false;
                unticket();
                return 1;
            }
            ChunkData data = template.chunks.get(chunkIndex);
            ChunkPos pos = new ChunkPos(data.x, data.z);
            if (ticket == null) {
                ticket = pos;
                // FULL 级别的临时 ticket，既不强制 ticking，也不保留整张地图。
                level.getChunkSource().addRegionTicket(BUILD_TICKET, pos, 0, pos);
                return 0;
            }
            // getChunkFuture 在服务器线程内部会 managedBlock，故只轮询非阻塞的 getChunkNow。
            LevelChunk chunk = level.getChunkSource().getChunkNow(data.x, data.z);
            if (chunk == null) return 0;
            if (verifying) {
                boolean valid = sentinelMatches(chunk, data);
                unticket();
                if (valid) chunkIndex++;
                else {
                    deleteMarker(root, slot);
                    verifying = false;
                    chunkIndex = 0;
                }
                return 1;
            }
            // 重建开始前先撤销旧标记，失败或部分放置绝不能伪装为 ready。
            if (completed == 0) deleteMarker(root, slot);
            int used = 0;
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
            while (used < allowance && System.nanoTime() < deadline) {
                if (sectionIndex < data.sections.size()) {
                    Section section = data.sections.get(sectionIndex);
                    int index = cellIndex;
                    blockPos.set(data.x * 16 + (index & 15), section.y * 16 + (index >> 8),
                            data.z * 16 + ((index >> 4) & 15));
                    BlockState expected = states[section.states[index]];
                    if (chunk.getBlockState(blockPos) != expected) {
                        // 仅当前已就绪区块；不通知相邻方块，避免门/铁栏更新加载邻区块。
                        if (!level.setBlock(blockPos, expected, 2 | 16 | 32)
                                && chunk.getBlockState(blockPos) != expected)
                            throw new IllegalStateException("Could not place training block at " + blockPos);
                    }
                    cellIndex++;
                    completed++;
                    used++;
                    if (cellIndex == 4096) { cellIndex = 0; sectionIndex++; }
                } else if (entityIndex < data.entities.size()) {
                    CompoundTag tag = data.entities.get(entityIndex).copy();
                    BlockPos entityPos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                    BlockEntity entity = chunk.getBlockEntity(entityPos);
                    if (entity == null) throw new IllegalStateException("Missing training block entity at " + entityPos);
                    entity.loadWithComponents(tag, level.registryAccess());
                    entity.setChanged();
                    BlockState state = chunk.getBlockState(entityPos);
                    level.sendBlockUpdated(entityPos, state, state, 2);
                    entityIndex++;
                    completed++;
                    used++;
                } else {
                    unticket();
                    chunkIndex++;
                    sectionIndex = cellIndex = entityIndex = 0;
                    break;
                }
            }
            return used;
        }

        boolean sentinelMatches(LevelChunk chunk, ChunkData data) {
            int y = template.spawn.getY() - 1;
            BlockPos floor = new BlockPos(data.x * 16 + 8, y, data.z * 16 + 8);
            if (chunk.getBlockState(floor) != states[data.stateAt(8, y, 8)]) return false;
            if ((template.spawn.getX() >> 4) == data.x && (template.spawn.getZ() >> 4) == data.z) {
                BlockPos spawn = template.spawn;
                return chunk.getBlockState(spawn.below()) == states[data.stateAt(spawn.getX() & 15, y, spawn.getZ() & 15)]
                        && chunk.getBlockState(spawn).isAir() && chunk.getBlockState(spawn.above()).isAir();
            }
            return true;
        }

        void unticket() {
            if (ticket != null) {
                level.getChunkSource().removeRegionTicket(BUILD_TICKET, ticket, 0, ticket);
                ticket = null;
            }
        }
    }

    private static synchronized Template template() throws IOException {
        if (cachedTemplate == null) {
            try (InputStream stream = TrainingCampWorlds.class.getResourceAsStream(TEMPLATE)) {
                if (stream == null) throw new IOException("Missing training template: " + TEMPLATE);
                cachedTemplate = Template.read(stream);
            }
        }
        return cachedTemplate;
    }

    // 以下为无需注册表 Bootstrap 的资源解析和小型配置文件操作，供定向测试复用。
    static void ensureConfiguration(Path root, Template template) throws IOException {
        installMissing(root.resolve("train_maps/training.json"), template.mapConfiguration);
        installMissing(root.resolve("readyArea.json"), template.readyConfiguration);
    }

    private static void installMissing(Path target, byte[] contents) throws IOException {
        safePath(target);
        Files.createDirectories(target.getParent());
        if (Files.exists(target, NO_LINKS)) {
            if (!Files.isRegularFile(target, NO_LINKS)) throw new IOException("Not a regular training config: " + target);
            return;
        }
        Path temporary = Files.createTempFile(target.getParent(), ".training-", ".tmp");
        try {
            Files.write(temporary, contents);
            // 不使用 REPLACE_EXISTING；管理员已有配置优先。
            try { Files.move(temporary, target); }
            catch (java.nio.file.FileAlreadyExistsException race) {
                safePath(target);
                if (!Files.isRegularFile(target, NO_LINKS)) throw race;
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    static boolean markerMatches(Path root, int slot, Template template) {
        Path marker = marker(root, slot);
        try {
            safePath(marker);
            if (!Files.isRegularFile(marker, NO_LINKS) || Files.size(marker) > 1024) return false;
            JsonObject json = JsonParser.parseString(Files.readString(marker, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get("version").getAsInt() == template.version && json.get("slot").getAsInt() == slot
                    && json.get("templateSHA256").getAsString().equals(template.sha256);
        } catch (IOException | RuntimeException ignored) { return false; }
    }

    static void writeMarker(Path root, int slot, Template template) throws IOException {
        Path marker = marker(root, slot);
        safePath(marker);
        Files.createDirectories(marker.getParent());
        JsonObject json = new JsonObject();
        json.addProperty("version", template.version);
        json.addProperty("slot", slot);
        json.addProperty("templateSHA256", template.sha256);
        Path temporary = Files.createTempFile(marker.getParent(), ".slot-" + slot + "-", ".tmp");
        try {
            Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            // 原子换位失败即失败，不把部分标记当成成功。
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void deleteMarker(Path root, int slot) throws IOException {
        Path marker = marker(root, slot);
        safePath(marker);
        Files.deleteIfExists(marker);
    }

    private static Path marker(Path root, int slot) {
        checkSlot(slot);
        return root.resolve("training-camps/slot_" + slot + ".json");
    }

    private static void safePath(Path path) throws IOException {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Training path contains a symbolic link: " + part);
        }
    }

    record Section(int y, int[] states) { }
    record ChunkData(int x, int z, List<Section> sections, List<CompoundTag> entities) {
        int stateAt(int localX, int y, int localZ) {
            for (Section section : sections) {
                if (section.y == (y >> 4)) return section.states[((y & 15) << 8) | (localZ << 4) | localX];
            }
            throw new IllegalStateException("Missing training sentinel section");
        }
    }

    static final class Template {
        private static final int LIMIT = 16 * 1024 * 1024;
        private static final Set<String> ENTRIES = Set.of("level.dat", "layout.json", "sre-training.json",
                "readyArea.json", "train_maps/training.json", "region/r.0.0.mca");
        final int version;
        final String sha256;
        final byte[] mapConfiguration;
        final byte[] readyConfiguration;
        final List<CompoundTag> palette;
        final List<ChunkData> chunks;
        final BlockPos spawn;
        final long cells;

        private Template(byte[] archive, Map<String, byte[]> files) throws IOException {
            JsonObject identity = json(files.get("sre-training.json"));
            version = identity.get("version").getAsInt();
            if (version != 1 || !"starrailexpress:training".equals(identity.get("id").getAsString()))
                throw new IOException("Unsupported training template identity");
            try { sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)); }
            catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
            JsonObject layout = json(files.get("layout.json"));
            var bounds = layout.getAsJsonArray("bounds");
            // 此解析器仅接受当前有界的 80 区块训练模板，不接受玩家提交的任意世界。
            if (layout.get("version").getAsInt() != version || bounds.size() != 6
                    || bounds.get(0).getAsInt() != 0 || bounds.get(1).getAsInt() != 61
                    || bounds.get(2).getAsInt() != 0 || bounds.get(3).getAsInt() != 160
                    || bounds.get(4).getAsInt() != 96 || bounds.get(5).getAsInt() != 128)
                throw new IOException("Unsupported training template bounds");
            var lobby = layout.getAsJsonArray("areas").asList().stream().map(element -> element.getAsJsonObject())
                    .filter(area -> "lobby".equals(area.get("id").getAsString())).findFirst().orElseThrow();
            var point = lobby.getAsJsonArray("spawn");
            spawn = BlockPos.containing(point.get(0).getAsDouble(), point.get(1).getAsDouble(), point.get(2).getAsDouble());
            mapConfiguration = files.get("train_maps/training.json");
            readyConfiguration = files.get("readyArea.json");
            json(mapConfiguration);
            json(readyConfiguration).getAsJsonObject("readyArea");
            Map<CompoundTag, Integer> paletteIds = new LinkedHashMap<>();
            chunks = readRegion(files.get("region/r.0.0.mca"), paletteIds);
            palette = List.copyOf(paletteIds.keySet());
            cells = chunks.stream().mapToLong(chunk -> chunk.sections.size() * 4096L + chunk.entities.size()).sum();
        }

        static Template read(InputStream stream) throws IOException {
            try {
                byte[] archive = limited(stream, LIMIT);
                Map<String, Integer> sizes = directory(archive);
                Map<String, byte[]> files = new HashMap<>();
                try (var zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    long total = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (!ENTRIES.contains(name) || files.containsKey(name) || !sizes.containsKey(name))
                            throw new IOException("Unexpected or duplicate training ZIP entry: " + name);
                        byte[] bytes = limited(zip, LIMIT);
                        if (bytes.length != sizes.get(name) || (total += bytes.length) > 64L * 1024 * 1024)
                            throw new IOException("Training ZIP expansion mismatch");
                        files.put(name, bytes);
                        zip.closeEntry();
                    }
                }
                if (!files.keySet().equals(ENTRIES)) throw new IOException("Incomplete training ZIP");
                return new Template(archive, files);
            } catch (RuntimeException e) { throw new IOException("Invalid training template", e); }
        }

        private static Map<String, Integer> directory(byte[] bytes) throws IOException {
            ByteBuffer zip = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int end = -1;
            for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
                if (zip.getInt(i) == 0x06054b50 && i + 22 + u16(zip, i + 20) == bytes.length) { end = i; break; }
            }
            if (end < 0 || u16(zip, end + 4) != 0 || u16(zip, end + 6) != 0
                    || u16(zip, end + 8) != ENTRIES.size() || u16(zip, end + 10) != ENTRIES.size())
                throw new IOException("Invalid training ZIP directory");
            long offset = Integer.toUnsignedLong(zip.getInt(end + 16));
            if (offset + Integer.toUnsignedLong(zip.getInt(end + 12)) != end) throw new IOException("Invalid ZIP offsets");
            Map<String, Integer> sizes = new HashMap<>();
            int cursor = (int) offset;
            for (int i = 0; i < ENTRIES.size(); i++) {
                if (cursor < 0 || cursor + 46 > end || zip.getInt(cursor) != 0x02014b50)
                    throw new IOException("Malformed training ZIP entry");
                int length = u16(zip, cursor + 28);
                int next = cursor + 46 + length + u16(zip, cursor + 30) + u16(zip, cursor + 32);
                int mode = (zip.getInt(cursor + 38) >>> 16) & 0170000;
                int size = zip.getInt(cursor + 24);
                if (next > end || (mode != 0 && mode != 0100000) || (u16(zip, cursor + 8) & 1) != 0
                        || u16(zip, cursor + 34) != 0 || size < 0 || size > LIMIT)
                    throw new IOException("Unsafe training ZIP entry");
                String name = new String(bytes, cursor + 46, length, StandardCharsets.UTF_8);
                if (!ENTRIES.contains(name) || sizes.put(name, size) != null) throw new IOException("Unsafe training ZIP path");
                cursor = next;
            }
            if (cursor != end) throw new IOException("Trailing ZIP directory data");
            return sizes;
        }

        private static int u16(ByteBuffer bytes, int offset) { return Short.toUnsignedInt(bytes.getShort(offset)); }

        private static List<ChunkData> readRegion(byte[] region, Map<CompoundTag, Integer> palette) throws IOException {
            if (region.length < 8192 || region.length % 4096 != 0) throw new IOException("Invalid Anvil region length");
            ByteBuffer buffer = ByteBuffer.wrap(region).order(ByteOrder.BIG_ENDIAN);
            Set<Integer> sectors = new HashSet<>();
            sectors.add(0); sectors.add(1);
            List<ChunkData> result = new ArrayList<>();
            for (int index = 0; index < 1024; index++) {
                int location = buffer.getInt(index * 4);
                if (location == 0) continue;
                int start = location >>> 8;
                int count = location & 255;
                if (start < 2 || count == 0 || (long) (start + count) * 4096 > region.length)
                    throw new IOException("Anvil chunk outside region");
                for (int sector = start; sector < start + count; sector++) {
                    if (!sectors.add(sector)) throw new IOException("Overlapping Anvil chunks");
                }
                int offset = start * 4096;
                int length = buffer.getInt(offset);
                if (length <= 1 || length + 4L > count * 4096L) throw new IOException("Invalid Anvil chunk size");
                InputStream input = new ByteArrayInputStream(region, offset + 5, length - 1);
                int compression = Byte.toUnsignedInt(region[offset + 4]);
                if (compression == 1) input = new GZIPInputStream(input);
                else if (compression == 2) input = new InflaterInputStream(input);
                else if (compression != 3) throw new IOException("Unsupported/external Anvil compression");
                CompoundTag chunk;
                try (InputStream decoded = input; var data = new DataInputStream(new ByteArrayInputStream(limited(decoded, 2 * 1024 * 1024)))) {
                    chunk = NbtIo.read(data, NbtAccounter.create(8 * 1024 * 1024));
                }
                int x = chunk.getInt("xPos"), z = chunk.getInt("zPos");
                if (x != index % 32 || z != index / 32 || x >= 10 || z >= 8 || chunk.getInt("DataVersion") != 3955
                        || !"minecraft:full".equals(chunk.getString("Status"))) throw new IOException("Unexpected template chunk");
                List<Section> sections = new ArrayList<>();
                Set<Integer> seenY = new HashSet<>();
                for (Tag element : chunk.getList("sections", Tag.TAG_COMPOUND)) {
                    CompoundTag section = (CompoundTag) element;
                    int y = section.getByte("Y");
                    if (!seenY.add(y)) throw new IOException("Duplicate chunk section");
                    if (y < 3 || y > 5) continue; // 当前模板活动体积 Y61..95；其他节全为空气。
                    CompoundTag blockStates = section.getCompound("block_states");
                    var localPalette = blockStates.getList("palette", Tag.TAG_COMPOUND);
                    int size = localPalette.size();
                    if (size < 1 || size > 4096) throw new IOException("Invalid block palette");
                    int[] mapping = new int[size];
                    for (int p = 0; p < size; p++) {
                        CompoundTag state = localPalette.getCompound(p);
                        if (!state.contains("Name", Tag.TAG_STRING)) throw new IOException("Missing block state name");
                        mapping[p] = palette.computeIfAbsent(state, ignored -> palette.size());
                    }
                    int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(size - 1));
                    int perLong = 64 / bits;
                    long[] packed = blockStates.getLongArray("data");
                    if (size > 1 && packed.length != (4096 + perLong - 1) / perLong) throw new IOException("Invalid packed block states");
                    int[] states = new int[4096];
                    for (int cell = 0; cell < states.length; cell++) {
                        int value = size == 1 ? 0 : (int) ((packed[cell / perLong] >>> ((cell % perLong) * bits)) & ((1L << bits) - 1));
                        if (value >= size) throw new IOException("Palette index outside palette");
                        states[cell] = mapping[value];
                    }
                    sections.add(new Section(y, states));
                }
                if (sections.size() != 3) throw new IOException("Missing training sections");
                sections.sort(Comparator.comparingInt(Section::y));
                List<CompoundTag> entities = new ArrayList<>();
                for (Tag element : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
                    CompoundTag entity = (CompoundTag) element;
                    int ex = entity.getInt("x"), ey = entity.getInt("y"), ez = entity.getInt("z");
                    if ((ex >> 4) != x || (ez >> 4) != z || ey < 61 || ey >= 96
                            || !("minecraft:sign".equals(entity.getString("id"))
                            || "trainmurdermystery:trimmed_bed".equals(entity.getString("id"))))
                        throw new IOException("Invalid training block entity");
                    entities.add(entity);
                }
                result.add(new ChunkData(x, z, List.copyOf(sections), List.copyOf(entities)));
            }
            if (result.size() != 80) throw new IOException("Training template must contain exactly 80 chunks");
            return List.copyOf(result);
        }

        private static byte[] limited(InputStream stream, int limit) throws IOException {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Training resource exceeds size limit");
            return bytes;
        }

        private static JsonObject json(byte[] bytes) { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
    }
}
