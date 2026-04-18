package net.exmo.sre.mod_whitelist.client.network;

import net.exmo.sre.mod_whitelist.client.ClientModCache;
import net.exmo.sre.mod_whitelist.client.ModWhitelistClient;
import net.exmo.sre.mod_whitelist.common.ModInfo;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.exmo.sre.mod_whitelist.common.utils.SHA256Utils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side network handler for mod whitelist system
 * Sends mod information when player joins the game
 */
public class ModWhitelistClientNetworkHandler {
	
	private static boolean serverSyncHashValues = false; // Default to false

	/**
	 * Handles incoming ModWhitelistConfigPayload from server
	 */
	public static void handleModWhitelistConfigPayload(ModWhitelistConfigPayload payload, ClientPlayNetworking.Context context) {
		serverSyncHashValues = payload.syncHashValues();
		MWLogger.LOGGER.debug("Received mod whitelist config from server (sync hashes: {})", serverSyncHashValues);
	}

	/**
	 * Sends the mod whitelist payload to the server
	 * This includes mod IDs and their SHA256 hashes (if enabled)
	 * Also saves a local cache copy for verification
	 */
	public static void sendModWhitelistPayload() {
		try {
			List<ModInfo> modInfoList = generateModInfoList(serverSyncHashValues);
			ModWhitelistPayload payload = new ModWhitelistPayload(modInfoList, serverSyncHashValues);
			ClientPlayNetworking.send(payload);
			
			// Save a local copy for verification
			ClientModCache.saveCacheTemplate(modInfoList);
			
			MWLogger.LOGGER.info("Sent mod whitelist payload to server with {} mods (hashes: {})", modInfoList.size(), serverSyncHashValues ? "included" : "excluded");
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
		return generateModInfoList(true);
	}

	/**
	 * Generates a list of ModInfo for all loaded mods
	 *
	 * @param includeHashes whether to include SHA256 hashes or use dummy values
	 * @return list of ModInfo objects
	 */
	public static List<ModInfo> generateModInfoList(boolean includeHashes) {
		List<ModInfo> modInfoList = new ArrayList<>();
		
		for (String modId : ModWhitelistClient.mods) {
			String sha256Hash;
			if (includeHashes) {
				// Create a hash based on mod ID and version
				String modInfo = modId + ":" + getModVersion(modId);
				sha256Hash = SHA256Utils.hash(modInfo);
			} else {
				// Use a dummy hash for this mod's own mods only
				if (modId.equals("starrailexpress") || modId.startsWith("starrailexpress") ||
					modId.equals("noellesroles") || modId.startsWith("noellesroles") ||
					modId.equals("stupid_express") || modId.startsWith("stupid_express") ||
					modId.equals("harpymodloader") || modId.startsWith("harpymodloader")) {
					String modInfo = modId + ":" + getModVersion(modId);
					sha256Hash = SHA256Utils.hash(modInfo);
				} else {
					sha256Hash = "excluded"; // Dummy value for other mods
				}
			}
			
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
