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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

public class BlockTypeChecker {
    
    public static boolean isSeatBlock(Block block) {
        if (block instanceof MountableBlock)
            return true;
        // 兼容 Handcrafted 的座椅
        try {
            Class<?> seatClass = Class.forName("earth.terrarium.handcrafted.common.blocks.base.SittableBlock");
            if (seatClass.isInstance(block))
                return true;
        } catch (ClassNotFoundException ignored) {
            // Handcrafted 未安装，忽略
        }
        return false;
    }
    public static boolean isSeatEntity(Entity entity) {
        if (entity instanceof SeatEntity)
            return true;
        // 兼容 Handcrafted 的座椅
        try {
            Class<?> seatClass = Class.forName("earth.terrarium.handcrafted.common.entities.Seat");
            if (seatClass.isInstance(entity))
                return true;
        } catch (ClassNotFoundException ignored) {
            // Handcrafted 未安装，忽略
        }
        return false;
    }

    public static boolean isLightBlock(Block block) {
        return block instanceof LightBlockInterface;
    }
}
