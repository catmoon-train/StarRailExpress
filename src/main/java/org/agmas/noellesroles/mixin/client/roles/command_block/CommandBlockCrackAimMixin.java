package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.game.command_block.CommandBlockCracks;

import io.wifi.starrailexpress.content.item.StandardRevolverItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 巡警手枪的准星也能打到命令方块裂缝，且不会抢走更近的玩家。 */
@Mixin(StandardRevolverItem.class)
public class CommandBlockCrackAimMixin {
    @Inject(method = "getGunTarget", at = @At("RETURN"), cancellable = true)
    private static void noellesroles$cracks(Player user, CallbackInfoReturnable<HitResult> cir) {
        cir.setReturnValue(CommandBlockCracks.prefer(user, cir.getReturnValue(), 20.0F));
    }
}
