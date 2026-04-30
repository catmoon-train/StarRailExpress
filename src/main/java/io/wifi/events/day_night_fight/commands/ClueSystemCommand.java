package io.wifi.events.day_night_fight.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.wifi.events.day_night_fight.clue.ClueSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ClueSystemCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dnf:clue")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("content", StringArgumentType.greedyString())
                                        .executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "title"), StringArgumentType.getString(ctx, "content"))))))
                .then(Commands.literal("times")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(ctx -> setTimes(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("sendbook")
                        .then(Commands.argument("clue_uuids_csv", StringArgumentType.greedyString())
                                .executes(ctx -> sendBook(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "clue_uuids_csv"))))));
    }

    private static int spawn(ServerPlayer player, String title, String content) {
        var entry = ClueSystem.spawnClueEntity((ServerLevel) player.level(), player.blockPosition(), title, content);
        var data = ClueSystem.getData(player);
        data.clues.add(entry);
        data.sync();
        player.sendSystemMessage(Component.translatable("commands.sre.clue.spawn.success", title, entry.clueEntityUuid()));
        return 1;
    }

    private static int setTimes(ServerPlayer player, int count) {
        var data = ClueSystem.getData(player);
        data.sendTimesLeft = count;
        data.sync();
        player.sendSystemMessage(Component.translatable("commands.sre.clue.times", count));
        return 1;
    }

    private static int clear(ServerPlayer player) {
        var data = ClueSystem.getData(player);
        data.clues.clear();
        data.sentClues.clear();
        data.sendTimesLeft = 0;
        data.sync();
        player.sendSystemMessage(Component.translatable("commands.sre.clue.clear"));
        return 1;
    }

    private static int list(ServerPlayer player) {
        var data = ClueSystem.getData(player);
        player.sendSystemMessage(Component.translatable("commands.sre.clue.list.count", data.clues.size()));
        for (var clue : data.clues) {
            player.sendSystemMessage(Component.translatable("commands.sre.clue.list.entry", clue.title(), clue.clueEntityUuid()));
        }
        return 1;
    }

    private static int sendBook(ServerPlayer player, String uuidsCsv) {
        try {
            java.util.List<UUID> ids = java.util.Arrays.stream(uuidsCsv.split(","))
                    .map(String::trim).filter(v -> !v.isEmpty()).map(UUID::fromString).toList();
            boolean ok = ClueSystem.sendCluesAsBook(player, ids);
            player.sendSystemMessage(Component.translatable(ok ? "commands.sre.clue.sendbook.success" : "commands.sre.clue.sendbook.fail"));
            return ok ? 1 : 0;
        } catch (Exception ex) {
            player.sendSystemMessage(Component.translatable("commands.sre.clue.sendbook.uuid_format_error"));
            return 0;
        }
    }
}
