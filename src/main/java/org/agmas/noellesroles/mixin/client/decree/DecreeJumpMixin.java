package org.agmas.noellesroles.mixin.client.decree;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.client.decree.DecreeClient;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 避免客户端先跳起再被服务器拉回；不拦其它实体或整合服玩家。 */
@Mixin(LivingEntity.class)
public abstract class DecreeJumpMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void decree$jump(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && DecreeClient.isRestricted(Decree.MOVEMENT)) ci.cancel();
    }
}
