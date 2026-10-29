package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.SteveMiner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** 冒险模式只对 Steve 手持专属镐的任务点开放挖掘，不授予地图编辑权限。 */
@Mixin(Player.class)
public class SteveMiningPermissionMixin {
    @Inject(method = "blockActionRestricted", at = @At("HEAD"), cancellable = true)
    private void noellesroles$allowTaskMining(Level level, BlockPos pos, GameType gameType,
            CallbackInfoReturnable<Boolean> cir) {
        if (gameType == GameType.ADVENTURE && SteveMiner.canMine((Player) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }
}
