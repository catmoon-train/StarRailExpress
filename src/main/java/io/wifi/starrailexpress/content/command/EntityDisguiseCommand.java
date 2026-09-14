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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.content.command.argument.EntityTypeArgumentType;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code /sre:disguise} —— 通用实体伪装。
 *
 * <pre>
 * /sre:disguise &lt;player&gt; &lt;entity_type&gt; [nbt]                    长期伪装，直到手动解除
 * /sre:disguise seconds &lt;seconds&gt; &lt;player&gt; &lt;entity_type&gt; [nbt]  限时伪装
 * /sre:disguise clear &lt;player&gt;                                   解除伪装
 * /sre:disguise query &lt;player&gt;                                   查询是否处于伪装状态
 * </pre>
 *
 * 所有回显都走翻译键（{@code commands.sre.entitydisguise.*}，见
 * {@code assets/starrailexpress/lang}），玩家名与实体名以组件形式下发，
 * 因此每个客户端看到的都是自己语言的名字。
 */
public final class EntityDisguiseCommand {

    private static final String KEY = "commands.sre.entitydisguise.";

    private EntityDisguiseCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:disguise")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(EntityDisguiseCommand::clear)))
                .then(Commands.literal("query")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(EntityDisguiseCommand::query)))
                .then(Commands.literal("seconds")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("entity_type", EntityTypeArgumentType.entityType())
                                                .executes(context -> apply(context,
                                                        secondsToTicks(context), null))
                                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                                        .executes(context -> apply(context,
                                                                secondsToTicks(context),
                                                                CompoundTagArgument.getCompoundTag(context, "nbt"))))))))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("entity_type", EntityTypeArgumentType.entityType())
                                .executes(context -> apply(context, 0, null))
                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(context -> apply(context, 0,
                                                CompoundTagArgument.getCompoundTag(context, "nbt")))))));
    }

    /** 翻译键消息：[EntityDisguise] 前缀 + 正文。 */
    private static MutableComponent message(String key, Object... args) {
        return Component.translatable(KEY + "prefix").append(Component.translatable(KEY + key, args));
    }

    /** 实体类型的本地化名字；模组实体没提供翻译时回退到 {@code namespace:path}。 */
    private static Component entityName(EntityType<?> type) {
        return Component.translatableWithFallback(type.getDescriptionId(), EntityType.getKey(type).toString());
    }

    private static int secondsToTicks(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "seconds") * 20;
    }

    private static int apply(CommandContext<CommandSourceStack> context, int durationTicks, @Nullable CompoundTag nbt)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        EntityType<?> type = EntityTypeArgumentType.getEntityType(context, "entity_type");
        int seconds = durationTicks / 20;

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
                target.getDisplayName(), entityName(type), seconds), true);
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
        source.sendSuccess(() -> message("query.disguised", target.getDisplayName(),
                entityName(state.type()), eyeHeight, nbtBytes), false);
        return 1;
    }
}
