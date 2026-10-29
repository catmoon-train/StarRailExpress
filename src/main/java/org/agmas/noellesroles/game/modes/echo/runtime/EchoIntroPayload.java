package org.agmas.noellesroles.game.modes.echo.runtime;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 开局运镜期间的解说文字时间轴；只含本地化键与 tick 区间，不含任何坐标或他人信息。
 *
 * <p>相机轨道本身走既有的 {@code AdvancedCameraPayload}，本包只负责「这一段时间屏幕显示什么」。
 * 客户端按 tick 自己推进，不参与任何权威判定。
 */
public record EchoIntroPayload(UUID match, int totalTicks, List<Cue> cues) implements CustomPacketPayload {
    public static final int MAX_CUES = 8;
    public static final int MAX_KEY = 64;

    /** 一条解说：{@code [startTick, endTick)} 内显示 title + body。 */
    public record Cue(String titleKey, String bodyKey, int startTick, int endTick) {
        public Cue {
            Objects.requireNonNull(titleKey); Objects.requireNonNull(bodyKey);
            if (titleKey.isBlank() || titleKey.length() > MAX_KEY || bodyKey.length() > MAX_KEY)
                throw new IllegalArgumentException("解说键缺失或超长");
            if (startTick < 0 || endTick <= startTick) throw new IllegalArgumentException("解说区间非法");
        }
    }

    public static final Type<EchoIntroPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/intro"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoIntroPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUUID(p.match()); buf.writeVarInt(p.totalTicks()); buf.writeVarInt(p.cues().size());
        for (var cue : p.cues()) {
            buf.writeUtf(cue.titleKey(), MAX_KEY); buf.writeUtf(cue.bodyKey(), MAX_KEY);
            buf.writeVarInt(cue.startTick()); buf.writeVarInt(cue.endTick());
        }
    }, buf -> {
        var match = buf.readUUID(); int total = buf.readVarInt(); int size = buf.readVarInt();
        if (total < 0 || total > 20_000 || size < 0 || size > MAX_CUES) throw new IllegalArgumentException("开局解说超限");
        var cues = new ArrayList<Cue>(size);
        for (int n = 0; n < size; n++)
            cues.add(new Cue(buf.readUtf(MAX_KEY), buf.readUtf(MAX_KEY), buf.readVarInt(), buf.readVarInt()));
        return new EchoIntroPayload(match, total, cues);
    });

    public EchoIntroPayload {
        Objects.requireNonNull(match); cues = List.copyOf(cues);
        if (totalTicks < 0 || totalTicks > 20_000 || cues.size() > MAX_CUES) throw new IllegalArgumentException("开局解说超限");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    /** 当前 tick 应显示的解说；没有则返回 null。 */
    public Cue cueAt(int tick) {
        for (var cue : cues) if (tick >= cue.startTick() && tick < cue.endTick()) return cue;
        return null;
    }
}
