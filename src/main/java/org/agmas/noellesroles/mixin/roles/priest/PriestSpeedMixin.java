package org.agmas.noellesroles.mixin.roles.priest;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.agmas.noellesroles.role_data.neutral.PriestRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 本游戏 {@code Player#getSpeed} 会覆盖原版移速属性，神父的 AttributeModifier 因此不生效。
 * 在最终移速上按冲刺进度叠乘，最高约 +1000%。
 */
@Mixin(Player.class)
public abstract class PriestSpeedMixin {

    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    private float noellesroles$priestHeavenSpeed(float original) {
        Player player = (Player) (Object) this;
        PriestRoleData data = RoleData.getNullable(PriestRoleData.class, player);
        if (data == null) {
            return original;
        }
        int ticks = data.sprintTicks;
        if (ticks <= 0 && !data.autoSprint) {
            return original;
        }
        float ramp = Math.min(1.0F, ticks / (float) PriestHeavenManager.SPEED_RAMP_TICKS);
        if (data.autoSprint) {
            ramp = Math.max(ramp, 0.25F);
        }
        return original * (1.0F + (float) PriestHeavenManager.MAX_SPEED_MULTIPLIER * ramp);
    }
}
