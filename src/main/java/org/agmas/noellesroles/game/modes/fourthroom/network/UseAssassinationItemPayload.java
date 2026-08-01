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

package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.agmas.noellesroles.game.modes.fourthroom.shop.FourthRoomShopItem;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record UseAssassinationItemPayload(String itemId, String targetId) implements CustomPacketPayload {
    public static final Type<UseAssassinationItemPayload> ID = new Type<>(SRE.id("fourth_room_use_item"));
    public static final StreamCodec<FriendlyByteBuf, UseAssassinationItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            UseAssassinationItemPayload::itemId,
            ByteBufCodecs.STRING_UTF8,
            UseAssassinationItemPayload::targetId,
            UseAssassinationItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<UseAssassinationItemPayload> {
        @Override
        public void receive(@NotNull UseAssassinationItemPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomShopItem item = FourthRoomShopItem.byId(payload.itemId());
            if (item == null || payload.targetId().isBlank()) {
                return;
            }
            FourthRoomGameManager.of(context.player().serverLevel())
                    .useAssassinationItem(context.player().getUUID(), UUID.fromString(payload.targetId()), item);
        }
    }
}