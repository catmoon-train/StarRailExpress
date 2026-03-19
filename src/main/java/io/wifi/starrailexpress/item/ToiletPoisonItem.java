package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.block.ToiletBlock;
import io.wifi.starrailexpress.block_entity.ToiletBlockEntity;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ToiletPoisonItem extends Item {

    public ToiletPoisonItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 获取玩家视线指向的方块
        HitResult hitResult = player.pick(5.0, 0.0f, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos pos = blockHitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            // 检查是否是马桶
            if (state.getBlock() instanceof ToiletBlock) {
                if (!world.isClientSide) {
                    // 获取马桶方块实体
                    if (world.getBlockEntity(pos) instanceof ToiletBlockEntity blockEntity) {
                        // 如果马桶没有被下毒
                        if (!blockEntity.hasPoison()) {
                            blockEntity.setHasPoison(true, player.getUUID());
                            stack.shrink(1);

                            // 播放酿造台酿药的声音（仅客户端播放，投毒者自己能听到）
                            player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 1.0F);

                            // 记录回放
                            if (SRE.REPLAY_MANAGER != null) {
                                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                                        BuiltInRegistries.ITEM.getKey(org.agmas.noellesroles.init.ModItems.TOILET_POISON));
                            }

                            return InteractionResultHolder.success(stack);
                        }
                    }
                } else {
                    return InteractionResultHolder.success(stack);
                }
            }
        }

        return InteractionResultHolder.pass(stack);
    }
}
