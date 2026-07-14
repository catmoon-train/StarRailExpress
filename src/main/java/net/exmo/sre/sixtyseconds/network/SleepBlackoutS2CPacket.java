package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：睡觉时间强制入眠黑屏演出。收到后客户端进入 {@code durationTicks} 的
 * 全屏黑幕（渐入渐出），期间随机显示幸存者独白 + 自身状态变化文字（见 SixtySecondsSleepOverlay）。
 */
public record SleepBlackoutS2CPacket(int durationTicks) implements CustomPacketPayload {
    public static final Type<SleepBlackoutS2CPacket> ID = new Type<>(Noellesroles.id("sleep_blackout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepBlackoutS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeVarInt(packet.durationTicks()),
            buf -> new SleepBlackoutS2CPacket(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
