package io.wifi.starrailexpress.mixin.whitelist;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.mod_whitelist.client.ModWhitelistClient;
import io.wifi.starrailexpress.mod_whitelist.client.network.ModWhitelistClientNetworkHandler;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
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
