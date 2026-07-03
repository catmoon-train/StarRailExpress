package org.agmas.noellesroles.mixin.roles.engineer;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.item.CrowbarItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
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
import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.content.item.AlarmTrapItem;
import org.agmas.noellesroles.content.item.ReinforcementItem;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
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

                // 仍然消耗冷却时间——与普通门一致按角色折算冷却，避免亡命徒撬加固门 45s、撬普通门仅 11s 的失衡。
                // 加固门本身已需两次撬动（先破加固、再开门），按同样折算后总耗时自然高于普通门。
                if (!player.isCreative()) {
                    int baseCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.CROWBAR, 45 * 20);
                    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
                    int cooldown;
                    if (gameWorldComponent != null && gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                        cooldown = baseCooldown / 4;
                    } else if (gameWorldComponent != null && gameWorldComponent.isRole(player, RedHouseRoles.FURANDORU)) {
                        cooldown = baseCooldown / 6;
                    } else {
                        cooldown = baseCooldown;
                    }
                    player.getCooldowns().addCooldown(context.getItemInHand().getItem(), cooldown);
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