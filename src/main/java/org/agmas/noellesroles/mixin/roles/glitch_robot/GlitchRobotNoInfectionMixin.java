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

package org.agmas.noellesroles.mixin.roles.glitch_robot;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InfectedPlayerComponent.class)
public abstract class GlitchRobotNoInfectionMixin {

    @Shadow private Player player;

    @Inject(method = "infect", at = @At("HEAD"), cancellable = true)
    private void glitchRobotNoInfection(Player infectorPlayer, CallbackInfo ci) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld.isRole(this.player, ModRoles.GLITCH_ROBOT)) {
            ci.cancel();
        }
    }
}
