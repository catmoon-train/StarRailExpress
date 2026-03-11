package org.agmas.noellesroles.mixin.client.roles.puppeteer;

import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让刀可以攻击傀儡本体实体（客户端目标检测）
 */
@Mixin(KnifeItem.class)
public class PuppeteerBodyKnifeTargetMixin {
    
    @Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
    private static void allowPuppeteerBodyTarget(Player user, CallbackInfoReturnable<HitResult> cir) {
        // 扩展目标检测，包含 PuppeteerBodyEntity
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user, 
            entity -> (entity instanceof Player player && player.isAlive() && !player.isSpectator()) 
                    || entity instanceof PuppeteerBodyEntity, 
            4f);
        cir.setReturnValue(result);
    }
}