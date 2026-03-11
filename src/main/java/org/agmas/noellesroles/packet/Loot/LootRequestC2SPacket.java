package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 抽奖请求包
 * <p>
 *     客户端向服务端发送抽奖请求
 *     服务端检验请求是否通过：
 *     通过则进行一次抽奖，返回抽奖结果给客户端，客户端利用此结果生成抽奖结果动画ui
 * </p>
 */
public record LootRequestC2SPacket(int poolID) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_REQUEST_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_request");
    public static final Type<LootRequestC2SPacket> ID = new CustomPacketPayload.Type<>(LOOT_REQUEST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootRequestC2SPacket> CODEC;
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        // 抽取的池子 ID
        buf.writeInt(this.poolID);
    }

    public static LootRequestC2SPacket read(FriendlyByteBuf buf) {
        return new LootRequestC2SPacket(buf.readInt());
    }
    static {
        CODEC = StreamCodec.ofMember(LootRequestC2SPacket::write, LootRequestC2SPacket::read);
    }
}
