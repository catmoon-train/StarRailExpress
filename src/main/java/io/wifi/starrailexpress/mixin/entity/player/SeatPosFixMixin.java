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

package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntity.class)
public class SeatPosFixMixin {
    @ModifyArgs(method = "dismountVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dismountTo(DDD)V"))
    private void fixWheelchairDismount(Args args, Entity vehicle) {
        // 仅当车辆是轮椅且标记为“耐久耗尽”时介入
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            player.getCooldowns().addCooldown(TMMBlocks.ACACIA_BRANCH.asItem(), 10);
            if (vehicle instanceof SeatEntity) {
                var lastPos = MountableBlock.lastPos.get(player.getUUID());
                if (lastPos != null) {
                    if (lastPos.distanceTo(player.position()) < 5) {
                        int lx = (int) lastPos.x();
                        int ly = (int) lastPos.y();
                        int lz = (int) lastPos.z();
                        if (player.level().getBlockState(new BlockPos(lx, ly + 1, lz))
                                .getBlock() instanceof MountableBlock) {
                            args.set(0, lastPos.x);
                            args.set(1, lastPos.y + 2.25);
                            args.set(2, lastPos.z);
                        } else {
                            args.set(0, lastPos.x);
                            args.set(1, lastPos.y + 0.25);
                            args.set(2, lastPos.z);
                        }

                        // 下座椅添加cooldown
                    } else {
                        var vec = player.position();
                        args.set(0, vec.x);
                        args.set(1, vec.y + 0.25);
                        args.set(2, vec.z);
                    }

                    // 移除记录,防止连续坐椅子时累积高度
                    MountableBlock.lastPos.remove(player.getUUID());
                }
            }
        }
    }

}
