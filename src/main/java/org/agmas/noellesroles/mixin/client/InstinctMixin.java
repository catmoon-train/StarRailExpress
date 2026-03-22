package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SREClient.class)
public abstract class InstinctMixin {

    @Shadow
    public static KeyMapping instinctKeybind;

    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void b(CallbackInfoReturnable<Boolean> cir) {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;

        // 检查玩家是否正在被操纵师控制 - 如果是，禁止使用杀手本能
        if (noellesroles$isPlayerBeingControlled(player)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRole(player, ModRoles.BETTER_VIGILANTE)) {
            var betterC = BetterVigilantePlayerComponent.KEY.get(player);
            if (betterC.lastStandActivated) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    /**
     * 检查玩家是否正在被操纵师控制
     */
    @Unique
    private static boolean noellesroles$isPlayerBeingControlled(Player player) {
        if (player == null)
            return false;

        // 遍历所有玩家，检查是否有操纵师正在控制当前玩家
        for (Player otherPlayer : player.level().players()) {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(otherPlayer.level());
            if (gameWorldComponent.isRole(otherPlayer, ModRoles.MANIPULATOR)) {
                ManipulatorPlayerComponent manipulatorComponent = ManipulatorPlayerComponent.KEY.get(otherPlayer);
                if (manipulatorComponent.isControlling &&
                        manipulatorComponent.target != null &&
                        manipulatorComponent.target.equals(player.getUUID())) {
                    return true;
                }
            }
        }
        return false;
    }
}
