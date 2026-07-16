package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.Noellesroles;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端→客户端：合成台可用的「家里容器」库存快照（物品 → 数量）。
 * <p>
 * 客户端<b>读不到</b>箱子/木桶等容器的内容（原版只在玩家打开容器时才同步），
 * 所以合成 GUI 的「可合成/材料够不够」判定必须靠服务端下发的这份快照
 * （+ 客户端本地已知的自己背包）。发送时机：打开合成台、每次合成后。
 * 只包含该站配方用到的配料物品，包体很小。
 */
public record SixtySecondsStationStockS2CPacket(Map<Item, Integer> stock) implements CustomPacketPayload {
    public static final Type<SixtySecondsStationStockS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_station_stock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsStationStockS2CPacket> CODEC =
            StreamCodec.ofMember(SixtySecondsStationStockS2CPacket::encode,
                    SixtySecondsStationStockS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(stock.size());
        for (Map.Entry<Item, Integer> entry : stock.entrySet()) {
            buf.writeVarInt(BuiltInRegistries.ITEM.getId(entry.getKey()));
            buf.writeVarInt(entry.getValue());
        }
    }

    public static SixtySecondsStationStockS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<Item, Integer> stock = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Item item = BuiltInRegistries.ITEM.byId(buf.readVarInt());
            stock.put(item, buf.readVarInt());
        }
        return new SixtySecondsStationStockS2CPacket(stock);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
