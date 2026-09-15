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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.scene.MapStatusBarRuntime;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /sre:state} —— 修改地图配置里可选的「状态条」数值（温度 / 渴度 / 饱食度 / 污染值）。
 *
 * <p>
 * 结构：{@code /sre:state <状态名|all|now> add|set|get <玩家> [数值]}
 * <ul>
 * <li>状态名：{@code warmth} / {@code thirst} / {@code hunger} / {@code pollution}（忽略大小写），
 * 必须是当前地图配置正在使用的那个；</li>
 * <li>{@code now}：当前游玩地图配置里实际使用的状态条（不用记名字）；</li>
 * <li>{@code all}：不挑类型，直接作用于该玩家<b>当前实际使用</b>的状态条（还没被追踪时回退到地图配置）；</li>
 * <li>{@code add} 支持负数、{@code set} 直接赋值、{@code get} 不需要数值。</li>
 * </ul>
 *
 * <p>
 * 数值范围与状态条本身一致（{@code 0} ~ {@link MapStatusBarRuntime#MAX_VALUE}），超出会被参数层直接拒绝；
 * 数值降到 0 时的死亡判定沿用状态条原有规则（由运行时 tick 处理）。
 */
public final class StateCommand {

    /** 与状态条数值一致的取值范围。 */
    private static final int MAX = MapStatusBarRuntime.MAX_VALUE;

    private static final String ALL = "all";
    private static final String NOW = "now";

    private StateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:state")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Stream.concat(Stream.of(ALL, NOW), typeNames()), builder))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(-MAX, MAX))
                                                .executes(context -> executeChange(context, true)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, MAX))
                                                .executes(context -> executeChange(context, false)))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(StateCommand::executeGet)))));
    }

    // ==================== add / set ====================

    private static int executeChange(CommandContext<CommandSourceStack> context, boolean add)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String selector = StringArgumentType.getString(context, "state");
        if (!validateSelector(source, selector)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int amount = IntegerArgumentType.getInteger(context, "amount");

        int applied = 0;
        for (ServerPlayer target : targets) {
            MapStatusBarType type = resolveType(target, selector);
            boolean success = type != null
                    && (add ? MapStatusBarRuntime.addValue(target, type, amount)
                            : MapStatusBarRuntime.setValue(target, type, amount));
            if (!success) {
                source.sendFailure(Component.translatable("commands.sre.state.not_tracked", target.getName()));
                continue;
            }
            applied++;
            final MapStatusBarType resultType = type;
            final int current = MapStatusBarRuntime.getValue(target, type);
            final int delta = amount;
            if (add) {
                source.sendSuccess(() -> Component.translatable("commands.sre.state.add",
                        target.getName(), barName(resultType), signed(delta), current), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.sre.state.set",
                        target.getName(), barName(resultType), current), true);
            }
        }
        return applied;
    }

    // ==================== get ====================

    private static int executeGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String selector = StringArgumentType.getString(context, "state");
        if (!validateSelector(source, selector)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");

        int total = 0;
        for (ServerPlayer target : targets) {
            MapStatusBarType type = resolveType(target, selector);
            Integer value = type == null ? null : MapStatusBarRuntime.getValue(target, type);
            if (value == null) {
                source.sendFailure(Component.translatable("commands.sre.state.not_tracked", target.getName()));
                continue;
            }
            total += value;
            final MapStatusBarType resultType = type;
            final int current = value;
            source.sendSuccess(() -> Component.translatable("commands.sre.state.get",
                    target.getName(), barName(resultType), current, MAX), false);
        }
        return total;
    }

    // ==================== 参数解析 ====================

    /**
     * 与具体玩家无关的校验：状态名是否合法、地图有没有启用状态条、具名状态条是否就是本图在用的那个。
     *
     * @return 校验通过返回 {@code true}；失败时会向来源发送失败提示
     */
    private static boolean validateSelector(CommandSourceStack source, String selector) {
        if (ALL.equalsIgnoreCase(selector)) {
            return true;
        }
        MapStatusBarType mapType = MapStatusBarRuntime.getMapStatusBarType(source.getLevel());
        if (mapType == MapStatusBarType.NONE) {
            source.sendFailure(Component.translatable("commands.sre.state.not_enabled"));
            return false;
        }
        MapStatusBarType type = NOW.equalsIgnoreCase(selector) ? mapType : MapStatusBarType.byName(selector);
        if (type == MapStatusBarType.NONE) {
            source.sendFailure(Component.translatable("commands.sre.state.unknown", selector, availableNames()));
            return false;
        }
        if (type != mapType) {
            source.sendFailure(Component.translatable("commands.sre.state.mismatch",
                    barName(mapType), barName(type)));
            return false;
        }
        return true;
    }

    /**
     * 针对具体玩家解析目标状态条。
     *
     * @return {@code all} 取该玩家当前使用的状态条；其余取字面类型；解析不到（{@link MapStatusBarType#NONE}）返回 {@code null}
     */
    private static MapStatusBarType resolveType(ServerPlayer target, String selector) {
        MapStatusBarType type;
        if (ALL.equalsIgnoreCase(selector)) {
            type = MapStatusBarRuntime.getTrackedType(target);
        } else if (NOW.equalsIgnoreCase(selector)) {
            type = MapStatusBarRuntime.getMapStatusBarType(target.level());
        } else {
            type = MapStatusBarType.byName(selector);
        }
        return type == MapStatusBarType.NONE ? null : type;
    }

    /** 状态条显示名（复用地图配置界面的翻译键）。 */
    private static Component barName(MapStatusBarType type) {
        return Component.translatable("sre.map_helper.settings.mapStatusBar." + type.name());
    }

    /** 补全用：全部状态条英文名（小写，不含 NONE）。 */
    private static Stream<String> typeNames() {
        return Arrays.stream(MapStatusBarType.values())
                .filter(type -> type != MapStatusBarType.NONE)
                .map(type -> type.name().toLowerCase(Locale.ROOT));
    }

    /** 错误提示里列出的可用状态名。 */
    private static String availableNames() {
        return typeNames().collect(Collectors.joining(", "));
    }

    /** 数值带正负号，便于在 add 的回显里看出方向。 */
    private static String signed(int amount) {
        return amount >= 0 ? "+" + amount : String.valueOf(amount);
    }
}
