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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.RoundOverrides;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /tmm:round}：设置<b>下一局</b>的时长与初始金币（游戏设置页也走这里）。
 *
 * <p>
 * 为什么单独一个指令，而不是塞进 {@code /tmm:config}：
 * <ul>
 * <li>{@code /tmm:config} 改的是<b>配置文件</b>（要权限 3、会写盘、影响之后所有局）；
 * 这两个值要的是「只影响下一局、改完立刻生效、重启就清空」；</li>
 * <li>配置项不会把新值推给客户端，设置页改了以后自己读不到真值；本指令写完会广播快照。</li>
 * </ul>
 * 状态存在 {@link RoundOverrides}，值用 {@code -1}（时长）/ {@code 0}（金币）表示「跟随默认」。
 */
public class RoundSettingsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:round")
                .requires(source -> source.hasPermission(SREConfig.instance().startGameRequiredPermission))
                .executes(RoundSettingsCommand::show)
                // tmm:round time <分钟>   0 / -1 = 跟随模式默认
                .then(Commands.literal("time")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(-1, 180))
                                .executes(ctx -> setMinutes(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))))
                // tmm:round money <金币>  -1 = 跟随配置，0 = 不发钱
                .then(Commands.literal("money")
                        .then(Commands.argument("coins", IntegerArgumentType.integer(-1, 100000))
                                .executes(ctx -> setCoins(ctx, IntegerArgumentType.getInteger(ctx, "coins")))))
                .then(Commands.literal("reset").executes(RoundSettingsCommand::reset)));
    }

    private static int setMinutes(CommandContext<CommandSourceStack> ctx, int minutes) {
        boolean changed = RoundOverrides.setMinutes(minutes);
        RoundOverrides.syncToAll(ctx.getSource().getServer());
        int value = RoundOverrides.minutes();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                value > 0 ? "commands.sre.round.time.set" : "commands.sre.round.time.auto",
                value > 0 ? value : 0), true);
        return changed ? 1 : 0;
    }

    private static int setCoins(CommandContext<CommandSourceStack> ctx, int coins) {
        boolean changed = RoundOverrides.setCoins(coins);
        RoundOverrides.syncToAll(ctx.getSource().getServer());
        int value = RoundOverrides.coins();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                value >= 0 ? "commands.sre.round.money.set" : "commands.sre.round.money.auto",
                value >= 0 ? value : 0), true);
        return changed ? 1 : 0;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        RoundOverrides.reset();
        RoundOverrides.syncToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.round.reset"), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        int minutes = RoundOverrides.minutes();
        int coins = RoundOverrides.coins();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.round.status",
                minutes > 0 ? String.valueOf(minutes) : "-",
                coins > 0 ? String.valueOf(coins) : "-"), false);
        return 1;
    }
}
