package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：在护盾激活后，判断（无击杀者时）玩家是否允许死亡。
 * 若任意监听器返回 {@code false}，则玩家不会死亡。
 *
 * <p>Event interface to determine whether a player is allowed to die (without a killer),
 * evaluated after the shield has been activated.
 * If any listener returns {@code false}, the player's death will be cancelled.
 */
public interface AfterShieldAllowPlayerDeath {

    /**
     * 在护盾激活后，判断玩家是否允许因指定原因死亡（无击杀者）的事件。
     * 游戏当前定义的死亡类型名称有：
     * 'fell_out_of_train'、'poison'、'grenade'、'bat_hit'、'gun_shot'、'knife_stab'。
     * 其他未显式定义的死亡类型默认为 'generic'。
     *
     * <p>Event callback to determine if a player is allowed to die for a specific death type,
     * after activating the shield (no killer variant).
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot', 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     *
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<AfterShieldAllowPlayerDeath> EVENT = createArrayBacked(AfterShieldAllowPlayerDeath.class, listeners -> (player, deathReason) -> {
        for (AfterShieldAllowPlayerDeath listener : listeners) {
            if (!listener.allowDeath(player, deathReason)) {
                return false;
            }
        }
        return true;
    });

    /**
     * 在护盾激活后，判断玩家是否允许因指定原因死亡（无击杀者）。
     *
     * <p>Determines whether the given player is allowed to die from the specified
     * death reason after the shield has been activated (no killer variant).
     *
     * @param player     将要死亡的玩家 / the player about to die
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death reason
     * @return {@code true} 若允许死亡 / {@code true} if the death is allowed
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean allowDeath(Player player, ResourceLocation deathReason);
}