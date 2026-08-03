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

package io.wifi.starrailexpress.client.hat;

import io.wifi.starrailexpress.network.HatEquipmentSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端帽子装备缓存。
 * <p>
 * 保存服务器广播的所有玩家的帽子装备状态（uuid → 帽子皮肤名），
 * 供渲染层查询任意玩家（包括其他玩家）当前装备的帽子。
 * 由 {@link HatEquipmentSyncPayload} 的接收器维护。
 */
@Environment(EnvType.CLIENT)
public final class ClientHatEquipmentCache {
    private static final Map<UUID, String> EQUIPPED_HATS = new ConcurrentHashMap<>();

    private ClientHatEquipmentCache() {
    }

    /** 应用服务器同步包 */
    public static void applySync(HatEquipmentSyncPayload payload) {
        if (payload.fullSync()) {
            EQUIPPED_HATS.clear();
        }
        for (Map.Entry<UUID, String> entry : payload.entries().entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
    }

    /** 获取某玩家当前装备的帽子皮肤名；未装备或无数据时返回 "default" */
    public static String getHatSkin(UUID uuid) {
        if (uuid == null) {
            return "default";
        }
        String skin = EQUIPPED_HATS.get(uuid);
        return skin == null || skin.isBlank() ? "default" : skin;
    }

    /**
     * 本地乐观更新（例如玩家在装备界面点击后立即生效），
     * 服务器随后的权威广播会覆盖此值。
     */
    public static void setLocalOptimistic(UUID uuid, String skinName) {
        apply(uuid, skinName);
    }

    public static void clear() {
        EQUIPPED_HATS.clear();
    }

    private static void apply(UUID uuid, String skinName) {
        if (uuid == null) {
            return;
        }
        if (skinName == null || skinName.isBlank() || "default".equals(skinName)) {
            EQUIPPED_HATS.remove(uuid);
        } else {
            EQUIPPED_HATS.put(uuid, skinName);
        }
    }
}
