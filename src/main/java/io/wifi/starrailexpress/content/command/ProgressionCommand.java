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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.backpack.BackpackManager.ActivateResult;
import io.wifi.starrailexpress.backpack.FactionCardCooldown;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sre:pass activate <type> [target]} —— 使用一张阵营卡（固定阵营卡与自定义卡共用这一条命令）。
 *
 * <p>
 * 固定阵营卡只需要 {@code type}；自定义卡（{@code custom}）必须再给一个 {@code target}
 * （{@code killer} / {@code civilian} / {@code neutral} / {@code neutral_for_killer}），
 * 它就是本局要强制的目标阵营。背包界面弹屏选阵营后走的也是这条命令，
 * 使用 / 兑现 / 退款的规则统一在 {@link io.wifi.starrailexpress.backpack.FactionCardForcing}。
 */
public final class ProgressionCommand {
    private ProgressionCommand() {
    }

    /** 卡类型自动补全：除 {@link FactionCardType#NONE} 外的全部（含自定义卡）。 */
    private static final SuggestionProvider<CommandSourceStack> CARD_TYPES = (context, builder) -> {
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                builder.suggest(type.questKey);
            }
        }
        return builder.buildFuture();
    };

    /** 自定义卡的目标阵营自动补全：只有四个真实阵营。 */
    private static final SuggestionProvider<CommandSourceStack> TARGET_TYPES = (context, builder) -> {
        for (FactionCardType type : FactionCardType.values()) {
            if (type.isSelectableTarget()) {
                builder.suggest(type.questKey);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:pass")
                .then(Commands.literal("activate")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(CARD_TYPES)
                                .executes(context -> activate(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "type"),
                                        null))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(TARGET_TYPES)
                                        .executes(context -> activate(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "type"),
                                                StringArgumentType.getString(context, "target")))))));
    }

    private static int activate(ServerPlayer player, String rawType, String rawTarget) {
        FactionCardType type = FactionCardType.fromString(rawType);
        // 固定阵营卡的目标就是它自己；自定义卡的目标来自 target 参数
        FactionCardType target = type == FactionCardType.CUSTOM
                ? FactionCardType.targetFromString(rawTarget)
                : type;
        ActivateResult result = BackpackManager.activateCardResult(player, type, target);
        if (result == ActivateResult.SUCCESS) {
            return 1;
        }
        Component message = switch (result) {
            case ON_COOLDOWN -> Component.translatable("message.sre.progression.faction_card_on_cooldown",
                    Component.translatable(type.displayName),
                    FactionCardCooldown.formatRemaining(BackpackManager.remainingCooldownMs(player, type)));
            case ALREADY_ACTIVE -> Component.translatable("message.sre.progression.faction_card_already_active");
            case NEEDS_TARGET -> Component.translatable("message.sre.progression.faction_card_needs_target");
            case INVALID, SUCCESS -> Component.translatable("message.sre.progression.faction_card_activate_failed",
                    Component.translatable(type.displayName));
        };
        player.displayClientMessage(message, true);
        return 0;
    }
}
