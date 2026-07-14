package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.RevolverItem;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem;
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
 * 60s 模式：枪械准星可锁定<b>低语怪/夜袭者</b>（客户端目标检测；服务端结算见
 * {@code SixtySecondsGunTracerMixin}）。priority 高于 {@code UndeadGunTargetMixin}
 * 使本注入先执行；仅本模式运行中才接管（并保留原有全部可命中实体），其余模式 PASS 交回原链。
 */
@Mixin(value = RevolverItem.class, priority = 1100)
public class SixtySecondsGunTargetMixin {

    @Inject(method = "getGunTarget", at = @At("HEAD"), cancellable = true)
    private static void sixtySeconds$allowMobTargets(Player user, CallbackInfoReturnable<HitResult> cir) {
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                || SixtySecondsMod.MODE == null
                || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
            return; // 非本模式：交回原版/其它 mixin 的目标检测
        }
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        || entity instanceof PuppeteerBodyEntity
                        || entity instanceof PigeonEntity
                        || entity instanceof MorphlingKnifeDummyEntity
                        || entity instanceof UndeadEntity
                        // 60s 自研怪（游荡怪/夜袭者/Boss）：客户端 instanceof 判定（tag 不随实体同步到客户端）
                        || entity instanceof net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity
                        || entity.getTags().contains(SixtySecondsWhisperSystem.WHISPER_TAG)
                        || entity.getTags().contains(SixtySecondsDefenseSystem.ASSAULT_TAG),
                20f);
        cir.setReturnValue(result);
    }
}
