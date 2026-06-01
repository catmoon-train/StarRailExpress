package net.exmo.sre.mod_whitelist.server.network;

import net.exmo.sre.mod_whitelist.common.ModInfo;
import net.exmo.sre.mod_whitelist.common.ResourcePackInfo;
import net.exmo.sre.mod_whitelist.common.ShaderPackInfo;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload;
import net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.network.ResourcePackWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.network.ShaderPackWhitelistPayload;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.exmo.sre.mod_whitelist.server.config.AllowedHashConfigFiles;
import net.exmo.sre.mod_whitelist.server.config.MWServerConfig;
import net.exmo.sre.mod_whitelist.server.config.MismatchType;
import net.exmo.sre.mod_whitelist.server.storage.PlayerModInfoStorage;
import net.exmo.sre.mod_whitelist.server.storage.ViolationRecordStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side network handler for mod whitelist system
 * Receives and validates mod information from players
 * Also manages timeout for players who don't send their mod list
 */
public class ModWhitelistServerNetworkHandler {

	// Track players who have completed initial verification
	private static final Set<java.util.UUID> initiallyVerifiedPlayers = new HashSet<>();
	// Track players who are currently being verified to prevent spam
	private static final Set<java.util.UUID> verificationInProgress = new HashSet<>();

	/**
	 * Initializes the server network handler
	 * Called when server mod initializes
	 */
	public static void initializeServer() {
		// Register handler for ModWhitelistPayload
		ServerPlayNetworking.registerGlobalReceiver(ModWhitelistPayload.ID, ModWhitelistServerNetworkHandler::handleModWhitelistPayload);
		ServerPlayNetworking.registerGlobalReceiver(ResourcePackWhitelistPayload.ID, ModWhitelistServerNetworkHandler::handleResourcePackWhitelistPayload);
		ServerPlayNetworking.registerGlobalReceiver(ShaderPackWhitelistPayload.ID, ModWhitelistServerNetworkHandler::handleShaderPackWhitelistPayload);
		
		// Register player join event to start timeout tracking and send config
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ModWhitelistTimeoutTracker.registerPlayer(handler.player.getUUID());
			// Clean up any previous state for this player
			initiallyVerifiedPlayers.remove(handler.player.getUUID());
			MWLogger.LOGGER.debug("Started mod whitelist verification timeout for player {}", handler.player.getName().getString());
			
			// Send configuration to client
			boolean syncHashValues = shouldRequestHashValues();
			ModWhitelistConfigPayload configPayload = new ModWhitelistConfigPayload(syncHashValues);
			ServerPlayNetworking.send(handler.player, configPayload);
			MWLogger.LOGGER.debug("Sent mod whitelist config to player {} (sync hashes: {})", handler.player.getName().getString(), syncHashValues);
		});
		
		// Register player disconnect event to clean up
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ModWhitelistTimeoutTracker.removePlayer(handler.player.getUUID());
			initiallyVerifiedPlayers.remove(handler.player.getUUID());
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
		java.util.UUID playerUUID = player.getUUID();
		
		// Prevent verification spam
		if (verificationInProgress.contains(playerUUID)) {
			MWLogger.LOGGER.warn("Player {} attempted verification while another is in progress", player.getName().getString());
			return;
		}
		
		try {
			verificationInProgress.add(playerUUID);
			
			// Clear timeout since player sent payload
			ModWhitelistTimeoutTracker.clearTimeout(playerUUID);
			
			// Get player network information
			String playerIP = PlayerNetworkInfoUtil.getPlayerIP(player);
			String playerMAC = PlayerNetworkInfoUtil.getPlayerMACAddress(player);
			
			// Store player mods info
			PlayerModInfoStorage.storePlayerMods(playerUUID, player.getName().getString(), payload.mods(), playerIP, playerMAC);
			
			// Check if player is OP and should skip verification
			if (MWServerConfig.SKIP_VERIFICATION_FOR_OPS.value() && player.hasPermissions(2)) {
				initiallyVerifiedPlayers.add(playerUUID);
				MWLogger.LOGGER.info("Player {} from IP: {} passed mod whitelist check because op", 
						player.getName().getString(), playerIP);
				return;
			}
			
			// Check if mod filter is enabled
			if (!MWServerConfig.ENABLE_MOD_FILTER.value()) {
				initiallyVerifiedPlayers.add(playerUUID);
				MWLogger.LOGGER.info("Player {} from IP: {} passed mod whitelist check (filter disabled)", 
						player.getName().getString(), playerIP);
				return;
			}
			
			// Perform validation
			List<String> clientMods = payload.mods().stream()
					.map(ModInfo::modId)
					.collect(Collectors.toList());
			List<Pair<String, MismatchType>> mismatches = MWServerConfig.test(clientMods);
			mismatches.addAll(validateStarRailExpressHashes(payload.mods()));
			
			if (MWServerConfig.ENABLE_RESOURCE_PACK_VERIFICATION.value()) {
				mismatches.addAll(validateResourcePacks(payload.resourcePacks()));
			}
			
			if (MWServerConfig.ENABLE_SHADER_PACK_VERIFICATION.value()) {
				mismatches.addAll(validateShaderPacks(payload.shaderPacks()));
			}
			
			boolean isInitialVerification = !initiallyVerifiedPlayers.contains(playerUUID);
			
			if (!mismatches.isEmpty()) {
				if (isInitialVerification) {
					// Initial verification failure - record violation and disconnect
					ViolationRecordStorage.recordViolation(
							player.getName().getString(),
							playerUUID,
							playerIP,
							playerMAC,
							mismatches
					);
					
					MutableComponent reason = Component.translatable("模组不匹配");
					MWLogger.LOGGER.warn("========== [Mod Whitelist] Initial Verification Failed ==========");
					MWLogger.LOGGER.warn("Player: {}, IP: {}", player.getName().getString(), playerIP);
					
					for (Pair<String, MismatchType> mismatch : mismatches) {
	                    reason = switch (mismatch.getRight()) {
	                        case UNINSTALLED_BUT_SHOULD_INSTALL -> {
	                        	MWLogger.LOGGER.warn("  [MISSING] Mod: {} (should be installed)", mismatch.getLeft());
	                        	yield reason.append("\n").append(Component.literal("请安装模组： " + mismatch.getLeft()));
	                        }
	                        case INSTALLED_BUT_SHOULD_NOT_INSTALL -> {
	                        	MWLogger.LOGGER.warn("  [ILLEGAL] Mod/Resource/Shader: {} (should not be installed)", mismatch.getLeft());
	                        	yield reason.append("\n").append(Component.literal("请卸载: " + mismatch.getLeft()));
	                        }
	                    };
					}
					
					MWLogger.LOGGER.warn("=========================================================");
					player.connection.disconnect(reason);
				} else {
					// Re-verification failure - just log, don't disconnect
					MWLogger.LOGGER.warn("========== [Mod Whitelist] Re-verification Failed ==========");
					MWLogger.LOGGER.warn("Player: {}, IP: {}", player.getName().getString(), playerIP);
					for (Pair<String, MismatchType> mismatch : mismatches) {
						MWLogger.LOGGER.warn("  [MISMATCH] {}: {}", 
								mismatch.getRight() == MismatchType.UNINSTALLED_BUT_SHOULD_INSTALL ? "MISSING" : "ILLEGAL",
								mismatch.getLeft());
					}
					MWLogger.LOGGER.warn("=========================================================");
				}
			} else {
				// Verification successful
				if (isInitialVerification) {
					initiallyVerifiedPlayers.add(playerUUID);
					MWLogger.LOGGER.info("Player {} from IP: {} initial verification passed with {} mods, {} resource packs, {} shader packs", 
							player.getName().getString(), playerIP, clientMods.size(), payload.resourcePacks().size(), payload.shaderPacks().size());
				} else {
					MWLogger.LOGGER.info("Player {} from IP: {} re-verification passed with {} mods, {} resource packs, {} shader packs", 
							player.getName().getString(), playerIP, clientMods.size(), payload.resourcePacks().size(), payload.shaderPacks().size());
				}
			}
		} catch (Exception e) {
			MWLogger.LOGGER.error("Error handling mod whitelist payload from player {}", 
					player.getName().getString(), e);
		} finally {
			verificationInProgress.remove(playerUUID);
		}
	}

	private static boolean shouldRequestHashValues() {
		return MWServerConfig.SYNC_HASH_VALUES.value()
				|| MWServerConfig.VERIFY_STARRAILEXPRESS_HASHES.value()
				|| MWServerConfig.VERIFY_RESOURCE_PACK_HASHES.value()
				|| MWServerConfig.VERIFY_SHADER_PACK_HASHES.value();
	}

	private static void handleResourcePackWhitelistPayload(ResourcePackWhitelistPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!MWServerConfig.ENABLE_MOD_FILTER.value() || !MWServerConfig.ENABLE_RESOURCE_PACK_VERIFICATION.value()) {
			return;
		}
		List<Pair<String, MismatchType>> mismatches = validateResourcePacks(payload.resourcePacks());
		handleSecondaryVerificationResult(player, "resource pack", payload.resourcePacks().size(), mismatches);
	}

	private static void handleShaderPackWhitelistPayload(ShaderPackWhitelistPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!MWServerConfig.ENABLE_MOD_FILTER.value() || !MWServerConfig.ENABLE_SHADER_PACK_VERIFICATION.value()) {
			return;
		}
		List<Pair<String, MismatchType>> mismatches = validateShaderPacks(payload.shaderPacks());
		handleSecondaryVerificationResult(player, "shader pack", payload.shaderPacks().size(), mismatches);
	}

	private static void handleSecondaryVerificationResult(ServerPlayer player, String subject, int count,
														 List<Pair<String, MismatchType>> mismatches) {
		String playerIP = PlayerNetworkInfoUtil.getPlayerIP(player);
		if (MWServerConfig.SKIP_VERIFICATION_FOR_OPS.value() && player.hasPermissions(2)) {
			MWLogger.LOGGER.info("Player {} from IP: {} skipped {} whitelist check because op",
					player.getName().getString(), playerIP, subject);
			return;
		}
		if (mismatches.isEmpty()) {
			MWLogger.LOGGER.info("Player {} from IP: {} {} re-verification passed with {} entries",
					player.getName().getString(), playerIP, subject, count);
			return;
		}

		boolean initial = !initiallyVerifiedPlayers.contains(player.getUUID());
		MWLogger.LOGGER.warn("========== [Mod Whitelist] {} Verification Failed ==========", initial ? "Initial" : "Re");
		MWLogger.LOGGER.warn("Player: {}, IP: {}, Subject: {}", player.getName().getString(), playerIP, subject);
		for (Pair<String, MismatchType> mismatch : mismatches) {
			MWLogger.LOGGER.warn("  [MISMATCH] {}: {}",
					mismatch.getRight() == MismatchType.UNINSTALLED_BUT_SHOULD_INSTALL ? "MISSING" : "ILLEGAL",
					mismatch.getLeft());
		}
		MWLogger.LOGGER.warn("=========================================================");

		if (initial) {
			ViolationRecordStorage.recordViolation(
					player.getName().getString(),
					player.getUUID(),
					playerIP,
					PlayerNetworkInfoUtil.getPlayerMACAddress(player),
					mismatches
			);
			MutableComponent reason = Component.translatable("模组不匹配");
			for (Pair<String, MismatchType> mismatch : mismatches) {
				reason.append("\n").append(Component.literal("请检查: " + mismatch.getLeft()));
			}
			player.connection.disconnect(reason);
		}
	}

	private static List<Pair<String, MismatchType>> validateStarRailExpressHashes(List<ModInfo> mods) {
		List<Pair<String, MismatchType>> mismatches = new ArrayList<>();
		if (!MWServerConfig.VERIFY_STARRAILEXPRESS_HASHES.value()) {
			return mismatches;
		}

		Set<String> allowedHashes = new HashSet<>(MWServerConfig.ALLOWED_STARRAILEXPRESS_HASHES.value());
		allowedHashes.addAll(AllowedHashConfigFiles.loadStarRailExpressHashes());
		Set<String> normalizedAllowedHashes = allowedHashes.stream()
				.map(hash -> hash.toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());

		Map<String, ModInfo> modsById = new HashMap<>();
		for (ModInfo mod : mods) {
			modsById.put(mod.modId(), mod);
		}
		for (String modId : MWServerConfig.STARRAILEXPRESS_HASH_MOD_IDS.value()) {
			ModInfo mod = modsById.get(modId);
			if (mod == null) {
				mismatches.add(Pair.of("StarRailExpress Mod: " + modId, MismatchType.UNINSTALLED_BUT_SHOULD_INSTALL));
				continue;
			}
			String hash = mod.sha256();
			if ("excluded".equals(hash) || !normalizedAllowedHashes.contains(hash.toLowerCase(Locale.ROOT))) {
				mismatches.add(Pair.of("StarRailExpress Mod Hash: " + modId, MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL));
			}
		}
		return mismatches;
	}
	
	/**
	 * Validates resource packs against allowed hashes if hash verification is enabled
	 *
	 * @param resourcePacks the list of resource packs from client
	 * @return list of mismatches
	 */
	private static List<Pair<String, MismatchType>> validateResourcePacks(List<ResourcePackInfo> resourcePacks) {
		List<Pair<String, MismatchType>> mismatches = new ArrayList<>();
		
		if (MWServerConfig.VERIFY_RESOURCE_PACK_HASHES.value()) {
			// Check if all resource pack hashes are in the allowed list
			Set<String> allowedHashes = new HashSet<>(MWServerConfig.ALLOWED_RESOURCE_PACK_HASHES.value());
			allowedHashes.addAll(AllowedHashConfigFiles.loadResourcePackHashes());
			Set<String> normalizedAllowedHashes = allowedHashes.stream()
					.map(hash -> hash.toLowerCase(Locale.ROOT))
					.collect(Collectors.toSet());
			for (ResourcePackInfo pack : resourcePacks) {
				if ("excluded".equals(pack.sha256()) || !normalizedAllowedHashes.contains(pack.sha256().toLowerCase(Locale.ROOT))) {
					mismatches.add(Pair.of("Resource Pack: " + pack.packId(), MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL));
				}
			}
		}
		
		return mismatches;
	}
	
	/**
	 * Validates shader packs against allowed hashes if hash verification is enabled
	 *
	 * @param shaderPacks the list of shader packs from client
	 * @return list of mismatches
	 */
	private static List<Pair<String, MismatchType>> validateShaderPacks(List<ShaderPackInfo> shaderPacks) {
		List<Pair<String, MismatchType>> mismatches = new ArrayList<>();
		
		if (MWServerConfig.VERIFY_SHADER_PACK_HASHES.value()) {
			// Check if all shader pack hashes are in the allowed list
			Set<String> allowedHashes = new HashSet<>(MWServerConfig.ALLOWED_SHADER_PACK_HASHES.value());
			allowedHashes.addAll(AllowedHashConfigFiles.loadShaderPackHashes());
			Set<String> normalizedAllowedHashes = allowedHashes.stream()
					.map(hash -> hash.toLowerCase(Locale.ROOT))
					.collect(Collectors.toSet());
			for (ShaderPackInfo pack : shaderPacks) {
				if ("excluded".equals(pack.sha256()) || !normalizedAllowedHashes.contains(pack.sha256().toLowerCase(Locale.ROOT))) {
					mismatches.add(Pair.of("Shader Pack: " + pack.packId(), MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL));
				}
			}
		}
		
		return mismatches;
	}
}
