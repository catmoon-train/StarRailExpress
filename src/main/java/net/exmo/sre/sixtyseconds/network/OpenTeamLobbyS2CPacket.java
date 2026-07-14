package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：组队大厅快照。
 * {@code forceOpen=true} 表示命令主动打开页面；false 表示仅当页面已打开时原地刷新。
 */
public record OpenTeamLobbyS2CPacket(boolean forceOpen, int myPartyId, int[] partyIds, int[] partySizes,
        String[] memberLabels) implements CustomPacketPayload {
    public static final Type<OpenTeamLobbyS2CPacket> ID = new Type<>(Noellesroles.id("open_team_lobby"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTeamLobbyS2CPacket> CODEC =
            StreamCodec.ofMember(OpenTeamLobbyS2CPacket::encode, OpenTeamLobbyS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(forceOpen);
        buf.writeVarInt(myPartyId);
        buf.writeVarInt(partyIds.length);
        for (int i = 0; i < partyIds.length; i++) {
            buf.writeVarInt(partyIds[i]);
            buf.writeVarInt(partySizes[i]);
            buf.writeUtf(memberLabels[i]);
        }
    }

    public static OpenTeamLobbyS2CPacket decode(RegistryFriendlyByteBuf buf) {
        boolean forceOpen = buf.readBoolean();
        int myPartyId = buf.readVarInt();
        int n = buf.readVarInt();
        int[] ids = new int[n];
        int[] sizes = new int[n];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
            sizes[i] = buf.readVarInt();
            labels[i] = buf.readUtf();
        }
        return new OpenTeamLobbyS2CPacket(forceOpen, myPartyId, ids, sizes, labels);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
