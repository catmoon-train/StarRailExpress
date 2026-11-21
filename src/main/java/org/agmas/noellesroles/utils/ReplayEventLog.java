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
        } catch (Throwable t) {
            SRE.LOGGER.warn("Failed to record replay event: " + key, t);
        }
    }

    /**
     * 玩家占位符版：单个玩家的显示文本解析失败时回退成纯名字，保证事件本身不会丢。
     * 死亡/换职中途调用尤其需要这个兜底。
     */
    public static void recordPlayers(String key, Player... players) {
        Object[] args = new Object[players.length];
        for (int i = 0; i < players.length; i++) {
            try {
                args[i] = player(players[i]);
            } catch (Throwable t) {
                args[i] = name(players[i]);
            }
        }
        record(key, args);
    }

    /**
     * 回放里统一使用的玩家名（带职业着色）。
     *
     * <p>
     * 事件回放中所有「玩家」占位符都应当用这个：它会在名字后追加当前职业并着色，
     * 与时间线里其它事件保持一致的显示样式。只有确实需要裸名字时才用 {@link #name(Player)}。
     * </p>
     */
    public static Component player(Player player) {
        return GameReplayUtils.getReplayPlayerDisplayText(player, true);
    }

    /**
     * 纯玩家名（不带职业）。
     *
     * <p>
     * <b>不要</b>用它来填事件回放里的玩家占位符——那样只有裸名字，和 {@link #player(Player)}
     * 产出的「名字 (职业)」混在同一条回放里会明显不一致。当前仅保留作工具方法备用。
     * </p>
     */
    public static Component name(Player player) {
        return player == null ? Component.literal("?") : player.getName();
    }
}
