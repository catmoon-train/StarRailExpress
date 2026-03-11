package io.wifi.starrailexpress.mod_whitelist.client.network;

import io.wifi.starrailexpress.mod_whitelist.client.ClientModCache;
import io.wifi.starrailexpress.mod_whitelist.client.ModWhitelistClient;
import io.wifi.starrailexpress.mod_whitelist.common.ModInfo;
import io.wifi.starrailexpress.mod_whitelist.common.network.ModWhitelistPayload;
import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import io.wifi.starrailexpress.mod_whitelist.common.utils.SHA256Utils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side network handler for mod whitelist system
 * Sends mod information when player joins the game
 */
public class ModWhitelistClientNetworkHandler {

	/**
	 * Initializes the client network handler
	 * Called when client mod initializes
	 */
	public static void initializeClient() {
		// Network handler is now triggered via mixin injection
		// when ClientboundLoginPacket is handled
	}

	/**
	 * Sends the mod whitelist payload to the server
	 * This includes mod IDs and their SHA256 hashes
	 * Also saves a local cache copy for verification
	 */
	public static void sendModWhitelistPayload() {
		try {
			List<ModInfo> modInfoList = generateModInfoList();
			ModWhitelistPayload payload = new ModWhitelistPayload(modInfoList);
			ClientPlayNetworking.send(payload);
			
			// Save a local copy for verification
			ClientModCache.saveCacheTemplate(modInfoList);
			
			MWLogger.LOGGER.info("Sent mod whitelist payload to server with {} mods", modInfoList.size());
		} catch (Exception e) {
			MWLogger.LOGGER.error("Failed to send mod whitelist payload", e);
		}
	}

	/**
	 * Generates a list of ModInfo for all loaded mods with their SHA256 hashes
	 *
	 * @return list of ModInfo objects
	 */
	public static List<ModInfo> generateModInfoList() {
		List<ModInfo> modInfoList = new ArrayList<>();
		
		for (String modId : ModWhitelistClient.mods) {
			// Create a simple hash based on mod ID and version
			// In a real scenario, you might want to hash the actual jar file
			String modInfo = modId + ":" + getModVersion(modId);
			String sha256Hash = SHA256Utils.hash(modInfo);
			
			modInfoList.add(new ModInfo(modId, sha256Hash));
		}
		
		return modInfoList;
	}

	/**
	 * Gets the version of a mod
	 *
	 * @param modId the mod ID
	 * @return the mod version or "unknown"
	 */
	private static String getModVersion(String modId) {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(modId)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
