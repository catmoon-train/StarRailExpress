package org.agmas.noellesroles.mixin.roles.decree;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.agmas.noellesroles.game.roles.innocence.decree.DecreeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 先于职业的左键刀击注入执行，避免直接击杀绕过普通刺杀包。 */
@Mixin(value = ServerPlayer.class, priority = 2000)
public abstract class DecreeAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void decree$attack(Entity target, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if ((DecreeService.isRestricted(player, Decree.KNIFE) && DecreeService.isKnife(player.getMainHandItem()))
                || (DecreeService.isRestricted(player, Decree.ITEM_USE) && !player.getMainHandItem().isEmpty())) ci.cancel();
    }
}
