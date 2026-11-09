package io.wifi.srepackcompat.startup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 手机启动器不能执行独立 Java/AWT 清理器，改在当前进程删除后提示重启。 */
final class MobileModRemoval {
    private MobileModRemoval() { }

    static boolean isAndroidRuntime(Map<String, String> environment, String runtimeName, String vmName) {
        for (String key : List.of("POJAV_NATIVEDIR", "POJAV_RENDERER", "MOD_ANDROID_RUNTIME",
                "ZALITH_VERSION_CODE")) {
            String value = environment.get(key);
            if (value != null && !value.isBlank()) return true;
        }
        return runtimeName.toLowerCase(Locale.ROOT).contains("android")
                || vmName.toLowerCase(Locale.ROOT).contains("dalvik");
    }

    /** 沿用精确匹配及自身保护；Linux/Android 可 unlink 已打开的 JAR，但不得继续加载。 */
    static String removeAndDescribe(Path mods, List<String> names, Set<Path> protectedFiles) throws Exception {
        List<String> matches = ModRemovalWorker.find(mods, names, protectedFiles);
        if (matches.isEmpty()) return null;
        List<String> failures = new ArrayList<>();
        for (String name : matches) {
            try {
                ModRemovalWorker.delete(mods, name);
            } catch (Exception error) {
                failures.add(name + "：" + error.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            return "手机端过时 Mod 清理未完成，本次加载已停止。请退出游戏后手动删除以下文件，再重启游戏：\n"
                    + String.join("\n", failures) + "\n目录：" + mods.toAbsolutePath();
        }
        return "手机端已删除过时的 Mod，本次加载已停止，请重新启动游戏（无需更换 Java 或授予执行权限）：\n"
                + String.join("\n", matches) + "\n目录：" + mods.toAbsolutePath();
    }
}
