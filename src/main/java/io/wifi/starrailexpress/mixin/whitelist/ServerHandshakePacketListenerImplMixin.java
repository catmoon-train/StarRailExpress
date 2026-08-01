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

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * DEPRECATED: This mixin is no longer actively used.
 * Mod whitelist validation is now done in the game phase (after login) instead of during handshake.
 * This prevents issues with VC proxies and improves compatibility.
 * 
 * Kept for reference only.
 */
@Mixin(net.minecraft.server.network.ServerHandshakePacketListenerImpl.class)
public class ServerHandshakePacketListenerImplMixin {
	@Shadow @Final
	private Connection connection;

	// All mod whitelist validation logic moved to ModWhitelistServerNetworkHandler
	// which operates on the game phase instead of the handshake phase.
}
