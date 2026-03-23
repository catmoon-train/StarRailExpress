package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：在死亡处理流程早期，用于查找"真正的击杀者"。
 * 首个返回非 null 结果的监听器决定最终的击杀者。
 *
 * <p>Event interface invoked early in the death pipeline to resolve the "true killer".
 * The first listener returning a non-null result determines the final killer.
 */
public interface EarlyKillPlayer {

    /**
     * 用于查找真正击杀者的事件。
     * 首个返回非 null 玩家的监听器即为最终结果。
     *
     * <p>Event used to find the true killer of a player.
     * The first listener returning a non-null player wins.
     */
    Event<EarlyKillPlayer> FIND_KILLER_EVENT = createArrayBacked(EarlyKillPlayer.class,
            listeners -> (victim, killer, reason) -> {
                Player result = null;
                for (EarlyKillPlayer listener : listeners) {
                    result = listener.findTrueKiller(victim, killer, reason);
                    if (result != null)
                        return result;
                }
                return null;
            });

    /**
     * 尝试找到"真正的击杀者"。
     *
     * <p>Attempts to resolve the true killer for the given victim.
     *
     * @param victim 死亡的玩家 / the player who died
     * @param killer 原始击杀者（可能为 null 或不准确） / the original killer (may be null or inaccurate)
     * @param reason 死亡原因的资源定位符 / resource location identifying the death reason
     * @return 真正的击杀者，若无法确定则返回 null / the true killer, or null if undetermined
     */
    Player findTrueKiller(Player victim, Player killer, ResourceLocation reason);
}