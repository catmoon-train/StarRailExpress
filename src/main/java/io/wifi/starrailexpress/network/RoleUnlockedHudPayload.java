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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client payload for role unlock HUD animation.
 */
public record RoleUnlockedHudPayload(
        int globalGamesPlayed,
        List<String> unlockedRoleIds
) implements CustomPacketPayload {

    public static final Type<RoleUnlockedHudPayload> ID =
            new Type<>(SRE.id("role_unlocked_hud"));

    public static final StreamCodec<FriendlyByteBuf, RoleUnlockedHudPayload> CODEC =
            CustomPacketPayload.codec(RoleUnlockedHudPayload::encode, RoleUnlockedHudPayload::decode);

    public static void encode(RoleUnlockedHudPayload payload, FriendlyByteBuf buf) {
        buf.writeInt(payload.globalGamesPlayed());
        buf.writeInt(payload.unlockedRoleIds().size());
        for (String id : payload.unlockedRoleIds()) {
            buf.writeUtf(id);
        }
    }

    public static RoleUnlockedHudPayload decode(FriendlyByteBuf buf) {
        int games = buf.readInt();
        int size = buf.readInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readUtf());
        }
        return new RoleUnlockedHudPayload(games, ids);
    }

    @Override
    public Type<RoleUnlockedHudPayload> type() {
        return ID;
    }
}
