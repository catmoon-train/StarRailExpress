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

package io.wifi.starrailexpress.client.disguise;

import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import io.wifi.starrailexpress.network.EntityDisguiseSyncPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端实体伪装缓存（uuid → 当前伪装状态）。由 {@link EntityDisguiseSyncPayload} 维护。
 * <p>
 * 只依赖 common 类，因此眼高 mixin 在客户端也能直接查这张表。
 * <p>
 * {@link #isEmpty()} 走一个 volatile 标记，让「没有任何人伪装」这个绝大多数情况下的判断
 * 只是一次字段读（渲染与眼高两条路径每帧都会问一次）；真正的表仍是并发实现，读写都在客户端主线程。
 */
public final class ClientEntityDisguiseCache {

    private static final Map<UUID, EntityDisguiseState> STATES = new ConcurrentHashMap<>();
    private static volatile boolean empty = true;

    private ClientEntityDisguiseCache() {
    }

    public static void applySync(EntityDisguiseSyncPayload payload) {
        if (payload.fullSync()) {
            STATES.clear();
        }
        for (Map.Entry<UUID, EntityDisguiseState> entry : payload.entries().entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
        empty = STATES.isEmpty();
    }

    public static EntityDisguiseState get(UUID uuid) {
        if (uuid == null) {
            return EntityDisguiseState.NONE;
        }
        EntityDisguiseState state = STATES.get(uuid);
        return state == null ? EntityDisguiseState.NONE : state;
    }

    /** 无人被伪装时快速跳过：一次 volatile 读，不做哈希查找。 */
    public static boolean isEmpty() {
        return empty;
    }

    public static void clear() {
        STATES.clear();
        empty = true;
    }

    private static void apply(UUID uuid, EntityDisguiseState state) {
        if (uuid == null) {
            return;
        }
        if (state == null || state.isNone()) {
            STATES.remove(uuid);
        } else {
            STATES.put(uuid, state);
        }
        empty = STATES.isEmpty();
    }
}
