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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import io.wifi.starrailexpress.util.TrueFalseResult;

/**
 * 事件接口：玩家摔落到地面时触发（服务端，且仅对存活、非创造 / 旁观的玩家生效）。
 * <p>
 * 拦截型事件：按注册顺序依次询问监听器，第一个返回非 {@link TrueFalseResult#PASS} 的结果即被采纳，
 * 不再询问后续监听器与原始判断。注意本事件沿用 {@code On*} 前缀（通知型事件的惯例），
 * 但返回值会改变结果，语义上更接近 {@code Allow*} / {@code Can*} 系列。
 *
 * <p>Event interface fired when a player lands on the ground (server side, survival players only).
 * This is an intercepting event: listeners are queried in registration order and the first result
 * other than {@link TrueFalseResult#PASS} is adopted, skipping the remaining listeners and the
 * original logic. Note that it keeps the notification-style {@code On*} prefix while its return
 * value decides the outcome, so it behaves like the {@code Allow*} / {@code Can*} events.
 */
public interface OnPlayerFallOnGround {

    /**
     * 玩家摔落到地面时触发的事件。
     * 任意监听器返回非 {@link TrueFalseResult#PASS} 即采纳该结果并终止询问。
     *
     * <p>Event fired when a player lands on the ground.
     * Any listener returning a value other than {@link TrueFalseResult#PASS} decides the outcome.
     */
    Event<OnPlayerFallOnGround> EVENT = createArrayBacked(OnPlayerFallOnGround.class,
            listeners -> (player, y, onGround, blockState, blockPos) -> {

                for (OnPlayerFallOnGround listener : listeners) {
                    var result = listener.onFallOnGround(player, y, onGround, blockState, blockPos);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 玩家摔落到地面的回调。
     *
     * <p>Callback invoked when a player lands on the ground.
     *
     * @param player     落地的玩家 / the player who landed
     * @param y          落地时的 y 坐标 / the y coordinate on landing
     * @param onGround   是否已经落地；当前唯一调用点位于落地分支内，恒为 {@code true}
     *                   / whether the player is on ground (always {@code true} at the current call site)
     * @param blockState 落地所踩方块的状态 / the block state landed on
     * @param blockPos   落地所踩方块的位置 / the position of the block landed on
     * @return 摔落裁决 / the fall verdict:
     *         <ul>
     *         <li><b>{@link TrueFalseResult#TRUE}</b> — 判定为摔死（{@code fall_damage} 死因）
     *         / lethal fall</li>
     *         <li><b>{@link TrueFalseResult#FALSE}</b> — 判定不摔死，并连同原版落地伤害一起取消
     *         / not lethal, vanilla landing damage is cancelled as well</li>
     *         <li><b>{@link TrueFalseResult#PASS}</b> — 不做判断，交给下一个监听器与原始逻辑
     *         / no verdict, defer to the next listener and the original logic</li>
     *         </ul>
     */
    TrueFalseResult onFallOnGround(ServerPlayer player, double y, boolean onGround, BlockState blockState,
            BlockPos blockPos);
}
