package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：打开商人购买界面（{@code NpcShopScreen}）。
 * {@code price}/{@code stock} 是<b>当日快照</b>（服务端 {@code SixtySecondsNpcShop.ensureDaily} 算好），
 * 每次购买后服务端重推本包刷新（S2C 开屏 → C2S 动作 → 改状态 → 重推 S2C）。
 * {@code tokens} 为玩家可用资金（余额 + 背包实体币）。
 */
public record OpenNpcShopS2CPacket(int entityId, String npcName, int tokens, List<Row> rows)
        implements CustomPacketPayload {

    /** 一行商品：物品 id / 单次购买给的数量 / 当日单价 / 当日剩余库存。 */
    public record Row(String itemId, int count, int price, int stock) {
    }

    public static final Type<OpenNpcShopS2CPacket> ID = new Type<>(Noellesroles.id("open_npc_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcShopS2CPacket> CODEC =
            StreamCodec.ofMember(OpenNpcShopS2CPacket::encode, OpenNpcShopS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(npcName);
        buf.writeVarInt(tokens);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeUtf(row.itemId);
            buf.writeVarInt(row.count);
            buf.writeVarInt(row.price);
            buf.writeVarInt(row.stock);
        }
    }

    public static OpenNpcShopS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String npcName = buf.readUtf();
        int tokens = buf.readVarInt();
        int count = buf.readVarInt();
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Row(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new OpenNpcShopS2CPacket(entityId, npcName, tokens, rows);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
