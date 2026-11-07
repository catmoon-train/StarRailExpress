package io.wifi.starrailexpress.training;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrainingWorldInstallerTest {
    @TempDir Path temporary;
    private static final String MARKER = "{\"id\":\"starrailexpress:training\",\"version\":1}";

    @Test
    void installsActualTemplateAndReusesWithoutSilentUpgrade() throws Exception {
        Path world = TrainingWorldInstaller.installOrFind(temporary);
        assertEquals("SRE_TrainingGround", world.getFileName().toString());
        assertTrue(TrainingWorldInstaller.isTrainingWorld(world));
        assertEquals(MARKER, Files.readString(world.resolve(TrainingWorldInstaller.MARKER_FILE)));
        assertTrue(Files.size(world.resolve("region/r.0.0.mca")) > 8192);
        assertTrue(Files.exists(world.resolve("train_maps/training.json")));
        assertFalse(Files.exists(world.resolve("session.lock")));
        Files.writeString(world.resolve("training-settings.json"), "{\"enemyCount\":7}");
        Files.writeString(world.resolve("level.dat"), "player modified map");
        assertEquals(world, TrainingWorldInstaller.installOrFind(temporary));
        assertEquals("player modified map", Files.readString(world.resolve("level.dat")));
        assertEquals("{\"enemyCount\":7}", Files.readString(world.resolve("training-settings.json")));
        assertNoStageDirectories();
    }

    @Test
    void neverOverwritesNameCollisionAndFindsExistingAfterGap() throws Exception {
        Path ordinary = Files.createDirectory(temporary.resolve("SRE_TrainingGround"));
        Files.writeString(ordinary.resolve("level.dat"), "precious ordinary save");
        Path world = TrainingWorldInstaller.installOrFind(temporary);
        assertEquals("SRE_TrainingGround_1", world.getFileName().toString());
        assertEquals("precious ordinary save", Files.readString(ordinary.resolve("level.dat")));
        Files.delete(ordinary.resolve("level.dat"));
        Files.delete(ordinary);
        assertEquals(world, TrainingWorldInstaller.installOrFind(temporary));
    }

    @Test
    void markerRequiresExactIdentityAndSupportedVersion() throws Exception {
        assertFalse(TrainingWorldInstaller.isTrainingWorld(temporary));
        Path marker = temporary.resolve(TrainingWorldInstaller.MARKER_FILE);
        for (String invalid : new String[]{"{}", "{\"id\":\"someone:training\",\"version\":1}",
                MARKER.replace(":1", ":2"), MARKER.replace(":1", ":10"),
                MARKER.substring(0, MARKER.length() - 1) + ",\"extra\":true}", "x".repeat(300)}) {
            Files.writeString(marker, invalid);
            assertFalse(TrainingWorldInstaller.isTrainingWorld(temporary), invalid);
        }
        Files.writeString(marker, " { \"version\": 1, \"id\": \"starrailexpress:training\" }\n");
        assertTrue(TrainingWorldInstaller.isTrainingWorld(temporary));
    }

    @Test
    void rebuildRestoresOnlyMarkedWorldAndPreservesSettings() throws Exception {
        Path world = TrainingWorldInstaller.installOrFind(temporary);
        byte[] level = Files.readAllBytes(world.resolve("level.dat"));
        Files.writeString(world.resolve("level.dat"), "modified");
        Files.writeString(world.resolve("training-settings.json"), "{\"enemyCount\":32}");
        Files.writeString(world.resolve("extra-player-building.txt"), "old map");
        TrainingWorldInstaller.rebuild(world);
        assertArrayEquals(level, Files.readAllBytes(world.resolve("level.dat")));
        assertEquals("{\"enemyCount\":32}", Files.readString(world.resolve("training-settings.json")));
        assertFalse(Files.exists(world.resolve("extra-player-building.txt")));
        Path ordinary = Files.createDirectory(temporary.resolve("ordinary"));
        Files.writeString(ordinary.resolve("level.dat"), "untouched");
        assertThrows(IOException.class, () -> TrainingWorldInstaller.rebuild(ordinary));
        assertEquals("untouched", Files.readString(ordinary.resolve("level.dat")));
        assertNoStageDirectories();
    }

    @Test
    void refusesLockedWorldWithoutChangingAnything() throws Exception {
        Path world = TrainingWorldInstaller.installOrFind(temporary);
        byte[] level = Files.readAllBytes(world.resolve("level.dat"));
        try (var channel = FileChannel.open(world.resolve("session.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            assertThrows(IOException.class, () -> TrainingWorldInstaller.rebuild(world));
        }
        assertArrayEquals(level, Files.readAllBytes(world.resolve("level.dat")));
        assertNoStageDirectories();
    }

    @Test
    void rejectsTraversalAbsolutePathsWindowsAliasesAndAds() throws Exception {
        for (String name : new String[]{"../escaped", "/absolute", "C:/absolute", "foo/../escaped",
                "foo\\escaped", "file:stream", "CON.txt", "dir./file", "a//b", "a/./b"}) {
            byte[] zip = archive(name, new byte[]{1});
            assertThrows(IOException.class, () -> injected(zip), name);
            assertNoStageDirectories();
            assertFalse(Files.exists(temporary.resolve("SRE_TrainingGround")));
        }
    }

    @Test
    void rejectsArchiveSymlinkModeBeforeExtraction() throws Exception {
        byte[] zip = archive("linked-file", new byte[]{1});
        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int central = findCentral(zip);
        buffer.putShort(central + 4, (short) 0x0314);
        buffer.putInt(central + 38, 0120777 << 16);
        assertThrows(IOException.class, () -> injected(zip));
        assertNoStageDirectories();
    }

    @Test
    void rejectsTruncatedArchivesMissingFilesVersionsAndZipBombs() throws Exception {
        assertThrows(IOException.class, () -> injected(new byte[]{80, 75, 3, 4}));
        assertThrows(IOException.class, () -> injected(archive("sre-training.json", MARKER.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(IOException.class, () -> injected(archive("sre-training.json", MARKER.replace(":1", ":2")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(IOException.class, () -> injected(archive("bomb", new byte[17 * 1024 * 1024])));
        byte[] oversizedDeclared = archive("small", new byte[]{1});
        ByteBuffer.wrap(oversizedDeclared).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(findCentral(oversizedDeclared) + 24, 17 * 1024 * 1024);
        assertThrows(IOException.class, () -> injected(oversizedDeclared));
        byte[] directoryBomb = archive("directory/", new byte[1024 * 1024]);
        ByteBuffer.wrap(directoryBomb).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(findCentral(directoryBomb) + 24, 0);
        assertThrows(IOException.class, () -> injected(directoryBomb));
        assertNoStageDirectories();
    }

    @Test
    void rejectsTooManyEntriesAndCaseInsensitiveDuplicates() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < 1025; i++) {
                zip.putNextEntry(new ZipEntry("entry" + i));
                zip.closeEntry();
            }
        }
        assertThrows(IOException.class, () -> injected(bytes.toByteArray()));
        bytes.reset();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("file"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("FILE"));
            zip.closeEntry();
        }
        assertThrows(IOException.class, () -> injected(bytes.toByteArray()));
        assertNoStageDirectories();
    }

    @Test
    void refusesFilesystemSymlinksIncludingSettingsAndSaveDirectory() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path linked = temporary.resolve("linked");
        try {
            Files.createSymbolicLink(linked, outside);
        } catch (IOException | UnsupportedOperationException unavailable) {
            assumeTrue(false, "Host does not permit symbolic links: " + unavailable.getMessage());
        }
        assertThrows(IOException.class, () -> TrainingWorldInstaller.installOrFind(linked));
        Path saves = Files.createDirectory(temporary.resolve("saves"));
        Path world = TrainingWorldInstaller.installOrFind(saves);
        Path externalSettings = Files.writeString(outside.resolve("settings.json"), "external");
        Files.createSymbolicLink(world.resolve("training-settings.json"), externalSettings);
        assertThrows(IOException.class, () -> TrainingWorldInstaller.rebuild(world));
        assertEquals("external", Files.readString(externalSettings));
        assertTrue(TrainingWorldInstaller.isTrainingWorld(world));
        Files.createSymbolicLink(temporary.resolve("world-link"), world);
        assertFalse(TrainingWorldInstaller.isTrainingWorld(temporary.resolve("world-link")));
    }

    private Path injected(byte[] archive) throws IOException {
        return TrainingWorldInstaller.installOrFind(temporary, () -> new ByteArrayInputStream(archive));
    }

    private static byte[] archive(String name, byte[] contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(contents);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static int findCentral(byte[] archive) {
        byte[] signature = {80, 75, 1, 2};
        for (int i = 0; i < archive.length - 4; i++) {
            if (Arrays.equals(archive, i, i + 4, signature, 0, 4)) {
                return i;
            }
        }
        throw new AssertionError("Missing ZIP central directory");
    }

    private void assertNoStageDirectories() throws IOException {
        try (var paths = Files.list(temporary)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".sre-training-")));
        }
    }
}
