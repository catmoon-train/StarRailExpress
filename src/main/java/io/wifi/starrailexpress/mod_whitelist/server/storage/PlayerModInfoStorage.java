package io.wifi.starrailexpress.mod_whitelist.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wifi.starrailexpress.mod_whitelist.common.ModInfo;
import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Storage module for player mod information
 * Tracks mod lists and their SHA256 hashes for each player session
 */
public class PlayerModInfoStorage {
	private static final Path STORAGE_DIR = FabricLoader.getInstance().getConfigDir().resolve("mod_whitelist/player_mods");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	static {
		try {
			Files.createDirectories(STORAGE_DIR);
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to create storage directory for player mod info", e);
		}
	}

	/**
	 * Stores mod information for a player with network details
	 *
	 * @param playerUUID  the UUID of the player
	 * @param playerName  the name of the player
	 * @param mods        the list of mods with SHA256 hashes
	 * @param ipAddress   the player's IP address
	 * @param macAddress  the player's MAC address
	 */
	public static void storePlayerMods(UUID playerUUID, String playerName, List<ModInfo> mods, String ipAddress, String macAddress) {
		try {
			// Ensure directory exists before writing
			Files.createDirectories(STORAGE_DIR);
			
			Path playerFile = STORAGE_DIR.resolve(playerUUID + ".json");
			
			// Load existing data or create new
			JsonObject playerData;
			if (Files.exists(playerFile)) {
				String fileContent = Files.readString(playerFile);
				playerData = GSON.fromJson(fileContent, JsonObject.class);
				if (playerData == null) {
					playerData = new JsonObject();
				}
			} else {
				playerData = new JsonObject();
			}
			
			// Update player metadata
			playerData.addProperty("uuid", playerUUID.toString());
			playerData.addProperty("name", playerName);
			playerData.addProperty("last_join", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
			
			// Create entry for this session
			JsonObject sessionEntry = new JsonObject();
			sessionEntry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
			sessionEntry.addProperty("ip", ipAddress != null ? ipAddress : "unknown");
			sessionEntry.addProperty("mac", macAddress != null ? macAddress : "unknown");
			
			// Add mod list
			JsonArray modsArray = new JsonArray();
			for (ModInfo modInfo : mods) {
				JsonObject modObj = new JsonObject();
				modObj.addProperty("modId", modInfo.modId());
				modObj.addProperty("sha256", modInfo.sha256());
				modsArray.add(modObj);
			}
			sessionEntry.add("mods", modsArray);
			
			// Store in sessions array
			if (!playerData.has("sessions")) {
				playerData.add("sessions", new JsonArray());
			}
			JsonArray sessions = playerData.getAsJsonArray("sessions");
			sessions.add(sessionEntry);
			
			// Limit to last 100 sessions to prevent file from growing too large
			if (sessions.size() > 100) {
				JsonArray newSessions = new JsonArray();
				for (int i = sessions.size() - 100; i < sessions.size(); i++) {
					newSessions.add(sessions.get(i));
				}
				playerData.add("sessions", newSessions);
			}
			
			// Write to file
			Files.writeString(playerFile, GSON.toJson(playerData));
			
			MWLogger.LOGGER.debug("Stored mod info for player {} (UUID: {}) from IP: {} MAC: {}", 
					playerName, playerUUID, ipAddress, macAddress);
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to store mod info for player {}", playerName, e);
		}
	}

	/**
	 * Stores mod information for a player (legacy method without network info)
	 *
	 * @param playerUUID  the UUID of the player
	 * @param playerName  the name of the player
	 * @param mods        the list of mods with SHA256 hashes
	 */
	public static void storePlayerMods(UUID playerUUID, String playerName, List<ModInfo> mods) {
		storePlayerMods(playerUUID, playerName, mods, "unknown", "unknown");
	}

	/**
	 * Gets the stored mod information for a player
	 *
	 * @param playerUUID the UUID of the player
	 * @return a list of ModInfo from the latest session, or empty list if not found
	 */
	public static List<ModInfo> getPlayerMods(UUID playerUUID) {
		try {
			Path playerFile = STORAGE_DIR.resolve(playerUUID + ".json");
			
			if (!Files.exists(playerFile)) {
				return new ArrayList<>();
			}
			
			String fileContent = Files.readString(playerFile);
			JsonObject playerData = GSON.fromJson(fileContent, JsonObject.class);
			
			if (playerData == null || !playerData.has("sessions")) {
				return new ArrayList<>();
			}
			
			JsonArray sessions = playerData.getAsJsonArray("sessions");
			if (sessions.isEmpty()) {
				return new ArrayList<>();
			}
			
			// Get the latest session
			JsonObject latestSession = sessions.get(sessions.size() - 1).getAsJsonObject();
			JsonArray modsArray = latestSession.getAsJsonArray("mods");
			
			List<ModInfo> mods = new ArrayList<>();
			for (int i = 0; i < modsArray.size(); i++) {
				JsonObject modObj = modsArray.get(i).getAsJsonObject();
				mods.add(new ModInfo(
						modObj.get("modId").getAsString(),
						modObj.get("sha256").getAsString()
				));
			}
			
			return mods;
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to read mod info for player {}", playerUUID, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Gets all stored sessions for a player
	 *
	 * @param playerUUID the UUID of the player
	 * @return a list of all stored sessions with timestamps and mod lists
	 */
	public static List<Map<String, Object>> getPlayerSessions(UUID playerUUID) {
		try {
			Path playerFile = STORAGE_DIR.resolve(playerUUID + ".json");
			
			if (!Files.exists(playerFile)) {
				return new ArrayList<>();
			}
			
			String fileContent = Files.readString(playerFile);
			JsonObject playerData = GSON.fromJson(fileContent, JsonObject.class);
			
			if (playerData == null || !playerData.has("sessions")) {
				return new ArrayList<>();
			}
			
			JsonArray sessions = playerData.getAsJsonArray("sessions");
			List<Map<String, Object>> result = new ArrayList<>();
			
			for (int i = 0; i < sessions.size(); i++) {
				JsonObject session = sessions.get(i).getAsJsonObject();
				Map<String, Object> sessionMap = new HashMap<>();
				sessionMap.put("timestamp", session.get("timestamp").getAsString());
				sessionMap.put("modCount", session.getAsJsonArray("mods").size());
				result.add(sessionMap);
			}
			
			return result;
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to read sessions for player {}", playerUUID, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Checks if a player's current mods match their last recorded mods
	 *
	 * @param playerUUID  the UUID of the player
	 * @param currentMods the current mods to compare
	 * @return true if the mods match, false otherwise
	 */
	public static boolean compareWithLastSession(UUID playerUUID, List<ModInfo> currentMods) {
		List<ModInfo> lastMods = getPlayerMods(playerUUID);
		
		if (lastMods.size() != currentMods.size()) {
			return false;
		}
		
		// Sort both lists for comparison
		lastMods.sort(Comparator.comparing(ModInfo::modId));
		List<ModInfo> sortedCurrent = new ArrayList<>(currentMods);
		sortedCurrent.sort(Comparator.comparing(ModInfo::modId));
		
		for (int i = 0; i < lastMods.size(); i++) {
			ModInfo last = lastMods.get(i);
			ModInfo current = sortedCurrent.get(i);
			
			if (!last.modId().equals(current.modId()) || !last.sha256().equals(current.sha256())) {
				return false;
			}
		}
		
		return true;
	}
}
