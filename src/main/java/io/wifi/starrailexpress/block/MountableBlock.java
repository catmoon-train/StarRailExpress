package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block.entity.SeatEntity;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class MountableBlock extends Block {

    public MountableBlock(Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return super.getShape(state, world, pos, context);
    }

    public static Map<UUID, Vec3> lastPos = new HashMap<>();

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        float radius = 1;
        if (!player.isShiftKeyDown()
                && (player.position().subtract(pos.getCenter()).length() <= 1.4f
                        || player.position().add(0, 1d, 0).subtract(pos.getCenter()).length() <= 1.4f)
                && !(player.getMainHandItem().getItem() instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof MountableBlock)
                && world.getEntitiesOfClass(SeatEntity.class, AABB.ofSize(pos.getCenter(), radius, radius, radius),
                        Entity::isAlive).isEmpty()) {

            if (world.isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }

            if (!player.getCooldowns().isOnCooldown(TMMBlocks.ACACIA_BRANCH.asItem())) {
                player.getCooldowns().addCooldown(TMMBlocks.ACACIA_BRANCH.asItem(), 10);
                if (player.getVehicle() != null) {
                    return InteractionResult.FAIL;
                    // player.stopRiding();
                    // if (lastPos != null) {
                    // var ppos = lastPos.get(player.getUUID());
                    // if (ppos != null) {
                    // player.setPos(ppos);
                    // }
                    // }
                }

                SeatEntity seatEntity = TMMEntities.SEAT.create(world);

                if (seatEntity == null) {
                    return InteractionResult.PASS;
                }
                // 只在首次坐下时保存位置,避免连续坐椅子时累积高度
                if (!lastPos.containsKey(player.getUUID())) {
                    lastPos.put(player.getUUID(), player.position());
                }
                Vec3 sitPos = this.getSitPos(world, state, pos);
                Vec3 vec3d = Vec3.atLowerCornerOf(pos).add(sitPos);

                seatEntity.moveTo(vec3d.x, vec3d.y, vec3d.z, 0, 0);
                seatEntity.setSeatPos(pos);

                world.addFreshEntity(seatEntity);
                player.startRiding(seatEntity);

                return InteractionResult.sidedSuccess(false);
            }
        } else {
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    public abstract Vec3 getSitPos(Level world, BlockState state, BlockPos pos);
}
