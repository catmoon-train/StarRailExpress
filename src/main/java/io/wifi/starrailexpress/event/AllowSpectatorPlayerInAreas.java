package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：是否允许旁观玩家在指定区域停留。
 */
public interface AllowSpectatorPlayerInAreas {

    /**
     * 事件接口：是否允许旁观玩家在指定区域停留。
     */
    Event<AllowSpectatorPlayerInAreas> EVENT = createArrayBacked(AllowSpectatorPlayerInAreas.class, listeners -> (player) -> {
        for (AllowSpectatorPlayerInAreas listener : listeners) {
            if (listener.allowInAreas(player)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断玩家是否被允许因指定原因死亡（无击杀者）。
     *
     * <p>
     * Determines whether the given player is allowed to die from the specified
     * death reason (no-killer variant).
     *
     * @param player      将要死亡的玩家 / the player about to die
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death
     *                    reason
     * @return {@code true} 若允许死亡 / {@code true} if the death is allowed
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean allowInAreas(Player player);
}