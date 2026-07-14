package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsStations;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：在指定合成站合成配方（服务端校验站点/科技/供电/材料）。 */
public record StationCraftC2SPacket(String recipeId, BlockPos pos) implements CustomPacketPayload {
    public static final Type<StationCraftC2SPacket> ID = new Type<>(Noellesroles.id("station_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationCraftC2SPacket> CODEC =
            StreamCodec.ofMember(StationCraftC2SPacket::encode, StationCraftC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(recipeId);
        buf.writeBlockPos(pos);
    }

    public static StationCraftC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new StationCraftC2SPacket(buf.readUtf(), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(StationCraftC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsStations.handleCraft(context.player(), payload.recipeId(), payload.pos());
    }
}
