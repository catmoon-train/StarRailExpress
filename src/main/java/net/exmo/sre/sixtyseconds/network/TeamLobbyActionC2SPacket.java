package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamLobby;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：组队大厅操作（创建 / 加入 partyId / 离开 / 管理员解散 partyId）。 */
public record TeamLobbyActionC2SPacket(int action, int partyId) implements CustomPacketPayload {
    public static final Type<TeamLobbyActionC2SPacket> ID = new Type<>(Noellesroles.id("team_lobby_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamLobbyActionC2SPacket> CODEC =
            StreamCodec.ofMember(TeamLobbyActionC2SPacket::encode, TeamLobbyActionC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(partyId);
    }

    public static TeamLobbyActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new TeamLobbyActionC2SPacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(TeamLobbyActionC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsTeamLobby.handleAction(context.player(), payload.action(), payload.partyId());
    }
}
