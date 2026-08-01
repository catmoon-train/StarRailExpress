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
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModResourcePackUtil.class)
public class OnResourcePackReload {
    @Inject(
            method = "refreshAutoEnabledPacks",
            at = @At("TAIL")
    )
    private static void refreshAutoEnabledPacks(List<Pack> enabledProfiles, Map<String, Pack> allProfiles, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(ModWhitelistClientNetworkHandler::sendResourcePackWhitelistPayload);
        }
    }
}
