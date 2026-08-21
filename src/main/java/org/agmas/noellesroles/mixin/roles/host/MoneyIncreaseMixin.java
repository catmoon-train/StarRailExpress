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

package org.agmas.noellesroles.mixin.roles.host;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameUtils.class)
public class MoneyIncreaseMixin {
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"))
    private static void increaseMoney(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier,
            boolean force, CallbackInfo ci) {
        if (killer != null) {
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.CONDUCTOR) && !gameWorldComponent.isInnocent(killer)) {
                SREPlayerShopComponent component = SREPlayerShopComponent.KEY.get(killer);
                component.addToBalance(100);
            }
        }
    }

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"), cancellable = true)
    private static void jesterJest(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier,
            boolean forceDeath,
            CallbackInfo ci) {
        if (killer != null && !forceDeath) {
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                    .get(victim.level());
            if (gameWorldComponent.isRole(victim, ModRoles.JESTER)
                    && !gameWorldComponent.isRole(killer, ModRoles.JESTER) && gameWorldComponent.isInnocent(killer)) {
                SREPlayerPsychoComponent component = (SREPlayerPsychoComponent) SREPlayerPsychoComponent.KEY
                        .get(victim);
                if (component.getPsychoTicks() <= 0) {
                    component.startPsycho_time(GameConstants.getInTicks(0, 45), 0, true);
                    SREGameWorldComponent.getInstance(victim).refreshPsychoCount(true);
                    ci.cancel();
                }
            }
        }
    }
}
