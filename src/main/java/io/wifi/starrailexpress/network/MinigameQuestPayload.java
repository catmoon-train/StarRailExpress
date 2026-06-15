package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 小游戏任务点方块的网络通信
 */
public class MinigameQuestPayload {

    /** 服务端->客户端：打开配置界面（创造模式） */
    public record OpenConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenConfig> TYPE = new Type<>(SRE.id("minigame_quest_open_config"));
        public static final StreamCodec<FriendlyByteBuf, OpenConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenConfig::data,
                OpenConfig::new);

        @Override
        public Type<OpenConfig> type() { return TYPE; }
    }

    /** 服务端->客户端：打开小游戏界面（冒险模式） */
    public record OpenGame(BlockPos pos, String minigameId) implements CustomPacketPayload {
        public static final Type<OpenGame> TYPE = new Type<>(SRE.id("minigame_quest_open_game"));
        public static final StreamCodec<FriendlyByteBuf, OpenGame> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenGame::pos,
                ByteBufCodecs.STRING_UTF8, OpenGame::minigameId,
                OpenGame::new);

        @Override
        public Type<OpenGame> type() { return TYPE; }
    }

    /** 客户端->服务端：保存配置 */
    public record SaveConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SaveConfig> TYPE = new Type<>(SRE.id("minigame_quest_save_config"));
        public static final StreamCodec<FriendlyByteBuf, SaveConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SaveConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, SaveConfig::data,
                SaveConfig::new);

        @Override
        public Type<SaveConfig> type() { return TYPE; }
    }

    /** 客户端->服务端：小游戏完成通知（统一触发标识） */
    public record CompleteGame(BlockPos pos) implements CustomPacketPayload {
        public static final Type<CompleteGame> TYPE = new Type<>(SRE.id("minigame_quest_complete"));
        public static final StreamCodec<FriendlyByteBuf, CompleteGame> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, CompleteGame::pos,
                CompleteGame::new);

        @Override
        public Type<CompleteGame> type() { return TYPE; }
    }
}
