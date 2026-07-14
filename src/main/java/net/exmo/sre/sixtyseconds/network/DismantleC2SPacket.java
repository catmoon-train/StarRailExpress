package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDismantle;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：在拆解台拆解 1 件指定物品（服务端校验站点/距离/物品在包）。 */
public record DismantleC2SPacket(String itemId, BlockPos pos) implements CustomPacketPayload {
    public static final Type<DismantleC2SPacket> ID = new Type<>(Noellesroles.id("dismantle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DismantleC2SPacket> CODEC =
            StreamCodec.ofMember(DismantleC2SPacket::encode, DismantleC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeBlockPos(pos);
    }

    public static DismantleC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new DismantleC2SPacket(buf.readUtf(), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(DismantleC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsDismantle.handleDismantle(context.player(), payload.itemId(), payload.pos());
    }
}
