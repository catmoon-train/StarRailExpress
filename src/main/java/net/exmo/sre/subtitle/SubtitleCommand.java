package net.exmo.sre.subtitle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.network.PacketTracker;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

/**
 * /sre:subtitle 命令 —— 向玩家发送 COD 风格报幕字幕。
 *
 * <pre>
 * 基本用法：
 *   /sre:subtitle &lt;player&gt; &lt;mainText&gt; [subText] [durationTicks] [color] [typewriter] [position]
 *
 * position: center（屏幕中央，默认）| top（屏幕顶部）| bottom（屏幕底部）
 * </pre>
 */
public class SubtitleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            Commands.literal("sre:subtitle")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                    .then(Commands.argument("mainText", ComponentArgument.textComponent(registryAccess))
                        // /sre:subtitle <player> <mainText>
                        .executes(ctx -> execute(ctx, Component.empty(), 100, 0xFFFFFFFF, false, SubtitleS2CPayload.POS_CENTER))
                        .then(Commands.argument("subText", ComponentArgument.textComponent(registryAccess))
                            // /sre:subtitle <player> <mainText> <subText>
                            .executes(ctx -> execute(ctx,
                                ComponentArgument.getComponent(ctx, "subText"),
                                100, 0xFFFFFFFF, false, SubtitleS2CPayload.POS_CENTER))
                            .then(Commands.argument("durationTicks", IntegerArgumentType.integer(20, 6000))
                                // /sre:subtitle <player> <mainText> <subText> <duration>
                                .executes(ctx -> execute(ctx,
                                    ComponentArgument.getComponent(ctx, "subText"),
                                    IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                    0xFFFFFFFF, false, SubtitleS2CPayload.POS_CENTER))
                                .then(Commands.argument("color", ColorArgument.color())
                                    // /sre:subtitle <player> <mainText> <subText> <duration> <color>
                                    .executes(ctx -> execute(ctx,
                                        ComponentArgument.getComponent(ctx, "subText"),
                                        IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                        ColorArgument.getColor(ctx, "color").getColor(),
                                        false, SubtitleS2CPayload.POS_CENTER))
                                    .then(Commands.argument("typewriter", BoolArgumentType.bool())
                                        // /sre:subtitle <player> <mainText> <subText> <duration> <color> <typewriter>
                                        .executes(ctx -> execute(ctx,
                                            ComponentArgument.getComponent(ctx, "subText"),
                                            IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                            ColorArgument.getColor(ctx, "color").getColor(),
                                            BoolArgumentType.getBool(ctx, "typewriter"),
                                            SubtitleS2CPayload.POS_CENTER))
                                        .then(Commands.literal("center")
                                            .executes(ctx -> execute(ctx,
                                                ComponentArgument.getComponent(ctx, "subText"),
                                                IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                ColorArgument.getColor(ctx, "color").getColor(),
                                                BoolArgumentType.getBool(ctx, "typewriter"),
                                                SubtitleS2CPayload.POS_CENTER))
                                        )
                                        .then(Commands.literal("top")
                                            .executes(ctx -> execute(ctx,
                                                ComponentArgument.getComponent(ctx, "subText"),
                                                IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                ColorArgument.getColor(ctx, "color").getColor(),
                                                BoolArgumentType.getBool(ctx, "typewriter"),
                                                SubtitleS2CPayload.POS_TOP))
                                        )
                                        .then(Commands.literal("bottom")
                                            .executes(ctx -> execute(ctx,
                                                ComponentArgument.getComponent(ctx, "subText"),
                                                IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                ColorArgument.getColor(ctx, "color").getColor(),
                                                BoolArgumentType.getBool(ctx, "typewriter"),
                                                SubtitleS2CPayload.POS_BOTTOM))
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx,
                               Component subText, int durationTicks, int color, boolean typewriter, int position)
            throws CommandSyntaxException {

        Component mainText = ComponentArgument.getComponent(ctx, "mainText");
        var players = EntityArgument.getPlayers(ctx, "player");

        for (ServerPlayer player : players) {
            Component resolvedMain = ComponentUtils.updateForEntity(ctx.getSource(), mainText, player, 0);
            Component resolvedSub  = ComponentUtils.updateForEntity(ctx.getSource(), subText, player, 0);

            SubtitleS2CPayload payload = new SubtitleS2CPayload(
                    resolvedMain, resolvedSub, durationTicks, color, typewriter, position);
            PacketTracker.sendToClient(player, payload);
        }

        String posLabel;
        if (position == SubtitleS2CPayload.POS_TOP) posLabel = "top";
        else if (position == SubtitleS2CPayload.POS_BOTTOM) posLabel = "bottom";
        else posLabel = "center";
        ctx.getSource().sendSuccess(
                () -> Component.translatable("Send subtitle to %d player(s) [%s]. Main: %s",
                        players.size(), posLabel, mainText.getString()),
                true);
        return players.size();
    }

    // ---- 编程 API（给其他代码调用） ----

    /** 简单发送字幕给单个玩家（CENTER 模式） */
    public static void sendToPlayer(ServerPlayer player, Component mainText) {
        var payload = new SubtitleS2CPayload(mainText);
        PacketTracker.sendToClient(player, payload);
    }

    /** 发送 TOP 模式字幕给单个玩家（方便在任务系统中调用） */
    public static void sendToPlayerTop(ServerPlayer player, Component mainText, Component subText, int durationTicks) {
        var payload = new SubtitleS2CPayload(mainText, subText, durationTicks, 0xFFFFFFFF, false, SubtitleS2CPayload.POS_TOP);
        PacketTracker.sendToClient(player, payload);
    }

    /** 发送 BOTTOM 模式字幕给单个玩家 */
    public static void sendToPlayerBottom(ServerPlayer player, Component mainText, Component subText, int durationTicks) {
        var payload = new SubtitleS2CPayload(mainText, subText, durationTicks, 0xFFFFFFFF, false, SubtitleS2CPayload.POS_BOTTOM);
        PacketTracker.sendToClient(player, payload);
    }

    /** 发送字幕给单个玩家（完整参数） */
    public static void sendToPlayer(ServerPlayer player, Component mainText, Component subText,
                                     int durationTicks, int color, boolean typewriter, int screenPosition) {
        var payload = new SubtitleS2CPayload(mainText, subText, durationTicks, color, typewriter, screenPosition);
        PacketTracker.sendToClient(player, payload);
    }

    /** 发送字幕给所有在线玩家（CENTER 模式） */
    public static void sendToAll(Component mainText) {
        sendToAll(mainText, Component.empty(), 100, 0xFFFFFFFF, false, SubtitleS2CPayload.POS_CENTER);
    }

    /** 发送字幕给所有在线玩家（完整参数） */
    public static void sendToAll(Component mainText, Component subText,
                                  int durationTicks, int color, boolean typewriter, int screenPosition) {
        var server = io.wifi.starrailexpress.SRE.SERVER;
        if (server == null) return;
        var payload = new SubtitleS2CPayload(mainText, subText, durationTicks, color, typewriter, screenPosition);
        for (var player : server.getPlayerList().getPlayers()) {
            PacketTracker.sendToClient(player, payload);
        }
    }
}
