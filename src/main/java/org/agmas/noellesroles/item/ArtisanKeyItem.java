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

public class ArtisanKeyItem extends Item implements AdventureUsable {
    public ArtisanKeyItem(Properties properties) {
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
        if (!(doorEntity instanceof SmallDoorBlockEntity smallDoorEntity)) {
            return InteractionResult.PASS;
        }

        // 巧匠钥匙强制清除卡门并直接切换门状态，不改动门上的附加道具。
        doorEntity.setJammed(0);
        BlockState lowerState = world.getBlockState(lowerPos);
        if (!(lowerState.getBlock() instanceof SmallDoorBlock)) {
            return InteractionResult.PASS;
        }

        world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
        SmallDoorBlock.toggleDoor(lowerState, world, smallDoorEntity, lowerPos);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
