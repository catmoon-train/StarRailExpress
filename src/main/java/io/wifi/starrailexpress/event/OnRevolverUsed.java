package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当玩家使用手枪射击时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when a player fires a revolver.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnRevolverUsed {

    /**
     * 玩家使用手枪射击时触发的事件。
     * 参数：射击玩家、被击中的目标（可为 null）。
     *
     * <p>Event fired when a player uses a revolver.
     * Parameters: the shooting player, the hit target (may be null).
     */
    Event<OnRevolverUsed> EVENT = createArrayBacked(OnRevolverUsed.class, listeners -> (player, target) -> {
        for (OnRevolverUsed listener : listeners) {
            listener.onPlayerShoot(player, target);
        }
        return;
    });

    /**
     * 玩家射击时的回调方法。
     *
     * <p>Callback invoked when a player shoots with a revolver.
     *
     * @param player 射击的玩家 / the player who fired
     * @param target 被击中的目标玩家，可能为 null / the target player who was hit, may be null
     */
    void onPlayerShoot(ServerPlayer player, @Nullable ServerPlayer target);
}