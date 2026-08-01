/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;

public class ParticipationCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:participate")
                .executes(ParticipationCommand::toggle)
                .then(Commands.literal("join").executes(context -> setParticipating(context, true)))
                .then(Commands.literal("leave").executes(context -> setParticipating(context, false)))
                .then(Commands.literal("status").executes(ParticipationCommand::status))
                .then(Commands.literal("list")
                        .executes(ParticipationCommand::list))
                .then(Commands.literal("set")
                        // 仅 OP（权限等级 2）可修改他人参与状态
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> setOthers(context, null))
                                .then(Commands.literal("join").executes(context -> setOthers(context, true)))
                                .then(Commands.literal("leave").executes(context -> setOthers(context, false))))));
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            return 0;
        }
        boolean participating = ParticipationComponent.KEY.get(player.level()).toggleParticipating(player);
        sendStatus(context, player, participating);
        return 1;
    }

    private static int setParticipating(CommandContext<CommandSourceStack> context, boolean participating) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            return 0;
        }
        ParticipationComponent.KEY.get(player.level()).setParticipating(player.getUUID(), participating);
        sendStatus(context, player, participating);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            return 0;
        }
        boolean participating = ParticipationComponent.KEY.get(player.level()).isParticipating(player);
        sendStatus(context, player, participating);
        return 1;
    }

    /** 列出所有在线玩家的参与状态；OP 可点击对应文本切换玩家的参与状态。 */
    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Level level = source.getLevel();
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        boolean canToggle = source.hasPermission(2);

        long optedOut = players.stream()
                .filter(p -> !ParticipationComponent.KEY.get(p.level()).isParticipating(p))
                .count();

        source.sendSuccess(() -> Component.translatable("commands.sre.participation.list.title", optedOut)
                .withStyle(ChatFormatting.GOLD), false);

        if (players.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.sre.participation.list.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        for (ServerPlayer p : players) {
            boolean participating = ParticipationComponent.KEY.get(p.level()).isParticipating(p);
            String name = p.getGameProfile().getName();
            MutableComponent statusLabel = Component.translatable(participating
                    ? "commands.sre.participation.list.participating"
                    : "commands.sre.participation.list.not_participating")
                    .withStyle(participating ? ChatFormatting.GREEN : ChatFormatting.GOLD);

            MutableComponent line = Component.translatable("commands.sre.participation.list.line",
                    Component.literal(name).withStyle(ChatFormatting.WHITE), statusLabel);

            if (canToggle) {
                line = line.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/tmm:participate set " + name))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.sre.participation.list.toggle_hint", name))));
            }

            final MutableComponent finalLine = line;
            source.sendSuccess(() -> finalLine, false);
        }
        return 1;
    }

    /** 设置/切换其他玩家的参与状态（OP）。target 为 null 表示切换。 */
    private static int setOthers(CommandContext<CommandSourceStack> context, Boolean target)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int count = 0;
        for (ServerPlayer p : targets) {
            ParticipationComponent comp = ParticipationComponent.KEY.get(p.level());
            boolean newState = target != null ? target : !comp.isParticipating(p);
            comp.setParticipating(p.getUUID(), newState);

            // 通知被设置的玩家
            p.sendSystemMessage(Component.translatable(newState
                    ? "commands.sre.participation.joined"
                    : "commands.sre.participation.left"));

            // 反馈给执行者
            String name = p.getGameProfile().getName();
            final boolean fNew = newState;
            source.sendSuccess(() -> Component.translatable(fNew
                    ? "commands.sre.participation.set_other.join"
                    : "commands.sre.participation.set_other.leave", name), true);
            count++;
        }
        return count;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("commands.sre.participation.player_only"));
        }
        return player;
    }

    private static void sendStatus(CommandContext<CommandSourceStack> context, ServerPlayer player,
            boolean participating) {
        boolean running = SREGameWorldComponent.KEY.get(player.level()).isRunning();
        String key = participating ? "commands.sre.participation.joined" : "commands.sre.participation.left";
        context.getSource().sendSuccess(() -> Component.translatable(key), false);
        if (running) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.sre.participation.next_round"), false);
        }
    }
}
