package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：物资箱搜刮进度事件（搜打撤式搜刮动画）。
 * {@code state}：0=开始（duration 有效），1=中断，2=完成（{@code items} 为本次搜出的物资，
 * 供 HUD 逐件揭示动画展示）。
 */
public record SupplySearchS2CPacket(int state, BlockPos pos, int durationTicks, List<ItemStack> items)
        implements CustomPacketPayload {
    public static final int STATE_START = 0;
    public static final int STATE_CANCEL = 1;
    public static final int STATE_COMPLETE = 2;

    public SupplySearchS2CPacket(int state, BlockPos pos, int durationTicks) {
        this(state, pos, durationTicks, List.of());
    }

    public static final Type<SupplySearchS2CPacket> ID = new Type<>(Noellesroles.id("supply_search"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SupplySearchS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeVarInt(packet.state());
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.durationTicks());
                buf.writeVarInt(packet.items().size());
                for (ItemStack stack : packet.items()) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                }
            },
            buf -> {
                int state = buf.readVarInt();
                BlockPos pos = buf.readBlockPos();
                int duration = buf.readVarInt();
                int n = buf.readVarInt();
                List<ItemStack> items = new ArrayList<>(Math.min(n, 64));
                for (int i = 0; i < n; i++) {
                    items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                return new SupplySearchS2CPacket(state, pos, duration, items);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
