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

package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.scene.TrashCanBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record TrashCanConfigC2SPacket(BlockPos pos, boolean whitelistEnabled, List<String> whitelist,
                                      boolean blacklistEnabled, List<String> blacklist) implements CustomPacketPayload {
    public static final Type<TrashCanConfigC2SPacket> TYPE = new Type<>(Noellesroles.id("trash_can_config"));
    public static final StreamCodec<FriendlyByteBuf, TrashCanConfigC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            TrashCanConfigC2SPacket::write, TrashCanConfigC2SPacket::new);

    private TrashCanConfigC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean(), readStrings(buf), buf.readBoolean(), readStrings(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(whitelistEnabled);
        writeStrings(buf, whitelist);
        buf.writeBoolean(blacklistEnabled);
        writeStrings(buf, blacklist);
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf(256));
        }
        return values;
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value, 256);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrashCanConfigC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.isCreative()) return;
        BlockEntity be = player.serverLevel().getBlockEntity(payload.pos());
        if (be instanceof TrashCanBlockEntity trashCan) {
            trashCan.setConfig(payload.whitelistEnabled(), payload.whitelist(), payload.blacklistEnabled(), payload.blacklist());
            var state = player.serverLevel().getBlockState(payload.pos());
            player.serverLevel().sendBlockUpdated(payload.pos(), state, state, Block.UPDATE_ALL);
        }
    }
}
