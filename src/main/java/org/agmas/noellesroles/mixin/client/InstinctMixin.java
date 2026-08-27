/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.agmas.noellesroles.role_data.special.BetterVigilanteRoleData;
import org.agmas.noellesroles.role.ModRoles;
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

        // 迷雾区域内关闭本能
        if (org.agmas.noellesroles.client.scene.SceneFogClient.isLocalPlayerInFog()) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 鹈鹕肚内玩家不能开启本能
        if (PelicanManager.isStashed(player)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 检查玩家是否正在被操纵师控制 - 如果是，禁止使用杀手本能
        if (noellesroles$isPlayerBeingControlled(player)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // 鬼眼·杨间 诡域内：禁止开启杀手透视
        if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.EERIE_DOMAIN) || player.hasEffect(ModEffects.NO_INSTINCT)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRole(player, ModRoles.BETTER_VIGILANTE)) {
            var betterC = RoleData.getNullable(BetterVigilanteRoleData.class, player);
            if (betterC != null && betterC.lastStandActivated) {
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
                ManipulatorRoleData manipulatorComponent = RoleData.getNullable(ManipulatorRoleData.class, otherPlayer);
                if (manipulatorComponent != null && manipulatorComponent.isControlling &&
                        manipulatorComponent.target != null &&
                        manipulatorComponent.target.equals(player.getUUID())) {
                    return true;
                }
            }
        }
        return false;
    }
}
