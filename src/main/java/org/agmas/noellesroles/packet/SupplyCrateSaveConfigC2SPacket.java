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
            boolean hasNbt = entry.nbt() != null && !entry.nbt().isBlank();
            buf.writeBoolean(hasNbt);
            if (hasNbt) {
                buf.writeUtf(entry.nbt());
            }
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
            String nbt = buf.readBoolean() ? buf.readUtf() : null;
            entries.add(new SupplyCrateBlockEntity.SupplyCrateEntry(itemId, count, prob, nbt));
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
