package io.wifi.starrailexpress.mod_whitelist.client;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for mod whitelist templates
 * Stores a local copy of the player's current mod list for verification and comparison
 */
public class ClientModCache {
	private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("mod_whitelist");
	private static final Path CACHE_FILE = CACHE_DIR.resolve("mod_template.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	static {
		try {
			Files.createDirectories(CACHE_DIR);
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to create mod whitelist cache directory", e);
		}
	}

	/**
	 * Saves the current mod list as a local template
	 * Used for local verification and comparison purposes
	 *
	 * @param mods the list of mods to cache
	 */
	public static void saveCacheTemplate(List<ModInfo> mods) {
		try {
			JsonObject cacheData = new JsonObject();
			cacheData.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
			cacheData.addProperty("mod_count", mods.size());

			// Save mod list
			JsonArray modsArray = new JsonArray();
			for (ModInfo modInfo : mods) {
				JsonObject modObj = new JsonObject();
				modObj.addProperty("modId", modInfo.modId());
				modObj.addProperty("sha256", modInfo.sha256());
				modsArray.add(modObj);
			}
			cacheData.add("mods", modsArray);

			Files.writeString(CACHE_FILE, GSON.toJson(cacheData));
			MWLogger.LOGGER.debug("Saved mod whitelist template cache with {} mods", mods.size());
		} catch (IOException e) {
			MWLogger.LOGGER.warn("Failed to save mod whitelist cache template", e);
		}
	}

	/**
	 * Loads the cached mod template from local storage
	 *
	 * @return list of cached mods, or empty list if cache doesn't exist
	 */
	public static List<ModInfo> loadCacheTemplate() {
		try {
			if (!Files.exists(CACHE_FILE)) {
				return new ArrayList<>();
			}

			String fileContent = Files.readString(CACHE_FILE);
			JsonObject cacheData = GSON.fromJson(fileContent, JsonObject.class);

			if (cacheData == null || !cacheData.has("mods")) {
				return new ArrayList<>();
			}

			JsonArray modsArray = cacheData.getAsJsonArray("mods");
			List<ModInfo> mods = new ArrayList<>();

			for (int i = 0; i < modsArray.size(); i++) {
				JsonObject modObj = modsArray.get(i).getAsJsonObject();
				mods.add(new ModInfo(
						modObj.get("modId").getAsString(),
						modObj.get("sha256").getAsString()
				));
			}

			MWLogger.LOGGER.debug("Loaded mod whitelist template cache with {} mods", mods.size());
			return mods;
		} catch (IOException e) {
			MWLogger.LOGGER.warn("Failed to load mod whitelist cache template", e);
			return new ArrayList<>();
		}
	}

	/**
	 * Gets the timestamp when the cache was last updated
	 *
	 * @return timestamp string or "unknown" if cache doesn't exist
	 */
	public static String getCacheTimestamp() {
		try {
			if (!Files.exists(CACHE_FILE)) {
				return "unknown";
			}

			String fileContent = Files.readString(CACHE_FILE);
			JsonObject cacheData = GSON.fromJson(fileContent, JsonObject.class);

			if (cacheData != null && cacheData.has("timestamp")) {
				return cacheData.get("timestamp").getAsString();
			}
			return "unknown";
		} catch (IOException e) {
			return "unknown";
		}
	}

	/**
	 * Checks if the current mod list matches the cached template
	 *
	 * @param currentMods the current mods to compare
	 * @return true if they match, false otherwise
	 */
	public static boolean matchesCacheTemplate(List<ModInfo> currentMods) {
		List<ModInfo> cachedMods = loadCacheTemplate();

		if (cachedMods.size() != currentMods.size()) {
			return false;
		}

		// Sort both lists for comparison
		cachedMods.sort((a, b) -> a.modId().compareTo(b.modId()));
		List<ModInfo> sortedCurrent = new ArrayList<>(currentMods);
		sortedCurrent.sort((a, b) -> a.modId().compareTo(b.modId()));

		for (int i = 0; i < cachedMods.size(); i++) {
			ModInfo cached = cachedMods.get(i);
			ModInfo current = sortedCurrent.get(i);

			if (!cached.modId().equals(current.modId()) || !cached.sha256().equals(current.sha256())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Gets the cache file path (for debugging purposes)
	 *
	 * @return path to the cache file
	 */
	public static Path getCacheFilePath() {
		return CACHE_FILE;
	}
}
