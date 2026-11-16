package io.wifi.starrailexpress.training;

import net.minecraft.nbt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** 在原版持有存档锁、读取维度注册表之前迁移旧训练存档；不删除历史地图。 */
public final class TrainingWorldMigration {
    private TrainingWorldMigration() { }

    public static boolean isLegacyDimension(String id) {
        return id != null && id.matches("starrailexpress:training_[0-7]");
    }

    /** 仅接受带训练身份标记的存档；调用方必须已经持有原版存档锁。 */
    public static void migrate(Path root) throws IOException {
        if (!TrainingWorldInstaller.isTrainingWorld(root)) return;
        Map<Path, CompoundTag> changes = new LinkedHashMap<>();
        for (String name : new String[]{"level.dat", "level.dat_old"}) {
            Path file = root.resolve(name);
            safe(file);
            if (!Files.exists(file)) continue;
            CompoundTag tag = read(file);
            if (migrateLevel(tag)) changes.put(file, tag);
        }
        Path players = root.resolve("playerdata");
        safe(players);
        if (Files.isDirectory(players)) {
            try (var files = Files.list(players)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.dat(?:_old)?")).toList()) {
                    safe(file);
                    CompoundTag tag = read(file);
                    if (migratePlayer(tag)) changes.put(file, tag);
                }
            }
        }
        if (changes.isEmpty()) return;
        // 全部解析成功、全部备份成功后才开始替换；失败后可以幂等重试。
        Path backup = Files.createTempDirectory(root, "training-migration-backup-");
        for (Path file : changes.keySet()) {
            Path target = backup.resolve(root.relativize(file));
            Files.createDirectories(target.getParent());
            Files.copy(file, target);
        }
        for (var entry : changes.entrySet()) {
            Path temporary = Files.createTempFile(entry.getKey().getParent(), ".training-migrate-", ".dat");
            try {
                NbtIo.writeCompressed(entry.getValue(), temporary);
                Files.move(temporary, entry.getKey(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    static boolean migrateLevel(CompoundTag root) {
        CompoundTag data = root.getCompound("Data");
        CompoundTag dimensions = data.getCompound("WorldGenSettings").getCompound("dimensions");
        boolean changed = false;
        for (String id : new ArrayList<>(dimensions.getAllKeys())) {
            if (isLegacyDimension(id)) { dimensions.remove(id); changed = true; }
        }
        return migratePlayer(data.getCompound("Player")) || changed;
    }

    static boolean migratePlayer(CompoundTag player) {
        boolean changed = false;
        var lobby = TrainingLayout.get().area("lobby");
        if (isLegacyDimension(player.getString("Dimension"))) {
            player.putString("Dimension", "minecraft:overworld");
            ListTag position = new ListTag();
            for (double coordinate : new double[]{lobby.spawn().x, lobby.spawn().y, lobby.spawn().z}) position.add(DoubleTag.valueOf(coordinate));
            player.put("Pos", position);
            ListTag motion = new ListTag();
            for (int i = 0; i < 3; i++) motion.add(DoubleTag.valueOf(0));
            player.put("Motion", motion);
            player.putFloat("FallDistance", 0);
            player.remove("RootVehicle");
            changed = true;
        }
        if (isLegacyDimension(player.getString("SpawnDimension"))) {
            player.putString("SpawnDimension", "minecraft:overworld");
            player.putInt("SpawnX", (int) Math.floor(lobby.spawn().x));
            player.putInt("SpawnY", (int) Math.floor(lobby.spawn().y));
            player.putInt("SpawnZ", (int) Math.floor(lobby.spawn().z));
            player.putBoolean("SpawnForced", true);
            changed = true;
        }
        return changed;
    }

    private static CompoundTag read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Not a regular training data file: " + file);
        return NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
    }

    private static void safe(Path path) throws IOException {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IOException("Training migration refuses symbolic link: " + part);
    }
}
