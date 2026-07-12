package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开 loot 表编辑 GUI（携带当前共享表全量数据）。 */
public record OpenLootTableEditS2CPacket(SixtySecondsLootTable table) implements CustomPacketPayload {
    public static final Type<OpenLootTableEditS2CPacket> ID =
            new Type<>(Noellesroles.id("open_loot_table_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLootTableEditS2CPacket> CODEC =
            StreamCodec.ofMember(OpenLootTableEditS2CPacket::encode, OpenLootTableEditS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        table.writeTo(buf);
    }

    public static OpenLootTableEditS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenLootTableEditS2CPacket(SixtySecondsLootTable.readFrom(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
