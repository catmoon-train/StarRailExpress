package io.wifi.srepackcompat.startup;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 在 preLaunch 安装临时读穿入口；不修改 Loader/Mixin 字节码或 JVM 参数。 */
public final class StartupClassCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("sre-pack-compat/startup");
    private static final String TRANSFORMER = "net.fabricmc.loader.impl.game.patch.GameTransformer";
    private static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClassLoader";
    private static final String DELEGATE = "net.fabricmc.loader.impl.launch.knot.KnotClassDelegate";
    private static final String TRANSFORMER_SHA256 = "986c8daa840830274957078e89756cb15222b114ba3c5774993b75b61a035b71";
    private static final Map<String, String> DELEGATE_SHA256 = Map.of(
            "0.19.3", "da0f061a7c052a2183a9526f638dde014b1f119170045df746a54c09fbdcd02f",
            "0.20.1", "a76eb30607f72a60234ba6eb129c9e1ee2ae17db3cf2fe200dfcc5e047537989");
    private record Installation(Object transformer, Field field, Map<String, byte[]> original,
                                StartupClassByteCache cache, long started) { }
    private static Installation installed;

    private StartupClassCache() { }

    public static synchronized void install() {
        if (installed != null) return;
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT || loader.isDevelopmentEnvironment()) return;
        try {
            StartupCacheConfig config = StartupCacheConfig.load(
                    loader.getConfigDir().resolve("sre-pack-compat-startup.properties"));
            if (!config.enabled()) {
                LOGGER.info("Startup class cache disabled by config");
                return;
            }
            String version = loader.getModContainer("fabricloader").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString();
            String expected = DELEGATE_SHA256.get(version);
            if (expected == null) throw new IOException("Unverified Fabric Loader " + version);
            ClassLoader target = FabricLauncherBase.getLauncher().getTargetClassLoader();
            if (!target.getClass().getName().equals(KNOT)) throw new IOException("Unsupported game classloader");
            Field delegateField = accessible(target.getClass(), "delegate");
            Object delegate = delegateField.get(target);
            requireDigest(delegate.getClass(), DELEGATE, expected);
            Object transformer = FabricLoaderImpl.INSTANCE.getGameProvider().getEntrypointTransformer();
            requireDigest(transformer.getClass(), TRANSFORMER, TRANSFORMER_SHA256);
            if (!accessible(transformer.getClass(), "entrypointsLocated").getBoolean(transformer)) {
                throw new IOException("Game entrypoint patches are not ready");
            }
            Field patchesField = accessible(transformer.getClass(), "patchedClasses");
            Object patches = patchesField.get(transformer);
            if (patches == null || patches.getClass() != HashMap.class) {
                throw new IOException("Entrypoint patch table already customized");
            }
            Field sourcesField = accessible(delegate.getClass(), "codeSources");
            Object sourceSnapshot = sourcesField.get(delegate);
            Object rawUrlLoader = accessible(target.getClass(), "urlLoader").get(target);
            if (!(rawUrlLoader instanceof URLClassLoader urlLoader)) throw new IOException("No local URL classpath");
            long start = System.nanoTime();
            LocalJarClassIndex index = LocalJarClassIndex.build(urlLoader.getURLs(), urlLoader::findResource, () -> {
                try {
                    return sourcesField.get(delegate) == sourceSnapshot;
                } catch (IllegalAccessException error) {
                    return false;
                }
            });
            if (!index.isCurrent()) {
                index.close();
                throw new IOException("Classpath changed while indexing");
            }
            @SuppressWarnings("unchecked")
            Map<String, byte[]> original = (Map<String, byte[]>) patches;
            StartupClassByteCache cache = new StartupClassByteCache(original, index,
                    config.maxMiB() * 1024L * 1024L, config.maxEntries(),
                    TimeUnit.SECONDS.toNanos(config.timeoutSeconds()), System::nanoTime);
            // 入口定位已结束；唯一热路径为 transform -> patchedClasses.get。
            // 只替换可变实例字段，不使用 Unsafe、agent 或 --add-opens。
            patchesField.set(transformer, cache);
            installed = new Installation(transformer, patchesField, original, cache, start);
            LOGGER.info("Startup class cache enabled: loader={}, indexedClasses={}, indexMs={}, rawLimitMiB={}",
                    version, index.size(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), config.maxMiB());
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError error) {
            LOGGER.warn("Startup class cache not installed; using original Loader: {}", error.toString());
        }
    }

    public static synchronized boolean isInstalled() {
        return installed != null;
    }

    public static synchronized void finish(String reason) {
        Installation state = installed;
        if (state == null) return;
        installed = null;
        var stats = state.cache().stop(reason);
        try {
            // 其他补丁若已换走该表，绝不覆盖其新状态。
            if (state.field().get(state.transformer()) == state.cache()) {
                state.field().set(state.transformer(), state.original());
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.warn("Cannot restore startup patch table; raw cache is already released: {}", error.toString());
        }
        LOGGER.info("Startup class cache finished: reason={}, rawReads={}, hits={}, bypasses={}, failures={}, "
                        + "evictions={}, peakBytes={}, retainedBytes={}, preLaunchToReadyMs={}",
                stats.stopped(), stats.reads(), stats.hits(), stats.bypasses(), stats.failures(),
                stats.evictions(), stats.peakBytes(), stats.retainedBytes(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.started()));
    }

    private static Field accessible(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        if (!field.trySetAccessible()) throw new IllegalAccessException(type.getName() + "." + name);
        return field;
    }

    private static void requireDigest(Class<?> type, String name, String expected) throws IOException {
        if (!type.getName().equals(name)) throw new IOException("Customized Loader class: " + type.getName());
        try (var input = type.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            if (input == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.readAllBytes())).equals(expected)) {
                throw new IOException("Unverified Loader implementation: " + name);
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
