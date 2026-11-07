package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ModRemovalWorkerTest {
    @TempDir Path temp;

    @Test void parsesCommentsBomDuplicatesUnicodeAndSpaces() {
        assertEquals(List.of("旧版 mod-1.jar", "other.jar"), ModRemovalWorker.parse(
                List.of("\uFEFF# comment", "", " 旧版 mod-1.jar ", "other.jar", "other.jar")));
    }

    @Test void rejectsPathsWildcardsAndNonJars() {
        for (String bad : List.of("../a.jar", "sub/a.jar", "sub\\a.jar", "C:a.jar", "*.jar", "a?.jar", "a.zip", ".jar")) {
            assertThrows(IllegalArgumentException.class, () -> ModRemovalWorker.parse(List.of(bad)), bad);
        }
    }

    @Test void matchesOnlyExactRegularFilesAndProtectsOwnJar() throws Exception {
        Files.writeString(temp.resolve("old.jar"), "old");
        Files.writeString(temp.resolve("old-new.jar"), "new");
        Files.createDirectory(temp.resolve("directory.jar"));
        assertEquals(List.of("old.jar"), ModRemovalWorker.find(temp,
                List.of("old.jar", "missing.jar", "directory.jar"), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ModRemovalWorker.find(temp,
                List.of("old.jar"), Set.of(temp.resolve("old.jar"))));
        ModRemovalWorker.delete(temp, "old.jar");
        ModRemovalWorker.delete(temp, "missing.jar");
        assertTrue(Files.exists(temp.resolve("old-new.jar")));
        assertThrows(IllegalArgumentException.class, () -> ModRemovalWorker.delete(temp, "directory.jar"));
    }

    @Test void independentWorkerWaitsForParentExitBeforeDeletingAndKeepsOtherMods() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("实例 mods"));
        Path old = mods.resolve("旧 mod.jar");
        Files.writeString(old, "dummy jar");
        Files.writeString(mods.resolve("keep.jar"), "keep");
        Path parentReady = temp.resolve("parent-ready");
        Path release = temp.resolve("release");
        Process parent = javaProcess("parent.args", Parent.class.getName(), parentReady.toString(), release.toString());
        Process worker = null;
        try {
            await(parentReady, parent);
            Path list = temp.resolve("matches.txt");
            Path ready = temp.resolve("ready.txt");
            Files.write(list, List.of(old.getFileName().toString()), StandardCharsets.UTF_8);
            worker = javaProcess("worker.args", ModRemovalWorker.class.getName(), Long.toString(parent.pid()),
                    Long.toString(parent.info().startInstant().orElseThrow().toEpochMilli()), mods.toString(),
                    list.toString(), ready.toString(), "--headless");
            await(ready, worker);
            Thread.sleep(200);
            assertTrue(Files.exists(old), "父进程还活着时不能删除");
            Files.writeString(release, "exit");
            assertTrue(parent.waitFor(10, TimeUnit.SECONDS));
            assertTrue(worker.waitFor(20, TimeUnit.SECONDS));
            String log = new String(Files.readAllBytes(temp.resolve("worker.args.log")), StandardCharsets.UTF_8);
            assertEquals(0, worker.exitValue(), log);
            assertFalse(Files.exists(old), log);
            assertEquals("keep", Files.readString(mods.resolve("keep.jar")));
            assertTrue(Files.readString(temp.resolve("result.txt")).contains("已删除"));
        } finally {
            parent.destroyForcibly();
            if (worker != null) worker.destroyForcibly();
        }
    }

    private Process javaProcess(String argsName, String main, String... args) throws Exception {
        String cp = Path.of(ModRemovalWorkerTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator
                + Path.of(ModRemovalWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(cp);
        command.add(main);
        command.addAll(List.of(args));
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(temp.resolve(argsName + ".log").toFile()).start();
    }
    private static void await(Path marker, Process process) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(marker) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(25);
        assertTrue(Files.exists(marker), "子进程未就绪: " + marker);
    }
    public static final class Parent {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]), "ready");
            while (!Files.exists(Path.of(args[1]))) Thread.sleep(25);
        }
    }
}
