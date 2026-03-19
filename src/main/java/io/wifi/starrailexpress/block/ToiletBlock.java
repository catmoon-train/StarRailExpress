package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block.entity.SeatEntity;
import io.wifi.starrailexpress.block_entity.ToiletBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ToiletBlock extends CouchBlock implements EntityBlock {
    public static final VoxelShape SHAPE = Blocks.CHEST.defaultBlockState().getCollisionShape(null, null);

    public ToiletBlock(Properties settings) {
        super(settings);
    }

    @Override
    public Vec3 getNorthFacingSitPos(Level world, BlockState state, BlockPos pos) {
        return new Vec3(0.5f, -0.15f, 0.5f);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        // 检查玩家是否手持马桶毒药（主手或副手）
        if (!player.isCreative()) {
            ItemStack mainHandItem = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHandItem = player.getItemInHand(InteractionHand.OFF_HAND);

            ItemStack poisonItem = null;

            if (mainHandItem.is(org.agmas.noellesroles.init.ModItems.TOILET_POISON)) {
                poisonItem = mainHandItem;
            } else if (offHandItem.is(org.agmas.noellesroles.init.ModItems.TOILET_POISON)) {
                poisonItem = offHandItem;
            }

            if (poisonItem != null && world.getBlockEntity(pos) instanceof ToiletBlockEntity blockEntity) {
                if (!blockEntity.hasPoison()) {
                    blockEntity.setHasPoison(true, player.getUUID());
                    poisonItem.shrink(1);
                    // 播放酿造台酿药的声音（仅客户端播放，投毒者自己能听到）
                    player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
                // 已经有毒，返回成功但不消耗
                return InteractionResult.SUCCESS;
            }
        }

        // 父类坐下逻辑（复写以添加毒药检测）
        float radius = 1;
        if (!player.isShiftKeyDown()
                && (player.position().subtract(pos.getCenter()).length() <= 1.4f
                        || player.position().add(0, 1d, 0).subtract(pos.getCenter()).length() <= 1.4f)
                && !(player.getMainHandItem().getItem() instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof MountableBlock)
                && world.getEntitiesOfClass(SeatEntity.class, AABB.ofSize(pos.getCenter(), radius, radius, radius),
                        entity -> entity.isAlive()).isEmpty()) {

            if (world.isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }

            // 检查马桶是否有毒,如果有毒则设置延迟中毒任务
            if (world.getBlockEntity(pos) instanceof ToiletBlockEntity toiletEntity && toiletEntity.hasPoison()) {
                // 设置40 ticks(2秒)后中毒的任务
                io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
                    if (player.isAlive() && player.getVehicle() instanceof io.wifi.starrailexpress.block.entity.SeatEntity seat) {
                        BlockPos seatPos = seat.getSeatPos();
                        if (seatPos != null && seatPos.equals(pos)) {
                            // 检查马桶是否仍然有毒
                            if (world.getBlockEntity(pos) instanceof io.wifi.starrailexpress.block_entity.ToiletBlockEntity currentToilet &&
                                currentToilet.hasPoison()) {
                                // 触发中毒(不发送默认数据包,因为我们会显示自己的消息)
                                io.wifi.starrailexpress.util.PoisonComponentUtils.toiletPoison((net.minecraft.server.level.ServerPlayer) player, currentToilet, false);
                                player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("game.player.toilet_poisoned").withStyle(net.minecraft.ChatFormatting.RED),
                                    true
                                );
                            }
                        }
                    }
                }, 40);
            }

            // 调用父类的坐下逻辑
            return super.useWithoutItem(state, world, pos, player, hit);
        }

        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ToiletBlockEntity(TMMBlockEntities.TOILET, pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClientSide || !type.equals(TMMBlockEntities.TOILET)) {
            return null;
        }
        return ToiletBlockEntity::clientTick;
    }
}