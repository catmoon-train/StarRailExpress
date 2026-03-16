package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.block.TrainDoorBlock;
import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.SRE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.entity.LockEntity;
import org.agmas.noellesroles.entity.LockEntityManager;

import java.util.List;

/**
 * 门锁
 * <p>
 * - 可以锁门来影响撬锁器的功能
 * - 可以被钥匙和万能钥匙打开，对撬棍无效
 * - 锁的强度由长度决定
 * </p>
 * TODO: 一扇门是否可以放置多个锁（暂时允许多个锁）
 */
public class LockItem extends Item implements AdventureUsable {
    public LockItem(int length, float strength, Properties properties) {
        super(properties);
        this.length = length;
        this.resistance = strength;
    }

    /**
     * 计算锁实体的位置
     * 
     * @param context 上下文
     * @param door    被使用的门
     * @return 锁的坐标
     */
    public Vec3 getLockEntityPos(UseOnContext context, DoorBlockEntity door) {
        double x = context.getClickedPos().getX() + 0.5;
        // 确保锁位于上半部分
        double y = door.getBlockPos().above().getY();
        double z = context.getClickedPos().getZ() + 0.5;
        switch (door.getFacing()) {
            case EAST:
            case WEST:
                x = context.getClickLocation().x;
                break;
            case SOUTH:
            case NORTH:
                z = context.getClickLocation().z;
                break;
            default:
                break;
        }
        return new Vec3(x, y, z);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockEntity entity = world.getBlockEntity(context.getClickedPos());
        if (!(entity instanceof DoorBlockEntity))
            entity = world.getBlockEntity(context.getClickedPos().below());
        Player player = context.getPlayer();
        if (entity instanceof DoorBlockEntity doorEntity && player != null) {
            // 检查门是否已被破坏
            // 检查门是否支持
            var state = entity.getBlockState();
            if (!(state.getBlock() instanceof TrainDoorBlock)) {
                if (doorEntity.getKeyName().isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.engineer.not_support_door")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return InteractionResult.FAIL;
                }
            }
            if (doorEntity.isBlasted()) {
                if (!world.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.engineer.already_broken_lock")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                return InteractionResult.FAIL;
            }
            if (LockEntityManager.isDoorLocked(doorEntity)) {
                if (!world.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.engineer.already_locked")
                            .withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }

            // 判定玩家点击的方向：只允许在门的正反面点击
            Direction clickedFace = context.getClickedFace();
            Direction doorFacing = doorEntity.getFacing();
            if (clickedFace != doorFacing && clickedFace != doorFacing.getOpposite()) {
                return InteractionResult.PASS;
            }

            world.playSound(null, context.getClickedPos(), TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
            player.swing(InteractionHand.MAIN_HAND, true);
            // 将锁添加到世界中
            if (!world.isClientSide) {
                LockEntity lockEntity = new LockEntity(ModEntities.LOCK_ENTITY, world, length);
                lockEntity.setResistance(resistance);
                lockEntity.setPos(getLockEntityPos(context, doorEntity));
                lockEntity.setXRot(0.f);
                lockEntity.setYRot(doorEntity.getFacing().toYRot());
                world.addFreshEntity(lockEntity);
                // 在门上方一格记录锁
                LockEntityManager.getInstance().addLockEntity(doorEntity.getBlockPos().above(), lockEntity);
                if (!player.isCreative()) {
                    // 回放记录
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                            BuiltInRegistries.ITEM.getKey(this));
                    // 添加锁成功且非创造模式：消耗门锁
                    player.getItemInHand(context.getHand()).shrink(1);
                }
                // 锁门：包括临近的门
                LockEntityManager.lockNearByDoors(doorEntity, world, true);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useOn(context);
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setResistance(float resistance) {
        this.resistance = resistance;
    }

    // 添加工具提示
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.lock.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }

    private float resistance;
    private int length;
}
