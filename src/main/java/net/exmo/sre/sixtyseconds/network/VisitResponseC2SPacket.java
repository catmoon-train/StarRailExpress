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

import java.util.UUID;

/** 客户端→服务端：目标队成员对拜访请求的响应（同意/拒绝）。 */
public record VisitResponseC2SPacket(UUID visitor, boolean accept) implements CustomPacketPayload {
    public static final Type<VisitResponseC2SPacket> ID = new Type<>(Noellesroles.id("visit_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisitResponseC2SPacket> CODEC =
            StreamCodec.ofMember(VisitResponseC2SPacket::encode, VisitResponseC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(visitor);
        buf.writeBoolean(accept);
    }

    public static VisitResponseC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new VisitResponseC2SPacket(buf.readUUID(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(VisitResponseC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!SixtySecondsMod.isActive(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        SixtySecondsVisitSystem.respond(player, payload.visitor(), payload.accept());
    }
}
