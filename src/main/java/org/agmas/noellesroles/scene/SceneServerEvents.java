package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.event.OnRevolverUsed;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block.scene.TrainTargetBlock;

/**
 * 场景方块相关的服务端事件钩子。
 */
public final class SceneServerEvents {
    private SceneServerEvents() {
    }

    private static final double GUN_RANGE = 40.0;

    public static void register() {
        // 场景任务的定时检测（炉灶停留 / 祷告注视 / 独处）
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(
                SceneTaskManager::tick);

        // 手枪射线命中列车标靶 → 发出红石信号
        OnRevolverUsed.EVENT.register((ServerPlayer shooter, ServerPlayer target) -> {
            if (shooter == null) {
                return;
            }
            ServerLevel level = shooter.serverLevel();
            Vec3 eye = shooter.getEyePosition();
            Vec3 end = eye.add(shooter.getLookAngle().scale(GUN_RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, shooter));
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                if (level.getBlockState(pos).getBlock() instanceof TrainTargetBlock) {
                    TrainTargetBlock.onHit(level, pos);
                }
            }
        });
    }
}
