package org.agmas.noellesroles.mixin.roles.framing;

import io.wifi.starrailexpress.block.FoodPlatterBlock;
import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FoodPlatterBlock.class)
public abstract class DelusionVialApplyMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void defenseVialApply(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (world.isClientSide) return;
        if (player.isCreative()) return;
        BlockEntity platter = world.getBlockEntity(pos);
        if (platter instanceof BeveragePlateBlockEntity blockEntity) {
            if (player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.DELUSION_VIAL) && blockEntity.getPoisoner() == null) {
                blockEntity.setPoisoner(player.getStringUUID());
                player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 1.0F);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
        }
    }
}
