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

package net.exmo.mixin.client;

import net.exmo.sre.mod_whitelist.client.network.ModWhitelistClientNetworkHandler;
import net.irisshaders.iris.apiimpl.IrisApiV0ConfigImpl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IrisApiV0ConfigImpl.class)
public class IrisShaderReload {
    @Inject(method = "setShadersEnabledAndApply", at = @At("TAIL"))
    private static void setShadersEnabledAndApply(boolean enabled, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(ModWhitelistClientNetworkHandler::sendShaderPackWhitelistPayload);
        }
    }
}
