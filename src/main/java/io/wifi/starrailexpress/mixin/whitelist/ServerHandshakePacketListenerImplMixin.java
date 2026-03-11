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
