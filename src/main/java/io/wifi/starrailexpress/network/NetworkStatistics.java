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

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * 网络数据包统计内核。
 *
 * <p>这里有两个实例，分别对应一个 JVM 侧的流量：
 * <ul>
 *   <li>{@link #getInstance()} —— 服务端侧（服务端发出 / 收到）</li>
 *   <li>{@link #getClientInstance()} —— 客户端侧（客户端发出 / 收到）</li>
 * </ul>
 * 集成服务器（单人游戏）里两者处于同一个 JVM，因此 {@code Connection} 的注入点会先判定这条
 * 连接属于哪一侧，再路由到对应实例，两边的 {@code start}/{@code stop} 互不影响。
 *
 * <p>统计只在 {@link #startRecording()} 之后才写入内存；未记录时热路径只有一次 volatile 读取。
 *
 * <p>热路径设计（每包）为：1 次 volatile 读 → 1 次 instanceof → 1 次按包类型的查表 →
 * 若干原子累加。包类型的显示用 id 字符串、累加器对象、以及是否由外部（CCA 同步）接管，
 * 都在 {@link TypeCacheEntry} 里缓存，因此不会每包都拼接字符串或反复查统计表。
 */
public final class NetworkStatistics {

    private static final Logger LOGGER = LoggerFactory.getLogger("TMM-NetworkStats");

    private static final NetworkStatistics SERVER_INSTANCE = new NetworkStatistics("server");
    private static final NetworkStatistics CLIENT_INSTANCE = new NetworkStatistics("client");

    /** 单个包类型的累计统计，也是导出时会枚举到的对象。 */
    public static final class PacketTypeStats {
        private final String id;
        private final PacketStats outbound = new PacketStats();
        private final PacketStats inbound = new PacketStats();

        PacketTypeStats(String id) {
            this.id = id;
        }

        /** 包类型 id，例如 {@code starrailexpress:click_lockout}。 */
        public String getId() {
            return id;
        }

        /** 本侧发往对端的统计。 */
        public PacketStats getOutbound() {
            return outbound;
        }

        /** 对端发往本侧的统计。 */
        public PacketStats getInbound() {
            return inbound;
        }
    }

    /**
     * 热路径用的按包类型缓存条目。{@code stats} 为 {@code null} 表示该类型由外部记账
     * （CCA 组件同步有自己的标签，见 {@link #markExternallyTracked}），注入点应整包跳过。
     */
    public static final class TypeCacheEntry {
        private final String id;
        private final PacketTypeStats stats;

        private TypeCacheEntry(String id, PacketTypeStats stats) {
            this.id = id;
            this.stats = stats;
        }

        public String getId() {
            return id;
        }

        /** 导出标签与累加器；{@code null} 表示该类型被外部接管。 */
        public PacketTypeStats getStats() {
            return stats;
        }
    }

    private final String side;

    /** 包类型 -> 缓存条目，热路径查这张表。 */
    private final ConcurrentHashMap<CustomPacketPayload.Type<?>, TypeCacheEntry> typeCache = new ConcurrentHashMap<>();
    /** 包类型 id -> 累计统计，导出与命令枚举这张表。 */
    private final ConcurrentHashMap<String, PacketTypeStats> trackedTypes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, PlayerPacketStats> playerStats = new ConcurrentHashMap<>();

    private final LongAdder outboundPackets = new LongAdder();
    private final LongAdder outboundBytes = new LongAdder();
    private final LongAdder inboundPackets = new LongAdder();
    private final LongAdder inboundBytes = new LongAdder();
    /** 真实序列化成功 / 回退到估算值的包数，用于让导出的字节数可信度可自查。 */
    private final LongAdder measuredSizes = new LongAdder();
    private final LongAdder fallbackSizes = new LongAdder();

    private volatile boolean recording = false;
    private volatile long recordingStartedAt = 0L;

    private volatile Supplier<RegistryAccess> registryAccessSupplier = NetworkStatistics::serverRegistryAccess;
    private volatile Supplier<Player> localPlayerSupplier;

    private NetworkStatistics(String side) {
        this.side = side;
    }

    public static NetworkStatistics getInstance() {
        return SERVER_INSTANCE;
    }

    public static NetworkStatistics getClientInstance() {
        return CLIENT_INSTANCE;
    }

    private static RegistryAccess serverRegistryAccess() {
        MinecraftServer server = SRE.SERVER;
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }

    /** 由服务端初始化调用，保留为记录一条日志的入口。 */
    public void initialize() {
        LOGGER.info("Network Statistics initialized (server instance ready)");
    }

    // ------------------------------------------------------------------ 记录开关

    public boolean isRecording() {
        return recording;
    }

    /** 开启记录：先清空上一次的数据，再开始新的统计会话。 */
    public void startRecording() {
        // 先停写再清空，避免清空过程中仍有网络线程写入半截数据。
        recording = false;
        resetStats();
        recordingStartedAt = System.currentTimeMillis();
        recording = true;
    }

    /** 停止记录，已收集的数据保留在内存中。 */
    public void stopRecording() {
        recording = false;
    }

    /** 清空统计数据（不清除记录状态）。 */
    public void resetStats() {
        outboundPackets.reset();
        outboundBytes.reset();
        inboundPackets.reset();
        inboundBytes.reset();
        measuredSizes.reset();
        fallbackSizes.reset();
        typeCache.clear();
        trackedTypes.clear();
        playerStats.clear();
    }

    public String getSide() {
        return side;
    }

    /** 本次记录会话开始的时刻（epoch 毫秒），未开始时为 0。 */
    public long getRecordingStartedAt() {
        return recordingStartedAt;
    }

    // ------------------------------------------------------------------ 热路径

    /**
     * 取得（必要时创建）该包类型的缓存条目。每包一次查表，之后只读字段。
     */
    public TypeCacheEntry cacheEntry(CustomPacketPayload.Type<?> type) {
        TypeCacheEntry cached = typeCache.get(type);
        if (cached != null) {
            return cached;
        }
        return typeCache.computeIfAbsent(type, key -> {
            String id = key.id().toString();
            return new TypeCacheEntry(id, trackedTypes.computeIfAbsent(id, PacketTypeStats::new));
        });
    }

    /**
     * 声明某个包类型由外部记账（CCA 组件同步会因为要知道是哪个组件而自行记录），
     * {@code Connection} 注入点遇到这些类型直接跳过，避免重复计数。
     *
     * <p>每次同步都会调用，因此正常路径只做一次查表；重新标记（例如刚 clear 过或已被记成普通类型）
     * 才会新建条目。
     */
    public void markExternallyTracked(CustomPacketPayload.Type<?> type) {
        TypeCacheEntry existing = typeCache.get(type);
        if (existing != null && existing.stats == null) {
            return;
        }
        typeCache.put(type, new TypeCacheEntry(type.id().toString(), null));
    }

    /**
     * 记录一次数据包。{@code player} 可为 {@code null}（拿不到玩家身份时只记全局与类型维度）。
     *
     * @param outbound {@code true} 表示本侧发出，{@code false} 表示本侧收到
     */
    public void record(PacketTypeStats stats, long size, boolean outbound, PlayerPacketStats player) {
        if (outbound) {
            outboundPackets.increment();
            outboundBytes.add(size);
            stats.outbound.update(size);
            if (player != null) {
                player.recordOutbound(stats.id, size);
            }
        } else {
            inboundPackets.increment();
            inboundBytes.add(size);
            stats.inbound.update(size);
            if (player != null) {
                player.recordInbound(stats.id, size);
            }
        }
    }

    /**
     * 记录一次由外部自行命名的数据包（CCA 组件同步用，标签含组件 key）。
     */
    public void recordExternal(String label, long size, boolean outbound, PlayerPacketStats player) {
        PacketTypeStats stats = trackedTypes.computeIfAbsent(label, PacketTypeStats::new);
        record(stats, size, outbound, player);
    }

    // ------------------------------------------------------------------ 玩家维度

    public PlayerPacketStats playerStatsFor(String playerName) {
        return playerStats.computeIfAbsent(playerName, k -> new PlayerPacketStats());
    }

    public Map<String, PlayerPacketStats> getPlayerStats() {
        return playerStats;
    }

    /** 客户端侧用：当前本地玩家，取不到时返回 {@code null}。 */
    @Nullable
    public Player getLocalPlayer() {
        Supplier<Player> supplier = localPlayerSupplier;
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 由客户端初始化注入；服务端实例不需要。 */
    public void setLocalPlayerSupplier(Supplier<Player> supplier) {
        this.localPlayerSupplier = supplier;
    }

    // ------------------------------------------------------------------ 字节数可信度

    public void countMeasuredSize() {
        measuredSizes.increment();
    }

    public void countFallbackSize() {
        fallbackSizes.increment();
    }

    public long getMeasuredSizeCount() {
        return measuredSizes.sum();
    }

    public long getFallbackSizeCount() {
        return fallbackSizes.sum();
    }

    // ------------------------------------------------------------------ 注册表访问（真实序列化用）

    public void setRegistryAccessSupplier(Supplier<RegistryAccess> supplier) {
        this.registryAccessSupplier = supplier != null ? supplier : () -> RegistryAccess.EMPTY;
    }

    /** 供 {@link NetworkUtils} 编码载荷时使用；取不到时返回空注册表。 */
    public RegistryAccess resolveRegistryAccess() {
        try {
            RegistryAccess access = registryAccessSupplier.get();
            return access != null ? access : RegistryAccess.EMPTY;
        } catch (Throwable ignored) {
            return RegistryAccess.EMPTY;
        }
    }

    // ------------------------------------------------------------------ 查询与排行

    public long getOutboundPackets() {
        return outboundPackets.sum();
    }

    public long getInboundPackets() {
        return inboundPackets.sum();
    }

    public long getOutboundBytes() {
        return outboundBytes.sum();
    }

    public long getInboundBytes() {
        return inboundBytes.sum();
    }

    public double getAveragePacketSize() {
        long totalPackets = getOutboundPackets() + getInboundPackets();
        long totalBytes = getOutboundBytes() + getInboundBytes();
        return totalPackets > 0L ? (double) totalBytes / totalPackets : 0.0D;
    }

    /** 已跟踪的包类型数量。 */
    public int getTrackedTypeCount() {
        return trackedTypes.size();
    }

    public Collection<PacketTypeStats> getTrackedTypes() {
        return trackedTypes.values();
    }

    /**
     * 按指定维度排行。
     *
     * @param outbound {@code true} 排本侧发出的，{@code false} 排本侧收到的
     * @param byBytes  {@code true} 按总字节数，{@code false} 按包数量
     */
    public List<PacketTypeStats> topBy(boolean outbound, boolean byBytes, int limit) {
        Comparator<PacketTypeStats> comparator = byBytes
                ? Comparator.comparingLong((PacketTypeStats s) ->
                        (outbound ? s.outbound : s.inbound).getTotalSize())
                : Comparator.comparingLong((PacketTypeStats s) ->
                        (outbound ? s.outbound : s.inbound).getCount());
        List<PacketTypeStats> result = new ArrayList<>(trackedTypes.values());
        result.removeIf(s -> (outbound ? s.outbound : s.inbound).getCount() == 0L);
        result.sort(comparator.reversed());
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /** 按平均包大小排行，同样过滤掉没有样本的类型。 */
    public List<PacketTypeStats> topByAverageSize(boolean outbound, int limit) {
        List<PacketTypeStats> result = new ArrayList<>(trackedTypes.values());
        result.removeIf(s -> (outbound ? s.outbound : s.inbound).getCount() == 0L);
        result.sort(Comparator.comparingDouble((PacketTypeStats s) ->
                (outbound ? s.outbound : s.inbound).getAverageSize()).reversed());
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    public void logStats() {
        LOGGER.info("=== Network Statistics [{}] ===", side);
        LOGGER.info("Recording: {}", recording);
        LOGGER.info("Total packets sent: {}", getOutboundPackets());
        LOGGER.info("Total packets received: {}", getInboundPackets());
        LOGGER.info("Total bytes sent: {}", getOutboundBytes());
        LOGGER.info("Total bytes received: {}", getInboundBytes());
        LOGGER.info("Tracked packet types: {}", getTrackedTypeCount());
        LOGGER.info("================================");
    }
}
