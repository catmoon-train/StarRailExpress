package io.wifi.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.mixins.LanguageInstanceAccessor;
import io.wifi.starrailexpress.SREConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 服务端语言覆盖管理器。
 * <p>
 * vanilla 专用服务器的 {@link Language} 只会被初始化成 en_us（{@code Language#loadDefault}
 * 只解析
 * classpath 里的 {@code assets/minecraft/lang/en_us.json}）。本类在专用服务器上把
 * {@code Language}
 * 实例替换为“指定语言文件优先、en_us 兜底”的合并语言表，使服务端所有走
 * {@code Language.getInstance()} / {@code Component#getString()} / I18n 委托的 key
 * 解析
 * （含其它模组）都能命中并优先输出指定语言文本。
 * <p>
 * 配置项 {@code downloadVanillaLanguageFile} 开启时，会为指定语言代码从 Mojang 官方下载
 * 原版语言文件（{@code assets/minecraft/lang/&lt;code&gt;.json}，专用服务器本地只有 en_us），
 * 使原版 key 也能按指定语言翻译。下载在后台守护线程执行、不阻塞启动线程，结果缓存到游戏根目录
 * （项目目录）的 {@code sre_lang_cache/&lt;mc版本&gt;/&lt;code&gt;.json}，与任何世界存档无关。
 */
public final class ServerLanguageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLanguageManager.class);
    private static final String VANILLA_EN_US = "/assets/minecraft/lang/en_us.json";
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String CACHE_DIR_NAME = "sre_lang_cache";
    private static final String USER_AGENT = "StarRailExpress-server-language-fetcher";

    /** 防止重复触发下载（如配置热重载时）。 */
    private static final AtomicBoolean VANILLA_DOWNLOADING = new AtomicBoolean(false);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ServerLanguageManager() {
    }

    /**
     * 仅当运行在纯专用服务器（EnvType.SERVER）时按配置覆盖服务端语言；客户端/单人/LAN 环境不做任何事。
     * <ul>
     * <li>{@code loadServerLanguageId} 为空 → 不覆盖，保持 vanilla 默认（en_us）。</li>
     * <li>值为 {@code auto} → 跟随 JVM 系统区域（Locale#getDefault）解析为 Minecraft 语言代码。</li>
     * <li>其它值 → 直接作为语言代码（如 zh_cn）。</li>
     * </ul>
     * 方法幂等，可在配置热重载后再次调用。
     */
    public static void applyFromConfig() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        String raw;
        boolean downloadVanilla;
        try {
            SREConfig config = SREConfig.instance();
            raw = config.loadServerLanguageId;
            downloadVanilla = config.downloadVanillaLanguageFile;
        } catch (Exception e) {
            LOGGER.warn("[SRE-Server][Language Override] Reading config failed! Skip.", e);
            return;
        }
        if (raw == null || raw.isBlank()) {
            return;
        }
        String code = normalizeCode(raw);
        // en_us 本地已有原版文件，无需下载；其余语言才考虑 Mojang 原版翻译
        install(code, downloadVanilla && !"en_us".equals(code));
    }

    /**
     * 用当前可用数据重建并应用语言实例。合并顺序（后写覆盖）：
     * <ol>
     * <li>vanilla en_us（classpath 基础层，保证与默认实例一致的完整回退）</li>
     * <li>全部模组的 en_us</li>
     * <li>原版 {@code <code>.json}（缓存命中时本地读入）</li>
     * <li>全部模组的 {@code <code>.json}（模组可覆盖原版 key）</li>
     * </ol>
     * 本地 IO 不阻塞（扫模组 assets、读缓存小文件均为毫秒级），网络请求只会在后台线程触发。
     */
    private static void install(String code, boolean wantVanilla) {
        Map<String, String> merged = new LinkedHashMap<>();
        int vanillaEnUsFiles = loadClasspathLanguage(merged, VANILLA_EN_US);
        int modEnUsFiles = loadModLanguages(merged, "en_us");

        int vanillaCodeFiles = 0;
        int modCodeFiles = 0;
        if (!"en_us".equals(code)) {
            if (wantVanilla) {
                vanillaCodeFiles = loadCachedVanillaLanguage(merged, code);
            }
            modCodeFiles = loadModLanguages(merged, code);
        }

        try {
            // 确保 Language 的静态初始化已执行，避免其 clinit 随后回写覆盖我们的实例
            Language.getInstance();
        } catch (Throwable t) {
            LOGGER.debug("[SRE-Server][Language Override] Language 初始化异常（忽略）", t);
        }
        LanguageInstanceAccessor.sre_setLanguage(new MergedLanguage(merged));

        if ("en_us".equals(code)) {
            LOGGER.info("[SRE-Server][Language Override] 未配置非 en_us 语言，仅合并全部模组的 en_us 翻译，key={}", merged.size());
        } else {
            if (vanillaCodeFiles == 0 && modCodeFiles == 0) {
                LOGGER.warn("[SRE-Server][Language Override] 没有找到任何 {}.json（模组或原版缓存），仅保留 en_us 回退", code);
            }
            LOGGER.info("[SRE-Server][Language Override] 服务端语言已切换为 {}，key={}（vanilla en_us {} 个文件、模组 en_us {} 个文件、原版 {} {} 个文件、模组 {} {} 个文件）",
                    code, merged.size(), vanillaEnUsFiles, modEnUsFiles, code, vanillaCodeFiles, code, modCodeFiles);
        }

        // 缓存缺失且开启开关时，后台下载原版语言文件，完成后会再次调用本方法重装
        if (wantVanilla && vanillaCodeFiles == 0) {
            startVanillaDownload(code);
        }
    }

    /** 解析用户配置为 Minecraft 语言代码。 */
    private static String normalizeCode(String raw) {
        String trimmed = raw.trim();
        if ("auto".equalsIgnoreCase(trimmed)) {
            Locale locale = Locale.getDefault();
            String lang = locale.getLanguage();
            String country = locale.getCountry();
            if (lang == null || lang.isEmpty()) {
                return "en_us";
            }
            if (country == null || country.isEmpty()) {
                return lang.toLowerCase(Locale.ROOT);
            }
            return (lang + "_" + country).toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** 从 classpath 读取单个语言文件（vanilla en_us 保证基础层存在）。 */
    private static int loadClasspathLanguage(Map<String, String> target, String resource) {
        try (InputStream in = Language.class.getResourceAsStream(resource)) {
            if (in == null) {
                return 0;
            }
            Language.loadFromJson(in, target::put);
            return 1;
        } catch (Exception e) {
            LOGGER.debug("[SRE-Server][Language Override] 读取 classpath 语言文件失败: {}", resource, e);
            return 0;
        }
    }

    /** 遍历所有已加载模组，合并其 lang 目录中的指定语言文件。返回实际读取的文件数。 */
    private static int loadModLanguages(Map<String, String> target, String code) {
        String fileName = code + ".json";
        int loadedFiles = 0;
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            for (Path root : container.getRootPaths()) {
                Path assets;
                try {
                    assets = root.resolve("assets");
                    if (!Files.isDirectory(assets)) {
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }
                try (Stream<Path> stream = Files.walk(assets, 3)) {
                    List<Path> files = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName() != null && fileName.equals(p.getFileName().toString()))
                            .filter(p -> p.getParent() != null && p.getParent().getFileName() != null
                                    && "lang".equals(p.getParent().getFileName().toString()))
                            .toList();
                    for (Path file : files) {
                        try (InputStream in = Files.newInputStream(file)) {
                            Language.loadFromJson(in, target::put);
                            loadedFiles++;
                        } catch (Exception e) {
                            LOGGER.warn("[SRE-Server][Language Override] 读取语言文件失败: {}", file, e);
                        }
                    }
                } catch (Exception e) {
                    // 该容器根路径不可遍历（如 minecraft jar 未挂载文件系统），跳过
                }
            }
        }
        return loadedFiles;
    }

    // ------------------------------------------------------------------
    // Mojang 官方原版语言文件的下载与缓存（缓存位于游戏根目录，非世界目录）
    // ------------------------------------------------------------------

    /** 读取本地缓存的 Mojang 原版语言文件。返回是否成功读入 1 个文件。 */
    private static int loadCachedVanillaLanguage(Map<String, String> target, String code) {
        Path file = vanillaCachePath(code);
        if (file == null || !Files.isRegularFile(file)) {
            return 0;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Language.loadFromJson(in, target::put);
            LOGGER.info("[SRE-Server][Language Override] 已从缓存加载原版语言文件: {}", file);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[SRE-Server][Language Override] 读取原版缓存失败（将重新下载）: {}", file, e);
            return 0;
        }
    }

    /** 原版语言文件缓存路径：游戏根目录/sre_lang_cache/&lt;mc版本&gt;/&lt;code&gt;.json */
    private static Path vanillaCachePath(String code) {
        try {
            String mcVersion = SharedConstants.getCurrentVersion().getName();
            return FabricLoader.getInstance().getGameDir()
                    .resolve(CACHE_DIR_NAME)
                    .resolve(mcVersion)
                    .resolve(code + ".json");
        } catch (Exception e) {
            LOGGER.debug("[SRE-Server][Language Override] 无法解析缓存目录", e);
            return null;
        }
    }

    /** 缓存缺失时启动后台守护线程下载原版语言文件；下载完成后自动重新应用语言实例。 */
    private static void startVanillaDownload(String code) {
        if (!VANILLA_DOWNLOADING.compareAndSet(false, true)) {
            LOGGER.debug("[SRE-Server][Language Override] 原版语言文件下载已在途，跳过重复触发: {}", code);
            return;
        }
        Thread downloader = new Thread(() -> {
            try {
                downloadVanillaLanguage(code);
            } finally {
                VANILLA_DOWNLOADING.set(false);
            }
        }, "sre-lang-download");
        downloader.setDaemon(true);
        downloader.start();
    }

    /** 按 Mojang 官方链路下载 assets/minecraft/lang/&lt;code&gt;.json 并写入缓存，成功后重装语言实例。 */
    private static void downloadVanillaLanguage(String code) {
        try {
            // 1. 版本清单 → 找当前 MC 版本的 version json 地址
            String manifest = httpGet(VERSION_MANIFEST_URL);
            if (manifest == null) {
                return;
            }
            String mcVersion = SharedConstants.getCurrentVersion().getName();
            String versionUrl = null;
            JsonObject manifestRoot = JsonParser.parseString(manifest).getAsJsonObject();
            for (JsonElement element : manifestRoot.getAsJsonArray("versions")) {
                JsonObject version = element.getAsJsonObject();
                if (mcVersion.equals(version.get("id").getAsString())) {
                    versionUrl = version.get("url").getAsString();
                    break;
                }
            }
            if (versionUrl == null) {
                LOGGER.warn("[SRE-Server][Language Override] 版本清单中找不到 Minecraft 版本 {}", mcVersion);
                return;
            }

            // 2. 版本 json → assetIndex 地址（只下资产索引，避免下载整个 client.jar）
            String versionJson = httpGet(versionUrl);
            if (versionJson == null) {
                return;
            }
            JsonObject versionRoot = JsonParser.parseString(versionJson).getAsJsonObject();
            if (!versionRoot.has("assetIndex")) {
                LOGGER.warn("[SRE-Server][Language Override] 版本 json 缺少 assetIndex 字段: {}", mcVersion);
                return;
            }
            String assetIndexUrl = versionRoot.getAsJsonObject("assetIndex").get("url").getAsString();

            // 3. 资产索引 → minecraft/lang/<code>.json 的 hash
            String assetIndexJson = httpGet(assetIndexUrl);
            if (assetIndexJson == null) {
                return;
            }
            JsonObject objects = JsonParser.parseString(assetIndexJson).getAsJsonObject().getAsJsonObject("objects");
            String objectKey = "minecraft/lang/" + code + ".json";
            if (!objects.has(objectKey)) {
                LOGGER.warn("[SRE-Server][Language Override] 官方资产中没有语言文件 {}（代码可能不存在）", objectKey);
                return;
            }
            String hash = objects.getAsJsonObject(objectKey).get("hash").getAsString();

            // 4. 下载语言文件本体
            String languageUrl = "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;
            String content = httpGet(languageUrl);
            if (content == null) {
                return;
            }

            // 写入缓存前先用 vanilla 解析器验证内容有效，避免缓存到损坏响应
            try {
                Language.loadFromJson(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), (k, v) -> {
                });
            } catch (Exception e) {
                LOGGER.warn("[SRE-Server][Language Override] 下载的内容不是有效的语言文件，已丢弃: {}", objectKey, e);
                return;
            }

            Path target = vanillaCachePath(code);
            if (target == null) {
                LOGGER.warn("[SRE-Server][Language Override] 无法解析缓存目录，放弃下载 {}", code);
                return;
            }
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("[SRE-Server][Language Override] 已下载 Mojang 官方原版语言文件 -> {}", target);

            // 下载完成，重装语言实例（此时缓存可被本地读入）
            install(code, true);
        } catch (Exception e) {
            LOGGER.warn("[SRE-Server][Language Override] 原版语言文件下载失败(code={})，将在下次启用配置时重试", code, e);
        }
    }

    /** 阻塞式 GET（只在后台下载线程调用）。失败返回 null 并记日志。 */
    private static String httpGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.warn("[SRE-Server][Language Override] 请求返回状态码 {}: {}", response.statusCode(), url);
                return null;
            }
            return response.body();
        } catch (Exception e) {
            LOGGER.warn("[SRE-Server][Language Override] 请求失败: {}", url, e);
            return null;
        }
    }

    /**
     * 合并语言表的 Language 实现：先命中指定语言文件、再命中 en_us、最后回退调用方传入的 fallback。
     * <p>
     * {@link #getVisualOrder} 采用与 vanilla {@code Language$1} 完全一致的 LTR（无双向排版）实现。
     */
    private static final class MergedLanguage extends Language {

        private final Map<String, String> storage;

        private MergedLanguage(Map<String, String> storage) {
            this.storage = storage;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return storage.getOrDefault(key, fallback);
        }

        @Override
        public boolean has(String key) {
            return storage.containsKey(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return sink -> text.visit(
                    (style, content) -> StringDecomposer.iterateFormatted(content, style, sink)
                            ? Optional.empty()
                            : FormattedText.STOP_ITERATION,
                    Style.EMPTY).isEmpty();
        }
    }
}
