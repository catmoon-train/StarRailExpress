package io.wifi.starrailexpress.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent.FactionCardType;
import io.wifi.starrailexpress.network.OpenProgressionScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ProgressionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:pass")
                .executes(context -> open(context.getSource(), null))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> open(context.getSource(),
                                GameProfileArgument.getGameProfiles(context, "player"))))
                .then(Commands.literal("refresh")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> refresh(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> refresh(context.getSource(),
                                        getSinglePlayer(context.getSource(),
                                                GameProfileArgument.getGameProfiles(context,
                                                        "player")))))
                        .then(Commands.literal("weekly")
                                .executes(context -> refreshWeekly(context.getSource(),
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> refreshWeekly(context.getSource(),
                                                getSinglePlayer(context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "player")))))))
                .then(Commands.literal("sync")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> sync(context.getSource(),
                                        getSinglePlayer(context.getSource(),
                                                GameProfileArgument.getGameProfiles(context,
                                                        "player"))))))
                .then(Commands.literal("activate")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .executes(context -> activate(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "faction")))))
                .then(Commands.literal("givecard")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> giveCard(context.getSource(),
                                                        getSinglePlayer(context.getSource(),
                                                                GameProfileArgument.getGameProfiles(
                                                                        context, "player")),
                                                        StringArgumentType.getString(context, "faction"),
                                                        IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("xp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> setXp(context.getSource(),
                                                        getSinglePlayer(context.getSource(),
                                                                GameProfileArgument.getGameProfiles(
                                                                        context, "player")),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> addXp(context.getSource(),
                                                        getSinglePlayer(context.getSource(),
                                                                GameProfileArgument.getGameProfiles(
                                                                        context, "player")),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))));
    }

    private static int open(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (!SREConfig.instance().enableProgressionSystem) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.disabled"));
            return 0;
        }
        if (profiles == null || profiles.isEmpty()) {
            ServerPlayer player = source.getPlayerOrException();
            openScreen(player);
            source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.open.self"), false);
            return 1;
        }
        for (GameProfile profile : profiles) {
            ServerPlayer target = source.getServer().getPlayerList().getPlayer(profile.getId());
            if (target != null) {
                openScreen(target);
                source.sendSuccess(
                        () -> Component.translatable("cmd.stupid_express.progression.open.other", profile.getName()),
                        true);
            } else {
                source.sendFailure(
                        Component.translatable("cmd.stupid_express.progression.open.offline", profile.getName()));
            }
        }
        return 1;
    }

    private static int refresh(CommandSourceStack source, ServerPlayer player) {
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.error.player_not_found"));
            return 0;
        }
        SREPlayerProgressionComponent component = SREPlayerProgressionComponent.KEY.get(player);
        component.forceRefreshTasks();
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.refresh.success",
                player.getName().getString()), true);
        return 1;
    }

    private static int refreshWeekly(CommandSourceStack source, ServerPlayer player) {
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.error.player_not_found"));
            return 0;
        }
        SREPlayerProgressionComponent component = SREPlayerProgressionComponent.KEY.get(player);
        component.forceRefreshWeeklyTasks();
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.refresh.weekly.success",
                player.getName().getString()), true);
        return 1;
    }

    private static int sync(CommandSourceStack source, ServerPlayer player) {
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.error.player_not_found"));
            return 0;
        }
        SREPlayerProgressionComponent component = SREPlayerProgressionComponent.KEY.get(player);
        boolean pulled = component.tryPullTasksFromNetwork();
        if (!pulled) {
            component.forceRefreshTasks();
        }
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.sync.success",
                player.getName().getString()), true);
        return 1;
    }

    private static int activate(CommandSourceStack source, ServerPlayer player, String faction) {
        SREPlayerProgressionComponent component = SREPlayerProgressionComponent.KEY.get(player);
        FactionCardType type = FactionCardType.fromString(faction);
        if (!component.activateFactionCard(type)) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.activate.failed", faction));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.activate.success",
                Component.translatable(type.displayName)), false);
        return 1;
    }

    private static int giveCard(CommandSourceStack source, ServerPlayer player, String faction, int count) {
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.error.player_not_found"));
            return 0;
        }
        FactionCardType type = FactionCardType.fromString(faction);
        if (type == FactionCardType.NONE) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.givecard.invalid", faction));
            return 0;
        }
        SREPlayerProgressionComponent.KEY.get(player).addFactionCard(type, count);
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.givecard.success",
                player.getName().getString(), count, Component.translatable(type.displayName)), true);
        return 1;
    }

    private static int setXp(CommandSourceStack source, ServerPlayer player, int amount) {
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.error.player_not_found"));
            return 0;
        }
        SREPlayerProgressionComponent.KEY.get(player).setTotalExperienceValue(amount);
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.xp.set",
                player.getName().getString(), amount), true);
        return 1;
    }

    private static int addXp(CommandSourceStack source, ServerPlayer player, int amount) {
        if (player == null) {
            source.sendFailure(Component.translatable("cmd.stupid_express.progression.error.player_not_found"));
            return 0;
        }
        SREPlayerProgressionComponent.KEY.get(player).grantExperience(amount, "控制台奖励");
        source.sendSuccess(() -> Component.translatable("cmd.stupid_express.progression.xp.add", amount,
                player.getName().getString()), true);
        return 1;
    }

    private static ServerPlayer getSinglePlayer(CommandSourceStack source, Collection<GameProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        GameProfile profile = profiles.iterator().next();
        return source.getServer().getPlayerList().getPlayer(profile.getId());
    }

    private static void openScreen(ServerPlayer player) {
        SREPlayerProgressionComponent.KEY.get(player).sync();
        ServerPlayNetworking.send(player, OpenProgressionScreenPayload.INSTANCE);
    }
}