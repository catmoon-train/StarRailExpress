package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.SteveMiner;
import org.agmas.noellesroles.game.command_block.item.StevePickaxeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 任务方块可能没有硬度或不可破坏，Steve 的采矿按工具固定耗时结算。 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class SteveMiningProgressMixin {
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void noellesroles$taskMiningProgress(Player player, BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<Float> cir) {
        if (SteveMiner.canMine(player, pos)
                && player.getMainHandItem().getItem() instanceof StevePickaxeItem pickaxe) {
            cir.setReturnValue(1.0F / pickaxe.miningTicks());
        }
    }
}
