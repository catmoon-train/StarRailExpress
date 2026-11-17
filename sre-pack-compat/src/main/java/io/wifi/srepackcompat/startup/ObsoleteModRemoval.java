package io.wifi.srepackcompat.startup;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** preLaunch 检查内置删除名单；桌面使用独立清理器，手机在当前进程清理后停止加载。 */
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
            // 启动器不一定设置 POJAV/ZALITH 环境变量；mods 落在 Android 共享存储时同样按手机处理，
            // 否则会走桌面分支 System.exit(0)，表现为“删完就关游戏”。
            if (MobileModRemoval.isAndroidRuntime(System.getenv(),
                    System.getProperty("java.runtime.name", ""), System.getProperty("java.vm.name", ""))
                    || MobileModRemoval.isMobileGameDir(mods)) {
                // 手机端已把 JAR 交给 Loader，删文件并不能中途卸载它；
                // 因此只清理文件、本次继续启动，避免一次清理把整局游戏挡在启动阶段。
                var removal = MobileModRemoval.remove(mods, names, protectedFiles);
                if (removal.nothingMatched()) return;
                System.getLogger("sre_pack_compat").log(
                        removal.failed().isEmpty() ? System.Logger.Level.INFO : System.Logger.Level.WARNING,
                        removal.describe() + "\n目录：" + mods.toAbsolutePath());
                return;
            }
            try {
                startWorker(mods, matches);
            } catch (Exception error) {
                // 独立清理器不可用（缺 java、受限启动器等）时退化为进程内清理并继续启动，
                // 绝不再把“清理失败”变成“游戏打不开”。
                System.getLogger("sre_pack_compat").log(System.Logger.Level.WARNING,
                        "过时 Mod 独立清理器启动失败，改为进程内清理并继续加载：" + error.getMessage());
                var removal = MobileModRemoval.remove(mods, names, protectedFiles);
                System.getLogger("sre_pack_compat").log(System.Logger.Level.WARNING,
                        removal.describe() + "\n目录：" + mods.toAbsolutePath());
                return;
            }
            System.exit(0);
            throw new IllegalStateException("游戏进程未能退出，禁止继续加载过时 Mod");
        } catch (Exception error) {
            throw new IllegalStateException("过时 Mod 清理失败，请检查 obsolete-mods.txt 或退出游戏后手动删除指定文件", error);
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
        var self = ProcessHandle.current();
        Path executable = resolveJavaExecutable(Path.of(System.getProperty("java.home")),
                self.info().command().orElse(null));
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

    /** 优先复用实际运行的 Java，避免启动器改写 java.home 后拼出不存在的路径。 */
    static Path resolveJavaExecutable(Path javaHome, String currentCommand) {
        if (currentCommand != null && !currentCommand.isBlank()) {
            Path current = Path.of(currentCommand);
            String name = current.getFileName().toString();
            if ((name.equalsIgnoreCase("java.exe") || name.equalsIgnoreCase("javaw.exe")
                    || name.equals("java")) && Files.isRegularFile(current)) {
                return current.toAbsolutePath();
            }
        }
        Path bin = javaHome.resolve("bin");
        for (String name : List.of("javaw.exe", "java.exe", "java")) {
            Path candidate = bin.resolve(name);
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        throw new IllegalStateException("找不到独立清理器所需的 Java 可执行文件：java.home="
                + javaHome + "，当前进程=" + currentCommand);
    }
}
