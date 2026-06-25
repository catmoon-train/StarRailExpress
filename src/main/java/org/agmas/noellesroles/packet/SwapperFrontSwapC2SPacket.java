package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 交换者 G 键瞬移交换技能网络包（客户端 -> 服务端）。
 *
 * <p>按下技能键时发送，服务端据此把交换者与其正前方目标交换位置。无需携带目标，
 * 由服务端按视线射线选定正前方玩家。
 */
public record SwapperFrontSwapC2SPacket() implements CustomPacketPayload {

    public static final Type<SwapperFrontSwapC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "swapper_front_swap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwapperFrontSwapC2SPacket> CODEC =
        StreamCodec.unit(new SwapperFrontSwapC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
