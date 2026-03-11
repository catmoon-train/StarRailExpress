package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerPsychoComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PlayerShopComponentCooldownMixin
 * - 修改仇杀客的疯狂模式冷却时间为30秒
 * - 原版CD为300秒（5分钟），仇杀客改为30秒
 */
@Mixin(targets = "io.wifi.starrailexpress.cca.PlayerShopComponent")
public class PlayerPsychoComponentCooldownMixin {

    /**
     * 拦截usePsychoMode方法，为仇杀客设置自定义的疯狂模式冷却时间
     * 在方法开头注入，这样后续的原方法调用会使用我们设置的CD
     */
    @Inject(method = "usePsychoMode", at = @At("TAIL"), remap = false)
    private static void noellesroles$modifyBloodFeudistPsychoCooldown(@NotNull Player player, CallbackInfoReturnable<Boolean> cir) {
        // 只在服务端处理
        if (player.level().isClientSide()) {
            return;
        }

        // 检查玩家角色
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.level());
        if (gameWorld != null && gameWorld.isRole(player, ModRoles.BLOOD_FEUDIST)) {
            // 仇杀客的疯狂模式冷却时间改为30秒（600 ticks）
            // 原版CD为300秒（6000 ticks）
            player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE, 15 * 20+SREConfig.psychoModeCooldown);
            PlayerPsychoComponent psychoComponent = PlayerPsychoComponent.KEY.get(player);
            psychoComponent.setPsychoTicks(20 * 22);
        }
    }
}
