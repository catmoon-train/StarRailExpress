package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsBreakIn;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：撬棍/开锁器选定闯入目标队。服务端按主手物品重新校验等级/报警属性并消耗。 */
public record BreakInExecuteC2SPacket(int targetTeamId) implements CustomPacketPayload {
    public static final Type<BreakInExecuteC2SPacket> ID = new Type<>(Noellesroles.id("break_in_execute"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreakInExecuteC2SPacket> CODEC =
            StreamCodec.ofMember(BreakInExecuteC2SPacket::encode, BreakInExecuteC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(targetTeamId);
    }

    public static BreakInExecuteC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new BreakInExecuteC2SPacket(buf.readVarInt());
    }

    public static void handle(BreakInExecuteC2SPacket payload, ServerPlayNetworking.Context context) {
        net.minecraft.server.level.ServerPlayer player = context.player();
        if (!net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(player.level())
                || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        SixtySecondsBreakIn.execute(player, payload.targetTeamId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
