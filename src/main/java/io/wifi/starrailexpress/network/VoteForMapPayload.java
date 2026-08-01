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

import io.wifi.starrailexpress.game.voting.MapVotingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record VoteForMapPayload(String mapId) implements CustomPacketPayload {
    public static final Type<VoteForMapPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("starrailexpress", "vote_for_map"));

    public static final StreamCodec<FriendlyByteBuf, VoteForMapPayload> CODEC = CustomPacketPayload.codec(
            VoteForMapPayload::write, VoteForMapPayload::new
    );

    public VoteForMapPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(mapId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler {
        public static void handle(VoteForMapPayload payload, ServerPlayer player) {
            // 通过投票管理器处理投票
            MapVotingManager.getInstance().voteForMap(player.getUUID(), payload.mapId());
        }
    }
}