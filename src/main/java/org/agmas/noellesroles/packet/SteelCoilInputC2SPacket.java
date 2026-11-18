package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 金属怪钢卷形态的移动输入包：客户端每 tick 上报前进输入位与当前视角朝向。
 *
 * <p>服务端据此驱动带惯性的滚动，因此不需要新增按键绑定（复用原有移动键）。
 * movementBits 位定义与操纵师一致：1=前进, 2=后退, 4=左(A), 8=右(D)。
 * 其中左/右用于决定钢卷往哪边转弯。
 */
public record SteelCoilInputC2SPacket(int movementBits, float yaw) implements CustomPacketPayload {

    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "steel_coil_input");
    public static final CustomPacketPayload.Type<SteelCoilInputC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SteelCoilInputC2SPacket> CODEC =
            StreamCodec.ofMember(SteelCoilInputC2SPacket::write, SteelCoilInputC2SPacket::read);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.movementBits);
        buf.writeFloat(this.yaw);
    }

    public static SteelCoilInputC2SPacket read(FriendlyByteBuf buf) {
        return new SteelCoilInputC2SPacket(buf.readVarInt(), buf.readFloat());
    }
}
