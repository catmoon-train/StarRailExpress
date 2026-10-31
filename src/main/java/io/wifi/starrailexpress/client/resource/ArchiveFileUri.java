package io.wifi.starrailexpress.client.resource;

import java.net.URI;

/** 从 ZIP 文件系统的根 URI 提取原始文件 URI，保留嵌套 URI 中恰好一层转义。 */
public final class ArchiveFileUri {
    private ArchiveFileUri() {
    }

    public static URI resolve(URI packRoot, URI fallback) {
        if (!"jar".equalsIgnoreCase(packRoot.getScheme())) {
            return fallback;
        }
        String raw = packRoot.getRawSchemeSpecificPart();
        int separator = raw.indexOf("!/");
        if (separator < 0) {
            return fallback;
        }
        try {
            // jar: 是不透明 URI，ZIP provider 可保留方括号；嵌套 file: 是层次 URI，需补转义。
            // 已有的 %20 / %25 保持原样，不能再次 URL 解码（也不能把 '+' 变成空格）。
            String nested = raw.substring(0, separator).replace("[", "%5B").replace("]", "%5D");
            URI archive = URI.create(nested);
            return "file".equalsIgnoreCase(archive.getScheme()) ? archive : fallback;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
