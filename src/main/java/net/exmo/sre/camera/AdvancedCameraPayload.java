package net.exmo.sre.camera;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 网络包：控制客户端的高级相机导演。
 *
 * <p>当 {@link #clear()} 为 {@code true} 时，立即清除当前轨道并恢复视角；否则 {@link #json()} 为一条轨道的
 * JSON 字符串（见 {@link AdvancedCameraSequence} 的 schema），客户端解析后开始播放。
 *
 * @param clear 是否清除当前轨道
 * @param json  轨道 JSON（clear 为 true 时忽略）
 */
public record AdvancedCameraPayload(boolean clear, String json) implements CustomPacketPayload {

    public static final Type<AdvancedCameraPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "advanced_camera"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedCameraPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AdvancedCameraPayload::clear,
            ByteBufCodecs.STRING_UTF8, AdvancedCameraPayload::json,
            AdvancedCameraPayload::new);

    /** 构造一条播放轨道的包。 */
    public static AdvancedCameraPayload play(String json) {
        return new AdvancedCameraPayload(false, json);
    }

    /** 构造一条清除轨道的包。 */
    public static AdvancedCameraPayload clearPayload() {
        return new AdvancedCameraPayload(true, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
