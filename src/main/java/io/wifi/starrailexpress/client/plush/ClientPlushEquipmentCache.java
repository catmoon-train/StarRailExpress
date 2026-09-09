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

package io.wifi.starrailexpress.client.plush;

import io.wifi.starrailexpress.network.PlushEquipmentSyncPayload;
import io.wifi.starrailexpress.plush.PlushEquipmentIdentity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端身份玩偶缓存（uuid → 编码后的玩偶身份）。
 */
@Environment(EnvType.CLIENT)
public final class ClientPlushEquipmentCache {
    private static final Map<UUID, String> EQUIPPED = new ConcurrentHashMap<>();
    private static final Map<UUID, ItemStack> STACKS = new ConcurrentHashMap<>();

    private ClientPlushEquipmentCache() {
    }

    public static void applySync(PlushEquipmentSyncPayload payload) {
        if (payload.fullSync()) {
            EQUIPPED.clear();
            STACKS.clear();
        }
        for (Map.Entry<UUID, String> entry : payload.entries().entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
    }

    public static String getEncoded(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        String encoded = EQUIPPED.get(uuid);
        return encoded == null ? "" : encoded;
    }

    public static ItemStack getStack(UUID uuid) {
        String encoded = getEncoded(uuid);
        if (encoded.isBlank()) {
            return ItemStack.EMPTY;
        }
        return STACKS.computeIfAbsent(uuid, id -> {
            PlushEquipmentIdentity identity = PlushEquipmentIdentity.decode(encoded);
            return identity == null ? ItemStack.EMPTY : identity.toStack();
        });
    }

    public static void clear() {
        EQUIPPED.clear();
        STACKS.clear();
    }

    private static void apply(UUID uuid, String encoded) {
        if (uuid == null) {
            return;
        }
        STACKS.remove(uuid);
        if (encoded == null || encoded.isBlank()) {
            EQUIPPED.remove(uuid);
        } else {
            EQUIPPED.put(uuid, encoded);
        }
    }
}
