package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.IronDoorKeyItem;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
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

import java.util.function.Supplier;

import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;

public class TrainDoorBlock extends SmallDoorBlock {
    public TrainDoorBlock(Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(typeSupplier, settings);
    }

    @FunctionalInterface
    public interface DoorUseSuperFunction {
        InteractionResult apply(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit);
    }

    public static InteractionResult useWithoutItemGeneric(
            DoorUseSuperFunction superFunction, // 父类方法的回调
            BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {

        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
            if (entity.isBlasted()) {
                return InteractionResult.PASS;
            }
            boolean requiresKey = !entity.getKeyName().isEmpty();
            if (requiresKey) {
                // 需要钥匙时，调用传入的父类逻辑，并把当前方法的参数传递进去
                return superFunction.apply(state, world, pos, player, hit);
            }
            if (player.isCreative()
                    || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player)) {
                return open(state, world, entity, lowerPos);
            } else {
                ItemStack mainHandItem = player.getMainHandItem();
                boolean hasLockpick = mainHandItem.is(TMMItems.LOCKPICK) || mainHandItem.is(ModItems.MASTER_KEY) || mainHandItem.is(FunnyItems.BOWEN_BADGE);
                boolean hasIronDoorKey = mainHandItem.getItem() instanceof IronDoorKeyItem;

                if (entity.isOpen()) {
                    return open(state, world, entity, lowerPos);
                } else {
                    if (entity.isJammed()) {
                        if (!world.isClientSide) {
                            world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                    TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                            player.displayClientMessage(Component.translatable("tip.door.locked"), true);
                        }
                        return InteractionResult.FAIL;
                    }
                    if (hasLockpick) {
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f,
                                TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                        return open(state, world, entity, lowerPos);
                    } else if (hasIronDoorKey) {
                        if (!player.isCreative()) {
                            mainHandItem.hurtAndBreak(1, player, player.getEquipmentSlotForItem(mainHandItem));
                        }
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        return useWithoutItemGeneric(
                (s, w, p, pl, h) -> super.useWithoutItem(s, w, p, pl, h), // lambda 调用父类并传递参数
                state, world, pos, player, hit);
    }
}
