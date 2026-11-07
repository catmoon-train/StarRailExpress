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

package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 事件回放的自定义事件记录入口。
 * <p>
 * 统一走 {@code SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable(key, args))}，
 * 文案键全部为 {@code replay.event.*}（见 {@code assets/starrailexpress/lang/}）。
 * 回放系统不可用时静默忽略，绝不影响触发点本身的游戏逻辑。
 */
public final class ReplayEventLog {

    private ReplayEventLog() {
    }

    /** 记录一条自定义回放事件。 */
    public static void record(String key, Object... args) {
        try {
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable(key, args));
            }
        } catch (Throwable ignored) {
            // 回放不可用时不影响游戏逻辑
        }
    }

    /** 回放里统一使用的玩家名（带职业着色）。 */
    public static Component player(Player player) {
        return GameReplayUtils.getReplayPlayerDisplayText(player, true);
    }

    /** 纯玩家名，用于明确要求「xxx」而非职业着色名的文案。 */
    public static Component name(Player player) {
        return player == null ? Component.literal("?") : player.getName();
    }
}
