package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.content.clue.ClueSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ClueSystemCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("clue")
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
        player.sendSystemMessage(Component.literal("已在主世界生成线索实体: " + title + " uuid=" + entry.clueEntityUuid()));
        return 1;
    }

    private static int setTimes(ServerPlayer player, int count) {
        var data = ClueSystem.getData(player);
        data.sendTimesLeft = count;
        data.sync();
        player.sendSystemMessage(Component.literal("线索发送次数=" + count + "（0=无限）"));
        return 1;
    }

    private static int clear(ServerPlayer player) {
        var data = ClueSystem.getData(player);
        data.clues.clear();
        data.sentClues.clear();
        data.sendTimesLeft = 0;
        data.sync();
        player.sendSystemMessage(Component.literal("线索记录已清空"));
        return 1;
    }

    private static int list(ServerPlayer player) {
        var data = ClueSystem.getData(player);
        player.sendSystemMessage(Component.literal("当前线索数: " + data.clues.size()));
        for (var clue : data.clues) {
            player.sendSystemMessage(Component.literal("- " + clue.title() + " | " + clue.clueEntityUuid()));
        }
        return 1;
    }

    private static int sendBook(ServerPlayer player, String uuidsCsv) {
        try {
            java.util.List<UUID> ids = java.util.Arrays.stream(uuidsCsv.split(","))
                    .map(String::trim).filter(v -> !v.isEmpty()).map(UUID::fromString).toList();
            boolean ok = ClueSystem.sendCluesAsBook(player, ids);
            player.sendSystemMessage(Component.literal(ok ? "线索书已发送到配置书架" : "发送失败（数量超限/线索无效/书架无空位）"));
            return ok ? 1 : 0;
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal("UUID格式错误，请使用逗号分隔"));
            return 0;
        }
    }
}
