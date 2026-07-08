package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.TrainLightBlock;
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
        return block instanceof TrainLightBlock;
    }
}
