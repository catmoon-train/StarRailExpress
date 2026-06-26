package org.agmas.noellesroles.mixin.client.roles.undead_lord;

import io.wifi.starrailexpress.content.item.RevolverItem;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让左轮（及委托其目标检测的暗器）的准星可以锁定亡灵之主的亡灵实体（客户端目标检测）。
 * 复刻原版谓词并追加 {@link UndeadEntity}，命中后由 {@code UndeadGunPayloadMixin} 在服务端结算击杀。
 */
@Mixin(RevolverItem.class)
public class UndeadGunTargetMixin {

    @Inject(method = "getGunTarget", at = @At("HEAD"), cancellable = true)
    private static void allowUndeadTarget(Player user, CallbackInfoReturnable<HitResult> cir) {
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        || entity instanceof PuppeteerBodyEntity
                        || entity instanceof PigeonEntity
                        || entity instanceof MorphlingKnifeDummyEntity
                        || entity instanceof UndeadEntity,
                20f);
        cir.setReturnValue(result);
    }
}
