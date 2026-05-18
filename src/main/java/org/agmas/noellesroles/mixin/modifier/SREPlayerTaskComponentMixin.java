package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SREPlayerTaskComponent.class)
public abstract class SREPlayerTaskComponentMixin {

    /**
     * 工作狂效果：在任务刷新间隔计算后应用50%加速
     * 通过在方法末尾修改 nextTaskTimer 来实现
     */
    @Inject(method = "serverTick", at = @At("TAIL"))
    public void onWorkaholicTaskRefresh(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (self.getPlayer() instanceof ServerPlayer player) {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
            if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.WORKAHOLIC)) {
                // 工作狂效果：任务刷新速度加快50%
                SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(player.level());
                long gameElapsedTicks = Math.max(0, gameTimeComponent.getResetTime() - gameTimeComponent.getTime());
                int minCooldown = (int) (GameConstants.getDynamicMinTaskCooldown(gameElapsedTicks) * 0.5);
                int maxCooldown = (int) (GameConstants.getDynamicMaxTaskCooldown(gameElapsedTicks) * 0.5);
                int newTimer = (int) (player.getRandom().nextFloat() * (maxCooldown - minCooldown) + minCooldown);
                self.nextTaskTimer = Math.max(newTimer, 2);
                self.sync();
            }
        }
    }
}
