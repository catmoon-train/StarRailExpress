package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 神父天堂序列同步：相位、咏诵剩余、加速进度、屏幕时间与预留音效槽。
 *
 * <p>{@code soundIndex}：{@code -1} 无音效；{@code 0-14} 对应一句咏诵；
 * {@code 15} 咏诵循环；{@code 16} 转职标题；{@code 17} 加速标题；
 * {@code 18} 终章标题；{@code 100} 尾奏。
 */
public record PriestHeavenStateS2CPacket(
        int phase,
        int lyricIndex,
        int chantRemain,
        int accelElapsed,
        int visualTime,
        int soundIndex
) implements CustomPacketPayload {
    public static final int SOUND_NONE = -1;
    public static final int SOUND_CHANTING = 15;
    public static final int SOUND_TRANSFORM = 16;
    public static final int SOUND_ACCEL = 17;
    public static final int SOUND_FINALE = 18;
    public static final int SOUND_ENDING = 100;

    public static final ResourceLocation PAYLOAD_ID = Noellesroles.id("priest_heaven_state");
    public static final Type<PriestHeavenStateS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PriestHeavenStateS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeVarInt(packet.phase());
                buf.writeVarInt(packet.lyricIndex());
                buf.writeVarInt(packet.chantRemain());
                buf.writeVarInt(packet.accelElapsed());
                buf.writeVarInt(packet.visualTime());
                buf.writeVarInt(packet.soundIndex());
            },
            buf -> new PriestHeavenStateS2CPacket(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
