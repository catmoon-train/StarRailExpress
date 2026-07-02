package org.agmas.noellesroles.game.roles.neutral.jester;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 小丑：靠近门框时获得无碰撞效果（不显示粒子）。
 */
public final class JesterHandler {

    private static final double DOOR_CHECK_RANGE = 1.5;

    public static void handler(ServerPlayer player, SREGameWorldComponent cca) {
        Level level = player.level();
        if (player.hasEffect(ModEffects.SAFE_TIME))
            return;
        if (level.getGameTime() % 20 == 5) { // 1s检测一次，减少卡顿
            boolean nearDoor = player.getTags().contains("nearDoor") && player.hasEffect(ModEffects.NO_COLLIDE);
            BlockPos playerPos = player.blockPosition();
            boolean foundDoor = false;

            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos checkPos = playerPos.offset(dx, dy, dz);
                        double dist = Math.sqrt(
                                (checkPos.getX() + 0.5 - player.getX()) * (checkPos.getX() + 0.5 - player.getX())
                                        + (checkPos.getY() + 0.5 - player.getY())
                                                * (checkPos.getY() + 0.5 - player.getY())
                                        + (checkPos.getZ() + 0.5 - player.getZ())
                                                * (checkPos.getZ() + 0.5 - player.getZ()));
                        if (dist <= DOOR_CHECK_RANGE) {
                            if (level.getBlockState(checkPos).getBlock() instanceof SmallDoorBlock) {
                                foundDoor = true;
                                break;
                            }
                        }
                    }
                    if (foundDoor)
                        break;
                }
                if (foundDoor)
                    break;
            }

            if (foundDoor != nearDoor) {
                foundDoor = nearDoor;
                if (nearDoor) {
                    // 靠近门框：添加无碰撞效果，不显示粒子
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            ModEffects.NO_COLLIDE, 20 * 3, 0, true, false, false));
                    player.addTag("nearDoor");
                } else {
                    // 远离门框：移除无碰撞效果
                    player.removeEffect(ModEffects.NO_COLLIDE);
                    player.removeTag("nearDoor");
                }
            }
        }
    }
}
