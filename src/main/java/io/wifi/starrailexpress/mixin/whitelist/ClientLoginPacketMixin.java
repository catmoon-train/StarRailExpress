package io.wifi.starrailexpress.mixin.whitelist;

import io.wifi.starrailexpress.mod_whitelist.client.ModWhitelistClient;
import io.wifi.starrailexpress.mod_whitelist.client.network.ModWhitelistClientNetworkHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
		ModWhitelistClientNetworkHandler.sendModWhitelistPayload();
	}

	// All mod whitelist logic moved to ModWhitelistClientNetworkHandler
	// and ModWhitelistServerNetworkHandler which operate on the game phase
	// instead of the handshake phase.
}
