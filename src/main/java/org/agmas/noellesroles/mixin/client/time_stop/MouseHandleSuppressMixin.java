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

package org.agmas.noellesroles.mixin.client.time_stop;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandleSuppressMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void noe$restrainMouse(double d, CallbackInfo ci) {

        if (Minecraft.getInstance() == null)
            return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.isPlayerAliveAndInSurvival()
                && player.hasEffect((ModEffects.TIME_STOP))) {
            if (TimeStopEffect.clientCanMovePlayers.contains(player.getUUID())) {
                return;
            }
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void noe$restrainScroll(long l, double d, double e, CallbackInfo ci) {

        if (Minecraft.getInstance() == null)
            return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.isPlayerAliveAndInSurvival()
                && player.hasEffect((ModEffects.TIME_STOP))) {
            if (TimeStopEffect.clientCanMovePlayers.contains(player.getUUID())) {
                return;
            }
            ci.cancel();
        }
    }
}
