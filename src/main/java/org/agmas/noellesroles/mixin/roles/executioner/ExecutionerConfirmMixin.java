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

package org.agmas.noellesroles.mixin.roles.executioner;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.killer.ExecutionerRoleData;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameUtils.class)
public class ExecutionerConfirmMixin {
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"), cancellable = true)
    private static void executionerConfirm(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier,
            boolean force,
            CallbackInfo ci) {
        final var world = victim.level();
        if (world == null)
            return;

        if (killer == null)
            return;
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
        if (gameWorldComponent == null)
            return;

        if (gameWorldComponent.isRole(killer, ModRoles.EXECUTIONER)) {
            Player executioner = killer;
            ExecutionerRoleData executionerPlayerComponent = RoleData.getNullable(ExecutionerRoleData.class, executioner);
            if (executionerPlayerComponent != null && executionerPlayerComponent.target != null
                    && executionerPlayerComponent.target.equals(victim.getUUID())) {
                executionerPlayerComponent.assignRandomTarget();
                executionerPlayerComponent.sync();
            }
        }
        if (force)
            return;
        final var role = gameWorldComponent.getRole(killer);
        if (role == null)
            return;
        if (killer != null) {
            if (victim != null) {
                if (role.getIdentifier().equals(ModRoles.EXECUTIONER_ID)) {
                    // 射击狂热期间不受锁定目标影响，可以击杀任何玩家
                    if (ExecutionerRoleData.isInFrenzy(killer)) {
                        return; // 不取消击杀
                    }
                    ExecutionerRoleData killerData = RoleData.getNullable(ExecutionerRoleData.class, killer);
                    if (killerData != null && killerData.target != null
                            && !killerData.target.equals(victim.getUUID())) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}