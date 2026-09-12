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

/**
 * 客户端点击锁定状态，由服务端 {@code ClickLockoutPayload} 同步。
 */
public final class ClickAntiCheatClient {
    private static long lockUntilLocalMs;

    private ClickAntiCheatClient() {
    }

    public static void applyLockout(int remainingMillis) {
        if (remainingMillis <= 0) {
            lockUntilLocalMs = 0L;
            return;
        }
        lockUntilLocalMs = System.currentTimeMillis() + remainingMillis;
    }

    public static boolean isLocked() {
        return lockUntilLocalMs > 0L && System.currentTimeMillis() < lockUntilLocalMs;
    }

    public static void clear() {
        lockUntilLocalMs = 0L;
    }
}
