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

package org.agmas.noellesroles.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.game.modes.funny.rotation.DraftOrderPriority;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * 职业选择「前置位」指令。
 *
 * <p>{@code /sre:draft_priority <玩家>}：标记该玩家<b>下次</b>参加志愿海选 / 职业轮选 /
 * 单选职业轮选时，选择职业的序号处于前置位（从 1 号开始）；一次性，消费后自动移出，
 * 不影响任何职业池与概率，只是排序提前。
 * <p>{@code /sre:draft_priority clear <玩家>}：撤销某个玩家的前置标记。
 * <p>{@code /sre:draft_priority clear}：清空全部前置标记。
 */
public final class DraftPriorityCommand {
    private DraftPriorityCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((CommandDispatcher<CommandSourceStack> dispatcher,
                registry, environment) -> dispatcher.register(
                Commands.literal("sre:draft_priority")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DraftPriorityCommand::set))
                        .then(Commands.literal("clear")
                                .executes(DraftPriorityCommand::clearAll)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(DraftPriorityCommand::clearOne)))));
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        DraftOrderPriority.add(target.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.draft_priority.set", target.getName()), true);
        return 1;
    }

    private static int clearOne(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        boolean removed = DraftOrderPriority.remove(target.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(
                removed ? "message.noellesroles.draft_priority.cleared_one"
                        : "message.noellesroles.draft_priority.not_pending",
                target.getName()), true);
        return removed ? 1 : 0;
    }

    private static int clearAll(CommandContext<CommandSourceStack> context) {
        Collection<UUID> snapshot = DraftOrderPriority.pendingSnapshot();
        snapshot.forEach(DraftOrderPriority::remove);
        int count = snapshot.size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.draft_priority.cleared_all", count), true);
        return count;
    }
}
