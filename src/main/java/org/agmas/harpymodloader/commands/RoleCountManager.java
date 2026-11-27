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

package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class RoleCountManager {

    // 用于存储手动设置的杀手和侦探数量
    public static int forcedKillerCount = -1; // -1表示使用自动计算
    public static int forcedVigilanteCount = -1; // -1表示使用自动计算
    public static int forcedNeutralCount = -1; // -1表示使用自动计算

    /**
     * 「自动」的哨兵值：{@code /setRoleCount <kind> -1} 让该种类回到「按人数自动算」。
     *
     * <p>
     * 下面三个 get*Count 都按 {@code >= 0} 判断是否强制，所以 -1 天然表示自动。
     */
    public static final int AUTO = -1;

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setRoleCount")
                .requires(serverCommandSource -> serverCommandSource
                        .hasPermission(SREConfig.instance().modifyEnableStatusRequiredPermission))
                .then(Commands.literal("killer")
                        .then(Commands.argument("count", IntegerArgumentType.integer(AUTO))
                                .executes(RoleCountManager::setKillerCount)))
                .then(Commands.literal("vigilante")
                        .then(Commands.argument("count", IntegerArgumentType.integer(AUTO))
                                .executes(RoleCountManager::setVigilanteCount)))
                .then(Commands.literal("neutral")
                        .then(Commands.argument("count", IntegerArgumentType.integer(AUTO))
                                .executes(RoleCountManager::setNeutralCount)))
                // 单个种类恢复自动：/setRoleCount killer|vigilante|neutral -1
                // 设置页三个人数行各有一个「自动」按钮，点它不能把另外两种也一起重置，
                // 所以这里必须支持按种类恢复；下面的 reset 保留原语义（三种一起清空）。
                .then(Commands.literal("reset")
                        .executes(RoleCountManager::resetCounts))
                .executes(RoleCountManager::resetCounts));
    }

    private static int setNeutralCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedNeutralCount = count;
        if (count == -1) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.auto",
                    Component.translatable("display.type.role.neutral_all")), false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setrolecount.set",
                            Component.translatable("display.type.role.neutral_all"), count),
                    false);
        }
        return 1;
    }

    private static int setKillerCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedKillerCount = count;
        if (count == -1) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.auto",
                    Component.translatable("display.type.role.killer")), false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setrolecount.set",
                            Component.translatable("display.type.role.killer"), count),
                    false);
        }
        return 1;
    }

    private static int setVigilanteCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedVigilanteCount = count;
        if (count == -1) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.auto",
                    Component.translatable("display.type.role.vigilante")), false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setrolecount.set",
                            Component.translatable("display.type.role.vigilante"), count),
                    false);
        }
        return 1;
    }

    private static int resetCounts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        forcedKillerCount = -1;
        forcedVigilanteCount = -1;
        forcedNeutralCount = -1;
        context.getSource().sendSuccess(() -> Component.translatable("Reseted!"), false);
        return 1;
    }

    // 获取实际使用的杀手数量（考虑强制设置或自动计算）
    public static int getKillerCount(int playerCount) {
        Integer override = io.wifi.starrailexpress.event.GameplayExtensionEvents.KILLER_COUNT_OVERRIDE.invoker().get(playerCount);
        if (override != null) return override;
        if (forcedKillerCount >= 0) {
            return Math.min(forcedKillerCount, playerCount); // 确保不超过玩家总数
        } else {
            return autoKillerCount(playerCount);
        }
    }

    // 获取实际使用的侦探数量（考虑强制设置或自动计算）
    // 注意：这里刻意不跟随 getKillerCount 的 KILLER_COUNT_OVERRIDE。
    // 命令方块事件局会占满杀手名额（killer=0），但警长仍按人数正常分配（警长不会被标为异常）。
    // 两侧数量是否真的落地由 SREMurderGameMode 的角色池补位与收尾校验负责。
    public static int getVigilanteCount(int playerCount) {
        if (forcedVigilanteCount >= 0) {
            return Math.min(forcedVigilanteCount, playerCount); // 确保不超过玩家总数
        } else {
            return autoVigilanteCount(playerCount);

        }
    }

    public static int getNeutralCount(int playerCount) {
        if (forcedNeutralCount >= 0) {
            return Math.min(forcedNeutralCount, playerCount);
        }
        return autoNeutralCount(playerCount);
    }

    // ══════════════════════════════════════════════════════════════════
    // 自动公式（唯一实现）
    //
    // 设置页要显示「强制值是多少 / 自动会是多少」，所以把公式单独暴露出来：
    // 上面三个 get*Count 与设置页的快照包的都走这里，公式只有一份。
    // ══════════════════════════════════════════════════════════════════

    /** 自动公式下的杀手数量：{@code 玩家数 / 6}。 */
    public static int autoKillerCount(int playerCount) {
        return playerCount / 6;
    }

    /**
     * 自动公式下的义警（侦探）数量：{@code 玩家数 / 6}。
     *
     * <p>
     * 与杀手同式但<b>刻意不共用</b>一个方法：命令方块事件局会把杀手压到 0（见
     * {@link #getKillerCount}），而义警仍按人数正常分配，两者口径不同。
     */
    public static int autoVigilanteCount(int playerCount) {
        return playerCount / 6;
    }

    /** 自动公式下的中立数量：玩家数不到阈值就是 0，否则至少 1。 */
    public static int autoNeutralCount(int playerCount) {
        int minPlayer = HarpyModLoaderConfig.HANDLER.instance().neutralMinPlayerCount;
        if (playerCount <= minPlayer) {
            return 0;
        }
        return Math.max(1, playerCount / 6);
    }
}