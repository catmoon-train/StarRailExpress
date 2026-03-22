package org.agmas.noellesroles.mixin.roles.engineer;

import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.item.CrowbarItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.entity.LockEntityManager;
import org.agmas.noellesroles.item.AlarmTrapItem;
import org.agmas.noellesroles.item.ReinforcementItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin：拦截撬棍使用，处理工程师的加固门和警报陷阱
 */
@Mixin(CrowbarItem.class)
public class EngineerCrowbarMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void engineer$interceptCrowbar(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        Player player = context.getPlayer();

        if (player == null)
            return;

        // 获取门的 BlockEntity
        BlockEntity entity = world.getBlockEntity(context.getClickedPos());
        if (!(entity instanceof DoorBlockEntity)) {
            entity = world.getBlockEntity(context.getClickedPos().below());
        }

        if (entity instanceof DoorBlockEntity door && !door.isBlasted()) {
            // 首先检查警报陷阱 - 无论是否有加固都会触发
            AlarmTrapItem.triggerAlarmTrap(door, world);

            // 然后检查加固
            if (ReinforcementItem.consumeReinforcement(door)) {
                // 加固被消耗，门不会被破坏
                if (!world.isClientSide) {
                    // 播放加固被破坏的声音
                    world.playSound(null, context.getClickedPos(),
                            SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.8f);

                    // 给使用撬棍的玩家发送消息
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.engineer.reinforcement_broken")
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                }

                // 仍然消耗冷却时间
                if (!player.isCreative()) {
                    player.getCooldowns().addCooldown(context.getItemInHand().getItem(), 1200);
                }

                // 取消原版行为（门不会被破坏）
                cir.setReturnValue(InteractionResult.SUCCESS);
            } else {
                if (!world.isClientSide) {
                    // 删除锁
                    BlockPos lockEntityPos = (LockEntityManager.getInstance()
                            .getNearByLockPos(door.getBlockPos().above(), world));
                    if (lockEntityPos != null) {
                        LockEntityManager.getInstance().removeLockEntity(lockEntityPos);
                        LockEntityManager.setDoorLocked(world, door, false);
                    }
                }
            }
            // 如果没有加固，但触发了警报，继续执行原版撬棍逻辑（门会被破坏）
        }
    }
}