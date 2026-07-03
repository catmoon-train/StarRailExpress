package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.index.SREBlocks;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public class SniperProjectileUtil {

    /**
     * 获取狙击枪的目标，忽略屏障方块和本模组的屏障镶板。
     * 使用与左轮手枪相同的 ProjectileUtil.getHitResultOnViewVector 进行射线检测，
     * 如果射线命中了屏障方块，则自动跳过穿透到后方。
     */
    public static HitResult getSniperHitResult(Entity shooter, Predicate<Entity> filter, double range) {
        // 像左轮一样使用原版射线检测
        HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnViewVector(shooter, filter, (float) range);

        // 如果命中屏障方块，尝试穿透
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            if (isBarrierBlock(shooter.level(), hitPos)) {
                // 从屏障后方继续射线
                Vec3 startVec = shooter.getEyePosition(1.0F);
                Vec3 lookVec = shooter.getViewVector(1.0F);
                // 从屏障方块后方重新开始检测
                Vec3 newStart = blockHit.getLocation().add(lookVec.scale(0.5));
                double remainingRange = range - startVec.distanceTo(newStart);
                if (remainingRange > 0) {
                    HitResult penetrativeHit = getBlockHitResultIgnoringBarriers(shooter.level(), newStart, newStart.add(lookVec.scale(remainingRange)));
                    if (penetrativeHit instanceof BlockHitResult) {
                        return penetrativeHit;
                    }
                }
            }
        }
        return hit;
    }

    /**
     * 检测方块碰撞，忽略屏障方块和本模组的屏障镶板
     */
    private static BlockHitResult getBlockHitResultIgnoringBarriers(Level level, Vec3 startVec, Vec3 endVec) {
        Vec3 direction = endVec.subtract(startVec);
        double distance = direction.length();
        direction = direction.normalize();

        double stepSize = 0.1;
        int steps = (int) (distance / stepSize);

        for (int i = 1; i <= steps; i++) {
            Vec3 testPos = startVec.add(direction.scale(i * stepSize));
            BlockPos blockPos = BlockPos.containing(testPos);

            if (!isBarrierBlock(level, blockPos)) {
                if (!level.getBlockState(blockPos).isAir() && level.getBlockState(blockPos).isSuffocating(level, blockPos)) {
                    Direction hitDirection = Direction.getNearest(direction.x, direction.y, direction.z);
                    return new BlockHitResult(testPos, hitDirection, blockPos, false);
                }
            }
        }

        BlockPos endBlock = BlockPos.containing(endVec);
        if (!isBarrierBlock(level, endBlock) && !level.getBlockState(endBlock).isAir()) {
            Direction hitDirection = Direction.getNearest(direction.x, direction.y, direction.z);
            return new BlockHitResult(endVec, hitDirection, endBlock, false);
        }

        return null;
    }

    /**
     * 检查方块是否是屏障方块
     */
    private static boolean isBarrierBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.BARRIER) ||
               level.getBlockState(pos).is(Blocks.LIGHT) ||
               level.getBlockState(pos).is(SREBlocks.TRAIN_LIGHT) ||
               level.getBlockState(pos).is(SREBlocks.REMOTE_REDSTONE) ||
               level.getBlockState(pos).is(TMMBlocks.BARRIER_PANEL) ||
               level.getBlockState(pos).is(TMMBlocks.LIGHT_BARRIER);
    }
}
