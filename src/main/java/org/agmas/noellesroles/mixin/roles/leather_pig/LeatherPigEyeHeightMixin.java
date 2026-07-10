package org.agmas.noellesroles.mixin.roles.leather_pig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 皮革噶的：伪装成猪期间把玩家的眼高压到猪的眼高。
 *
 * <p>碰撞箱（0.6×1.8）保持不变——地图是按人的尺寸做的。只改眼高，于是相机、准星射线、
 * 枪械命中判定这些读 {@code getEyeY()} 的地方一起下移，画面和命中点不会错开。
 */
@Mixin(Player.class)
public abstract class LeatherPigEyeHeightMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions noellesroles$lowerEyeToPig(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        if (!LeatherPigPlayerComponent.isDisguised(self)) {
            return dimensions;
        }
        // 取较小值：游泳、睡觉等姿态的眼高本就低于猪，不该被抬回来
        float eyeHeight = Math.min(dimensions.eyeHeight(), LeatherPigPlayerComponent.PIG_EYE_HEIGHT);
        return dimensions.withEyeHeight(eyeHeight);
    }
}
