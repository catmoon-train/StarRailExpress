package io.wifi.srepackcompat.startup;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** preLaunch 检查内置删除名单；独立清理器就绪后结束当前游戏进程。 */
public final class ObsoleteModRemoval {
    private ObsoleteModRemoval() { }

    public static void check() {
        try {
            var loader = FabricLoader.getInstance();
            var mod = loader.getModContainer("sre_pack_compat").orElseThrow();
            Path resource = mod.findPath("obsolete-mods.txt").orElseThrow();
            List<String> names = ModRemovalWorker.parse(Files.readAllLines(resource, StandardCharsets.UTF_8));
            if (names.isEmpty()) return;
            Path mods = loader.getGameDir().resolve("mods");
            var protectedFiles = new HashSet<Path>();
            // 生产态 code source 指向兼容 JAR；开发态则为 classes 目录。
            protectedFiles.add(Path.of(ObsoleteModRemoval.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            List<String> matches = ModRemovalWorker.find(mods, names, protectedFiles);
            if (matches.isEmpty()) return;
            startWorker(mods, matches);
            System.exit(0);
            throw new IllegalStateException("游戏进程未能退出，禁止继续加载过时 Mod");
        } catch (Exception error) {
            throw new IllegalStateException("无法启动过时 Mod 清理，请检查 obsolete-mods.txt 或手动删除指定文件", error);
        }
    }

    private static void startWorker(Path mods, List<String> matches) throws Exception {
        Path work = Files.createTempDirectory("sre-obsolete-mods-");
        String classResource = ModRemovalWorker.class.getName().replace('.', '/') + ".class";
        Path workerClass = work.resolve(classResource);
        Files.createDirectories(workerClass.getParent());
        try (var stream = ModRemovalWorker.class.getClassLoader().getResourceAsStream(classResource)) {
            if (stream == null) throw new IllegalStateException("缺少独立清理器类");
            Files.copy(stream, workerClass);
        }
        Path list = work.resolve("matches.txt");
        Path ready = work.resolve("ready.txt");
        Files.write(list, matches, StandardCharsets.UTF_8);
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        Path executable = javaBin.resolve("javaw.exe");
        if (!Files.isRegularFile(executable)) executable = javaBin.resolve("java");
        var self = ProcessHandle.current();
        Process process = new ProcessBuilder(executable.toString(), "-cp", work.toString(),
                ModRemovalWorker.class.getName(), Long.toString(self.pid()),
                Long.toString(self.info().startInstant().orElseThrow().toEpochMilli()),
                mods.toAbsolutePath().toString(), list.toString(), ready.toString())
                .redirectErrorStream(true).redirectOutput(work.resolve("worker.log").toFile()).start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
        if (!Files.exists(ready)) {
            process.destroyForcibly();
            throw new IllegalStateException("清理窗口未就绪，详见 " + work.resolve("worker.log"));
        }
    }
}
