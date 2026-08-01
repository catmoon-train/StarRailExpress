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

package io.wifi.starrailexpress.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.roster.RoleRosterState;

/**
 * 客户端缓存的职业轮换名单，由 {@code RoleRosterSyncPayload} 更新，供查看 / 编辑界面读取。
 */
public final class ClientRoleRosterCache {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile RoleRosterState state = RoleRosterState.DISABLE;

    private ClientRoleRosterCache() {
    }

    public static void update(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            RoleRosterState parsed = GSON.fromJson(json, RoleRosterState.class);
            if (parsed != null) {
                state = parsed.normalized();
            }
        } catch (RuntimeException ignored) {
            // 保留旧值
        }
    }

    /** 返回当前缓存名单的副本，界面可在本地自由改动而不影响缓存。 */
    public static RoleRosterState snapshot() {
        return state.copy();
    }

    public static void clear() {
        state = RoleRosterState.DISABLE;
    }
}
