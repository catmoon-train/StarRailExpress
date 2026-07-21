package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截原版 {@link LivingEntity#heal(float)}，将瞬间治疗/再生等原版回血
 * 转为 60s 健康值恢复（原版血量每秒已被钉满，不拦截则治疗药水完全浪费）。
 */
@Mixin(LivingEntity.class)
public class SixtySecondsHealMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void sre$convertHealToStatsHealth(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;
        if (!SixtySecondsMod.isActive(player.level())) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        int heal = Math.round(amount * 2.0F);
        stats.health = Math.min(stats.healthMax, stats.health + heal);
        stats.sync();
        ci.cancel();
    }
}
