package io.wifi.starrailexpress.training;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrainingWorldMigrationTest {
    @TempDir Path root;

    @Test void allEightOldDimensionsMigratePlayerAndRespawnWithoutLosingInventory() {
        for (int slot = 0; slot < 8; slot++) {
            CompoundTag player = player(slot);
            player.putString("InventorySentinel", "keep");
            player.put("RootVehicle", new CompoundTag());
            assertTrue(TrainingWorldMigration.migratePlayer(player));
            assertEquals("minecraft:overworld", player.getString("Dimension"));
            assertEquals("minecraft:overworld", player.getString("SpawnDimension"));
            assertEquals(TrainingLayout.get().area("lobby").spawn().y, player.getList("Pos", Tag.TAG_DOUBLE).getDouble(1));
            assertEquals("keep", player.getString("InventorySentinel"));
            assertFalse(player.contains("RootVehicle"));
            assertEquals(0, player.getFloat("FallDistance"));
            assertFalse(TrainingWorldMigration.migratePlayer(player));
        }
    }

    @Test void onlyExactLegacyIdsAreRecognized() {
        for (String id : new String[]{"minecraft:overworld", "minecraft:the_nether", "starrailexpress:training", "starrailexpress:training_8", "starrailexpress:training_00", "other:training_0", "starrailexpress:training_0_extra"}) {
            CompoundTag player = new CompoundTag();
            player.putString("Dimension", id);
            assertFalse(TrainingWorldMigration.migratePlayer(player), id);
            assertEquals(id, player.getString("Dimension"));
        }
        assertFalse(TrainingWorldMigration.isLegacyDimension(null));
    }

    @Test void removesSavedDimensionDefinitionsButKeepsOtherWorldsAndData() {
        CompoundTag tag = level();
        CompoundTag dimensions = tag.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions");
        assertTrue(TrainingWorldMigration.migrateLevel(tag));
        assertEquals(1, dimensions.size());
        assertTrue(dimensions.contains("minecraft:overworld"));
        assertEquals("keep", tag.getCompound("Data").getString("LevelName"));
        assertFalse(TrainingWorldMigration.migrateLevel(tag));
    }

    @Test void migratesFilesWithBackupsAndLeavesRegionsSettingsUntouched() throws Exception {
        mark();
        NbtIo.writeCompressed(level(), root.resolve("level.dat"));
        NbtIo.writeCompressed(level(), root.resolve("level.dat_old"));
        Path player = Files.createDirectories(root.resolve("playerdata")).resolve(UUID.randomUUID() + ".dat");
        NbtIo.writeCompressed(player(7), player);
        byte[] before = Files.readAllBytes(root.resolve("level.dat"));
        Path region = Files.createDirectories(root.resolve("dimensions/starrailexpress/training_7/region")).resolve("r.0.0.mca");
        Files.writeString(region, "legacy map");
        Files.writeString(root.resolve("training-settings.json"), "settings");
        TrainingWorldMigration.migrate(root);
        assertEquals("minecraft:overworld", read(player).getString("Dimension"));
        assertEquals("minecraft:overworld", read(root.resolve("level.dat_old")).getCompound("Data").getCompound("Player").getString("Dimension"));
        Path backup;
        try (var paths = Files.list(root)) { backup = paths.filter(p -> p.getFileName().toString().startsWith("training-migration-backup-")).findFirst().orElseThrow(); }
        assertArrayEquals(before, Files.readAllBytes(backup.resolve("level.dat")));
        assertTrue(Files.exists(backup.resolve("playerdata").resolve(player.getFileName())));
        assertEquals("legacy map", Files.readString(region));
        assertEquals("settings", Files.readString(root.resolve("training-settings.json")));
        TrainingWorldMigration.migrate(root);
        try (var paths = Files.list(root)) { assertEquals(1, paths.filter(p -> p.getFileName().toString().startsWith("training-migration-backup-")).count()); }
    }

    @Test void unrelatedSavesAreNeverReadOrModified() throws Exception {
        Files.writeString(root.resolve("level.dat"), "not even nbt");
        TrainingWorldMigration.migrate(root);
        assertEquals("not even nbt", Files.readString(root.resolve("level.dat")));
    }

    @Test void corruptPlayerDataAbortsBeforeAnyReplacement() throws Exception {
        mark();
        NbtIo.writeCompressed(level(), root.resolve("level.dat"));
        byte[] before = Files.readAllBytes(root.resolve("level.dat"));
        Files.writeString(Files.createDirectories(root.resolve("playerdata")).resolve(UUID.randomUUID() + ".dat"), "broken");
        assertThrows(IOException.class, () -> TrainingWorldMigration.migrate(root));
        assertArrayEquals(before, Files.readAllBytes(root.resolve("level.dat")));
    }

    @Test void removedDimensionsAreNotShippedAndMigrationIsRegistered() throws Exception {
        for (int slot = 0; slot < 8; slot++) assertNull(getClass().getResourceAsStream("/data/starrailexpress/dimension/training_" + slot + ".json"));
        String mixins = Files.readString(Path.of("src/main/resources/starrailexpress.mixins.json"));
        assertTrue(mixins.contains("world.TrainingSaveMigrationMixin"));
    }

    private void mark() throws IOException { Files.writeString(root.resolve("sre-training.json"), "{\"id\":\"starrailexpress:training\",\"version\":1}"); }
    private static CompoundTag read(Path file) throws IOException { return NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024)); }
    private static CompoundTag player(int slot) {
        CompoundTag player = new CompoundTag();
        player.putString("Dimension", "starrailexpress:training_" + slot);
        player.putString("SpawnDimension", "starrailexpress:training_" + slot);
        return player;
    }
    private static CompoundTag level() {
        CompoundTag root = new CompoundTag(), data = new CompoundTag(), worldgen = new CompoundTag(), dimensions = new CompoundTag();
        for (int slot = 0; slot < 8; slot++) dimensions.put("starrailexpress:training_" + slot, new CompoundTag());
        dimensions.put("minecraft:overworld", new CompoundTag());
        worldgen.put("dimensions", dimensions); data.put("WorldGenSettings", worldgen);
        data.put("Player", player(3)); data.putString("LevelName", "keep"); root.put("Data", data);
        return root;
    }
}
