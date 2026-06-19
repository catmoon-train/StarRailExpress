package io.wifi.starrailexpress.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.roster.RoleRosterState;

/**
 * 客户端缓存的职业轮换名单，由 {@code RoleRosterSyncPayload} 更新，供查看 / 编辑界面读取。
 */
public final class ClientRoleRosterCache {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile RoleRosterState state = RoleRosterState.createDefault();

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
        state = RoleRosterState.createDefault();
    }
}
