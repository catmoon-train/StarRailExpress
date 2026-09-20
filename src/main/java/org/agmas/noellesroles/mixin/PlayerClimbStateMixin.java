package org.agmas.noellesroles.mixin;

import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.api.ClimbState;
import org.agmas.noellesroles.api.PlayerClimbState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 把攀爬状态挂到 {@code Player} 上（和主模组 {@code PlayerEntityMixin} 里的体力条
 * {@code sprintingTicks} 一样：字段在玩家身上、两端各持一份、不额外同步）。
 *
 * <p>这样任何职业只要 {@code SRERole#canClimbWalls(Player)} 返回 true 就能攀爬，
 * 不需要再绑定攀爬专属的 RoleData。
 */
@Mixin(Player.class)
public abstract class PlayerClimbStateMixin implements PlayerClimbState {

    @Unique
    private ClimbState noellesroles$climbState = new ClimbState();

    @Override
    public ClimbState noellesroles$climbState() {
        return this.noellesroles$climbState;
    }
}
