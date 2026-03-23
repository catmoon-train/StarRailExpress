package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当玩家护盾被击破时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when a player's shield is broken.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnShieldBroken {

    /**
     * 玩家护盾被击破时触发的事件。
     * 参数：受害者（被击破护盾的玩家）、击杀者（击破护盾的玩家）。
     *
     * <p>Event fired when a player's shield is broken.
     * Parameters: victim (the player whose shield was broken), killer (the player who broke it).
     */
    Event<OnShieldBroken> EVENT = createArrayBacked(OnShieldBroken.class,
            listeners -> (a, b) -> {
                for (OnShieldBroken listener : listeners) {
                    listener.onShieldBroken(a, b);
                }
            });

    /**
     * 护盾被击破时的回调方法。
     *
     * <p>Callback invoked when a player's shield is broken.
     *
     * @param victim 护盾被击破的玩家 / the player whose shield was broken
     * @param killer 击破护盾的玩家 / the player who broke the shield
     */
    void onShieldBroken(Player victim, Player killer);
}
