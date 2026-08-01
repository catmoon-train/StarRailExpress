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

package io.wifi.starrailexpress.mixin.whitelist;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
import net.exmo.sre.mod_whitelist.client.ModWhitelistClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static io.wifi.starrailexpress.client.SREClient.cachedHighLightMap;

/**
 * DEPRECATED: This mixin is no longer actively used.
 * Mod whitelist information is now sent via ModWhitelistPayload after login instead of during handshake.
 * This prevents issues with VC proxies and improves compatibility.
 * 
 * Kept for reference only.
 */
@Mixin(ClientPacketListener.class)
public class ClientLoginPacketMixin {
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void handleLogin(ClientboundLoginPacket clientboundLoginPacket, CallbackInfo ci) {
		ModWhitelistClient.onInitializeClient();

		ClientPlayNetworking.send(new ModVersionPacket(SRE.modPacketVersion));
		SRE.LOGGER.info("Send client version {} to verify.", SRE.modPacketVersion);
		cachedHighLightMap.clear();
	}

	// All mod whitelist logic moved to ModWhitelistClientNetworkHandler
	// and ModWhitelistServerNetworkHandler which operate on the game phase
	// instead of the handshake phase.
}
