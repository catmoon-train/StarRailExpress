/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当玩家死亡时触发（无击杀者版本）。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when a player dies (no-killer variant).
 * All listeners are invoked (non-cancellable event).
 */
public interface OnPlayerDeath {

    /**
     * 玩家死亡时触发的事件（无击杀者）。
     * 游戏当前定义的死亡类型名称有：
     * 'fell_out_of_train'、'poison'、'grenade'、'bat_hit'、'gun_shot'、'knife_stab'。
     * 其他未显式定义的死亡类型默认为 'generic'。
     *
     * <p>Event callback invoked when a player dies (no-killer variant).
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     *
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnPlayerDeath> EVENT = createArrayBacked(OnPlayerDeath.class, listeners -> (player, deathReason) -> {
        for (OnPlayerDeath listener : listeners) {
            listener.onPlayerDeath(player, deathReason);
        }
        return;
    });

    /**
     * 玩家死亡时的回调方法（无击杀者）。
     *
     * <p>Callback invoked when a player dies (no-killer variant).
     *
     * @param player     死亡的玩家 / the player who died
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death reason
     */
    void onPlayerDeath(Player player, ResourceLocation deathReason);
}