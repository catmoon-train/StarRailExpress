package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.client.CommandBlockProgressClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.client.gui.MapRuleIntroHud;

/** 命令方块开场时不弹地图规则卡，避免和镜头叠在一起。 */
@Mixin(MapRuleIntroHud.class)
public class CommandBlockMapIntroMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private static void skipDuringCommandBlock(String id, CallbackInfo ci) {
        if (CommandBlockProgressClient.isShowing()) {
            ci.cancel();
        }
    }
}
