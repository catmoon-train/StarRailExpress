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

package org.agmas.noellesroles.game.roles.innocence.builder;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端全局墙位置注册表
 * 用于弹射物碰撞检测等服务端逻辑
 */
public class BuilderWallPositions {

    /** 所有活跃的客户端墙方块位置（线程安全） */
    private static final Set<BlockPos> activePositions = ConcurrentHashMap.newKeySet();

    /**
     * 添加一堵墙的所有位置
     */
    public static void addWall(Set<BlockPos> positions) {
        activePositions.addAll(positions);
    }

    /**
     * 移除一堵墙的所有位置
     */
    public static void removeWall(Set<BlockPos> positions) {
        activePositions.removeAll(positions);
    }

    /**
     * 检查某个位置是否有建筑师墙
     */
    public static boolean isWallAt(BlockPos pos) {
        return activePositions.contains(pos);
    }

    /**
     * 获取所有活跃墙位置（只读）
     */
    public static Set<BlockPos> getActivePositions() {
        return Collections.unmodifiableSet(activePositions);
    }

    /**
     * 清除所有墙位置（游戏结束时调用）
     */
    public static void clearAll() {
        activePositions.clear();
    }
}
