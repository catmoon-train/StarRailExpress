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
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.util.TrueFalseResult;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当触发GameUtils.killPlayer触发。
 * 所有监听器均会被调用（非拦截型事件）。
 * 请尽量不要使用此API，除非此玩家在大部分情况下不应当被杀死。
 */
public interface OnKillPlayerTriggered {

    /**
     * 当触发GameUtils.killPlayer触发。<br/>
     * <li>返回{@link TrueFalseResult#PASS}跳过逻辑（应当默认返回此）</li>
     * <li>返回{@link TrueFalseResult#TRUE}允许击杀</li>
     * <li>返回{@link TrueFalseResult#FALSE}不允许击杀</li>
     * 
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnKillPlayerTriggered> EVENT = createArrayBacked(OnKillPlayerTriggered.class,
            listeners -> (victim, spawnBody, killer, deathReasosn, forceKill) -> {
                for (OnKillPlayerTriggered listener : listeners) {
                    var a = listener.onKillPlayerTriggered(victim, spawnBody, killer, deathReasosn, forceKill);
                    if (a != null && !a.isPass())
                        return a;
                }
                return TrueFalseResult.PASS;
            });

    TrueFalseResult onKillPlayerTriggered(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason, boolean forceDeath);
}