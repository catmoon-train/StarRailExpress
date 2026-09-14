/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 把 HTTP 与 SQL 的流量记进 {@link NetworkStatistics} 的调用点门面。
 *
 * <p>这两类流量不属于 Minecraft 数据包，因此各自走独立通道、由独立开关控制
 * （{@code /tmm:netstats http start} 与 {@code /tmm:netstats sql start}）。关着的时候
 * 每个调用点的开销只有一次 volatile 读取。
 *
 * <p><b>字节口径</b>（都是下界，不含协议层开销）：
 * <ul>
 *   <li>HTTP：发出 = URL 字节 + 请求体字节；收到 = 响应体字节。不含请求头/响应头、TLS 记录层与 TCP 开销。</li>
 *   <li>SQL：发出 = SQL 语句文本 + 绑定参数；收到 = 结果集列值。不含 MySQL 协议包头与 TLS。
 *       写入类语句只计次数、收到的字节记 0（没有结果集）。</li>
 * </ul>
 * 字符串一律按 UTF-8 字节数计算，驱动实际发送前还会做字符集转换与压缩，因此数值只能用于横向比较。
 *
 * <p>记账时机：HTTP 拿到响应就记（非 200 的响应体也算收到）；SQL 只在语句执行成功时记，
 * 失败的语句无法确定传输了多少字节。一条 JDBC 语句块（含批量执行）记一次交互，字节按行累计。
 *
 * <p><b>侧别</b>由调用点决定：MySQL（HikariCP 连接池）与 SyncRequests、服务端语言下载都发生在服务端，
 * 记入 {@link NetworkStatistics#getInstance()}；场景资源下载在客户端，记入
 * {@link NetworkStatistics#getClientInstance()}。
 */
public final class TrafficRecorder {

    /** 路径里形如 UUID 的段会被折叠成该占位符。 */
    private static final String UUID_PLACEHOLDER = "{uuid}";
    /** 路径里过长的随机令牌段（资源哈希、签名等）会被折叠成该占位符。 */
    private static final String TOKEN_PLACEHOLDER = "{token}";
    /** 长到不像普通路径名的段，按令牌处理。 */
    private static final Pattern TOKEN_SEGMENT = Pattern.compile("[A-Za-z0-9_.~-]{24,}");
    /** MySQL 里数值/时间参数在网络上大致占 8 字节，远小于字符串参数，按固定值估算即可。 */
    private static final long SQL_NUMBER_BYTES = 8L;

    private TrafficRecorder() {
    }

    // ------------------------------------------------------------------ HTTP

    /** 服务端发起的 HTTP 请求（SyncRequests 玩家数据接口、服务端语言下载等）。 */
    public static void httpServer(String method, String url,
            @Nullable String requestBody, @Nullable String responseBody) {
        http(NetworkStatistics.getInstance(), method, url, utf8Bytes(requestBody), utf8Bytes(responseBody));
    }

    /** 客户端发起的 HTTP 请求。 */
    public static void httpClient(String method, String url,
            @Nullable String requestBody, @Nullable String responseBody) {
        http(NetworkStatistics.getClientInstance(), method, url, utf8Bytes(requestBody), utf8Bytes(responseBody));
    }

    /**
     * 流式响应（下载大文件时调用方自己按块累计字节数）。
     *
     * @param requestBytes  请求体字节数，GET 传 0
     * @param responseBytes 本次响应实际收到的字节数，续传时只算这次新增的部分
     */
    public static void httpClientStreamed(String method, String url, long requestBytes, long responseBytes) {
        http(NetworkStatistics.getClientInstance(), method, url, Math.max(0L, requestBytes),
                Math.max(0L, responseBytes));
    }

    private static void http(NetworkStatistics stats, String method, String url,
            long requestBytes, long responseBytes) {
        if (stats == null || !stats.isHttpRecording()) {
            return;
        }
        stats.getHttpTraffic().record(httpEndpoint(method, url), requestBytes + utf8Bytes(url), responseBytes);
    }

    /**
     * HTTP 的分类标签：{@code 方法 host/路径}，去掉查询串与锚点，并把路径里的 UUID 与长令牌段
     * 折叠成占位符——否则 SyncRequests 那种带玩家 UUID 的接口会为每个玩家生成一条标签。
     */
    public static String httpEndpoint(String method, String url) {
        String normalizedMethod = method == null || method.isBlank()
                ? "HTTP"
                : method.trim().toUpperCase();
        String rawUrl = url == null ? "" : url.trim();
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                host = uri.getAuthority();
            }
            if (host == null || host.isBlank()) {
                return normalizedMethod + " " + stripQuery(rawUrl);
            }
            return normalizedMethod + " " + host + normalizePath(uri.getPath());
        } catch (IllegalArgumentException exception) {
            // URL 不合 URI 规范时退化成去查询串的原串，统计不该因为一个怪 URL 而中断。
            return normalizedMethod + " " + stripQuery(rawUrl);
        }
    }

    private static String normalizePath(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(path.length());
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()) {
                continue;
            }
            builder.append('/').append(normalizeSegment(segment));
        }
        return builder.length() == 0 ? "/" : builder.toString();
    }

    private static String normalizeSegment(String segment) {
        if (looksLikeUuid(segment)) {
            return UUID_PLACEHOLDER;
        }
        if (TOKEN_SEGMENT.matcher(segment).matches()) {
            return TOKEN_PLACEHOLDER;
        }
        return segment;
    }

    private static boolean looksLikeUuid(String segment) {
        if (segment.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(segment);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String stripQuery(String url) {
        int cut = url.length();
        int query = url.indexOf('?');
        if (query >= 0 && query < cut) {
            cut = query;
        }
        int fragment = url.indexOf('#');
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return url.substring(0, cut);
    }

    // ------------------------------------------------------------------ SQL

    /**
     * 开始记录一条 SQL 语句。返回的累加器在记录关闭时是空操作，
     * 因此调用点可以无条件地累加，读起来没有额外的分支。
     */
    public static SqlStatement sql(String statementType, String table) {
        return new SqlStatement(NetworkStatistics.getInstance(), statementType, table);
    }

    /** SQL 的分类标签：{@code 语句类型 表名}，例如 {@code SELECT player_sync_data}。 */
    public static String sqlEndpoint(String statementType, String table) {
        String type = statementType == null || statementType.isBlank()
                ? "SQL"
                : statementType.trim().toUpperCase();
        String name = table == null || table.isBlank() ? "(unknown)" : table.trim();
        return type + " " + name;
    }

    /** 一条 SQL 语句的字节累加器，见 {@link #sql(String, String)}。 */
    public static final class SqlStatement {

        private final NetworkStatistics stats;
        private final String endpoint;
        private final boolean enabled;
        private long requestBytes;
        private long resultBytes;

        private SqlStatement(NetworkStatistics stats, String statementType, String table) {
            this.stats = stats;
            this.endpoint = sqlEndpoint(statementType, table);
            this.enabled = stats != null && stats.isSqlRecording();
        }

        /** 记 SQL 语句文本本身。 */
        public SqlStatement text(String sql) {
            if (enabled && sql != null) {
                requestBytes += utf8Bytes(sql);
            }
            return this;
        }

        /** 记一个字符串型绑定参数。 */
        public SqlStatement param(@Nullable String value) {
            if (enabled && value != null) {
                requestBytes += utf8Bytes(value);
            }
            return this;
        }

        /** 记若干个数值/时间型绑定参数。 */
        public SqlStatement numbers(int count) {
            if (enabled && count > 0) {
                requestBytes += SQL_NUMBER_BYTES * count;
            }
            return this;
        }

        /** 直接累加请求字节数（调用方已自行算好时使用）。 */
        public SqlStatement request(long bytes) {
            if (enabled && bytes > 0L) {
                requestBytes += bytes;
            }
            return this;
        }

        /** 记一行结果集里读出的字符串列。 */
        public SqlStatement result(@Nullable String value) {
            if (enabled && value != null) {
                resultBytes += utf8Bytes(value);
            }
            return this;
        }

        /** 直接累加结果集字节数。 */
        public SqlStatement results(long bytes) {
            if (enabled && bytes > 0L) {
                resultBytes += bytes;
            }
            return this;
        }

        /** 落地这条语句的统计。没有调用过它就不会产生任何记录。 */
        public void record() {
            if (!enabled) {
                return;
            }
            stats.getSqlTraffic().record(endpoint, requestBytes, resultBytes);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static long utf8Bytes(@Nullable String value) {
        return value == null ? 0L : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
