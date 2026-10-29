package org.agmas.noellesroles.mixin.client;

import org.agmas.noellesroles.game.command_block.item.StevePickaxeItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;

/** 只完成本地挖掘动画，不预测删除任务方块，防止方块闪烁和任务点暂时消失。 */
@Mixin(MultiPlayerGameMode.class)
public class SteveMiningPredictionMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void noellesroles$keepTaskBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof StevePickaxeItem) {
            cir.setReturnValue(true);
        }
    }
}
