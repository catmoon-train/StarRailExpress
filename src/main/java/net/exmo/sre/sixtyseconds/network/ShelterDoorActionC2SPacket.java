package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDoorMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：统一门菜单里选中的操作（action = {@code SixtySecondsDoorMenu.ACTION_*}）。 */
public record ShelterDoorActionC2SPacket(BlockPos pos, int action) implements CustomPacketPayload {
    public static final Type<ShelterDoorActionC2SPacket> ID = new Type<>(Noellesroles.id("shelter_door_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShelterDoorActionC2SPacket> CODEC =
            StreamCodec.ofMember(ShelterDoorActionC2SPacket::encode, ShelterDoorActionC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
    }

    public static ShelterDoorActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ShelterDoorActionC2SPacket(buf.readBlockPos(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(ShelterDoorActionC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsDoorMenu.handleAction(context.player(), payload.pos(), payload.action());
    }
}
