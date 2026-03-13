package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.cca.StarTrainWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.item.IronDoorKeyItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

public class TrainDoorBlock extends SmallDoorBlock {
    public TrainDoorBlock(Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(typeSupplier, settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {

        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
            if (entity.isBlasted()) {
                return InteractionResult.PASS;
            }
            boolean requiresKey = !entity.getKeyName().isEmpty();
            if (requiresKey){
                return super.useWithoutItem(state, world, pos, player, hit);
            }
            if (player.isCreative()
                    || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player)
                    || StarTrainWorldComponent.KEY.get(world).getSpeed() == 0) {
                return open(state, world, entity, lowerPos);
            } else {
                ItemStack mainHandItem = player.getMainHandItem();
                boolean hasLockpick = mainHandItem.is(TMMItems.LOCKPICK);
                boolean hasIronDoorKey = mainHandItem.getItem() instanceof IronDoorKeyItem;

                if (entity.isOpen()) {
                    return open(state, world, entity, lowerPos);
                } else {
                    if (hasLockpick) {
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                        return open(state, world, entity, lowerPos);
                    } else if (hasIronDoorKey) {
                        // 扣除铁门钥匙的耐久
                        if (!player.isCreative()) {
                            mainHandItem.hurtAndBreak(1, player, player.getEquipmentSlotForItem(mainHandItem));
                        }
                        // 播放声音并开门
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
                        return open(state, world, entity, lowerPos);
                    } else {
                        if (!world.isClientSide) {
                            world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                    TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                            player.displayClientMessage(Component.translatable("tip.door.locked"), true);
                        }
                        return InteractionResult.FAIL;
                    }
                }
            }
        }

        return InteractionResult.PASS;
    }
}
