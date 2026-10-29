package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.SteveMiner;
import org.agmas.noellesroles.game.command_block.item.StevePickaxeItem;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Steve 的铁镐挖任务点时不拆方块，只发一份基础掉落。 */
@Mixin(ServerPlayerGameMode.class)
public class StevePickaxeBreakMixin {
    @Shadow
    @Final
    protected ServerPlayer player;


    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void noellesroles$steveKeepsTaskBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.player.getMainHandItem().getItem() instanceof StevePickaxeItem) {
            // 无论采矿是否成功，专属镐都不能落入原版删除方块的分支。
            boolean mined = SteveMiner.mine(this.player, pos);
            this.player.connection.send(new ClientboundBlockUpdatePacket(this.player.level(), pos));
            cir.setReturnValue(mined);
        }
    }
}
