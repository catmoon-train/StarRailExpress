package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.api.replay.ReplayEventTypes.*;
import io.wifi.starrailexpress.api.replay.serializers.*;

public class ReplayApiInitializer {

    public static void init() {
        // 注册所有内置事件的序列化器和反序列化器
        ReplayEventRegistry.register(EventType.PLAYER_KILL, PlayerKillDetails.class, new PlayerKillDetailsSerializer(),
                new PlayerKillDetailsSerializer());
        ReplayEventRegistry.register(EventType.PLAYER_POISONED, PlayerPoisonedDetails.class,
                new PlayerPoisonedDetailsSerializer(), new PlayerPoisonedDetailsSerializer());
        ReplayEventRegistry.register(EventType.TASK_COMPLETE, TaskCompleteDetails.class,
                new TaskCompleteDetailsSerializer(), new TaskCompleteDetailsSerializer());
        ReplayEventRegistry.register(EventType.STORE_BUY, StoreBuyDetails.class, new StoreBuyDetailsSerializer(),
                new StoreBuyDetailsSerializer());
        ReplayEventRegistry.register(EventType.DOOR_LOCK, DoorActionDetails.class, new DoorActionDetailsSerializer(),
                new DoorActionDetailsSerializer());
        ReplayEventRegistry.register(EventType.DOOR_UNLOCK, DoorActionDetails.class, new DoorActionDetailsSerializer(),
                new DoorActionDetailsSerializer());
        ReplayEventRegistry.register(EventType.DOOR_OPEN, DoorActionDetails.class, new DoorActionDetailsSerializer(),
                new DoorActionDetailsSerializer());
        ReplayEventRegistry.register(EventType.DOOR_CLOSE, DoorActionDetails.class, new DoorActionDetailsSerializer(),
                new DoorActionDetailsSerializer());
        ReplayEventRegistry.register(EventType.LOCKPICK_ATTEMPT, LockpickAttemptDetails.class,
                new LockpickAttemptDetailsSerializer(), new LockpickAttemptDetailsSerializer());
        ReplayEventRegistry.register(EventType.ITEM_USED, ItemUsedDetails.class, new ItemUsedDetailsSerializer(),
                new ItemUsedDetailsSerializer());
        ReplayEventRegistry.register(EventType.MOOD_CHANGE, MoodChangeDetails.class, new MoodChangeDetailsSerializer(),
                new MoodChangeDetailsSerializer());
        ReplayEventRegistry.register(EventType.PSYCHO_STATE_CHANGE, PsychoStateChangeDetails.class,
                new PsychoStateChangeDetailsSerializer(), new PsychoStateChangeDetailsSerializer());
        ReplayEventRegistry.register(EventType.BLACKOUT_START, BlackoutEventDetails.class,
                new BlackoutEventDetailsSerializer(), new BlackoutEventDetailsSerializer());
        ReplayEventRegistry.register(EventType.BLACKOUT_END, BlackoutEventDetails.class,
                new BlackoutEventDetailsSerializer(), new BlackoutEventDetailsSerializer());
        ReplayEventRegistry.register(EventType.GRENADE_THROWN, GrenadeThrownDetails.class,
                new GrenadeThrownDetailsSerializer(), new GrenadeThrownDetailsSerializer());
        ReplayEventRegistry.register(EventType.CHANGE_ROLE, ChangeRoleDetails.class, new ChangeRoleDetailsSerializer(),
                new ChangeRoleDetailsSerializer());
                ReplayEventRegistry.register(EventType.ARMOR_BREAK, ArmorBreakDetails.class, new ArmorBreakDetailsSerializer(),
                new ArmorBreakDetailsSerializer());
        ReplayEventRegistry.register(EventType.PLAYER_REVIVAL, PlayerRevivalDetails.class,
                new PlayerRevivalDetailsSerializer(), new PlayerRevivalDetailsSerializer());

        // 注册自定义事件的默认序列化器和反序列化器
        // 注意：CUSTOM_EVENT 本身不直接注册，而是通过 registerCustomEvent 注册具体的自定义事件ID
        // ReplayEventRegistry.register(EventType.CUSTOM_EVENT,
        // CustomEventDetails.class, new CustomEventDetailsSerializer(), new
        // CustomEventDetailsSerializer());
    }
}