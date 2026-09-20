package org.agmas.noellesroles.mixin.roles.scout;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.innocence.ClimbPoseRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让攀爬职业的「动作」姿态能保持住。
 *
 * <p>{@code Player.updatePlayerPose} 每 tick 都会把姿态重置成原版认定的值
 * （站立 / 蹲下 / 游泳 / 滑翔…），所以技能切换出来的姿态会被立刻冲掉。
 * 这里和仓库里已有的 {@code FearSitPoseMixin} / {@code SwimPoseEffectMixin} 一样，
 * 在入口直接接管。
 *
 * <p>注意：只有**姿态**存在 RoleData 里（{@link ClimbPoseRoleData}），
 * 攀爬本身的运行状态挂在 Player 上，没有 RoleData 的职业（例如冒险家）也能爬。
 *
 * <p>正因为 {@code updatePlayerPose} 被取消了，姿态值不会在 tick 内来回跳变，
 * 实体数据追踪器也就不会每 tick 重复发包。
 */
@Mixin(Player.class)
public class ScoutPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void noellesroles$scoutPose(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        ClimbPoseRoleData data = RoleData.getNullable(ClimbPoseRoleData.class, player);
        if (data != null && data.hasCustomPose()) {
            player.setPose(data.currentPose());
            ci.cancel();
        }
    }
}
