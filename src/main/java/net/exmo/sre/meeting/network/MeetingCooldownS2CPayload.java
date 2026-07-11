package net.exmo.sre.meeting.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * S2C：会议冷却同步。散会（冷却开始）/ 游戏结束（冷却清零）/ 玩家中途加入时下发，
 * 供瞄准尸体的 HUD 提示显示剩余冷却（开局冷却由客户端用地图配置本地计算）。
 *
 * @param cooldownEndGameTime 会议间冷却结束的世界 gameTime，0 表示无冷却
 * @param reportedBodies      已上报过的尸体 UUID（同一具尸体不能重复召开会议）
 */
public record MeetingCooldownS2CPayload(long cooldownEndGameTime,
        List<UUID> reportedBodies) implements CustomPacketPayload {

    public static final Type<MeetingCooldownS2CPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_cooldown"));

    public static final StreamCodec<FriendlyByteBuf, MeetingCooldownS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, MeetingCooldownS2CPayload::cooldownEndGameTime,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), MeetingCooldownS2CPayload::reportedBodies,
            MeetingCooldownS2CPayload::new);

    @Override
    public Type<MeetingCooldownS2CPayload> type() {
        return ID;
    }
}
