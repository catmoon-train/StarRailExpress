package io.wifi.starrailexpress.client.resource;

import io.wifi.starrailexpress.client.model.CustomItemModelPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 可选 DashLoader 桥接；使用公开 Cache API 的反射绑定，基础模组不链接第三方类。 */
public final class DashLoaderCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("SRE/DashLoader");
    private static final ReloadSaveBarrier BARRIER = new ReloadSaveBarrier();
    private static final AtomicBoolean FRESH_REQUEST = new AtomicBoolean();
    private static volatile boolean freshReload;
    private static boolean cacheable;
    private static volatile net.minecraft.server.packs.resources.ResourceManager currentResources;
    private static volatile boolean cacheLoad;
    private static Binding binding;

    private record Binding(Object cache, Method load, Method status, Method save, Method reset, Method directory) {
        static Binding create() throws ReflectiveOperationException {
            Class<?> api = Class.forName("dev.notalpha.dashloader.api.cache.Cache");
            Object cache = Class.forName("dev.notalpha.dashloader.client.DashLoaderClient").getField("CACHE").get(null);
            return new Binding(cache, api.getMethod("load", String.class), api.getMethod("getStatus"),
                    api.getMethod("save", Consumer.class), api.getMethod("reset"), api.getMethod("getDir"));
        }
    }

    private DashLoaderCompat() { }

    private static Binding binding() {
        if (binding == null) {
            try {
                binding = Binding.create();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported DashLoader Cache API", error);
            }
        }
        return binding;
    }

    public static void requestFreshReload() {
        FRESH_REQUEST.set(true);
        ResourceFingerprint.clear();
    }

    public static boolean isFreshReload() {
        return freshReload;
    }

    /** 在任何资源准备任务启动之前选择缓存，避免工作线程观察到上一轮状态。 */
    public static void beginReload(List<PackResources> packs, net.minecraft.server.packs.resources.ResourceManager resources) {
        currentResources = resources;
        freshReload = FRESH_REQUEST.getAndSet(false);
        cacheable = true;
        String key;
        long start = System.nanoTime();
        try {
            key = fingerprint(packs);
        } catch (IOException | RuntimeException error) {
            // 无法确定内容时宁可完整重载，也不能命中可能属于另一份内容的缓存。
            cacheable = false;
            key = "sre-uncached-" + UUID.randomUUID();
            LOGGER.warn("Cannot fingerprint resource packs; using fresh resources ({})", error.toString());
        }
        try {
            Binding api = binding();
            api.load.invoke(api.cache, key);
            cacheLoad = "LOAD".equals(String.valueOf(api.status.invoke(api.cache)));
            LOGGER.info("Resource cache {} prepared in {} ms ({})", key,
                    (System.nanoTime() - start) / 1_000_000, api.status.invoke(api.cache));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot prepare DashLoader cache", error);
        }
    }

    /** 此方法也用于隔离测试验证资源包顺序及文件内容对缓存键的影响。 */
    public static String fingerprint(List<PackResources> packs) throws IOException {
        MessageDigest digest = ResourceFingerprint.digest();
        ResourceFingerprint.text(digest, "sre-dash-v2");
        // DashLoader 原键只含模组 ID/版本；版本不变但资源 JAR 更新时也必须失效。
        for (var mod : FabricLoader.getInstance().getAllMods().stream()
                .sorted(Comparator.comparing(m -> m.getMetadata().getId())).toList()) {
            ResourceFingerprint.text(digest, mod.getMetadata().getId());
            ResourceFingerprint.text(digest, mod.getMetadata().getVersion().getFriendlyString());
            if (mod.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
                for (Path path : mod.getOrigin().getPaths()) {
                    ResourceFingerprint.path(digest, path);
                }
            }
        }
        Path config = FabricLoader.getInstance().getConfigDir().resolve("dashloader.json");
        if (Files.isRegularFile(config)) {
            ResourceFingerprint.path(digest, config);
        }
        for (PackResources pack : packs) {
            ResourceFingerprint.text(digest, pack.getClass().getName());
            // 服务器的 UUID 不代表内容，不让相同内容仅因换 UUID 而重新烘焙。
            ResourceFingerprint.text(digest, pack.location().source() == net.minecraft.server.packs.repository.PackSource.SERVER
                    ? "server" : pack.packId());
            if (pack instanceof PackFingerprintSource source) {
                source.sre$appendFingerprint(digest);
            } else if (!(pack instanceof VanillaPackResources)) {
                appendVirtualPack(digest, pack);
            }
        }
        CustomItemModelPlugin.collectModelIds().stream().map(Object::toString).sorted()
                .forEach(id -> ResourceFingerprint.text(digest, id));
        return "sre-" + ResourceFingerprint.finish(digest);
    }

    private static void appendVirtualPack(MessageDigest digest, PackResources pack) throws IOException {
        // 动态内存包无磁盘路径：按真实条目内容失效，而不是信任显示名或包实例地址。
        var resources = new TreeMap<String, net.minecraft.server.packs.resources.IoSupplier<InputStream>>();
        var metadata = pack.getRootResource("pack.mcmeta");
        if (metadata != null) {
            resources.put("pack.mcmeta", metadata);
        }
        for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
            pack.listResources(PackType.CLIENT_RESOURCES, namespace, "", (id, supplier) ->
                    resources.put(id.toString(), supplier));
        }
        for (var entry : resources.entrySet()) {
            ResourceFingerprint.text(digest, entry.getKey());
            MessageDigest content = ResourceFingerprint.digest();
            try (InputStream stream = entry.getValue().get()) {
                ResourceFingerprint.stream(content, stream);
            }
            ResourceFingerprint.text(digest, ResourceFingerprint.finish(content));
        }
    }

    /** 与加载层的成功回调同一线程调用，先建立保存屏障，再允许发起下一次重载。 */
    public static void finishReload(boolean success) {
        Binding api = binding();
        try {
            if (!success) {
                requestFreshReload();
                cacheLoad = false;
            }
            if (!success || !cacheable) {
                api.reset.invoke(api.cache);
                return;
            }
            if (!"SAVE".equals(String.valueOf(api.status.invoke(api.cache)))) {
                api.reset.invoke(api.cache);
                return;
            }
            BARRIER.start(() -> {
                try {
                    // 重写同键缓存时先撤销提交标记，崩溃/中断留下的半包不可再次加载。
                    Path directory = (Path) api.directory.invoke(api.cache);
                    Files.deleteIfExists(directory.resolve("metadata.bin"));
                    boolean saved = (boolean) api.save.invoke(api.cache, (Object) null);
                    LOGGER.info("Background cache save {}", saved ? "completed" : "failed; next reload will rebuild");
                } catch (Exception error) {
                    LOGGER.warn("Could not save DashLoader cache", error);
                } finally {
                    try {
                        api.reset.invoke(api.cache);
                    } catch (ReflectiveOperationException error) {
                        LOGGER.warn("Could not reset DashLoader cache", error);
                    }
                }
            }, task -> {
                Thread worker = new Thread(task, "sre-dash-cache-save");
                worker.setDaemon(true);
                worker.setPriority(Thread.NORM_PRIORITY - 1);
                worker.start();
            });
        } catch (ReflectiveOperationException error) {
            LOGGER.warn("Could not finish DashLoader reload", error);
        }
    }


    /** DashLoader 记录的是逐状态模型；调用方必须按方块去重后加载完整定义。 */
    public static java.util.Set<net.minecraft.resources.ResourceLocation> missingBlockIds() {
        try {
            Class<?> module = Class.forName("dev.notalpha.dashloader.client.model.ModelModule");
            Class<?> status = Class.forName("dev.notalpha.dashloader.api.cache.CacheStatus");
            Object load = status.getField("LOAD").get(null);
            Object missing = module.getField("MISSING_READ").get(null);
            Object value = missing.getClass().getMethod("get", status).invoke(missing, load);
            if (!(value instanceof java.util.Map<?, ?> map)) {
                throw new IllegalStateException("DashLoader LOAD has no missing blockstate map");
            }
            java.util.List<net.minecraft.client.resources.model.ModelResourceLocation> models = new java.util.ArrayList<>();
            for (Object model : map.values()) {
                models.add((net.minecraft.client.resources.model.ModelResourceLocation) model);
            }
            return BlockStateJsonFallback.uniqueBlocks(models);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read DashLoader missing blockstate models", error);
        }
    }

    public static net.minecraft.server.packs.resources.ResourceManager fallbackResources() {
        return cacheLoad ? currentResources : null;
    }
    public static <T> CompletableFuture<T> afterSave(Supplier<CompletableFuture<T>> reload, Executor executor) {
        return BARRIER.afterSave(reload, executor);
    }

    public static CompletableFuture<Void> pendingSave() {
        return BARRIER.completion();
    }
}
