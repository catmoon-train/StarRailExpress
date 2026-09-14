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
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * {@code /sre:disguise} —— 通用实体伪装。
 *
 * <pre>
 * /sre:disguise start &lt;长期&gt; &lt;player&gt; &lt;entity_type&gt; [nbt]     长期伪装，直到手动解除
 * /sre:disguise start &lt;seconds&gt; &lt;player&gt; &lt;entity_type&gt; [nbt]  限时伪装，&lt;seconds&gt; 秒后自动解除
 * /sre:disguise clear &lt;player&gt;                                解除伪装
 * /sre:disguise query &lt;player&gt;                                查询是否处于伪装状态
 * </pre>
 *
 * 时长是**必填**的：要么是 {@code 长期} 字面量，要么是秒数——不再有「不写时长就默认长期」的隐式形式。
 * 两个时长分支都带可选的 {@code [nbt]}（外观 NBT）。
 * <p>
 * 所有动作都是子命令，根节点下不再出现「当动作用的裸参数」——顺带避免了玩家名恰好叫
 * {@code clear} / {@code query} 时选不中的歧义（Brigadier 会优先匹配字面量）。
 * <p>
 * 所有回显都走翻译键（{@code commands.sre.entitydisguise.*}，见
 * {@code assets/starrailexpress/lang}），玩家名与实体名以组件形式下发，
 * 因此每个客户端看到的都是自己语言的名字。
 */
public final class EntityDisguiseCommand {

    private static final String KEY = "commands.sre.entitydisguise.";
    /** 长期（无限期）伪装的字面量；与 {@code /sre:morph} 共用同一个写法。 */
    public static final String INFINITE = "infinite";

    private EntityDisguiseCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("sre:disguise")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.literal(INFINITE)
                                .then(target(context -> 0, buildContext)))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .then(target(EntityDisguiseCommand::secondsToTicks, buildContext))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(EntityDisguiseCommand::clear)))
                .then(Commands.literal("query")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(EntityDisguiseCommand::query))));
    }

    /**
     * {@code start} 的第二段：{@code <player> <entity_type> [nbt]}，两个时长分支共用，
     * 由 {@code durationTicks} 提供该分支的时长（0 = 无限期）。
     * <p>
     * 实体类型用的是原版 {@code minecraft:resource} 参数（{@code /summon} 那个），
     * 所以候选与错误提示都跟原版一致，本模组不需要注册任何参数类型。
     */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> target(
            ToIntFunction<CommandContext<CommandSourceStack>> durationTicks, CommandBuildContext buildContext) {
        return Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("entity_type", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                        .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                        .executes(context -> apply(context, durationTicks.applyAsInt(context), null))
                        .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(context -> apply(context, durationTicks.applyAsInt(context),
                                        CompoundTagArgument.getCompoundTag(context, "nbt")))));
    }

    /** 翻译键消息：[EntityDisguise] 前缀 + 正文。 */
    private static MutableComponent message(String key, Object... args) {
        return Component.translatable(KEY + "prefix").append(Component.translatable(KEY + key, args));
    }

    private static int secondsToTicks(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "seconds") * 20;
    }

    private static int apply(CommandContext<CommandSourceStack> context, int durationTicks, @Nullable CompoundTag nbt)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        EntityType<?> type = ResourceArgument.getEntityType(context, "entity_type").value();
        int seconds = durationTicks / 20;

        if (type == EntityType.PLAYER) {
            // 原版参数会放行 minecraft:player，但玩家模型包装不成实体，得给个明确指向。
            source.sendFailure(message("player_not_supported"));
            return 0;
        }
        if (!EntityDisguise.disguise(target, type, nbt, durationTicks)) {
            // 外观没变时 set 返回 false（结束条件其实已经更新），别把它报成失败。
            if (EntityDisguise.get(target).type() == type) {
                source.sendSuccess(() -> message(
                        durationTicks > 0 ? "apply.same.timed" : "apply.same.long",
                        target.getDisplayName(), seconds), true);
                return 1;
            }
            source.sendFailure(message("apply.failed"));
            return 0;
        }
        source.sendSuccess(() -> message(
                durationTicks > 0 ? "apply.timed" : "apply.success",
                target.getDisplayName(), EntityDisguise.displayName(type), seconds), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!EntityDisguise.clear(target)) {
            source.sendFailure(message("clear.none", target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> message("clear.success", target.getDisplayName()), true);
        return 1;
    }

    private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        EntityDisguiseState state = EntityDisguise.get(target);
        if (state.isNone()) {
            source.sendSuccess(() -> message("query.none", target.getDisplayName()), false);
            return 0;
        }
        CompoundTag nbt = state.nbt();
        String eyeHeight = String.format(Locale.ROOT, "%.2f", state.eyeHeight());
        int nbtBytes = nbt == null ? 0 : nbt.sizeInBytes();
        // 结束条件：到期时间 / 由 predicate 结束 / 无限期。纯服务端读取，不涉及包体。
        int remaining = EntityDisguise.getRemainingTicks(target);
        Component endInfo = remaining > 0
                ? Component.translatable("commands.sre.duration.remaining", remaining / 20)
                : EntityDisguise.hasEndPredicate(target)
                        ? Component.translatable("commands.sre.duration.condition")
                        : Component.translatable("commands.sre.duration.infinite");
        source.sendSuccess(() -> message("query.disguised", target.getDisplayName(),
                EntityDisguise.displayName(state.type()), eyeHeight, nbtBytes, endInfo), false);
        return 1;
    }
}
