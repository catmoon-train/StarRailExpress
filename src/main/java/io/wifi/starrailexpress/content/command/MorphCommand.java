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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.morph.MorphApi;
import io.wifi.starrailexpress.morph.MorphAppearance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * {@code /sre:morph} —— 把玩家变形（{@code MorphApi}）暴露给指令，语法与 {@code /sre:disguise} 同构：
 *
 * <pre>
 * /sre:morph start infinite|&lt;seconds&gt; player &lt;target&gt; &lt;player&gt;
 * /sre:morph start infinite|&lt;seconds&gt; random &lt;player&gt;
 * /sre:morph start infinite|&lt;seconds&gt; texture &lt;texture&gt; &lt;slim|wide&gt; &lt;player&gt;
 * /sre:morph clear &lt;player&gt;
 * /sre:morph clearall
 * /sre:morph query &lt;player&gt;
 * </pre>
 *
 * 时长和 disguise 一样必填：{@code infinite} 字面量或秒数。三种模式：
 * <ul>
 * <li>{@code player} —— 复制目标玩家（皮肤 / 帽子 / 名牌 / 身份玩偶全跟目标走）。</li>
 * <li>{@code random} —— 随机一名存活玩家（走 {@code MorphApi.morphToRandomPlayer} 的默认筛选）。</li>
 * <li>{@code texture} —— 指定贴图，贴图用原版 {@code ResourceLocationArgument}（任意路径，不做存在性校验），
 * 模型型别用 {@code slim|wide} 字面量。</li>
 * </ul>
 * 变形会被 {@code MorphManager} 既有的生命周期清空（开局 / 结束 / 玩家重置 / 离线），
 * 所以 {@code infinite} 只在**本局内**无限期，与 {@code /sre:disguise} 的语义一致。
 * <p>
 * 回显全部走翻译键（{@code commands.sre.morph.*} 与共用的 {@code commands.sre.duration.*}），
 * 玩家名以组件下发，因此每个客户端看到自己语言的名字。
 */
public final class MorphCommand {

    private static final String KEY = "commands.sre.morph.";
    /** 无限期字面量，与 {@link EntityDisguiseCommand#INFINITE} 保持一致。 */
    private static final String INFINITE = EntityDisguiseCommand.INFINITE;

    private MorphCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:morph")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.literal(INFINITE)
                                .then(modes(context -> 0)))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .then(modes(MorphCommand::secondsToTicks))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MorphCommand::clear)))
                .then(Commands.literal("clearall")
                        .executes(MorphCommand::clearAll))
                .then(Commands.literal("query")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MorphCommand::query))));
    }

    /** 三种变形模式，两个时长分支共用；时长由 {@code durationTicks} 提供（0 = 无限期）。 */
    private static LiteralArgumentBuilder<CommandSourceStack> modes(
            ToIntFunction<CommandContext<CommandSourceStack>> durationTicks) {
        return Commands.literal("player")
                .then(Commands.argument("target_player", EntityArgument.player())
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> applyPlayer(context, durationTicks.applyAsInt(context)))))
                .then(Commands.literal("random")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> applyRandom(context, durationTicks.applyAsInt(context)))))
                .then(Commands.literal("texture")
                        .then(Commands.argument("texture", ResourceLocationArgument.id())
                                .then(Commands.literal("slim")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> applyTexture(context, true,
                                                        durationTicks.applyAsInt(context)))))
                                .then(Commands.literal("wide")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> applyTexture(context, false,
                                                        durationTicks.applyAsInt(context)))))));
    }

    private static MutableComponent message(String key, Object... args) {
        return Component.translatable(KEY + "prefix").append(Component.translatable(KEY + key, args));
    }

    /** 时长短语，与 /sre:disguise 共用同一批键。 */
    private static Component durationText(int durationTicks) {
        return durationTicks > 0
                ? Component.translatable("commands.sre.duration.remaining", durationTicks / 20)
                : Component.translatable("commands.sre.duration.infinite");
    }

    private static int secondsToTicks(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "seconds") * 20;
    }

    private static int applyPlayer(CommandContext<CommandSourceStack> context, int durationTicks)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ServerPlayer look = EntityArgument.getPlayer(context, "target_player");
        if (target.getUUID().equals(look.getUUID())) {
            // 变成自己等于解除变形——MorphApi 内部就是这么处理的，这里直接报错更清楚。
            context.getSource().sendFailure(message("apply.self"));
            return 0;
        }
        boolean changed = MorphApi.morphToPlayer(target, look.getUUID(), durationTicks);
        return reportApply(context, target, changed, look.getDisplayName(), durationTicks);
    }

    private static int applyRandom(CommandContext<CommandSourceStack> context, int durationTicks)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        boolean changed = MorphApi.morphToRandomPlayer(target, durationTicks);
        if (!changed && MorphApi.getAppearance(target).isNone()) {
            // 没有候选（场上没有其他存活玩家）→ 与「被规则否决」一样报失败。
            context.getSource().sendFailure(message("apply.failed"));
            return 0;
        }
        return reportApply(context, target, changed, null, durationTicks);
    }

    private static int applyTexture(CommandContext<CommandSourceStack> context, boolean slim, int durationTicks)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ResourceLocation texture = ResourceLocationArgument.getId(context, "texture");
        boolean changed = MorphApi.morphToTexture(target, texture, slim, durationTicks);
        return reportApply(context, target, changed,
                Component.literal(texture.toString()),
                durationTicks);
    }

    /**
     * 统一回显。{@code changed == false} 有两种可能：状态没变（结束条件已刷新）或被
     * {@code AllowPlayerMorph} 否决——和 {@code /sre:disguise} 一样靠比对当前外观区分。
     */
    private static int reportApply(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean changed,
            @Nullable Component what, int durationTicks) {
        CommandSourceStack source = context.getSource();
        if (!changed) {
            if (!MorphApi.getAppearance(target).isNone()) {
                source.sendSuccess(() -> message(
                        durationTicks > 0 ? "apply.same.timed" : "apply.same.long",
                        target.getDisplayName(), durationTicks / 20), true);
                return 1;
            }
            source.sendFailure(message("apply.failed"));
            return 0;
        }
        Component shown = what == null ? describe(context.getSource().getServer(), MorphApi.getAppearance(target))
                : what;
        source.sendSuccess(() -> message(
                durationTicks > 0 ? "apply.timed" : "apply.success",
                target.getDisplayName(), shown, durationTicks / 20), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!MorphApi.clearMorph(target)) {
            source.sendFailure(message("clear.none", target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> message("clear.success", target.getDisplayName()), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int count = MorphApi.getMorphedPlayers().size();
        MorphApi.clearAllMorphs(server);
        source.sendSuccess(() -> message("clearall.success", count), true);
        return count > 0 ? 1 : 0;
    }

    private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        MorphAppearance appearance = MorphApi.getAppearance(target);
        if (appearance.isNone()) {
            source.sendSuccess(() -> message("query.none", target.getDisplayName()), false);
            return 0;
        }
        Component time = durationText(MorphApi.getRemainingTicks(target));
        if (appearance.isPlayer() && appearance.targetPlayer() != null) {
            Component who = nameOf(source.getServer(), appearance.targetPlayer());
            source.sendSuccess(() -> message("query.player", target.getDisplayName(), who, time), false);
            return 1;
        }
        if (appearance.isTexture() && appearance.texture() != null) {
            String shape = appearance.slim() ? "slim" : "wide";
            source.sendSuccess(() -> message("query.texture", target.getDisplayName(),
                    Component.literal(appearance.texture().toString()), shape, time), false);
            return 1;
        }
        source.sendSuccess(() -> message("query.none", target.getDisplayName()), false);
        return 0;
    }

    /** 变形目标的显示名：优先在线玩家，其次名字缓存，最后退回 UUID。 */
    private static Component describe(@Nullable MinecraftServer server, MorphAppearance appearance) {
        if (appearance.isPlayer() && appearance.targetPlayer() != null) {
            return nameOf(server, appearance.targetPlayer());
        }
        if (appearance.isTexture() && appearance.texture() != null) {
            return Component.literal(appearance.texture().toString());
        }
        return Component.empty();
    }

    private static Component nameOf(@Nullable MinecraftServer server, UUID uuid) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                return online.getDisplayName();
            }
            var profile = server.getProfileCache() == null ? null : server.getProfileCache().get(uuid);
            if (profile.isPresent()) {
                return Component.literal(profile.get().getName());
            }
        }
        return Component.literal(uuid.toString().substring(0, 8));
    }
}
