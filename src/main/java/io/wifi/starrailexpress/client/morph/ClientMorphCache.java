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

package io.wifi.starrailexpress.client.morph;

import io.wifi.starrailexpress.morph.MorphAppearance;
import io.wifi.starrailexpress.network.MorphSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端变形外观缓存（uuid → 当前外观）。由 {@link MorphSyncPayload} 维护。
 */
@Environment(EnvType.CLIENT)
public final class ClientMorphCache {
    private static final Map<UUID, MorphAppearance> APPEARANCES = new ConcurrentHashMap<>();

    private ClientMorphCache() {
    }

    public static void applySync(MorphSyncPayload payload) {
        if (payload.fullSync()) {
            APPEARANCES.clear();
        }
        for (Map.Entry<UUID, MorphAppearance> entry : payload.entries().entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
    }

    public static MorphAppearance get(UUID uuid) {
        if (uuid == null) {
            return MorphAppearance.NONE;
        }
        MorphAppearance appearance = APPEARANCES.get(uuid);
        return appearance == null ? MorphAppearance.NONE : appearance;
    }

    public static void clear() {
        APPEARANCES.clear();
    }

    private static void apply(UUID uuid, MorphAppearance appearance) {
        if (uuid == null) {
            return;
        }
        if (appearance == null || appearance.isNone()) {
            APPEARANCES.remove(uuid);
        } else {
            APPEARANCES.put(uuid, appearance);
        }
    }
}
