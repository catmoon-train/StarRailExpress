package org.agmas.noellesroles.mixin;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 60s 倒地强制趴下姿势（两端生效）。只靠服务端每 tick {@code setSwimming(true)} 不够：
 * 本人客户端的 {@code updateSwimming} 每 tick 会把泳姿标志清掉再重算姿势，导致倒地者
 * 自己（含 F5）看不到趴下动作；这里在 {@code updatePlayerPose} 头部对倒地玩家直接钉死
 * SWIMMING 姿势并取消原版重算——服务端姿势不再每 tick 抖动，客户端本人/他人视角一致趴下。
 */
@Mixin(Player.class)
public abstract class SixtySecondsDownedPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void sixtySeconds$forceDownedPose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!SixtySecondsMod.isActive(self.level())) {
            return;
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(self);
        if (stats != null && stats.downed) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
