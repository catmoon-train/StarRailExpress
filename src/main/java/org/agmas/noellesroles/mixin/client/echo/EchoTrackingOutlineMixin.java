package org.agmas.noellesroles.mixin.client.echo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.game.modes.echo.client.EchoCardClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 仅本地授权轮廓；保留原版与其它模组来源，不给实体写全局发光标记。 */
@Mixin(Minecraft.class)
public class EchoTrackingOutlineMixin {
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean echo$authorizedOutline(boolean original, @Local(argsOnly = true) Entity entity) {
        return original || EchoCardClient.shouldGlow(entity);
    }
}
