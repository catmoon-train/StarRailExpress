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

package io.wifi.starrailexpress.mixin.client;

import com.mojang.text2speech.Narrator;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameNarrator.class)
public class GameNarratorMixin {
    @Shadow
    @Final
    private Narrator narrator;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "sayChat", at = @At("HEAD"), cancellable = true)
    private void disablesayChat(CallbackInfo cir) {
        if (!SREClient.isInLobby) {
            cir.cancel();
        }
    }

    @Inject(method = "getStatus", at = @At("HEAD"), cancellable = true)
    private void disableStatus(CallbackInfoReturnable<NarratorStatus> cir) {
        if (!SREClient.isInLobby) {
            NarratorStatus status = (NarratorStatus) this.minecraft.options.narrator().get();

            if (!status.equals(NarratorStatus.OFF) && !status.equals(NarratorStatus.SYSTEM)) {
                String string = Component.translatable("warning.narrator").getString();
                if (this.narrator != null)
                    this.narrator.say(string, false);
            }
            cir.setReturnValue(NarratorStatus.OFF);
            cir.cancel();
        }
    }
}
