package io.wifi.srepackcompat.startup;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/** 只对已核对的核心回调启用，不提前加载 SRE 或 Minecraft 类。 */
public final class SreModelLookupConfig {
    private static final String CORE_CLASS = "io/wifi/starrailexpress/client/model/GeneralModelLoadingPlugin.class";
    private static final String CORE_SHA256 = "51f039ce121efc04381043e1ab275c6b55a46866c6b2de570998e34152447189";
    private static Boolean supported;

    public static synchronized boolean shouldApply() {
        if (supported != null) return supported;
        supported = false;
        var logger = LoggerFactory.getLogger("sre-pack-compat/model-lookup");
        try {
            FabricLoader loader = FabricLoader.getInstance();
            var core = loader.getModContainer("starrailexpress");
            if (core.isEmpty()) return false;
            if (!enabled(loader.getConfigDir().resolve("sre-pack-compat-models.properties"))) {
                logger.info("SRE model candidate index disabled by config");
                return false;
            }
            var path = core.get().findPath(CORE_CLASS);
            if (path.isEmpty() || !matchesKnownCore(Files.readAllBytes(path.get()))) {
                logger.info("SRE model candidate index skipped: unverified core callback");
                return false;
            }
            try (var stream = SreModelLookupConfig.class.getClassLoader().getResourceAsStream(CORE_CLASS)) {
                supported = stream != null && matchesKnownCore(stream.readAllBytes());
            }
        } catch (IOException | RuntimeException error) {
            logger.warn("SRE model candidate index not enabled; keeping original lookup: {}", error.toString());
        }
        return supported;
    }

    static boolean enabled(Path file) throws IOException {
        if (Files.notExists(file)) return true;
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        String value = properties.getProperty("enabled", "true").trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IOException("Invalid enabled option");
        return Boolean.parseBoolean(value);
    }

    static boolean matchesKnownCore(byte[] bytes) {
        try {
            return CORE_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private SreModelLookupConfig() { }
}
