package net.exmo.sre.mod_whitelist.client.network;

import net.exmo.sre.mod_whitelist.client.ClientModCache;
import net.exmo.sre.mod_whitelist.client.ModWhitelistClient;
import net.exmo.sre.mod_whitelist.common.ModInfo;
import net.exmo.sre.mod_whitelist.common.ResourcePackInfo;
import net.exmo.sre.mod_whitelist.common.ShaderPackInfo;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.network.ResourcePackWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.network.ShaderPackWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.exmo.sre.mod_whitelist.common.utils.SHA256Utils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.repository.PackRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
			List<ResourcePackInfo> resourcePackInfoList = generateResourcePackInfoList(serverSyncHashValues);
			List<ShaderPackInfo> shaderPackInfoList = generateShaderPackInfoList(serverSyncHashValues);
			
			ModWhitelistPayload payload = new ModWhitelistPayload(
				modInfoList, 
				serverSyncHashValues,
				resourcePackInfoList,
				shaderPackInfoList
			);
			ClientPlayNetworking.send(payload);
			sendResourcePackWhitelistPayload();
			sendShaderPackWhitelistPayload();
			
			// Save a local copy for verification
			ClientModCache.saveCacheTemplate(modInfoList);
			
			MWLogger.LOGGER.info("Sent mod whitelist payload to server with {} mods, {} resource packs, {} shader packs (hashes: {})", 
				modInfoList.size(), resourcePackInfoList.size(), shaderPackInfoList.size(), serverSyncHashValues ? "included" : "excluded");
		} catch (Exception e) {
			MWLogger.LOGGER.error("Failed to send mod whitelist payload", e);
		}
	}

	public static void sendResourcePackWhitelistPayload() {
		try {
			if (!canSend(ResourcePackWhitelistPayload.ID)) {
				return;
			}
			List<ResourcePackInfo> resourcePackInfoList = generateResourcePackInfoList(serverSyncHashValues);
			ClientPlayNetworking.send(new ResourcePackWhitelistPayload(resourcePackInfoList, serverSyncHashValues));
			MWLogger.LOGGER.info("Sent resource pack whitelist payload to server with {} packs (hashes: {})",
					resourcePackInfoList.size(), serverSyncHashValues ? "included" : "excluded");
		} catch (Exception e) {
			MWLogger.LOGGER.error("Failed to send resource pack whitelist payload", e);
		}
	}

	public static void sendShaderPackWhitelistPayload() {
		try {
			if (!canSend(ShaderPackWhitelistPayload.ID)) {
				return;
			}
			List<ShaderPackInfo> shaderPackInfoList = generateShaderPackInfoList(serverSyncHashValues);
			ClientPlayNetworking.send(new ShaderPackWhitelistPayload(shaderPackInfoList, serverSyncHashValues));
			MWLogger.LOGGER.info("Sent shader pack whitelist payload to server with {} packs (hashes: {})",
					shaderPackInfoList.size(), serverSyncHashValues ? "included" : "excluded");
		} catch (Exception e) {
			MWLogger.LOGGER.error("Failed to send shader pack whitelist payload", e);
		}
	}

	private static boolean canSend(CustomPacketPayload.Type<?> type) {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.getConnection() != null && ClientPlayNetworking.canSend(type);
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
				sha256Hash = getModHash(modId);
			} else {
				// Use a dummy hash for this mod's own mods only
				if (modId.equals("starrailexpress") || modId.startsWith("starrailexpress") ||
					modId.equals("noellesroles") || modId.startsWith("noellesroles") ||
					modId.equals("stupid_express") || modId.startsWith("stupid_express") ||
					modId.equals("harpymodloader") || modId.startsWith("harpymodloader")) {
					sha256Hash = getModHash(modId);
				} else {
					sha256Hash = "excluded"; // Dummy value for other mods
				}
			}
			
			modInfoList.add(new ModInfo(modId, sha256Hash));
		}
		
		return modInfoList;
	}

	/**
	 * Generates a list of ResourcePackInfo for all enabled resource packs
	 *
	 * @param includeHashes whether to include SHA256 hashes or use dummy values
	 * @return list of ResourcePackInfo objects
	 */
	public static List<ResourcePackInfo> generateResourcePackInfoList(boolean includeHashes) {
		List<ResourcePackInfo> resourcePackInfoList = new ArrayList<>();
		
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null) {
				PackRepository resourcePacks = mc.getResourcePackRepository();
				if (resourcePacks != null) {
					// Get enabled resource packs
					Collection<String> enabledPacks = resourcePacks.getSelectedIds();
					
					for (String packId : enabledPacks) {
						Path packPath = resolveResourcePackPath(packId);
						if (packPath == null) {
							continue;
						}
						String sha256Hash;
						if (includeHashes) {
							sha256Hash = SHA256Utils.hash(packPath);
						} else {
							sha256Hash = "excluded"; // Dummy value
						}
						resourcePackInfoList.add(new ResourcePackInfo(packId, sha256Hash));
					}
				}
			}
		} catch (Exception e) {
			MWLogger.LOGGER.warn("Failed to collect resource pack info: {}", e.getMessage());
		}
		
		return resourcePackInfoList;
	}

	/**
	 * Generates a list of ShaderPackInfo for the current shader pack
	 *
	 * @param includeHashes whether to include SHA256 hashes or use dummy values
	 * @return list of ShaderPackInfo objects
	 */
	public static List<ShaderPackInfo> generateShaderPackInfoList(boolean includeHashes) {
		List<ShaderPackInfo> shaderPackInfoList = new ArrayList<>();
		
		try {
			Optional<String> currentShaderPack = getCurrentShaderPackName();
			if (currentShaderPack.isPresent()) {
				String packName = currentShaderPack.get();
				Path shaderPackPath = resolveShaderPackPath(packName);
				String sha256Hash;
				if (includeHashes && shaderPackPath != null) {
					sha256Hash = SHA256Utils.hash(shaderPackPath);
				} else if (includeHashes) {
					sha256Hash = SHA256Utils.hash(packName);
				} else {
					sha256Hash = "excluded"; // Dummy value
				}
				shaderPackInfoList.add(new ShaderPackInfo(packName, sha256Hash));
			}
		} catch (Exception e) {
			MWLogger.LOGGER.warn("Failed to collect shader pack info: {}", e.getMessage());
		}
		
		return shaderPackInfoList;
	}

	private static String getModHash(String modId) {
		try {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
			if (container.isPresent() && !container.get().getRootPaths().isEmpty()) {
				return SHA256Utils.hashPaths(container.get().getRootPaths());
			}
		} catch (Exception e) {
			MWLogger.LOGGER.warn("Failed to hash mod {} from root paths: {}", modId, e.getMessage());
		}
		String modInfo = modId + ":" + getModVersion(modId);
		return SHA256Utils.hash(modInfo);
	}

	private static Path resolveResourcePackPath(String packId) {
		if (packId == null || packId.isBlank()) {
			return null;
		}
		String fileName = packId;
		if (fileName.startsWith("file/")) {
			fileName = fileName.substring("file/".length());
		} else if (!fileName.endsWith(".zip")) {
			return null;
		}
		fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
		Path resourcePackDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").normalize();
		Path path = resourcePackDir.resolve(fileName).normalize();
		if (!path.startsWith(resourcePackDir) || !Files.exists(path)) {
			return null;
		}
		return path;
	}

	private static Optional<String> getCurrentShaderPackName() {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return Optional.empty();
		}
		try {
			return Iris.getIrisConfig().getShaderPackName()
					.filter(name -> !name.isBlank())
					.filter(name -> !"OFF".equalsIgnoreCase(name));
		} catch (Throwable throwable) {
			MWLogger.LOGGER.warn("Failed to read Iris shader pack config: {}", throwable.getMessage());
			return Optional.empty();
		}
	}

	private static Path resolveShaderPackPath(String packName) {
		Path shaderPackDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").normalize();
		Path path = shaderPackDir.resolve(packName).normalize();
		if (path.startsWith(shaderPackDir) && Files.exists(path)) {
			return path;
		}
		Path zipPath = shaderPackDir.resolve(packName + ".zip").normalize();
		if (zipPath.startsWith(shaderPackDir) && Files.exists(zipPath)) {
			return zipPath;
		}
		return null;
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
