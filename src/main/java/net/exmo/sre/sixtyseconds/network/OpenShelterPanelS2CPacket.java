package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：打开避难所控制面板（本队仪表盘快照）。
 * {@code memberFlags} 位：bit0=生病 bit1=倒地 bit2=变怪。
 */
public record OpenShelterPanelS2CPacket(int doorHp, int doorMaxHp, int doorLevel, boolean doorBroken,
        long powerRemainingTicks, int suppliesCount, List<String> techIds,
        List<String> memberNames, List<Integer> memberHealth, List<Integer> memberFlags)
        implements CustomPacketPayload {

    public static final Type<OpenShelterPanelS2CPacket> ID = new Type<>(Noellesroles.id("open_shelter_panel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShelterPanelS2CPacket> CODEC =
            StreamCodec.ofMember(OpenShelterPanelS2CPacket::encode, OpenShelterPanelS2CPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(doorHp);
        buf.writeVarInt(doorMaxHp);
        buf.writeVarInt(doorLevel);
        buf.writeBoolean(doorBroken);
        buf.writeVarLong(powerRemainingTicks);
        buf.writeVarInt(suppliesCount);
        buf.writeVarInt(techIds.size());
        for (String id : techIds) {
            buf.writeUtf(id);
        }
        buf.writeVarInt(memberNames.size());
        for (int i = 0; i < memberNames.size(); i++) {
            buf.writeUtf(memberNames.get(i));
            buf.writeVarInt(memberHealth.get(i));
            buf.writeVarInt(memberFlags.get(i));
        }
    }

    private static OpenShelterPanelS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int doorHp = buf.readVarInt();
        int doorMaxHp = buf.readVarInt();
        int doorLevel = buf.readVarInt();
        boolean doorBroken = buf.readBoolean();
        long power = buf.readVarLong();
        int supplies = buf.readVarInt();
        int techCount = buf.readVarInt();
        List<String> techIds = new ArrayList<>(techCount);
        for (int i = 0; i < techCount; i++) {
            techIds.add(buf.readUtf());
        }
        int members = buf.readVarInt();
        List<String> names = new ArrayList<>(members);
        List<Integer> health = new ArrayList<>(members);
        List<Integer> flags = new ArrayList<>(members);
        for (int i = 0; i < members; i++) {
            names.add(buf.readUtf());
            health.add(buf.readVarInt());
            flags.add(buf.readVarInt());
        }
        return new OpenShelterPanelS2CPacket(doorHp, doorMaxHp, doorLevel, doorBroken, power, supplies,
                techIds, names, health, flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
