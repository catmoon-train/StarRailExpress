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

import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：计算击杀者击杀玩家后应获得的金币奖励总量。
 * 所有监听器的返回值将被累加作为最终奖励金额。
 *
 * <p>
 * Event interface that computes the total balance (currency) to award to the
 * killer
 * when a player is killed. All listener return values are summed to produce the
 * final amount.
 */
public interface ShouldGiveKillerBalance {

    /**
     * 计算击杀者金币奖励的事件。
     * 游戏当前定义的死亡类型名称有：
     * 'fell_out_of_train'、'poison'、'grenade'、'bat_hit'、'gun_shot'、'knife_stab'。
     * 其他未显式定义的死亡类型默认为 'generic'。
     *
     * <p>
     * Event callback to calculate the balance reward for the killer.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     *
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<ShouldGiveKillerBalance> EVENT = createArrayBacked(ShouldGiveKillerBalance.class,
            listeners -> (victim, killer, deathReason) -> {
                for (ShouldGiveKillerBalance listener : listeners) {
                    var result = listener.shouldGiveKillerBalance(victim, killer, deathReason);
                    if (result != null && result != TrueFalseResult.PASS) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 计算击杀者应获得的金币奖励数量。
     *
     * <p>
     * Calculates the balance amount to award to the killer.
     *
     * @param victim      被击杀的玩家 / the player who was killed
     * @param killer      击杀者 / the player who performed the kill
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death
     *                    reason
     * @return 应给予击杀者的金币数量（将与其他监听器结果累加）/
     *         the balance amount to award (summed with other listeners' results)
     */
    TrueFalseResult shouldGiveKillerBalance(Player victim, Player killer, ResourceLocation deathReason);
}