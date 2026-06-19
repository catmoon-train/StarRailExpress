package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent.MurderTimeAction;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent.MurderTimeEvent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MurderTimeCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tmm:game")
                        .requires(source -> Harpymodloader.officialVerify && source.hasPermission(2))
                        .then(Commands.literal("murder_time")
                                .executes(MurderTimeCommand::status)
                                .then(Commands.literal("status").executes(MurderTimeCommand::status))
                                .then(Commands.literal("enabled")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(MurderTimeCommand::setEnabled)))
                                .then(Commands.literal("hud")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(MurderTimeCommand::setHud)))
                                .then(Commands.literal("defaults").executes(MurderTimeCommand::defaults))
                                .then(Commands.literal("reset_triggered").executes(MurderTimeCommand::resetTriggered))
                                .then(Commands.literal("events")
                                        .then(Commands.literal("list").executes(MurderTimeCommand::listEvents))
                                        .then(Commands.literal("clear").executes(MurderTimeCommand::clearEvents))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .suggests(MurderTimeCommand::suggestEventIds)
                                                        .executes(MurderTimeCommand::removeEvent)))
                                        .then(Commands.literal("trigger")
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .suggests(MurderTimeCommand::suggestEventIds)
                                                        .executes(MurderTimeCommand::triggerEvent)))
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .then(Commands.argument("elapsed_seconds",
                                                                IntegerArgumentType.integer(0, 14400))
                                                                .then(Commands.argument("action",
                                                                        StringArgumentType.word())
                                                                        .suggests(MurderTimeCommand::suggestActions)
                                                                        .then(Commands.argument("amount",
                                                                                IntegerArgumentType.integer(0))
                                                                                .then(Commands.argument(
                                                                                        "duration_seconds",
                                                                                        IntegerArgumentType.integer(0,
                                                                                                3600))
                                                                                        .executes(MurderTimeCommand::addEvent)))))))))));
    }

    private static MurderTimeEventComponent component(CommandContext<CommandSourceStack> context) {
        return MurderTimeEventComponent.KEY.get(context.getSource().getLevel());
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MurderTimeEventComponent component = component(context);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.status",
                component.isEnabled(),
                component.isHudEnabled(),
                component.getEvents().size()).withStyle(ChatFormatting.GOLD), false);
        return listEvents(context);
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        component(context).setEnabled(value);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.enabled", value).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setHud(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        component(context).setHudEnabled(value);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.hud", value).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int defaults(CommandContext<CommandSourceStack> context) {
        component(context).initializeDefaults();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.defaults").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetTriggered(CommandContext<CommandSourceStack> context) {
        component(context).resetTriggered();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.reset_triggered").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearEvents(CommandContext<CommandSourceStack> context) {
        component(context).clear();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.clear").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int listEvents(CommandContext<CommandSourceStack> context) {
        MurderTimeEventComponent component = component(context);
        if (component.getEvents().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.noellesroles.murder_time.empty").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        for (MurderTimeEvent event : component.getEvents()) {
            context.getSource().sendSystemMessage(Component.translatable(
                    "commands.noellesroles.murder_time.event_line",
                    event.id(),
                    formatSeconds(event.elapsedTicks() / 20),
                    event.action().name().toLowerCase(Locale.ROOT),
                    event.amount(),
                    durationOrCount(event),
                    event.warningTicks() / 20,
                    event.triggered()).withStyle(event.triggered() ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA));
        }
        return component.getEvents().size();
    }

    private static int removeEvent(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        boolean removed = component(context).removeEvent(id);
        if (!removed) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.noellesroles.murder_time.not_found", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.removed", id).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int triggerEvent(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        String id = StringArgumentType.getString(context, "id");
        MurderTimeEventComponent component = component(context);
        MurderTimeEvent event = component.getEvent(id);
        if (event == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.noellesroles.murder_time.not_found", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean success = component.triggerEvent(level, event, true);
        context.getSource().sendSuccess(() -> Component.translatable(
                success ? "commands.noellesroles.murder_time.triggered"
                        : "commands.noellesroles.murder_time.trigger_failed",
                id).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return success ? 1 : 0;
    }

    private static int addEvent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id");
        int elapsedSeconds = IntegerArgumentType.getInteger(context, "elapsed_seconds");
        String actionRaw = StringArgumentType.getString(context, "action");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int durationOrCount = IntegerArgumentType.getInteger(context, "duration_seconds");

        MurderTimeAction action;
        try {
            action = MurderTimeAction.byName(actionRaw);
        } catch (IllegalArgumentException ex) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.noellesroles.murder_time.bad_action", actionRaw).withStyle(ChatFormatting.RED));
            return 0;
        }

        MurderTimeEvent event = new MurderTimeEvent(
                id,
                "murder_time.event.custom." + id,
                elapsedSeconds * 20,
                action,
                action == MurderTimeAction.DROP_GOLD ? durationOrCount : durationOrCount * 20,
                amount,
                30 * 20,
                false,
                -1);
        component(context).addEvent(event);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.noellesroles.murder_time.added", id).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int durationOrCount(MurderTimeEvent event) {
        return event.action() == MurderTimeAction.DROP_GOLD ? event.durationTicks() : event.durationTicks() / 20;
    }

    private static CompletableFuture<Suggestions> suggestEventIds(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        component(context).getEvents().stream()
                .map(MurderTimeEvent::id)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (MurderTimeAction action : MurderTimeAction.values()) {
            String id = action.name().toLowerCase(Locale.ROOT);
            if (id.startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
