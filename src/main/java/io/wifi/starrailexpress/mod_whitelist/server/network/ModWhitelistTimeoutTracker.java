package io.wifi.starrailexpress.mod_whitelist.server.network;

import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Tracks mod whitelist payload timeout for players
 * Disconnects players who don't send their mod list within the timeout period
 */
public class ModWhitelistTimeoutTracker {
	private static final long TIMEOUT_MS = 7500; // 5 seconds
	private static final Map<UUID, Long> PLAYER_TIMEOUT_MAP = new HashMap<>();

	/**
	 * Registers a player for timeout checking
	 * Should be called when player enters the game (after login)
	 *
	 * @param playerUUID the UUID of the player
	 */
	public static void registerPlayer(UUID playerUUID) {
		PLAYER_TIMEOUT_MAP.put(playerUUID, System.currentTimeMillis());
	}

	/**
	 * Marks a player as having sent their mod list (removes from timeout tracking)
	 *
	 * @param playerUUID the UUID of the player
	 */
	public static void clearTimeout(UUID playerUUID) {
		PLAYER_TIMEOUT_MAP.remove(playerUUID);
	}

	/**
	 * Checks for timed-out players and disconnects them
	 * Should be called periodically from server tick event
	 *
	 * @param players list of currently connected players
	 */
	public static void checkTimeouts(@NotNull List<ServerPlayer> players) {
		long currentTime = System.currentTimeMillis();
		List<UUID> timedOutPlayers = new ArrayList<>();

		// Find timed-out players
		for (Map.Entry<UUID, Long> entry : PLAYER_TIMEOUT_MAP.entrySet()) {
			if (currentTime - entry.getValue() > TIMEOUT_MS) {
				timedOutPlayers.add(entry.getKey());
			}
		}

		// Disconnect timed-out players
		for (UUID playerUUID : timedOutPlayers) {
			ServerPlayer player = null;
			for (ServerPlayer p : players) {
				if (p.getUUID().equals(playerUUID)) {
					player = p;
					break;
				}
			}

			if (player != null) {
				Component reason = Component.literal("Mod whitelist verification timeout");
				player.connection.disconnect(reason);
				MWLogger.LOGGER.warn("Player {} disconnected due to mod whitelist timeout", 
						player.getName().getString());
			}

			PLAYER_TIMEOUT_MAP.remove(playerUUID);
		}
	}

	/**
	 * Removes a player from timeout tracking (e.g., when they disconnect)
	 *
	 * @param playerUUID the UUID of the player
	 */
	public static void removePlayer(UUID playerUUID) {
		PLAYER_TIMEOUT_MAP.remove(playerUUID);
	}

	/**
	 * Gets the number of players currently waiting for mod whitelist verification
	 *
	 * @return number of players in timeout tracking
	 */
	public static int getPendingCount() {
		return PLAYER_TIMEOUT_MAP.size();
	}
}
