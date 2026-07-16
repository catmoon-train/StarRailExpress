package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：打开商店货架编辑器（{@code NpcShopEditScreen}，创造/OP 专用）。
 * 下发整张表 + 该商人当前所用的档案名（编辑器默认跳到这一页）。
 * 编辑结果经 {@link NpcShopSaveC2SPacket} 上传并由服务端<b>显式落盘</b>——
 * 照抄 loot 表那套，因为 sync 只同步不持久化。
 */
public record OpenNpcShopEditS2CPacket(int entityId, String profile, SixtySecondsShopTable table)
        implements CustomPacketPayload {
    public static final Type<OpenNpcShopEditS2CPacket> ID = new Type<>(Noellesroles.id("open_npc_shop_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcShopEditS2CPacket> CODEC =
            StreamCodec.ofMember(OpenNpcShopEditS2CPacket::encode, OpenNpcShopEditS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(profile);
        table.writeTo(buf);
    }

    public static OpenNpcShopEditS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String profile = buf.readUtf();
        return new OpenNpcShopEditS2CPacket(entityId, profile, SixtySecondsShopTable.readFrom(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
