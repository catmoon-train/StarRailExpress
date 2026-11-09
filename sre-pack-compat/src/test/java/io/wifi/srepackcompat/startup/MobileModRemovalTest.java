package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobileModRemovalTest {
    @TempDir Path temp;

    @Test void detectsZalithLogEnvironmentWithOrdinaryOpenJdk() {
        Map<String, String> environment = Map.of(
                "POJAV_NATIVEDIR", "/data/app/com.movtery.zalithlauncher.v2/lib/arm64",
                "POJAV_RENDERER", "opengles3",
                "MOD_ANDROID_RUNTIME", "/data/user/0/com.movtery.zalithlauncher.v2/app_runtime_mod",
                "ZALITH_VERSION_CODE", "200034");
        assertTrue(MobileModRemoval.isAndroidRuntime(environment,
                "OpenJDK Runtime Environment", "OpenJDK 64-Bit Server VM"));
    }

    @Test void detectsEachLauncherMarkerAndAndroidRuntimeWithoutLauncherMarkers() {
        for (String key : List.of("POJAV_NATIVEDIR", "POJAV_RENDERER", "MOD_ANDROID_RUNTIME",
                "ZALITH_VERSION_CODE")) {
            assertTrue(MobileModRemoval.isAndroidRuntime(Map.of(key, "present"), "OpenJDK", "OpenJDK"), key);
        }
        assertTrue(MobileModRemoval.isAndroidRuntime(Map.of(), "Android Runtime", "ART"));
        assertTrue(MobileModRemoval.isAndroidRuntime(Map.of(), "", "Dalvik"));
    }

    @Test void doesNotTreatOrdinaryDesktopOrEmptyMarkersAsAndroid() {
        assertFalse(MobileModRemoval.isAndroidRuntime(Map.of(),
                "OpenJDK Runtime Environment", "OpenJDK 64-Bit Server VM"));
        assertFalse(MobileModRemoval.isAndroidRuntime(Map.of("POJAV_RENDERER", " ",
                "POJAV_NATIVEDIR", ""), "Java(TM) SE Runtime Environment", "Java HotSpot(TM) 64-Bit Server VM"));
    }

    @Test void removesExactMatchesWithoutWorkerAndNextLaunchCanContinue() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("实例 mods"));
        Path obsolete = mods.resolve("旧版 mod.jar");
        Files.writeString(obsolete, "old");
        Files.writeString(mods.resolve("旧版 mod-new.jar"), "keep");
        Files.writeString(mods.resolve("sre-pack-compat.jar"), "self");
        Files.createDirectory(mods.resolve("directory.jar"));
        List<String> names = List.of("旧版 mod.jar", "missing.jar", "directory.jar");
        Set<Path> protectedFiles = Set.of(mods.resolve("sre-pack-compat.jar"));

        String message = MobileModRemoval.removeAndDescribe(mods, names, protectedFiles);

        assertNotNull(message);
        assertTrue(message.contains("已删除"));
        assertTrue(message.contains("本次加载已停止"));
        assertTrue(message.contains("重新启动游戏"));
        assertTrue(message.contains("旧版 mod.jar"));
        assertTrue(message.contains(mods.toAbsolutePath().toString()));
        assertFalse(Files.exists(obsolete));
        assertEquals("keep", Files.readString(mods.resolve("旧版 mod-new.jar")));
        assertEquals("self", Files.readString(mods.resolve("sre-pack-compat.jar")));
        assertTrue(Files.isDirectory(mods.resolve("directory.jar")));
        assertNull(MobileModRemoval.removeAndDescribe(mods, names, protectedFiles));
    }

    @Test void protectsCompatJarBeforeDeletingAnyMatches() throws Exception {
        Path old = temp.resolve("old.jar");
        Path self = temp.resolve("sre-pack-compat.jar");
        Files.writeString(old, "old");
        Files.writeString(self, "self");
        assertThrows(IllegalArgumentException.class, () -> MobileModRemoval.removeAndDescribe(temp,
                List.of("old.jar", "sre-pack-compat.jar"), Set.of(self)));
        assertTrue(Files.exists(old));
        assertTrue(Files.exists(self));
    }

    @Test void rejectsUnsafeNamesBeforeDeletingAnyMatches() throws Exception {
        Path old = temp.resolve("old.jar");
        Files.writeString(old, "old");
        for (String name : List.of("../outside.jar", "sub/old.jar", "*.jar", "file.zip")) {
            assertThrows(IllegalArgumentException.class, () -> MobileModRemoval.removeAndDescribe(temp,
                    List.of("old.jar", name), Set.of()));
            assertTrue(Files.exists(old));
        }
    }

    @Test void noMatchesOrMissingModsDirectoryNeedsNoRestart() throws Exception {
        assertNull(MobileModRemoval.removeAndDescribe(temp.resolve("missing"), List.of("old.jar"), Set.of()));
        assertNull(MobileModRemoval.removeAndDescribe(temp, List.of(), Set.of()));
    }
}
