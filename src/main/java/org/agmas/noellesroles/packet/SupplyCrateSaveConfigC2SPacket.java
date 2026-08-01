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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.SupplyCrateBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 -> 服务端：保存物资箱配置
 */
public record SupplyCrateSaveConfigC2SPacket(
        BlockPos blockPos,
        List<SupplyCrateBlockEntity.SupplyCrateEntry> configItems,
        int refreshIntervalTicks,
        boolean refreshAll,
        boolean shared
) implements CustomPacketPayload {
    public static final ResourceLocation ID_LOC = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "supply_crate_save_config_c2s");
    public static final CustomPacketPayload.Type<SupplyCrateSaveConfigC2SPacket> ID =
            new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplyCrateSaveConfigC2SPacket> CODEC =
            StreamCodec.ofMember(SupplyCrateSaveConfigC2SPacket::encode, SupplyCrateSaveConfigC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeVarInt(configItems.size());
        for (var entry : configItems) {
            buf.writeUtf(entry.itemId());
            buf.writeVarInt(entry.count());
            buf.writeDouble(entry.probability());
        }
        buf.writeVarInt(refreshIntervalTicks);
        buf.writeBoolean(refreshAll);
        buf.writeBoolean(shared);
    }

    public static SupplyCrateSaveConfigC2SPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<SupplyCrateBlockEntity.SupplyCrateEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String itemId = buf.readUtf();
            int count = buf.readVarInt();
            double prob = buf.readDouble();
            entries.add(new SupplyCrateBlockEntity.SupplyCrateEntry(itemId, count, prob));
        }
        int interval = buf.readVarInt();
        boolean refreshAll = buf.readBoolean();
        boolean shared = buf.readBoolean();
        return new SupplyCrateSaveConfigC2SPacket(pos, entries, interval, refreshAll, shared);
    }

    /**
     * 在服务端处理配置保存
     */
    public static void handle(SupplyCrateSaveConfigC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.isCreative()) return; // 仅创造模式可配置

        BlockEntity be = player.level().getBlockEntity(packet.blockPos());
        if (be instanceof SupplyCrateBlockEntity crate) {
            crate.setConfigItems(packet.configItems());
            crate.setRefreshIntervalTicks(packet.refreshIntervalTicks());
            crate.setRefreshAllSimultaneously(packet.refreshAll());
            crate.setSharedSupplies(packet.shared());
        }
    }
}
