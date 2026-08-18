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

package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket;

import io.wifi.starrailexpress.client.SREClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side state for Embalmer masquerade: skin swaps and voice pitches. */
public class ClientEmbalmerState {
    private static final Map<UUID, UUID> swaps = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> pitches = new ConcurrentHashMap<>();
    private static long expiresAt = 0;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EmbalmerSkinSwapS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (payload.durationTicks() <= 0 || payload.swaps().isEmpty()) {
                        clear();
                        return;
                    }
                    swaps.clear();
                    swaps.putAll(payload.swaps());
                    pitches.clear();
                    pitches.putAll(payload.pitches());
                    var client = Minecraft.getInstance();
                    expiresAt = client.level != null ? SREClient.getTicksFromGameStart() + payload.durationTicks() : 0;
                }));
    }

    public static UUID replacement(UUID id) {
        if (!isActive() || id == null)
            return null;
        return swaps.get(id);
    }

    public static float pitch(UUID id) {
        if (!isActive() || id == null)
            return 1.0F;
        return pitches.getOrDefault(id, 1.0F);
    }

    public static boolean isActive() {
        var client = Minecraft.getInstance();
        if (client == null || client.level == null || swaps.isEmpty()) {
            clear();
            return false;
        }
        if (SREClient.getTicksFromGameStart() >= expiresAt) {
            clear();
            return false;
        }
        return true;
    }

    public static void clear() {
        swaps.clear();
        pitches.clear();
        expiresAt = 0;
    }
}
