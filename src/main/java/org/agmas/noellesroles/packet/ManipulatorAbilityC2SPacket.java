package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 操纵师附身期间，请求以被操控目标的身份释放目标自身的技能。
 * 无需负载：服务端从操纵师组件中读取当前目标，冷却记在目标身上。
 */
public record ManipulatorAbilityC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator_ability");
    public static final CustomPacketPayload.Type<ManipulatorAbilityC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ManipulatorAbilityC2SPacket> CODEC =
            StreamCodec.unit(new ManipulatorAbilityC2SPacket());

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
