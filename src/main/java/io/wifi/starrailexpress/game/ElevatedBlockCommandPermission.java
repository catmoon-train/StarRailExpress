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

package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;

/**
 * 命令方块 / 实体交互方块执行指令的权限提升开关。
 *
 * <p>
 * 开启后两者执行指令时按权限 3 检查（命令方块与实体交互均走 mixin）；
 * 关闭时命令方块保持原版 2，实体交互方块沿用 {@link SREConfig} 里已有的权限配置。
 * 状态写入 {@link SREConfig#elevateBlockCommandPermission}，重启后仍生效。
 */
// 这玩意就该在 server.propertise 里改...
public final class ElevatedBlockCommandPermission {
    public static final int ELEVATED_LEVEL = 3;

    private ElevatedBlockCommandPermission() {
    }

    public static boolean isEnabled() {
        SREConfig config = SREConfig.instance();
        return config != null && config.elevateBlockCommandPermission;
    }

    public static void setEnabled(boolean value) {
        SREConfig config = SREConfig.instance();
        if (config == null) {
            return;
        }
        config.elevateBlockCommandPermission = value;
        SREConfig.HANDLER.save();
    }

    public static int resolve(int configured) {
        return isEnabled() ? ELEVATED_LEVEL : configured;
    }
}
