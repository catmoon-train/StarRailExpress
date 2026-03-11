package io.wifi.starrailexpress.mod_whitelist.server.network;

import io.wifi.starrailexpress.mod_whitelist.common.ModInfo;
import io.wifi.starrailexpress.mod_whitelist.common.network.ModWhitelistPayload;
import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import io.wifi.starrailexpress.mod_whitelist.server.config.MWServerConfig;
import io.wifi.starrailexpress.mod_whitelist.server.config.MismatchType;
import io.wifi.starrailexpress.mod_whitelist.server.storage.PlayerModInfoStorage;
import io.wifi.starrailexpress.mod_whitelist.server.storage.ViolationRecordStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.stream.Collectors;

import static io.wifi.starrailexpress.mod_whitelist.server.config.MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL;
import static io.wifi.starrailexpress.mod_whitelist.server.config.MismatchType.UNINSTALLED_BUT_SHOULD_INSTALL;

/**
 * Server-side network handler for mod whitelist system
 * Receives and validates mod information from players
 * Also manages timeout for players who don't send their mod list
 */
public class ModWhitelistServerNetworkHandler {

	/**
	 * Initializes the server network handler
	 * Called when server mod initializes
	 */
	public static void initializeServer() {
		// Register handler for ModWhitelistPayload
		ServerPlayNetworking.registerGlobalReceiver(ModWhitelistPayload.ID, ModWhitelistServerNetworkHandler::handleModWhitelistPayload);
		
		// Register player join event to start timeout tracking
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ModWhitelistTimeoutTracker.registerPlayer(handler.player.getUUID());
			MWLogger.LOGGER.debug("Started mod whitelist verification timeout for player {}", handler.player.getName().getString());
		});
		
		// Register player disconnect event to clean up
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ModWhitelistTimeoutTracker.removePlayer(handler.player.getUUID());
		});
		
		// Register server tick event to check for timeouts
		ServerTickEvents.END_SERVER_TICK.register(ModWhitelistServerNetworkHandler::checkModWhitelistTimeouts);
	}

	/**
	 * Check for players who timed out waiting for mod list
	 * Disconnects them if they exceed the timeout
	 */
	private static void checkModWhitelistTimeouts(MinecraftServer server) {
		ModWhitelistTimeoutTracker.checkTimeouts(server.getPlayerList().getPlayers());
	}

	/**
	 * Handles incoming ModWhitelistPayload from client
	 *
	 * @param payload  the payload containing mod information
	 * @param context  the network context
	 */
	private static void handleModWhitelistPayload(ModWhitelistPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		
		try {
			// Clear the timeout for this player since they sent the payload
			ModWhitelistTimeoutTracker.clearTimeout(player.getUUID());
			
			// Get player network information
			String playerIP = PlayerNetworkInfoUtil.getPlayerIP(player);
			String playerMAC = PlayerNetworkInfoUtil.getPlayerMACAddress(player);
			
			// Extract mod IDs from payload
			List<String> clientMods = payload.mods().stream()
					.map(ModInfo::modId)
					.collect(Collectors.toList());
			
			// Store the mod information for this player with network details
			PlayerModInfoStorage.storePlayerMods(player.getUUID(), player.getName().getString(), payload.mods(), playerIP, playerMAC);
			
			// Check if mod filter is enabled
			if (!MWServerConfig.ENABLE_MOD_FILTER.value()) {
				MWLogger.LOGGER.info("Player {} from IP: {} passed mod whitelist check (filter disabled)", 
						player.getName().getString(), playerIP);
				return;
			}
			
			// Validate mod list against whitelist
			List<Pair<String, MismatchType>> mismatches = MWServerConfig.test(clientMods);
			
			if (!mismatches.isEmpty()) {
				// Record violation before disconnecting
				ViolationRecordStorage.recordViolation(
						player.getName().getString(),
						player.getUUID(),
						playerIP,
						playerMAC,
						mismatches
				);
				
				// Disconnect player if mod list doesn't match
				MutableComponent reason = Component.translatable("模组不匹配");
				
				// Log all mismatches to server console
				MWLogger.LOGGER.warn("========== [Mod Whitelist] Mod Mismatch Detected ==========");
				MWLogger.LOGGER.warn("Player: {}, IP: {}", player.getName().getString(), playerIP);
				MWLogger.LOGGER.warn("Total Mismatches: {}", mismatches.size());
				
				for (Pair<String, MismatchType> mismatch : mismatches) {
                    reason = switch (mismatch.getRight()) {
                        case UNINSTALLED_BUT_SHOULD_INSTALL -> {
                        	MWLogger.LOGGER.warn("  [MISSING] Mod: {} (should be installed)", mismatch.getLeft());
                        	yield reason.append("\n").append(
                                Component.literal("请安装模组： " +
                                        mismatch.getLeft()));
                        }
                        case INSTALLED_BUT_SHOULD_NOT_INSTALL -> {
                        	MWLogger.LOGGER.warn("  [ILLEGAL] Mod: {} (should not be installed)", mismatch.getLeft());
                        	yield reason.append("\n").append(
                                Component.literal("请卸载模组: "+
                                        mismatch.getLeft()));
                        }
                    };
				}
				
				MWLogger.LOGGER.warn("=========================================================");
				player.connection.disconnect(reason);
			} else {
				MWLogger.LOGGER.info("Player {} from IP: {} mod whitelist validation passed with {} mods", 
						player.getName().getString(), playerIP, clientMods.size());
			}
		} catch (Exception e) {
			MWLogger.LOGGER.error("Error handling mod whitelist payload from player {}", 
					player.getName().getString(), e);
		}
	}
}
