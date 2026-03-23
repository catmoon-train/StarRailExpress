package io.wifi.starrailexpress.event;


import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当一名玩家被另一名玩家击杀时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when one player kills another.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnPlayerKilledPlayer {

    /**
     * 死亡原因枚举，描述玩家被击杀的方式。
     *
     * <p>Enum representing the reason a player was killed.
     */
    public static enum DeathReason {
        /** 枪击 / Killed by a gun shot. */
        GUN_SHOOT,
        /** 刀刺 / Killed by a knife. */
        KNIFE,
        /** 未知原因 / Unknown cause of death. */
        UNKNOWN,
        /** 其他方式 / Other cause of death. */
        OTHER,
        /** 手榴弹 / Killed by a grenade. */
        GRENADE,
        /** 球棒 / Killed by a bat. */
        BAT,
        /** 中毒 / Killed by poison. */
        POISON,
        /** 箭矢 / Killed by an arrow. */
        ARROW,
        /** 三叉戟 / Killed by a trident. */
        TRIDENT
    }

    /**
     * 玩家被玩家击杀时触发的事件。
     *
     * <p>Event fired when a player is killed by another player.
     */
    Event<OnPlayerKilledPlayer> EVENT = createArrayBacked(OnPlayerKilledPlayer.class, listeners -> (victim, killer, reason) -> {
        for (OnPlayerKilledPlayer listener : listeners) {
            listener.playerKilled(victim, killer,reason);
        }
    });
    
    /**
     * 玩家被击杀时的回调方法。
     *
     * <p>Callback invoked when a player is killed by another player.
     *
     * @param victim 被击杀的玩家 / the player who was killed
     * @param killer 击杀者 / the player who performed the kill
     * @param reason 死亡原因 / the reason for the kill
     */
    void playerKilled(ServerPlayer victim, ServerPlayer killer, DeathReason reason);
}