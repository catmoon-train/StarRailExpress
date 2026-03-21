package io.wifi.starrailexpress.mod_whitelist.server.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.mod_whitelist.common.utils.MWLogger;
import io.wifi.starrailexpress.mod_whitelist.server.config.MWServerConfig;
import io.wifi.starrailexpress.mod_whitelist.server.storage.ViolationRecordStorage;
import io.wifi.starrailexpress.util.MutableMaxPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.UUID;

/**
 * Command handler for Mod Whitelist system and server management
 * Supports commands like: mw:reload, mw:maxplayers
 */
public class ModWhitelistCommand {

	/**
	 * Registers all mod whitelist commands
	 * Called during server initialization
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("mw:reload")
						.requires(source -> source.hasPermission(2)) // OP only
						.executes(ModWhitelistCommand::reloadConfig));

		dispatcher.register(
				Commands.literal("mw:maxplayers")
						.requires(source -> source.hasPermission(2)) // OP only
						.then(Commands.literal("get")
								.executes(ModWhitelistCommand::getMaxPlayers))
						.then(Commands.literal("set")
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
										.executes(ModWhitelistCommand::setMaxPlayers))));

		MWLogger.LOGGER.debug("Mod Whitelist commands registered");
	}

	/**
	 * Handles the mw:reload command
	 * Reloads configuration from file
	 */
	private static int reloadConfig(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		try {
			MWServerConfig.reloadConfig();
			source.sendSuccess(
					() -> Component.literal("§aMod Whitelist configuration reloaded successfully!"),
					true);
			MWLogger.LOGGER.info("Config reloaded by: " + source.getEntity().getName().getString());
			return Command.SINGLE_SUCCESS;
		} catch (Exception e) {
			source.sendFailure(
					Component.literal("§cFailed to reload Mod Whitelist configuration: " + e.getMessage()));
			MWLogger.LOGGER.error("Error reloading config", e);
			return 0;
		}
	}

	public static int maxPlayers = -404;

	/**
	 * Handles the mw:maxplayers get command
	 * Displays the current maximum player count
	 */
	private static int getMaxPlayers(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();

		if (server == null) {
			source.sendFailure(Component.literal("§cServer instance not available"));
			return 0;
		}

		int maxPlayers = server.getMaxPlayers();
		int currentPlayers = server.getPlayerList().getPlayers().size();

		source.sendSuccess(
				() -> Component
						.literal("§6Current Server Status: §f" + currentPlayers + "§6/§f" + maxPlayers + " players"),
				false);

		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Handles the mw:maxplayers set command
	 * Sets the maximum player count
	 */
	private static int setMaxPlayers(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		int newMaxPlayers = IntegerArgumentType.getInteger(context, "count");

		if (server == null) {
			source.sendFailure(Component.literal("§cServer instance not available"));
			return 0;
		}

		int oldMaxPlayers = server.getMaxPlayers();

		try {
			maxPlayers = newMaxPlayers;
			PlayerList playerList = server.getPlayerList();
			// 使用反射修改PlayerList中的maxPlayers字段
			try {
				// 修改最大玩家数
				((MutableMaxPlayer) playerList).setMaxPlayers(maxPlayers);
			} catch (Exception e) {
				MWLogger.LOGGER.error("Failed to modify PlayerList maxPlayers via reflection", e);
				source.sendFailure(Component.literal("§cFailed to update player list max players: " + e.getMessage()));
				return 0;
			}

			source.sendSuccess(
					() -> Component
							.literal("§aMax players changed from §f" + oldMaxPlayers + "§a to §f" + newMaxPlayers),
					true);

			String playerName = source.getEntity() != null ? source.getEntity().getName().getString() : "unknown";
			MWLogger.LOGGER
					.info("Max players changed from " + oldMaxPlayers + " to " + newMaxPlayers + " by " + playerName);

			return Command.SINGLE_SUCCESS;
		} catch (Exception e) {
			source.sendFailure(
					Component.literal("§cFailed to set max players: " + e.getMessage()));
			MWLogger.LOGGER.error("Error setting max players", e);
			return 0;
		}
	}

	/**
	 * Handles the mw:violations list command
	 * Lists all recorded violations
	 */
	public static int listAllViolations(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		try {
			List<ViolationRecordStorage.ViolationRecord> violations = ViolationRecordStorage.getAllViolations();

			source.sendSuccess(
					() -> Component.literal("§6=== Mod Whitelist Violations ==="),
					false);

			if (violations.isEmpty()) {
				source.sendSuccess(
						() -> Component.literal("§aNo violations recorded"),
						false);
				return Command.SINGLE_SUCCESS;
			}

			source.sendSuccess(
					() -> Component.literal("§6Total violations: §f" + violations.size()),
					false);

			// Show last 10 violations
			int startIndex = Math.max(0, violations.size() - 10);
			for (int i = startIndex; i < violations.size(); i++) {
				ViolationRecordStorage.ViolationRecord record = violations.get(i);
				MutableComponent msg = Component.literal("§7[" + record.timestamp + "] §f" + record.playerId +
						" §7(UUID: " + record.playerUUID + ")");
				source.sendSuccess(() -> msg, false);

				for (ViolationRecordStorage.ViolationDetail detail : record.violations) {
					String color = detail.violationType.name().contains("UNINSTALLED") ? "§c" : "§e";
					MutableComponent detailMsg = Component.literal("  " + color + detail.violationType.name() +
							": §f" + detail.modId);
					source.sendSuccess(() -> detailMsg, false);
				}
			}

			if (violations.size() > 10) {
				source.sendSuccess(
						() -> Component.literal("§7... and " + (violations.size() - 10) + " more violations"),
						false);
			}

			return Command.SINGLE_SUCCESS;
		} catch (Exception e) {
			source.sendFailure(
					Component.literal("§cFailed to list violations: " + e.getMessage()));
			MWLogger.LOGGER.error("Error listing violations", e);
			return 0;
		}
	}

	/**
	 * Handles the mw:violations player <uuid> command
	 * Shows violations for a specific player
	 */
	public static int getPlayerViolations(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String uuidStr = StringArgumentType.getString(context, "uuid");

		try {
			UUID playerUUID = UUID.fromString(uuidStr);
			List<ViolationRecordStorage.ViolationRecord> violations = ViolationRecordStorage
					.getPlayerViolations(playerUUID);

			if (violations.isEmpty()) {
				source.sendSuccess(
						() -> Component.literal("§aNo violations found for player with UUID: " + uuidStr),
						false);
				return Command.SINGLE_SUCCESS;
			}

			source.sendSuccess(
					() -> Component.literal("§6Violations for player (UUID: " + uuidStr + "): §f" + violations.size()),
					false);

			for (ViolationRecordStorage.ViolationRecord record : violations) {
				MutableComponent msg = Component.literal("§7[" + record.timestamp + "] §fIP: " + record.ipAddress +
						" §7MAC: " + record.macAddress);
				source.sendSuccess(() -> msg, false);

				for (ViolationRecordStorage.ViolationDetail detail : record.violations) {
					String color = detail.violationType.name().contains("UNINSTALLED") ? "§c" : "§e";
					MutableComponent detailMsg = Component.literal("  " + color + detail.description +
							": §f" + detail.modId);
					source.sendSuccess(() -> detailMsg, false);
				}
			}

			return Command.SINGLE_SUCCESS;
		} catch (IllegalArgumentException e) {
			source.sendFailure(
					Component.literal("§cInvalid UUID format: " + uuidStr));
			return 0;
		} catch (Exception e) {
			source.sendFailure(
					Component.literal("§cFailed to get player violations: " + e.getMessage()));
			MWLogger.LOGGER.error("Error getting player violations", e);
			return 0;
		}
	}

	/**
	 * Handles the mw:violations clear command
	 * Clears all violation records
	 */
	public static int clearViolations(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		try {
			// Note: This would require adding a clear method to ViolationRecordStorage
			// For now, we'll just inform the user about the file location
			source.sendSuccess(
					() -> Component.literal("§6Violation records file location:"),
					false);
			source.sendSuccess(
					() -> Component.literal("§f" + ViolationRecordStorage.getViolationsFilePath().toString()),
					false);
			source.sendSuccess(
					() -> Component.literal("§7Manually delete this file to clear all records"),
					false);

			return Command.SINGLE_SUCCESS;
		} catch (Exception e) {
			source.sendFailure(
					Component.literal("§cFailed to get violations file path: " + e.getMessage()));
			MWLogger.LOGGER.error("Error getting violations file path", e);
			return 0;
		}
	}
}
