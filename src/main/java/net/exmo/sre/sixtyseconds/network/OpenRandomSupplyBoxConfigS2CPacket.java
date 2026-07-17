package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端→客户端：打开随机物资箱配置 GUI。
 * 携带箱子坐标、等级（low/high）、全部可用类别列表、当前已启用的类别集合。
 */
public record OpenRandomSupplyBoxConfigS2CPacket(
        BlockPos pos,
        String tier,
        List<String> allCategories,
        Set<String> enabledCategories) implements CustomPacketPayload {

    public static final Type<OpenRandomSupplyBoxConfigS2CPacket> ID =
            new Type<>(Noellesroles.id("open_random_supply_box_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRandomSupplyBoxConfigS2CPacket> CODEC =
            StreamCodec.ofMember(OpenRandomSupplyBoxConfigS2CPacket::encode,
                    OpenRandomSupplyBoxConfigS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(tier);
        buf.writeVarInt(allCategories.size());
        for (String cat : allCategories) {
            buf.writeUtf(cat);
        }
        buf.writeVarInt(enabledCategories.size());
        for (String cat : enabledCategories) {
            buf.writeUtf(cat);
        }
    }

    public static OpenRandomSupplyBoxConfigS2CPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String tier = buf.readUtf();
        int allSize = buf.readVarInt();
        List<String> allCategories = new ArrayList<>();
        for (int i = 0; i < allSize; i++) {
            allCategories.add(buf.readUtf());
        }
        int enabledSize = buf.readVarInt();
        Set<String> enabledCategories = new LinkedHashSet<>();
        for (int i = 0; i < enabledSize; i++) {
            enabledCategories.add(buf.readUtf());
        }
        return new OpenRandomSupplyBoxConfigS2CPacket(pos, tier, allCategories, enabledCategories);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
