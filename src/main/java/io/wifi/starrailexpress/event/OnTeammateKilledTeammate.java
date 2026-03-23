package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当队友误杀队友时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when a teammate kills a teammate.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnTeammateKilledTeammate {

    /**
     * 队友误杀队友时触发的事件。
     *
     * <p>Event fired when a player kills a teammate.
     */
    Event<OnTeammateKilledTeammate> EVENT = createArrayBacked(OnTeammateKilledTeammate.class,
            listeners -> (victim, killer, isInnocent, deathReason) -> {
                for (OnTeammateKilledTeammate listener : listeners) {
                    listener.playerKilled(victim, killer, isInnocent, deathReason);
                }
            });

    /**
     * 队友被队友击杀时的回调方法。
     *
     * <p>Callback invoked when a player is killed by a teammate.
     *
     * @param victim      被击杀的玩家 / the player who was killed
     * @param killer      击杀者（队友） / the player who performed the kill (a teammate)
     * @param isInnocent  击杀者是否属于无辜误杀 / whether the kill was an innocent mistake
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death reason
     */
    void playerKilled(ServerPlayer victim, ServerPlayer killer, boolean isInnocent, ResourceLocation deathReason);
}