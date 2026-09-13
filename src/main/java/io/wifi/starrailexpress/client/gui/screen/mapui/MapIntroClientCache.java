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

package io.wifi.starrailexpress.client.gui.screen.mapui;

import io.wifi.starrailexpress.network.MapDisplayInfo;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端保存的地图展示数据。
 *
 * <p>
 * 服务端下发前就已经把地图解析成了 {@link MapDisplayInfo}，所以这里**只存 DTO**：
 * 不再有 JsonObject、不再有 JSON 解析、也没有「把 JsonObject 再 toString 回字符串」的往返。
 */
public final class MapIntroClientCache {
    private static final Map<String, MapDisplayInfo> MAPS = new LinkedHashMap<>();
    /** 本轮投票的候选地图 id（由投票同步包给出）；空表示尚未收到候选信息 */
    private static List<String> candidateIds = List.of();
    /** 当前缓存是否是「显示全部地图」视图（管理员） */
    private static boolean includeAll;
    private static long refreshRequestedAt;

    private MapIntroClientCache() {
    }

    public static void update(MapIntroSyncPayload payload) {
        accept(payload);
    }

    /**
     * 合并分块：{@code chunkIndex == 0} 时重置，后续块继续追加。
     * 返回当前已累积的完整视图，供界面一次性刷新。
     */
    public static MapIntroSyncPayload accept(MapIntroSyncPayload payload) {
        if (payload == null) {
            return snapshot();
        }
        // 旧服务端/不兼容格式解出的哨兵包：直接忽略，不动已有缓存
        if (payload.isIgnored()) {
            return snapshot();
        }
        if (payload.chunkIndex() <= 0) {
            MAPS.clear();
        }
        if (payload.maps() != null) {
            for (MapDisplayInfo info : payload.maps()) {
                if (info != null && info.id() != null) {
                    MAPS.put(info.id(), info);
                }
            }
        }
        refreshRequestedAt = 0L;
        return snapshot();
    }

    public static MapIntroSyncPayload snapshot() {
        return new MapIntroSyncPayload(List.copyOf(MAPS.values()));
    }

    public static void beginRefresh() {
        beginRefresh(false);
    }

    public static void beginRefresh(boolean includeAllRequested) {
        includeAll = includeAllRequested;
        refreshRequestedAt = System.currentTimeMillis();
    }

    /** Wait briefly for authoritative metadata, but never strand the opening if an optional packet is lost. */
    public static boolean isRefreshPending() {
        return refreshRequestedAt > 0L && System.currentTimeMillis() - refreshRequestedAt < 2_500L;
    }

    /** 是否还没收到过任何地图数据（用于「打开界面自动请求一次」）。 */
    public static boolean isEmpty() {
        return MAPS.isEmpty();
    }

    /** 当前缓存是否来自「显示全部地图」请求（决定管理员勾选框状态）。 */
    public static boolean isIncludeAll() {
        return includeAll;
    }

    @Nullable
    public static MapDisplayInfo get(String id) {
        return id == null ? null : MAPS.get(id);
    }

    /** 当前缓存里的全部地图（按收到顺序）。 */
    public static List<MapDisplayInfo> all() {
        return List.copyOf(MAPS.values());
    }

    public static void setCandidateIds(List<String> ids) {
        candidateIds = ids == null ? List.of() : List.copyOf(ids);
    }

    public static List<String> getCandidateIds() {
        return candidateIds;
    }

    /**
     * 本轮投票候选：按候选 id 过滤缓存。
     * 没收到候选信息时（例如用旧数据）退化为「所有登记了投票配置的地图」。
     */
    public static List<MapDisplayInfo> candidates() {
        if (candidateIds.isEmpty()) {
            List<MapDisplayInfo> fallback = new ArrayList<>();
            for (MapDisplayInfo info : MAPS.values()) {
                if (info.hasVoteConfig()) {
                    fallback.add(info);
                }
            }
            return List.copyOf(fallback);
        }
        List<MapDisplayInfo> out = new ArrayList<>(candidateIds.size());
        for (String id : candidateIds) {
            MapDisplayInfo info = MAPS.get(id);
            if (info != null) {
                out.add(info);
            }
        }
        return List.copyOf(out);
    }
}
