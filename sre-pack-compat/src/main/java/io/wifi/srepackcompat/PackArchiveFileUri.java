package io.wifi.srepackcompat;

import java.net.URI;

/** 独立兼容包的 ZIP URI 解析，不依赖主模组仍供 Lightspeed 使用的工具类。 */
public final class PackArchiveFileUri {
    private PackArchiveFileUri() { }

    public static URI resolve(URI root, URI fallback) {
        if (!"jar".equalsIgnoreCase(root.getScheme())) return fallback;
        String raw = root.getRawSchemeSpecificPart();
        int end = raw.indexOf("!/");
        if (end < 0) return fallback;
        try {
            URI archive = URI.create(raw.substring(0, end).replace("[", "%5B").replace("]", "%5D"));
            return "file".equalsIgnoreCase(archive.getScheme()) ? archive : fallback;
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }
}
