package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.content.entity.LockEntity;
import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 加固门道具
 * - 工程师商店物品（所有人可使用）
 * - 在商店以75金币购买
 * - 右键门：使门能够防御一次撬棍攻击
 * - 蹲下右键被卡住的门：解除卡住状态
 * - 蹲下右键已加固/有警报的门（工程师专属）：取下对应道具
 */
public class ScrewdriverItem extends Item implements AdventureUsable {

    public ScrewdriverItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);

        if (player == null)
            return InteractionResult.PASS;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        boolean isEngineer = gameWorld.isRole(player, ModRoles.ENGINEER);
        boolean isLockSmith = gameWorld.isRole(player, ModRoles.LOCKSMITH);
        // 检查是否为门方块
        if (state.getBlock() instanceof SmallDoorBlock) {
            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();

            if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity doorEntity) {
                // 蹲下右键：工程师专属功能 - 解除卡住状态或取下道具
                if (player.isShiftKeyDown()) {
                    // 优先检查是否要解除卡住状态
                    if (doorEntity.isJammed()) {
                        // 解除卡住
                        if (player.getCooldowns().isOnCooldown(this))
                            return InteractionResult.FAIL;
                        if (!player.isCreative())
                            player.getCooldowns().addCooldown(context.getItemInHand().getItem(), 20 * 10);
                        doorEntity.setJammed(0);
                        ReinforcementItem.unJamNearBy(context);
                        if (!world.isClientSide) {
                            world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                                    SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.2f);
                            player.displayClientMessage(Component.translatable("message.noellesroles.engineer.unjammed")
                                    .withStyle(ChatFormatting.GREEN), true);
                        }

                        // 不消耗物品
                        return InteractionResult.SUCCESS;
                    }

                    // 工程师专属：取下门上的道具
                    {
                        // 检查是否有加固
                        if ((isLockSmith || isEngineer) && ReinforcementItem.isDoorReinforced(doorEntity)) {
                            ReinforcementItem.setDoorReinforced(doorEntity, false);

                            if (!world.isClientSide) {
                                world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                                        SoundEvents.ANVIL_HIT, SoundSource.BLOCKS, 0.5f, 1.2f);
                                player.displayClientMessage(
                                        Component.translatable("message.noellesroles.engineer.removed_reinforcement")
                                                .withStyle(ChatFormatting.GREEN),
                                        true);
                                // 返还加固物品
                                if (isEngineer) {
                                    player.addItem(new ItemStack(ModItems.REINFORCEMENT));
                                }
                            }
                            return InteractionResult.SUCCESS;
                        }

                        // 检查是否有警报陷阱
                        if ((isLockSmith || isEngineer) && AlarmTrapItem.hasDoorAlarmTrap(doorEntity)) {
                            AlarmTrapItem.setDoorAlarmTrap(doorEntity, false);

                            if (!world.isClientSide) {
                                world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                                        SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 0.7f, 1.2f);
                                player.displayClientMessage(
                                        Component.translatable("message.noellesroles.engineer.removed_alarm")
                                                .withStyle(ChatFormatting.GREEN),
                                        true);
                                // 返还警报陷阱物品
                                if (isEngineer) {
                                    player.addItem(new ItemStack(ModItems.ALARM_TRAP));
                                }
                            }
                            return InteractionResult.SUCCESS;
                        }

                        // 检查门是否有锁
                        if (LockEntityManager.getInstance().getLockEntity(lowerPos.above()) != null) {
                            if (!world.isClientSide) {
                                world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                                        TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 0.7f, 1.2f);
                                player.displayClientMessage(
                                        Component.translatable("message.noellesroles.engineer.removed_lock")
                                                .withStyle(ChatFormatting.GREEN),
                                        true);
                                LockEntity lockEntity = LockEntityManager.getInstance().getLockEntity(lowerPos.above());
                                // 返还锁物品
                                ItemStack itemStack = new ItemStack(ModItems.LOCK_ITEM);
                                if (itemStack.getItem() instanceof LockItem lockItem) {
                                    lockItem.setLength(lockEntity.getLength());
                                    lockItem.setResistance(lockEntity.getResistance());
                                }
                                LockEntityManager.getInstance().removeLockEntity(lowerPos.above(), lockEntity);
                                if (isEngineer) {
                                    player.addItem(itemStack);
                                }
                                // 取消锁门：包括临近的门
                                LockEntityManager.lockNearByDoors(doorEntity, world, false);
                            }
                            return InteractionResult.SUCCESS;
                        }

                        // 没有可取下的道具
                        if (!world.isClientSide) {
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.engineer.nothing_to_remove")
                                            .withStyle(ChatFormatting.YELLOW),
                                    true);
                        }
                        return InteractionResult.FAIL;
                    }
                }

                // 普通右键：修复maybe
                if (doorEntity.isBlasted()) {
                    if (isLockSmith || isEngineer || player.isCreative()) {
                        if (player.getCooldowns().isOnCooldown(this))
                            return InteractionResult.FAIL;
                        if (!player.isCreative())
                            player.getCooldowns().addCooldown(context.getItemInHand().getItem(), 20 * 30);
                        world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                                TMMSounds.BLOCK_DOOR_TOGGLE, SoundSource.BLOCKS, 0.7f, 1.5f);
                        if (!world.isClientSide) {
                            doorEntity.setBlasted(false);
                            doorEntity.setOpen(true);
                            unBlastNearBy(context);
                            doorEntity.setChanged();
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.locksmith.fix")
                                            .withStyle(ChatFormatting.GREEN),
                                    true);
                        }
                        return InteractionResult.SUCCESS;
                    }
                    return InteractionResult.FAIL;
                }
            }
        }

        return InteractionResult.PASS;
    }

    public static void unBlastNearBy(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos clickpos = context.getClickedPos();
        Vec3i offsets[] = { new Vec3i(0, 0, -1), new Vec3i(0, 0, 1), new Vec3i(-1, 0, 0), new Vec3i(1, 0, 0) };
        for (int i = 0; i < offsets.length; i++) {
            BlockPos pos = clickpos.offset(offsets[i]);
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof SmallDoorBlock) {
                BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
                    entity.setBlasted(false);
                    entity.setOpen(true);
                    entity.setChanged();
                }
            }
        }
    }
}