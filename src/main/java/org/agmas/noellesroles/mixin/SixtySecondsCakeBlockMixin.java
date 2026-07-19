package org.agmas.noellesroles.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版蛋糕加强：
 * 1. 允许放在白色混凝土上（60s 模式下白色混凝土是建造标记方块）
 * 2. 右键食用蛋糕方块恢复 10 点饥饿值（原版 2 点）
 */
@Mixin(CakeBlock.class)
public abstract class SixtySecondsCakeBlockMixin {

    /**
     * 允许蛋糕放置在白色混凝土上方（60s 模式建造标记）。
     */
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void sre$allowOnWhiteConcrete(BlockState state, LevelReader level, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.WHITE_CONCRETE)) {
            cir.setReturnValue(true);
        }
    }

//    /**
//     * 右键食用原版蛋糕方块时恢复 10 点饥饿值（原版 2 点 → 5 个鸡腿）。
//     */
//    @Inject(method = "useWithoutItem", at = @At(value = "INVOKE",
//            target = "Lnet/minecraft/world/food/FoodData;eat(IIF)V"))
//    private void sre$boostCakeFoodRestore(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir) {
//        // 原版 eat(2, 0.1F) 只加 2 饥饿值——这里直接调用 eat(8, 0.1F) 补足差额到 10
//        // eat(2, 0.1F) 已被原版调用，再手动 eat(8, 0.1F) 合计 10
//        FoodData food = player.getFoodData();
//        food.eat(8, 0.1F);
//    }
}
