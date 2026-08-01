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

import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 方块工具类
 * - 用于各种方块查询
 */
public class BlockUtils {
    /**
     * 邻居门查询函数
     * <p>
     *     - 获取当前门左右两个门
     * </p>
     * @param door 当前门实体（应该是下半门）
     * @param world 当前世界
     * @return left:左侧门，right:右侧门；如果没有则返回null；会随查询的门的朝向变化
     */
    public static Pair<DoorBlockEntity,DoorBlockEntity> getNeighbourDoor(DoorBlockEntity door, Level world){
        Pair<DoorBlockEntity, DoorBlockEntity> ansPair = new Pair<>(null, null);
        if(door == null)
            return ansPair;
        BlockPos curDoorEntityPos = door.getBlockPos();
        switch (door.getFacing()) {
            case NORTH:
                if (world.getBlockEntity(curDoorEntityPos.east()) instanceof DoorBlockEntity right)
                    ansPair.second = right;
                if (world.getBlockEntity(curDoorEntityPos.west()) instanceof DoorBlockEntity left)
                    ansPair.first = left;
                break;
            case SOUTH:
                if (world.getBlockEntity(curDoorEntityPos.east()) instanceof DoorBlockEntity left)
                    ansPair.first = left;
                if (world.getBlockEntity(curDoorEntityPos.west()) instanceof DoorBlockEntity right)
                    ansPair.second = right;
                break;
            case EAST:
                if (world.getBlockEntity(curDoorEntityPos.north()) instanceof DoorBlockEntity left)
                    ansPair.first = left;
                if (world.getBlockEntity(curDoorEntityPos.south()) instanceof DoorBlockEntity right)
                    ansPair.second = right;
                break;
            case WEST:
                if (world.getBlockEntity(curDoorEntityPos.north()) instanceof DoorBlockEntity right)
                    ansPair.second = right;
                if (world.getBlockEntity(curDoorEntityPos.south()) instanceof DoorBlockEntity left)
                    ansPair.first = left;
                break;
            default:
                break;
        }
        return ansPair;
    }
}
