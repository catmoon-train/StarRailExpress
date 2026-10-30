package org.agmas.noellesroles.game.halloween.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 独立的服务端配置，绝不通过世界配置或同步包传给客户端。 */
public final class OnlineConfig {
    public boolean enabled = false;
    public String baseUrl = "";
    public String bridgeSecret = "";
    public int flushIntervalSeconds = 30;
    public int batchSize = 128;
    public int requestTimeoutSeconds = 10;

    public static OnlineConfig load(Path file) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(new OnlineConfig()) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            OnlineConfig config = gson.fromJson(reader, OnlineConfig.class);
            if (config == null) throw new IOException("Empty halloween-online configuration");
            return config;
        } catch (RuntimeException ex) {
            // 不传播可能包含 secret 的 Gson 错误原文。
            throw new IOException("Invalid halloween-online configuration JSON");
        }
    }

    public URI endpoint() {
        if (bridgeSecret == null || bridgeSecret.length() < 32)
            throw new IllegalArgumentException("bridgeSecret must contain at least 32 characters");
        if (batchSize < 1 || batchSize > 128 || flushIntervalSeconds < 1 || flushIntervalSeconds > 86400
                || requestTimeoutSeconds < 1 || requestTimeoutSeconds > 60)
            throw new IllegalArgumentException("Invalid halloween-online timing/batch limits");
        try {
            URI base = URI.create(baseUrl);
            String host = base.getHost();
            String path = base.getRawPath();
            boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host);
            if (!base.isAbsolute() || base.isOpaque() || host == null || base.getRawUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null
                    || !(path == null || path.isEmpty() || path.equals("/"))
                    || base.getPort() == 0 || base.getPort() > 65535
                    || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && local)))
                throw new IllegalArgumentException();
            return base.resolve(BridgeProtocol.PATH);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("baseUrl must be an HTTPS origin (HTTP only on literal loopback)");
        }
    }
}
