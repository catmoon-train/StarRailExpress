package io.wifi.starrailexpress.api.replay;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class ReplayEventTypes {

    public interface EventDetails {
        // 标记接口，所有事件详情类都应实现此接口
    }

    public enum EventType {
        GAME_START,
        GAME_END,
        PLAYER_JOIN,
        PLAYER_LEAVE,
        PLAYER_KILL,
        PLAYER_POISONED,
        TASK_COMPLETE,
        STORE_BUY,
        DOOR_LOCK,
        DOOR_UNLOCK,
        DOOR_OPEN,
        ARMOR_BREAK,
        DOOR_CLOSE,
        LOCKPICK_ATTEMPT,
        ITEM_USED,
        MOOD_CHANGE,
        PSYCHO_STATE_CHANGE,
        BLACKOUT_START,
        BLACKOUT_END,
        GRENADE_THROWN,
        CHANGE_ROLE,
        PLAYER_REVIVAL,
        /* 用于自定义事件 */ CUSTOM_EVENT
    }

    public record PlayerJoinLeaveDetails(UUID player, String scoreboardName)
            implements EventDetails {
    }

    public record PlayerKillDetails(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason)
            implements EventDetails {
    }

    public record PlayerRevivalDetails(UUID player, String Role) implements EventDetails {
    }

    public record ChangeRoleDetails(UUID player, String oldRole, String newRole) implements EventDetails {
    }

    public record PlayerPoisonedDetails(UUID poisonerUuid, UUID victimUuid) implements EventDetails {
    }

    // 任务完成事件详情
    public record TaskCompleteDetails(UUID playerUuid, ResourceLocation taskId) implements EventDetails {
    }

    // 商店购买事件详情
    public record StoreBuyDetails(UUID playerUuid, ResourceLocation itemId, int cost) implements EventDetails {
    }

    // 门操作事件详情（锁定、解锁、打开、关闭）
    public record DoorActionDetails(UUID playerUuid, BlockPos doorPos, boolean success) implements EventDetails {
    }

    // 撬锁尝试事件详情
    public record LockpickAttemptDetails(UUID playerUuid, BlockPos doorPos, boolean success) implements EventDetails {
    }

    // 物品使用事件详情
    public record ItemUsedDetails(UUID playerUuid, ResourceLocation itemId) implements EventDetails {
    }

    // 心情变化事件详情
    public record MoodChangeDetails(UUID playerUuid, int oldMood, int newMood) implements EventDetails {
    }

    // 精神病状态变化事件详情
    public record PsychoStateChangeDetails(UUID playerUuid, int oldState, int newState) implements EventDetails {
    }

    // 停电事件详情
    public record BlackoutEventDetails(long duration) implements EventDetails {
    }

    // 手榴弹投掷事件详情
    public record GrenadeThrownDetails(UUID playerUuid, BlockPos position) implements EventDetails {
    }

    // 手榴弹投掷事件详情
    public record ArmorBreakDetails(UUID playerUuid) implements EventDetails {
    }

    // 自定义事件详情，用于第三方模组
    public record CustomEventDetails(Component Message) implements EventDetails {
    }

    // Add more specific EventDetails classes for other event types
}