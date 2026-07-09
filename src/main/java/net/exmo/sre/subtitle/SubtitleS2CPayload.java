package net.exmo.sre.subtitle;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C 网络包：向客户端发送字幕报幕数据。
 * 
 * 用法：
 * 服务端构造 SubtitleS2CPayload 并通过 ServerPlayNetworking.send(player, payload) 发送。
 * 客户端接收后由 SubtitleHUD 渲染，实现类似 使命召唤(COD) 风格的文字报幕效果。
 *
 * @param mainText      主标题文字（Component，支持富文本/翻译键）
 * @param subText       副标题文字（可为空，显示在主标题下方）
 * @param durationTicks 总显示时长（tick，20tick=1秒），默认 100 (5秒)
 * @param color         文字颜色（ARGB int），0 表示使用默认白色
 * @param typewriter     是否启用打字机动效
 * @param screenPosition 屏幕位置：0 = CENTER（屏幕中央，COD风格），1 = TOP（屏幕顶部，兼容broadcast），2 = BOTTOM（屏幕底部）
 * @param showBackground 是否显示半透明背景底衬（默认 true）
 */
public record SubtitleS2CPayload(
        Component mainText,
        Component subText,
        int durationTicks,
        int color,
        boolean typewriter,
        int screenPosition,
        boolean showBackground
) implements CustomPacketPayload {

    /** 屏幕中央（COD 报幕风格） */
    public static final int POS_CENTER = 0;
    /** 屏幕顶部（兼容 broadcast 消息） */
    public static final int POS_TOP    = 1;
    /** 屏幕底部 */
    public static final int POS_BOTTOM = 2;

    public static final Type<SubtitleS2CPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "subtitle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubtitleS2CPayload> CODEC =
            new StreamCodec<RegistryFriendlyByteBuf, SubtitleS2CPayload>() {
                @Override
                public SubtitleS2CPayload decode(RegistryFriendlyByteBuf buf) {
                    return new SubtitleS2CPayload(
                            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SubtitleS2CPayload payload) {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.mainText());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.subText());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.durationTicks());
                    ByteBufCodecs.INT.encode(buf, payload.color());
                    ByteBufCodecs.BOOL.encode(buf, payload.typewriter());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.screenPosition());
                    ByteBufCodecs.BOOL.encode(buf, payload.showBackground());
                }
            };

    // 紧凑规范构造：对 subText / durationTicks 做防御性处理
    public SubtitleS2CPayload {
        if (subText == null) subText = Component.empty();
        durationTicks = Math.max(durationTicks, 20);
    }

    // ---- 便捷构造（默认显示背景） ----

    /** 默认白色、5秒、无打字机、屏幕中央 */
    public SubtitleS2CPayload(Component mainText) {
        this(mainText, Component.empty(), 100, 0xFFFFFFFF, false, POS_CENTER, true);
    }

    public SubtitleS2CPayload(Component mainText, Component subText) {
        this(mainText, subText, 100, 0xFFFFFFFF, false, POS_CENTER, true);
    }

    public SubtitleS2CPayload(Component mainText, int durationTicks) {
        this(mainText, Component.empty(), durationTicks, 0xFFFFFFFF, false, POS_CENTER, true);
    }

    public SubtitleS2CPayload(Component mainText, int durationTicks, int color) {
        this(mainText, Component.empty(), durationTicks, color, false, POS_CENTER, true);
    }

    public SubtitleS2CPayload(Component mainText, int durationTicks, int color, boolean typewriter) {
        this(mainText, Component.empty(), durationTicks, color, typewriter, POS_CENTER, true);
    }

    public SubtitleS2CPayload(Component mainText, int durationTicks, int color, boolean typewriter, int screenPosition) {
        this(mainText, Component.empty(), durationTicks, color, typewriter, screenPosition, true);
    }

    /** 带主/副标题的完整 6 参构造（showBackground 默认 true） */
    public SubtitleS2CPayload(Component mainText, Component subText, int durationTicks,
                              int color, boolean typewriter, int screenPosition) {
        this(mainText, subText, durationTicks, color, typewriter, screenPosition, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
