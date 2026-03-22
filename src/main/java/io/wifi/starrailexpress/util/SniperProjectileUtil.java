package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

import java.util.function.Predicate;

public class SniperProjectileUtil {

    /**
     * 获取狙击枪的目标，忽略屏障方块和本模组的屏障镶板
     */
    public static HitResult getSniperHitResult(Entity shooter, Predicate<Entity> filter, double range) {
        Vec3 startVec = shooter.getEyePosition(1.0F);
        Vec3 lookVec = shooter.getViewVector(1.0F);
        Vec3 endVec = startVec.add(lookVec.scale(range));

        // 首先检测实体碰撞
        HitResult entityHit = getEntityHitResult(shooter, startVec, endVec, filter);
        if (entityHit != null) {
            // 检查射击路径上是否有非屏障方块阻挡
            if (!isPathBlockedByNonBarrier(shooter.level(), startVec, entityHit.getLocation())) {
                return entityHit;
            }
        }

        // 如果没有击中实体，或者路径被阻挡，检测方块碰撞（忽略屏障）
        BlockHitResult blockHit = getBlockHitResultIgnoringBarriers(shooter.level(), startVec, endVec);
        return blockHit != null ? blockHit : new BlockHitResult(endVec, null, BlockPos.containing(endVec), false);
    }

    /**
     * 获取实体碰撞结果
     */
    private static HitResult getEntityHitResult(Entity shooter, Vec3 startVec, Vec3 endVec, Predicate<Entity> filter) {
        Level level = shooter.level();
        AABB searchBox = new AABB(startVec, endVec).inflate(1.0);

        Entity closestEntity = null;
        Vec3 closestHitPos = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : level.getEntities(shooter, searchBox, filter)) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            Vec3 hitPos = entityBox.clip(startVec, endVec).orElse(null);
            if (hitPos != null) {
                double distance = startVec.distanceToSqr(hitPos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                    closestHitPos = hitPos;
                }
            }
        }

        if (closestEntity != null) {
            return new EntityHitResult(closestEntity, closestHitPos);
        }
        return null;
    }

    /**
     * 检测方块碰撞，忽略屏障方块和本模组的屏障镶板
     */
    private static BlockHitResult getBlockHitResultIgnoringBarriers(Level level, Vec3 startVec, Vec3 endVec) {
        // 使用分步射线检测，跳过屏障方块
        Vec3 direction = endVec.subtract(startVec);
        double distance = direction.length();
        direction = direction.normalize();

        // 步长：0.1个方块
        double stepSize = 0.1;
        int steps = (int) (distance / stepSize);

        for (int i = 0; i <= steps; i++) {
            Vec3 testPos = startVec.add(direction.scale(i * stepSize));
            BlockPos blockPos = BlockPos.containing(testPos);

            // 检查是否是屏障方块
            if (!isBarrierBlock(level, blockPos)) {
                // 检查方块是否有碰撞体积
                if (!level.getBlockState(blockPos).isAir() && level.getBlockState(blockPos).isSuffocating(level, blockPos)) {
                    // 计算大致的碰撞方向
                    Direction hitDirection = Direction.getNearest(direction.x, direction.y, direction.z);
                    return new BlockHitResult(testPos, hitDirection, blockPos, false);
                }
            }
        }

        // 最后检测终点
        BlockPos endBlock = BlockPos.containing(endVec);
        if (!isBarrierBlock(level, endBlock) && !level.getBlockState(endBlock).isAir()) {
            Direction hitDirection = Direction.getNearest(direction.x, direction.y, direction.z);
            return new BlockHitResult(endVec, hitDirection, endBlock, false);
        }

        return null;
    }

    /**
     * 检查路径上是否有非屏障方块阻挡
     */
    private static boolean isPathBlockedByNonBarrier(Level level, Vec3 startVec, Vec3 endVec) {
        Vec3 direction = endVec.subtract(startVec);
        double distance = direction.length();
        direction = direction.normalize();

        double stepSize = 0.1;
        int steps = (int) (distance / stepSize);

        for (int i = 0; i <= steps; i++) {
            Vec3 testPos = startVec.add(direction.scale(i * stepSize));
            BlockPos blockPos = BlockPos.containing(testPos);

            if (!isBarrierBlock(level, blockPos) && !level.getBlockState(blockPos).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查方块是否是屏障方块
     */
    private static boolean isBarrierBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.BARRIER) ||
               level.getBlockState(pos).is(TMMBlocks.BARRIER_PANEL) ||
               level.getBlockState(pos).is(TMMBlocks.LIGHT_BARRIER);
    }
}
