package org.agmas.noellesroles.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.api.ClimbState;
import org.agmas.noellesroles.api.PlayerClimbState;
import org.agmas.noellesroles.role.bouns.roles.ScoutRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 攀爬移动。
 *
 * <p>原版玩家位移是客户端权威的，但 {@code travel} 会把 WASD 输入按「水平空气控制」
 * 叠加上去，方向和攀爬需要的方向不一致。这里在 travel 入口处：
 * <ol>
 * <li>把输入向量换成 {@link Vec3#ZERO}，屏蔽原版输入位移；</li>
 * <li>自己按视线方向把攀爬速度写进 {@code deltaMovement}。</li>
 * </ol>
 *
 * <p>攀爬状态挂在 Player 上（{@code PlayerClimbState}），客户端只看自己的本地预判
 * {@code predicting}，不需要服务端同步。
 *
 * <p>和 {@code LimpTravelMixin} 一样用 {@code @ModifyVariable(argsOnly = true)} 改 travel 的入参。
 * 只处理客户端（服务端玩家位移同样由客户端上报，服务端不需要也不应该自己推）。
 */
@Mixin(LivingEntity.class)
public class ScoutClimbTravelMixin {

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 noellesroles$scoutClimbTravel(Vec3 input) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return input;
        }
        if (player.level() == null || !player.level().isClientSide()) {
            return input;
        }
        ClimbState state = PlayerClimbState.of(player);
        if (state == null || !state.predicting) {
            return input;
        }
        ScoutRole.applyClimbPhysics(player);
        player.setDeltaMovement(ScoutRole.climbVelocity(player, input));
        // 交出移动控制权：这一 tick 的位移完全由上面写死的速度决定
        return Vec3.ZERO;
    }
}
