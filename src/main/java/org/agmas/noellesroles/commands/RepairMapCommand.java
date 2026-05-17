package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RepairMapCommand {
    private RepairMapCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var lockAdd = literal("add")
                    .then(argument("mapId", StringArgumentType.word())
                            .then(argument("lockId", StringArgumentType.word())
                                    .then(argument("requiredItem", StringArgumentType.word())
                                            .then(argument("consume", BoolArgumentType.bool())
                                                    .executes(context -> addLock(context.getSource(),
                                                            StringArgumentType.getString(context, "mapId"),
                                                            StringArgumentType.getString(context, "lockId"),
                                                            StringArgumentType.getString(context, "requiredItem"),
                                                            BoolArgumentType.getBool(context, "consume")))))));
            var lockRemove = literal("remove")
                    .then(argument("mapId", StringArgumentType.word())
                            .executes(context -> removeLock(context.getSource(),
                                    StringArgumentType.getString(context, "mapId"))));
            var lockList = literal("list")
                    .then(argument("mapId", StringArgumentType.word())
                            .executes(context -> listLocks(context.getSource(),
                                    StringArgumentType.getString(context, "mapId"))));

            var escapeAdd = literal("add")
                    .then(argument("mapId", StringArgumentType.word())
                            .then(argument("routeId", StringArgumentType.word())
                                    .then(argument("capacity", IntegerArgumentType.integer(1, 12))
                                            .then(argument("requiredItemsCsv", StringArgumentType.greedyString())
                                                    .executes(context -> addEscape(context.getSource(),
                                                            StringArgumentType.getString(context, "mapId"),
                                                            StringArgumentType.getString(context, "routeId"),
                                                            IntegerArgumentType.getInteger(context, "capacity"),
                                                            StringArgumentType.getString(context, "requiredItemsCsv")))))));
            var escapeRemove = literal("remove")
                    .then(argument("mapId", StringArgumentType.word())
                            .then(argument("routeId", StringArgumentType.word())
                                    .executes(context -> removeEscape(context.getSource(),
                                            StringArgumentType.getString(context, "mapId"),
                                            StringArgumentType.getString(context, "routeId")))));
            var escapeList = literal("list")
                    .then(argument("mapId", StringArgumentType.word())
                            .executes(context -> listEscapes(context.getSource(),
                                    StringArgumentType.getString(context, "mapId"))));

            dispatcher.register(literal("repairmap")
                    .requires(source -> source.hasPermission(2))
                    .then(literal("lock").then(lockAdd).then(lockRemove).then(lockList))
                    .then(literal("escape").then(escapeAdd).then(escapeRemove).then(escapeList)));
        });
    }

    private static int addLock(CommandSourceStack source, String mapId, String lockId, String requiredItem,
            boolean consume) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = targetedBlock(player);
        MapConfig.MapEntry map = map(source, mapId);
        MapConfig.RepairConfig repair = repair(map);
        repair.lockedDoors.removeIf(entry -> entry.pos.toBlockPos().equals(pos));
        MapConfig.LockedDoorEntry entry = new MapConfig.LockedDoorEntry();
        entry.pos = MapConfig.Pos.from(pos);
        entry.lockId = lockId;
        entry.requiredItem = requiredItem;
        entry.consume = consume;
        repair.lockedDoors.add(entry);
        save(source);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.repair.map_lock_added",
                mapId, lockId, pos.getX(), pos.getY(), pos.getZ(), requiredItem, consume), true);
        return 1;
    }

    private static int removeLock(CommandSourceStack source, String mapId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = targetedBlock(player);
        MapConfig.MapEntry map = map(source, mapId);
        MapConfig.RepairConfig repair = repair(map);
        boolean removed = repair.lockedDoors.removeIf(entry -> entry.pos.toBlockPos().equals(pos));
        save(source);
        source.sendSuccess(() -> Component.translatable(removed
                        ? "message.noellesroles.repair.map_lock_removed"
                        : "message.noellesroles.repair.map_lock_not_found",
                mapId, pos.getX(), pos.getY(), pos.getZ()).withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return removed ? 1 : 0;
    }

    private static int listLocks(CommandSourceStack source, String mapId) {
        MapConfig.MapEntry map = map(source, mapId);
        MapConfig.RepairConfig repair = repair(map);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.repair.map_lock_list",
                mapId, repair.lockedDoors.size()).withStyle(ChatFormatting.GOLD), false);
        for (MapConfig.LockedDoorEntry entry : repair.lockedDoors) {
            source.sendSuccess(() -> Component.literal("- " + entry.lockId + " @ "
                    + entry.pos.x + " " + entry.pos.y + " " + entry.pos.z
                    + " -> " + entry.requiredItem + " consume=" + entry.consume), false);
        }
        return repair.lockedDoors.size();
    }

    private static int addEscape(CommandSourceStack source, String mapId, String routeId, int capacity,
            String requiredItemsCsv) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = targetedBlock(player);
        MapConfig.MapEntry map = map(source, mapId);
        MapConfig.RepairConfig repair = repair(map);
        repair.escapeRoutes.removeIf(entry -> entry.id.equals(routeId));
        MapConfig.EscapeRouteEntry entry = new MapConfig.EscapeRouteEntry();
        entry.id = routeId;
        entry.displayKey = "repair.escape_route." + routeId;
        entry.pos = MapConfig.Pos.from(pos);
        entry.capacity = capacity;
        entry.requiredItems.addAll(Arrays.stream(requiredItemsCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList());
        repair.escapeRoutes.add(entry);
        save(source);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.repair.map_escape_added",
                mapId, routeId, pos.getX(), pos.getY(), pos.getZ(), capacity, requiredItemsCsv), true);
        return 1;
    }

    private static int removeEscape(CommandSourceStack source, String mapId, String routeId) {
        MapConfig.MapEntry map = map(source, mapId);
        MapConfig.RepairConfig repair = repair(map);
        boolean removed = repair.escapeRoutes.removeIf(entry -> entry.id.equals(routeId));
        save(source);
        source.sendSuccess(() -> Component.translatable(removed
                ? "message.noellesroles.repair.map_escape_removed"
                : "message.noellesroles.repair.map_escape_not_found", mapId, routeId), true);
        return removed ? 1 : 0;
    }

    private static int listEscapes(CommandSourceStack source, String mapId) {
        MapConfig.MapEntry map = map(source, mapId);
        MapConfig.RepairConfig repair = repair(map);
        source.sendSuccess(() -> Component.translatable("message.noellesroles.repair.map_escape_list",
                mapId, repair.escapeRoutes.size()).withStyle(ChatFormatting.GOLD), false);
        for (MapConfig.EscapeRouteEntry entry : repair.escapeRoutes) {
            source.sendSuccess(() -> Component.literal("- " + entry.id + " @ "
                    + entry.pos.x + " " + entry.pos.y + " " + entry.pos.z
                    + " capacity=" + entry.capacity + " requires=" + String.join(",", entry.requiredItems)), false);
        }
        return repair.escapeRoutes.size();
    }

    private static BlockPos targetedBlock(ServerPlayer player) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        HitResult hit = player.pick(8.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Component.translatable("message.noellesroles.repair.map_need_target")).create();
    }

    private static MapConfig.MapEntry map(CommandSourceStack source, String mapId) {
        MapConfig.MapEntry map = ServerMapConfig.getInstance(source.getServer()).getMapById(mapId);
        if (map == null) {
            throw new IllegalArgumentException("Unknown map id: " + mapId);
        }
        return map;
    }

    private static MapConfig.RepairConfig repair(MapConfig.MapEntry map) {
        if (map.repair == null) {
            map.repair = new MapConfig.RepairConfig();
        }
        return map.repair;
    }

    private static void save(CommandSourceStack source) {
        ServerMapConfig.getInstance(source.getServer()).saveConfig(source.getServer());
    }
}
