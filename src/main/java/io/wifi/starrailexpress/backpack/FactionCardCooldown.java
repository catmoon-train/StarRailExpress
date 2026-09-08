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

package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.network.chat.Component;

/**
 * 同种类阵营卡使用间隔（真实墙钟，跨对局）。间隔与开关来自 {@link SREConfig}。
 */
public final class FactionCardCooldown {
    private FactionCardCooldown() {
    }

    public static long durationMs(boolean enabled, int minutes) {
        if (!enabled || minutes <= 0) {
            return 0L;
        }
        return minutes * 60_000L;
    }

    public static long remainingMs(long lastUsedAt, long now, boolean enabled, int minutes) {
        long duration = durationMs(enabled, minutes);
        if (duration <= 0L || lastUsedAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, lastUsedAt + duration - now);
    }

    public static long remainingMs(BackpackState state, FactionCardType type, long now) {
        SREConfig config = SREConfig.instance();
        return remainingMs(state.lastUsedAt(type), now, config.enableFactionCardTypeCooldown,
                config.factionCardTypeCooldownMinutes);
    }

    public static long remainingMs(BackpackState state, FactionCardType type) {
        return remainingMs(state, type, System.currentTimeMillis());
    }

    public static boolean isOnCooldown(BackpackState state, FactionCardType type) {
        return remainingMs(state, type) > 0L;
    }

    /** 向上取整到秒，冷却中至少显示 1 秒。 */
    public static Remaining split(long remainingMs) {
        long totalSeconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        return new Remaining(totalSeconds / 60L, totalSeconds % 60L);
    }

    public static Component formatRemaining(long remainingMs) {
        Remaining remaining = split(remainingMs);
        if (remaining.minutes() > 0L) {
            return Component.translatable("sre.duration.minutes_seconds", remaining.minutes(), remaining.seconds());
        }
        return Component.translatable("sre.duration.seconds", remaining.seconds());
    }

    public record Remaining(long minutes, long seconds) {
    }
}
