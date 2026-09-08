package io.wifi.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import io.wifi.mixins.LanguageInstanceAccessor;
import io.wifi.starrailexpress.SREConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public final class ServerLanguageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLanguageManager.class);
    private static final String VANILLA_EN_US = "/assets/minecraft/lang/en_us.json";

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
        try {
            raw = SREConfig.instance().loadServerLanguageId;
        } catch (Exception e) {
            LOGGER.warn("[SRE-Server][Language Override] Reading loadServerLanguageId failed! Skip.", e);
            return;
        }
        if (raw == null || raw.isBlank()) {
            return;
        }
        String code = normalizeCode(raw);

        // 先放 en_us（classpath 的 vanilla en_us 作基础，保证与默认实例一致的完整回退），
        // 再叠加各模组 en_us，最后叠加指定语言：后写覆盖，即“指定语言优先、en_us 兜底”。
        Map<String, String> merged = new LinkedHashMap<>();
        int vanillaFiles = loadClasspathLanguage(merged, VANILLA_EN_US);
        int enUsFiles = loadModLanguages(merged, "en_us");
        int codeFiles = "en_us".equals(code) ? 0 : loadModLanguages(merged, code);

        if ("en_us".equals(code)) {
            LOGGER.info("[SRE-Server][Language Override] 未配置非 en_us 语言，仅合并全部模组的 en_us 翻译");
        } else if (codeFiles == 0) {
            LOGGER.warn("[SRE-Server][Language Override] 没有在任何模组中找到语言文件 assets/*/lang/{}.json，仅保留 en_us 回退", code);
        }

        try {
            // 确保 Language 的静态初始化已执行，避免其 clinit 随后回写覆盖我们的实例
            Language.getInstance();
        } catch (Throwable t) {
            LOGGER.debug("[SRE-Server][Language Override] Language 初始化异常（忽略）", t);
        }
        LanguageInstanceAccessor.sre_setLanguage(new MergedLanguage(merged));
        LOGGER.info("[SRE-Server][Language Override] 服务端语言已切换为 {}，共 {} 个 key（vanilla en_us {} 个文件、模组 en_us {} 个文件、指定语言 {} 个文件）",
                code, merged.size(), vanillaFiles, enUsFiles, codeFiles);
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
