package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.screen.ReplayScreenSavedData;
import io.wifi.starrailexpress.api.replay.screen.ReplayScreenService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class ReplayScreenCommand {
    private static final String[] DIRECTIONS = { "north", "south", "east", "west" };

    private ReplayScreenCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:replay_screen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                                                .then(Commands.argument("height", IntegerArgumentType.integer(1, 32))
                                                        .then(Commands.argument("direction", StringArgumentType.word())
                                                                .suggests((context, builder) -> SharedSuggestionProvider
                                                                        .suggest(DIRECTIONS, builder))
                                                                .executes(ReplayScreenCommand::create)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ReplayScreenCommand::remove)))
                .then(Commands.literal("list").executes(ReplayScreenCommand::list))
                .then(Commands.literal("set_default")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ReplayScreenCommand::setDefault)))
                .then(Commands.literal("show")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ReplayScreenCommand::show))));
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        String id = StringArgumentType.getString(context, "id");
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        int width = IntegerArgumentType.getInteger(context, "width");
        int height = IntegerArgumentType.getInteger(context, "height");
        Direction direction = parseDirection(StringArgumentType.getString(context, "direction"));
        if (direction == null) {
            context.getSource().sendFailure(Component.literal("Direction must be north, south, east, or west."));
            return 0;
        }
        ReplayScreenService.createScreen(level, id, pos, width, height, direction);
        context.getSource().sendSuccess(() -> Component.literal("Created replay screen '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        boolean removed = ReplayScreenService.removeScreen(context.getSource().getLevel(), id);
        if (!removed) {
            context.getSource().sendFailure(Component.literal("Replay screen not found: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed replay screen '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ReplayScreenSavedData data = ReplayScreenSavedData.get(context.getSource().getLevel());
        if (data.screens().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No replay screens configured.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        Component message = Component.literal("Replay screens: ").withStyle(ChatFormatting.GOLD);
        for (ReplayScreenSavedData.ReplayScreenEntry entry : data.screens().values()) {
            boolean isDefault = entry.id().equals(data.defaultScreenId());
            message = message.copy().append(Component.literal("\n- " + entry.id()
                    + " [" + entry.width() + "x" + entry.height() + " "
                    + entry.direction().getSerializedName() + "]"
                    + (isDefault ? " default" : "")).withStyle(isDefault ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
        Component result = message;
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setDefault(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ReplayScreenSavedData data = ReplayScreenSavedData.get(context.getSource().getLevel());
        if (!data.setDefaultScreen(id)) {
            context.getSource().sendFailure(Component.literal("Replay screen not found: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Default replay screen set to '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        if (SRE.REPLAY_MANAGER == null || !ReplayScreenService.show(context.getSource().getLevel(), id, SRE.REPLAY_MANAGER)) {
            context.getSource().sendFailure(Component.literal("Unable to show replay on screen: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Showing replay on screen '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Direction parseDirection(String value) {
        Direction direction = Direction.byName(value);
        if (direction == null || direction.getAxis().isVertical()) {
            return null;
        }
        return direction;
    }
}
