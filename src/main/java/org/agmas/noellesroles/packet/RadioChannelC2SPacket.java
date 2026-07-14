package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.RadioItem;
import org.agmas.noellesroles.init.ModItems;

/** 客户端→服务端：对讲机频道操作（{@code leave=true} 退出，否则接入 {@code channel}）。 */
public record RadioChannelC2SPacket(int channel, boolean leave) implements CustomPacketPayload {
    public static final Type<RadioChannelC2SPacket> ID = new Type<>(Noellesroles.id("radio_channel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioChannelC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeVarInt(packet.channel());
                buf.writeBoolean(packet.leave());
            },
            buf -> new RadioChannelC2SPacket(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RadioChannelC2SPacket payload, ServerPlayNetworking.Context context) {
        var player = context.player();
        if (payload.leave()) {
            RadioItem.leave(player);
            return;
        }
        // 必须持有对讲机才能接入
        boolean hasRadio = player.getInventory().hasAnyMatching(s -> s.is(ModItems.RADIO));
        if (!hasRadio) {
            RadioItem.leave(player);
            return;
        }
        RadioItem.joinChannel(player, payload.channel());
    }
}
