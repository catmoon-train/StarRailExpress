package io.wifi.starrailexpress.mod_whitelist.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import io.wifi.starrailexpress.mod_whitelist.server.config.MismatchType;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage module for recording players kicked for mod violations
 * Maintains a separate log file with violation details
 */
public class ViolationRecordStorage {
	private static final Path VIOLATIONS_FILE = FabricLoader.getInstance().getConfigDir().resolve("mod_whitelist/violations.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	static {
		try {
			// Ensure directory exists
			Files.createDirectories(VIOLATIONS_FILE.getParent());
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to create violations storage directory", e);
		}
	}

	/**
	 * Records a player violation with detailed information
	 *
	 * @param playerId    the player's name/ID
	 * @param playerUUID  the player's UUID
	 * @param ipAddress   the player's IP address
	 * @param macAddress  the player's MAC address
	 * @param violations  list of violated mods with their mismatch types
	 */
	public static void recordViolation(String playerId, UUID playerUUID, String ipAddress, String macAddress, 
									 List<Pair<String, MismatchType>> violations) {
		try {
			// Ensure directory exists
			Files.createDirectories(VIOLATIONS_FILE.getParent());
			
			// Load existing violations or create new structure
			JsonObject violationsData;
			if (Files.exists(VIOLATIONS_FILE)) {
				String fileContent = Files.readString(VIOLATIONS_FILE);
				violationsData = GSON.fromJson(fileContent, JsonObject.class);
				if (violationsData == null) {
					violationsData = new JsonObject();
				}
			} else {
				violationsData = new JsonObject();
				violationsData.add("violations", new JsonArray());
			}
			
			// Create violation entry
			JsonObject violationEntry = new JsonObject();
			violationEntry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
			violationEntry.addProperty("player_id", playerId);
			violationEntry.addProperty("player_uuid", playerUUID.toString());
			violationEntry.addProperty("ip_address", ipAddress != null ? ipAddress : "unknown");
			violationEntry.addProperty("mac_address", macAddress != null ? macAddress : "unknown");
			
			// Add violation details
			JsonArray violationsArray = new JsonArray();
			for (Pair<String, MismatchType> violation : violations) {
				JsonObject violationDetail = new JsonObject();
				violationDetail.addProperty("mod_id", violation.getLeft());
				violationDetail.addProperty("violation_type", violation.getRight().name());
				violationDetail.addProperty("description", getViolationDescription(violation.getRight()));
				violationsArray.add(violationDetail);
			}
			violationEntry.add("violations", violationsArray);
			
			// Add to violations array
			JsonArray violationsArrayMain = violationsData.getAsJsonArray("violations");
			if (violationsArrayMain == null) {
				violationsArrayMain = new JsonArray();
				violationsData.add("violations", violationsArrayMain);
			}
			violationsArrayMain.add(violationEntry);
			
			// Limit to last 1000 violations to prevent file from growing too large
			if (violationsArrayMain.size() > 1000) {
				JsonArray newViolations = new JsonArray();
				for (int i = violationsArrayMain.size() - 1000; i < violationsArrayMain.size(); i++) {
					newViolations.add(violationsArrayMain.get(i));
				}
				violationsData.add("violations", newViolations);
			}
			
			// Write to file
			Files.writeString(VIOLATIONS_FILE, GSON.toJson(violationsData));
			
			MWLogger.LOGGER.info("Recorded violation for player {} (UUID: {}) - {} violations", 
					playerId, playerUUID, violations.size());
					
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to record violation for player {}", playerId, e);
		}
	}

	/**
	 * Gets human-readable description for violation type
	 *
	 * @param mismatchType the type of mismatch
	 * @return descriptive string
	 */
	private static String getViolationDescription(MismatchType mismatchType) {
		return switch (mismatchType) {
			case UNINSTALLED_BUT_SHOULD_INSTALL -> "Missing required mod";
			case INSTALLED_BUT_SHOULD_NOT_INSTALL -> "Illegal mod installed";
		};
	}

	/**
	 * Gets all recorded violations
	 *
	 * @return list of all violation records
	 */
	public static List<ViolationRecord> getAllViolations() {
		List<ViolationRecord> records = new ArrayList<>();
		
		try {
			if (!Files.exists(VIOLATIONS_FILE)) {
				return records;
			}
			
			String fileContent = Files.readString(VIOLATIONS_FILE);
			JsonObject violationsData = GSON.fromJson(fileContent, JsonObject.class);
			
			if (violationsData == null || !violationsData.has("violations")) {
				return records;
			}
			
			JsonArray violationsArray = violationsData.getAsJsonArray("violations");
			for (int i = 0; i < violationsArray.size(); i++) {
				JsonObject violationObj = violationsArray.get(i).getAsJsonObject();
				ViolationRecord record = parseViolationRecord(violationObj);
				if (record != null) {
					records.add(record);
				}
			}
		} catch (IOException e) {
			MWLogger.LOGGER.error("Failed to read violations file", e);
		}
		
		return records;
	}

	/**
	 * Gets violations for a specific player UUID
	 *
	 * @param playerUUID the player's UUID
	 * @return list of violations for that player
	 */
	public static List<ViolationRecord> getPlayerViolations(UUID playerUUID) {
		List<ViolationRecord> records = new ArrayList<>();
		List<ViolationRecord> allViolations = getAllViolations();
		
		for (ViolationRecord record : allViolations) {
			if (record.playerUUID.equals(playerUUID)) {
				records.add(record);
			}
		}
		
		return records;
	}

	/**
	 * Parses a JsonObject into a ViolationRecord
	 *
	 * @param violationObj the JSON object containing violation data
	 * @return parsed ViolationRecord or null if parsing failed
	 */
	private static ViolationRecord parseViolationRecord(JsonObject violationObj) {
		try {
			String timestamp = violationObj.get("timestamp").getAsString();
			String playerId = violationObj.get("player_id").getAsString();
			UUID playerUUID = UUID.fromString(violationObj.get("player_uuid").getAsString());
			String ipAddress = violationObj.get("ip_address").getAsString();
			String macAddress = violationObj.get("mac_address").getAsString();
			
			List<ViolationDetail> violations = new ArrayList<>();
			JsonArray violationsArray = violationObj.getAsJsonArray("violations");
			if (violationsArray != null) {
				for (int j = 0; j < violationsArray.size(); j++) {
					JsonObject violationDetailObj = violationsArray.get(j).getAsJsonObject();
					String modId = violationDetailObj.get("mod_id").getAsString();
					MismatchType violationType = MismatchType.valueOf(violationDetailObj.get("violation_type").getAsString());
					String description = violationDetailObj.get("description").getAsString();
					
					violations.add(new ViolationDetail(modId, violationType, description));
				}
			}
			
			return new ViolationRecord(timestamp, playerId, playerUUID, ipAddress, macAddress, violations);
		} catch (Exception e) {
			MWLogger.LOGGER.warn("Failed to parse violation record", e);
			return null;
		}
	}

	/**
	 * Gets the path to the violations file (for admin access)
	 *
	 * @return path to violations.json
	 */
	public static Path getViolationsFilePath() {
		return VIOLATIONS_FILE;
	}

	/**
	 * Represents a single violation record
	 */
	public static class ViolationRecord {
		public final String timestamp;
		public final String playerId;
		public final UUID playerUUID;
		public final String ipAddress;
		public final String macAddress;
		public final List<ViolationDetail> violations;

		public ViolationRecord(String timestamp, String playerId, UUID playerUUID, 
							 String ipAddress, String macAddress, List<ViolationDetail> violations) {
			this.timestamp = timestamp;
			this.playerId = playerId;
			this.playerUUID = playerUUID;
			this.ipAddress = ipAddress;
			this.macAddress = macAddress;
			this.violations = violations;
		}
	}

	/**
	 * Represents a single mod violation detail
	 */
	public static class ViolationDetail {
		public final String modId;
		public final MismatchType violationType;
		public final String description;

		public ViolationDetail(String modId, MismatchType violationType, String description) {
			this.modId = modId;
			this.violationType = violationType;
			this.description = description;
		}
	}
}