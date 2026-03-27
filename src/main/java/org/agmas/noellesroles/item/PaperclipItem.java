package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.entity.LockEntityManager;

public class PaperclipItem extends Item implements AdventureUsable {
    public PaperclipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = world.getBlockState(clickedPos);
        BlockPos lowerPos = clickedPos;
        if (clickedState.getBlock() instanceof SmallDoorBlock) {
            lowerPos = clickedState.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? clickedPos : clickedPos.below();
        }

        if (!(world.getBlockEntity(lowerPos) instanceof DoorBlockEntity doorEntity)) {
            return InteractionResult.PASS;
        }
        if (doorEntity.isBlasted()) {
            return InteractionResult.FAIL;
        }
        if (doorEntity.isJammed()) {
            if (!world.isClientSide) {
                world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                        TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("tip.door.jammed"), true);
            }
            return InteractionResult.FAIL;
        }

        // 若门受工程师锁影响，则交由锁系统处理（触发原撬锁小游戏链路）。
        var lockPos = LockEntityManager.getInstance().getNearByLockPos(lowerPos.above(), world);
        if (lockPos != null && LockEntityManager.getInstance().getLockEntity(lockPos) != null) {
            return InteractionResult.PASS;
        }

        if (!(doorEntity instanceof SmallDoorBlockEntity smallDoorEntity)) {
            return InteractionResult.PASS;
        }

        BlockState lowerState = world.getBlockState(lowerPos);
        if (!(lowerState.getBlock() instanceof SmallDoorBlock)) {
            return InteractionResult.PASS;
        }

        world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
        SmallDoorBlock.toggleDoor(lowerState, world, smallDoorEntity, lowerPos);

        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
