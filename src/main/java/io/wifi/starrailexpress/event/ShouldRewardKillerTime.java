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

public interface ShouldRewardKillerTime {

    /**
     * 是否奖励击杀时间
     */
    Event<ShouldRewardKillerTime> EVENT = createArrayBacked(ShouldRewardKillerTime.class,
            listeners -> (victim, killer, deathReason, forceKill, spawnBody) -> {
                for (ShouldRewardKillerTime listener : listeners) {
                    var result = listener.shouldRewardKillerTime(victim, killer, deathReason, forceKill, spawnBody);
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
    TrueFalseResult shouldRewardKillerTime(Player victim, Player killer, ResourceLocation deathReason,
            boolean forceKill, boolean spawnBody);
}