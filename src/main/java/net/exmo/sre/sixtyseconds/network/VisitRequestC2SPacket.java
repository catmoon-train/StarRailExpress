package net.exmo.sre.sixtyseconds.network;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsVisitSystem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：发起拜访请求（目标队 + 类型 交易/进入避难所）。 */
public record VisitRequestC2SPacket(int targetTeamId, int requestType) implements CustomPacketPayload {
    public static final Type<VisitRequestC2SPacket> ID = new Type<>(Noellesroles.id("visit_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisitRequestC2SPacket> CODEC =
            StreamCodec.ofMember(VisitRequestC2SPacket::encode, VisitRequestC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(targetTeamId);
        buf.writeVarInt(requestType);
    }

    public static VisitRequestC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new VisitRequestC2SPacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(VisitRequestC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!SixtySecondsMod.isActive(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        SixtySecondsVisitSystem.request(player, payload.targetTeamId(), payload.requestType());
    }
}
