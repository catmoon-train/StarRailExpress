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

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.exmo.sre.repair.state.RepairModeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.player.Player.class)
public class SpawnMixin {
    @Inject(at = @At("HEAD"), method = "die", cancellable = true)
    public void onDeath(DamageSource damageSource, CallbackInfo ci) {
        final var player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && RepairModeState.downPlayer(serverPlayer)) {
            ci.cancel();
            return;
        }
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            final var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent != null) {
                if (gameWorldComponent.isRunning()) {
                    ci.cancel();
                    player.setHealth(20.0F);
                    GameUtils.killPlayer(player, false, player.getLastAttacker() instanceof Player killerPlayer ? killerPlayer : null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                }
            }
        }
    }
}
