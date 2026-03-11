package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.ArrayList;
import java.util.List;

public record LootPoolsInfoS2CPacket(List<LotteryManager.LotteryPool> pools) implements CustomPacketPayload{
    public static final ResourceLocation LOOT_RESULT_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_pools_info");
    public static final CustomPacketPayload.Type<LootPoolsInfoS2CPacket> ID = new Type<>(LOOT_RESULT_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootPoolsInfoS2CPacket> CODEC;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        // 写入卡池数量
        buf.writeInt(pools.size());
        // 写入卡池
        for(LotteryManager.LotteryPool pool : pools)
        {
            // 写入卡池基本信息
            buf.writeInt(pool.getPoolID());
            buf.writeUtf(pool.getName());
            buf.writeUtf(pool.getType());
            // 写入卡池品质-物品列表
            List<Pair<Double, List<String>>> qualityListGroupConfigs = pool.getQualityListGroupConfigs();
            buf.writeInt(qualityListGroupConfigs.size());
            for(Pair<Double, List<String>> qualityList : qualityListGroupConfigs)
            {
                // 写入品质概率
                buf.writeDouble(qualityList.first);
                // 写入该品质包含物品
                buf.writeInt(qualityList.second.size());
                for(String item : qualityList.second)
                    buf.writeUtf(item);
            }
        }
    }

    public static LootPoolsInfoS2CPacket read(FriendlyByteBuf buf) {
        int poolSize = buf.readInt();
        List<LotteryManager.LotteryPool> pools = new ArrayList<>();
        for (int i = 0; i < poolSize; ++i)
        {
            // 读取卡池基本信息
            int poolID = buf.readInt();
            String name = buf.readUtf();
            String type = buf.readUtf();
            // 读取品质-物品列表
            int qualityListGroupConfigsSize = buf.readInt();
            List<Pair<Double, List<String>>> qualityListGroupConfigs = new ArrayList<>();
            for (int j = 0; j < qualityListGroupConfigsSize; ++j)
            {
                // 读取品质概率
                Double qualityProbability = buf.readDouble();
                // 读取该品质包含物品
                int qualityListSize = buf.readInt();
                List<String> qualityList = new ArrayList<>();
                for (int k = 0; k < qualityListSize; ++k)
                    qualityList.add(buf.readUtf());
                qualityListGroupConfigs.add(new Pair<>(qualityProbability, qualityList));
            }
            pools.add(new LotteryManager.LotteryPool(name,poolID, type, qualityListGroupConfigs));
        }
        return new LootPoolsInfoS2CPacket(
            pools
        );
    }
    static {
        CODEC = StreamCodec.ofMember(LootPoolsInfoS2CPacket::write, LootPoolsInfoS2CPacket::read);
    }
}
