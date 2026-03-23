package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口（仅客户端）：回合开始时欢迎计时器触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Client-only event interface fired when the round-start welcome timer triggers.
 * All listeners are invoked (non-cancellable event).
 */
@Environment(EnvType.CLIENT)
public interface OnRoundStartWelcomeTimmer {

    /**
     * 回合开始欢迎计时器事件（仅客户端）。
     *
     * <p>Client-only event fired when the round-start welcome timer fires.
     */
    Event<OnRoundStartWelcomeTimmer> EVENT = createArrayBacked(OnRoundStartWelcomeTimmer.class,
            listeners -> (player, deathReason) -> {
                for (OnRoundStartWelcomeTimmer listener : listeners) {
                    listener.onWelcome(player, deathReason);
                }
                return;
            });

    /**
     * 回合开始欢迎计时器触发时的回调方法。
     *
     * <p>Callback invoked when the round-start welcome timer fires.
     *
     * @param player      本地玩家 / the local player
     * @param welcomeTime 欢迎计时器的剩余时间（tick） / the remaining welcome timer in ticks
     */
    void onWelcome(Player player, int welcomeTime);
}