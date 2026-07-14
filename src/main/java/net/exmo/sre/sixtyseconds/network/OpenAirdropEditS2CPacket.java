package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开空投物资自定义编辑 GUI。 */
public record OpenAirdropEditS2CPacket(SixtySecondsLootTable table) implements CustomPacketPayload {
    public static final Type<OpenAirdropEditS2CPacket> ID =
            new Type<>(Noellesroles.id("open_airdrop_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAirdropEditS2CPacket> CODEC =
            StreamCodec.ofMember(OpenAirdropEditS2CPacket::encode, OpenAirdropEditS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) { table.writeTo(buf); }

    public static OpenAirdropEditS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenAirdropEditS2CPacket(SixtySecondsLootTable.readFrom(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
