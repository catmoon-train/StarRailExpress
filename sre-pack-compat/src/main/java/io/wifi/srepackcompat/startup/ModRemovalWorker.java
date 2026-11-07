package io.wifi.srepackcompat.startup;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 独立 JVM 清理器，只依赖 JDK，游戏退出后才删除明确列出的普通 JAR。 */
public final class ModRemovalWorker {
    private ModRemovalWorker() { }

    public static List<String> parse(List<String> lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String raw : lines) {
            String name = raw.replace("\uFEFF", "").trim();
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (!name.endsWith(".jar") || name.contains("/") || name.contains("\\")
                    || name.contains(":") || name.contains("*") || name.contains("?")
                    || name.contains("\0") || name.equals(".jar")) {
                throw new IllegalArgumentException("过时 Mod 名单必须为精确 .jar 文件名: " + name);
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    public static List<String> find(Path mods, List<String> names, Set<Path> protectedFiles) throws Exception {
        Path directory = mods.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(directory)) throw new IllegalArgumentException("mods 不能是符号链接");
        List<String> found = new ArrayList<>();
        for (String name : parse(names)) {
            Path file = directory.resolve(name);
            if (protectedFiles.stream().anyMatch(p -> p.toAbsolutePath().normalize().equals(file))) {
                throw new IllegalArgumentException("不能将兼容包自身加入删除名单: " + name);
            }
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) found.add(name);
        }
        return found;
    }

    public static void delete(Path mods, String name) throws Exception {
        parse(List.of(name));
        Path directory = mods.toAbsolutePath().normalize();
        // 检查整个父链，拒绝通过链接把删除路径导向实例之外。
        for (Path p = directory; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("拒绝链接目录: " + p);
        }
        Path file = directory.resolve(name);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("拒绝删除非普通文件: " + name);
        }
        Files.delete(file);
    }

    /** 参数为父进程 PID、启动时间、mods 路径、命中名单、就绪标记；最后可选 --headless 仅供测试。 */
    public static void main(String[] args) throws Exception {
        long pid = Long.parseLong(args[0]);
        long started = Long.parseLong(args[1]);
        Path mods = Path.of(args[2]);
        List<String> names = parse(Files.readAllLines(Path.of(args[3]), StandardCharsets.UTF_8));
        boolean headless = args.length == 6 && args[5].equals("--headless");
        JLabel label = new JLabel("正在删除过时的 Mod，请重启游戏。正在等待游戏退出……", SwingConstants.CENTER);
        if (!headless) {
            if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("无法显示清理提示窗口");
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("整合包更新：清理过时 Mod");
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                frame.add(label);
                frame.setSize(650, 160);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
        }
        Files.writeString(Path.of(args[4]), "ready", StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        try {
            var parent = ProcessHandle.of(pid);
            if (parent.isPresent()) {
                long actual = parent.get().info().startInstant().orElseThrow().toEpochMilli();
                if (actual != started) throw new IllegalStateException("父进程身份已变化，取消删除");
                parent.get().onExit().get(90, TimeUnit.SECONDS);
            }
            for (String name : names) {
                Exception last = null;
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                do {
                    try { delete(mods, name); last = null; break; }
                    catch (Exception error) { last = error; Thread.sleep(250); }
                } while (System.nanoTime() < deadline);
                if (last != null) failures.add(name + ": " + last.getMessage());
            }
        } catch (Exception error) {
            failures.add("清理已取消: " + error);
        }
        String status = failures.isEmpty() ? "过时的 Mod 已删除，请重启游戏。"
                : "部分文件未删除，请退出游戏后手动处理：" + String.join("；", failures);
        System.out.println(status);
        Files.writeString(Path.of(args[4]).resolveSibling("result.txt"), status, StandardCharsets.UTF_8);
        if (!headless) SwingUtilities.invokeLater(() -> {
            label.setText("<html><body style='width:450px'>" + status.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
            ((JFrame) SwingUtilities.getWindowAncestor(label)).setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        });
        if (headless && !failures.isEmpty()) System.exit(1);
    }
}
