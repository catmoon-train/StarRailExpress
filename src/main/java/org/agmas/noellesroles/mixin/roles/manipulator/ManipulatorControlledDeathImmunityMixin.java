package org.agmas.noellesroles.mixin.roles.manipulator;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截「被操纵师操控的玩家」因环境/陷阱触发的强制死亡（{@code forceKillPlayer}）。
 *
 * <p>场景陷阱（滚石/滚木碾压、钟乳石、焚化炉、下水道窒息等）与出界/落水/岩浆的二次判定都走
 * {@code forceDeath = true}，会绕过 {@link io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller}
 * 否决事件。这里在所有强制死亡的唯一入口 {@link GameUtils#killPlayer} HEAD 处兜底：
 * 若受害者正被操控且死因属于环境/陷阱，则取消死亡并把其回溯到受伤前的安全落点。
 *
 * <p>镜像 {@code AdventurerDeathImmunityMixin} 的实现方式。非强制死亡（玩家击杀等）不受影响。
 */
@Mixin(GameUtils.class)
public abstract class ManipulatorControlledDeathImmunityMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;Z"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void noellesroles$manipulatorControlledHazardImmunity(
            Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (!forceDeath) {
            return; // 非强制死亡由 AllowPlayerDeathWithKiller 否决事件处理
        }
        if (InControlCCA.blockHazardDeathIfControlled(victim, deathReason)) {
            ci.cancel();
        }
    }
}
