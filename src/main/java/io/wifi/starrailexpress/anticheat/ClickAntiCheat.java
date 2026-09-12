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

package io.wifi.starrailexpress.anticheat;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.network.ClickLockoutPayload;
import io.wifi.starrailexpress.network.PacketTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点击频率反作弊：左右键合计 CPS 超过阈值后锁定左右键一段时间。
 * 对空左键只能从挥手包统计，因此检测挂在数据包层。
 */
public final class ClickAntiCheat {
    public static final int CPS_LIMIT = 24;
    public static final int LOCKOUT_MILLIS = 5_000;

    public enum ClickType {
        LEFT,
        RIGHT
    }

    private static final Map<UUID, PlayerClickState> STATES = new ConcurrentHashMap<>();

    private ClickAntiCheat() {
    }

    public static boolean isEnabled() {
        return SREConfig.instance().enableClickAc;
    }

    public static boolean isLocked(ServerPlayer player) {
        if (!isEnabled() || player == null) {
            return false;
        }
        PlayerClickState state = STATES.get(player.getUUID());
        return state != null && state.isLocked();
    }

    /**
     * @return {@code true} 时应取消本次左右键相关数据包
     */
    public static boolean onClick(ServerPlayer player, ClickType type) {
        if (!isEnabled() || player == null || player.isCreative() || player.isSpectator()) {
            return false;
        }
        PlayerClickState state = STATES.computeIfAbsent(player.getUUID(), unused -> new PlayerClickState());
        if (state.isLocked()) {
            return true;
        }

        long now = System.currentTimeMillis();
        long tick = player.serverLevel().getGameTime();
        if (!state.tryRecord(type, tick, now)) {
            return false;
        }
        if (state.clickTimes.size() > CPS_LIMIT) {
            state.lockUntilMs = now + LOCKOUT_MILLIS;
            state.clickTimes.clear();
            player.displayClientMessage(
                    Component.translatable("message.starrailexpress.click_ac.locked", LOCKOUT_MILLIS / 1000)
                            .withStyle(ChatFormatting.RED),
                    true);
            PacketTracker.sendToClient(player, new ClickLockoutPayload(LOCKOUT_MILLIS));
            return true;
        }
        return false;
    }

    public static void onPlayerDisconnect(UUID playerId) {
        STATES.remove(playerId);
    }

    private static final class PlayerClickState {
        private final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
        private long lockUntilMs;
        private long lastLeftTick = Long.MIN_VALUE;
        private long lastRightTick = Long.MIN_VALUE;

        private boolean isLocked() {
            return lockUntilMs > 0 && System.currentTimeMillis() < lockUntilMs;
        }

        /**
         * @return {@code true} 表示这是一次新的点击（同一 tick 同侧只计一次）
         */
        private boolean tryRecord(ClickType type, long tick, long nowMs) {
            if (type == ClickType.LEFT) {
                if (lastLeftTick == tick) {
                    return false;
                }
                lastLeftTick = tick;
            } else {
                if (lastRightTick == tick) {
                    return false;
                }
                lastRightTick = tick;
            }
            clickTimes.addLast(nowMs);
            while (!clickTimes.isEmpty() && nowMs - clickTimes.peekFirst() >= 1000L) {
                clickTimes.removeFirst();
            }
            return true;
        }
    }
}
