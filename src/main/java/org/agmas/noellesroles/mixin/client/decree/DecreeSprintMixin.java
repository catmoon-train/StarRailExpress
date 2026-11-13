package org.agmas.noellesroles.mixin.client.decree;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.decree.DecreeClient;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 同时拦住冲刺键、双击前进与自动冲刺来源；允许正常解除疾跑。 */
@Mixin(Entity.class)
public abstract class DecreeSprintMixin {
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void decree$sprint(boolean sprinting, CallbackInfo ci) {
        if (sprinting && (Object) this == Minecraft.getInstance().player && DecreeClient.isRestricted(Decree.SPRINT)) {
            ci.cancel();
        }
    }
}
