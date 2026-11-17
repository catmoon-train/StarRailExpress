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

    /**
     * 启动器常不设置 POJAV/ZALITH 环境变量，此时只能看游戏目录位置。
     * Android 上游戏目录一定落在共享存储（/storage/emulated/…、Android/data/…）。
     */
    static boolean isMobileGameDir(Path gameDirOrMods) {
        String path = gameDirOrMods.toAbsolutePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.contains("/android/data/") || path.contains("/android/media/")
                || path.contains("/storage/emulated/") || path.startsWith("/storage/")
                || path.contains("/sdcard/");
    }

    /** 手机端清理结果：删除成功不再中断启动，下次启动自动生效；失败只告警。 */
    record Removal(List<String> removed, List<String> failed) {
        boolean nothingMatched() {
            return removed.isEmpty() && failed.isEmpty();
        }

        String describe() {
            if (!removed.isEmpty() && failed.isEmpty()) {
                return "手机端已删除过时的 Mod，本次仍加载已打开的旧文件，下次启动生效：\n"
                        + String.join("\n", removed);
            }
            if (!failed.isEmpty()) {
                return "手机端过时 Mod 清理未完成，本次继续启动。请退出游戏后手动删除以下文件：\n"
                        + String.join("\n", failed);
            }
            return "";
        }
    }

    /** 沿用精确匹配及自身保护；Linux/Android 可 unlink 已打开的 JAR，但清理只在下次启动生效。 */
    static Removal remove(Path mods, List<String> names, Set<Path> protectedFiles) throws Exception {
        List<String> matches = ModRemovalWorker.find(mods, names, protectedFiles);
        if (matches.isEmpty()) return new Removal(List.of(), List.of());
        List<String> removed = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String name : matches) {
            try {
                ModRemovalWorker.delete(mods, name);
                removed.add(name);
            } catch (Exception error) {
                failures.add(name + "：" + error.getMessage());
            }
        }
        return new Removal(List.copyOf(removed), List.copyOf(failures));
    }

    /** 保留原字符串接口，供调用方与测试复用。 */
    static String removeAndDescribe(Path mods, List<String> names, Set<Path> protectedFiles) throws Exception {
        Removal removal = remove(mods, names, protectedFiles);
        if (removal.nothingMatched()) return null;
        return removal.describe() + "\n目录：" + mods.toAbsolutePath();
    }
}
