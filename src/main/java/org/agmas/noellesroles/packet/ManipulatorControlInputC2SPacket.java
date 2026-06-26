package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 操纵师附身移动输入包。
 * 操纵师按下移动/跳跃键时，客户端每 tick 发送当前输入位掩码与朝向，
 * 服务端据此驱动被操控目标移动（目标自身处于 MOVE_BANED，不会与之冲突）。
 *
 * <p>{@code stop=true} 表示请求立即结束操控（例如打开背包时）。
 *
 * <p>movementBits 位定义：
 * 1=前进, 2=后退, 4=左, 8=右, 16=跳跃, 32=潜行/疾跑, 64=使用/交互（开门等）。
 */
public record ManipulatorControlInputC2SPacket(int movementBits, float yaw, float pitch, boolean stop)
        implements CustomPacketPayload {

    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;
    public static final int BIT_JUMP = 16;
    public static final int BIT_SPRINT = 32;
    public static final int BIT_USE = 64;

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator_control_input");
    public static final CustomPacketPayload.Type<ManipulatorControlInputC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ManipulatorControlInputC2SPacket> CODEC =
            StreamCodec.ofMember(ManipulatorControlInputC2SPacket::write, ManipulatorControlInputC2SPacket::read);

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.movementBits);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
        buf.writeBoolean(this.stop);
    }

    public static ManipulatorControlInputC2SPacket read(FriendlyByteBuf buf) {
        return new ManipulatorControlInputC2SPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }
}
