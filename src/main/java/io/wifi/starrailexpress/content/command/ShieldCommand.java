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

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 统一护盾指令：
 *   sre:shield <normal|timed|weak> <add|set|get|clear> [参数...] [目标...]
 *
 * - normal: add/set <层数>  | get | clear
 * - timed:  add/set <层数> <秒> <重置计时器并叠加:true|false> | get | clear
 * - weak:   add/set <层数> <秒> <死亡原因> | get | clear
 *
 * 时间单位为秒（内部转换为 tick）。weak 的死亡原因支持 "*"（抵挡任意死亡原因）。
 * 旧指令 tmm:shield / sre:armor 作为重定向保留以兼容。
 */
public class ShieldCommand {
    private static final String TYPE_NORMAL = "normal";
    private static final String TYPE_TIMED = "timed";
    private static final String TYPE_WEAK = "weak";

    private static final SuggestionProvider<CommandSourceStack> DEATH_REASON_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("*");
        for (String id : GameConstants.DeathReasons.getAllDeathReasonIds()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> shield = dispatcher.register(Commands.literal("sre:shield")
                .requires(source -> source.hasPermission(2))
                .then(buildNormal())
                .then(buildTimed())
                .then(buildWeak()));
        // 兼容旧指令
        dispatcher.register(Commands.literal("sre:armor").redirect(shield));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildNormal() {
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add")
                .then(Commands.argument("layers", IntegerArgumentType.integer(0))
                        .executes(ctx -> normalAdd(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> normalAdd(ctx, EntityArgument.getPlayers(ctx, "targets")))));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
                .then(Commands.argument("layers", IntegerArgumentType.integer(0))
                        .executes(ctx -> normalSet(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> normalSet(ctx, EntityArgument.getPlayers(ctx, "targets")))));
        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get")
                .executes(ctx -> normalGet(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> normalGet(ctx, EntityArgument.getPlayers(ctx, "targets"))));
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
                .executes(ctx -> normalClear(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> normalClear(ctx, EntityArgument.getPlayers(ctx, "targets"))));
        return Commands.literal(TYPE_NORMAL)
                .then(add)
                .then(set)
                .then(get)
                .then(clear);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTimed() {
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add")
                .then(Commands.argument("layers", IntegerArgumentType.integer(0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .then(Commands.argument("reset", BoolArgumentType.bool())
                                        .executes(ctx -> timedAdd(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> timedAdd(ctx, EntityArgument.getPlayers(ctx, "targets")))))));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
                .then(Commands.argument("layers", IntegerArgumentType.integer(0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .then(Commands.argument("reset", BoolArgumentType.bool())
                                        .executes(ctx -> timedSet(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> timedSet(ctx, EntityArgument.getPlayers(ctx, "targets")))))));
        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get")
                .executes(ctx -> timedGet(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> timedGet(ctx, EntityArgument.getPlayers(ctx, "targets"))));
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
                .executes(ctx -> timedClear(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> timedClear(ctx, EntityArgument.getPlayers(ctx, "targets"))));
        return Commands.literal(TYPE_TIMED)
                .then(add)
                .then(set)
                .then(get)
                .then(clear);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildWeak() {
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add")
                .then(Commands.argument("layers", IntegerArgumentType.integer(0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .then(Commands.argument("deathReason", StringArgumentType.word())
                                        .suggests(DEATH_REASON_SUGGESTIONS)
                                        .executes(ctx -> weakAdd(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> weakAdd(ctx, EntityArgument.getPlayers(ctx, "targets")))))));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
                .then(Commands.argument("layers", IntegerArgumentType.integer(0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .then(Commands.argument("deathReason", StringArgumentType.word())
                                        .suggests(DEATH_REASON_SUGGESTIONS)
                                        .executes(ctx -> weakSet(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> weakSet(ctx, EntityArgument.getPlayers(ctx, "targets")))))));
        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get")
                .executes(ctx -> weakGet(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> weakGet(ctx, EntityArgument.getPlayers(ctx, "targets"))));
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
                .executes(ctx -> weakClear(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> weakClear(ctx, EntityArgument.getPlayers(ctx, "targets"))));
        LiteralArgumentBuilder<CommandSourceStack> remove = Commands.literal("remove")
                .then(Commands.argument("layers", IntegerArgumentType.integer(1))
                        .executes(ctx -> weakRemove(ctx, ImmutableList.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> weakRemove(ctx, EntityArgument.getPlayers(ctx, "targets")))));
        return Commands.literal(TYPE_WEAK)
                .then(add)
                .then(set)
                .then(get)
                .then(remove)
                .then(clear);
    }

    // ===== normal =====
    private static int normalAdd(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            c.armor += layers;
            c.sync();
        }
        feedback(ctx, Component.translatable("commands.sre.shield.normal.add", targets.size(), layers));
        return layers * targets.size();
    }

    private static int normalSet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            c.armor = Math.max(0, layers);
            c.timedArmor.clear();
            c.sync();
        }
        feedback(ctx, Component.translatable("commands.sre.shield.normal.set", targets.size(), layers));
        return layers * targets.size();
    }

    private static int normalGet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        MutableComponent out = Component.translatable("commands.sre.shield.normal.get.header").withStyle(ChatFormatting.GREEN);
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            out.append(Component.translatable("commands.sre.shield.normal.get.entry", e.getName().getString(), c.armor));
        }
        ctx.getSource().sendSuccess(() -> out, true);
        return 1;
    }

    private static int normalClear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            c.armor = 0;
            c.sync();
        }
        feedback(ctx, Component.translatable("commands.sre.shield.normal.clear", targets.size()));
        return 1;
    }

    // ===== timed =====
    private static int timedAdd(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        boolean reset = BoolArgumentType.getBool(ctx, "reset");
        int ticks = seconds * 20;
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent.KEY.get(e).addTimedArmor(layers, ticks, reset);
        }
        Component resetText = Component.translatable(reset
                ? "commands.sre.shield.timed.reset_stack" : "commands.sre.shield.timed.reset_only");
        feedback(ctx, Component.translatable("commands.sre.shield.timed.add", targets.size(), layers, seconds, resetText));
        return 1;
    }

    private static int timedSet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        boolean reset = BoolArgumentType.getBool(ctx, "reset");
        int ticks = seconds * 20;
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            if (reset) {
                c.addTimedArmor(layers, ticks, true);
            } else {
                c.setTimedArmor(layers, ticks);
            }
        }
        Component resetText = Component.translatable(reset
                ? "commands.sre.shield.timed.reset_stack" : "commands.sre.shield.timed.set_direct");
        feedback(ctx, Component.translatable("commands.sre.shield.timed.set", targets.size(), layers, seconds, resetText));
        return 1;
    }

    private static int timedGet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        MutableComponent out = Component.translatable("commands.sre.shield.timed.get.header").withStyle(ChatFormatting.GREEN);
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            out.append(Component.translatable("commands.sre.shield.timed.get.entry",
                    e.getName().getString(), c.armor, c.timedArmor.size()));
        }
        ctx.getSource().sendSuccess(() -> out, true);
        return 1;
    }

    private static int timedClear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer e : targets) {
            SREArmorPlayerComponent c = SREArmorPlayerComponent.KEY.get(e);
            c.armor = 0;
            c.timedArmor.clear();
            c.sync();
        }
        feedback(ctx, Component.translatable("commands.sre.shield.timed.clear", targets.size()));
        return 1;
    }

    // ===== weak =====
    private static int weakAdd(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        int ticks = seconds * 20;
        boolean blockAll;
        Set<ResourceLocation> reasons = new HashSet<>();
        blockAll = parseDeathReason(ctx, reasons);
        for (ServerPlayer e : targets) {
            SREWeakArmorPlayerComponent c = SREWeakArmorPlayerComponent.KEY.get(e);
            for (int i = 0; i < layers; i++) {
                c.giveWeakArmor(ticks, reasons, blockAll);
            }
        }
        Component block = blockAll ? Component.translatable("commands.sre.shield.weak.block_any")
                : Component.literal(String.join("/", reasons.stream().map(ResourceLocation::toString).toList()));
        feedback(ctx, Component.translatable("commands.sre.shield.weak.add", targets.size(), layers, seconds, block));
        return 1;
    }

    private static int weakSet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        int ticks = seconds * 20;
        boolean blockAll;
        Set<ResourceLocation> reasons = new HashSet<>();
        blockAll = parseDeathReason(ctx, reasons);
        for (ServerPlayer e : targets) {
            SREWeakArmorPlayerComponent.KEY.get(e).setWeakArmor(layers, ticks, reasons, blockAll);
        }
        Component block = blockAll ? Component.translatable("commands.sre.shield.weak.block_any")
                : Component.literal(String.join("/", reasons.stream().map(ResourceLocation::toString).toList()));
        feedback(ctx, Component.translatable("commands.sre.shield.weak.set", targets.size(), layers, seconds, block));
        return 1;
    }

    private static int weakGet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        MutableComponent out = Component.translatable("commands.sre.shield.weak.get.header").withStyle(ChatFormatting.GREEN);
        for (ServerPlayer e : targets) {
            SREWeakArmorPlayerComponent c = SREWeakArmorPlayerComponent.KEY.get(e);
            Component block = c.blockAllDeathReasons ? Component.translatable("commands.sre.shield.weak.block_any")
                    : Component.literal(String.join("/", c.blockedDeathReasons.stream().map(ResourceLocation::toString).toList()));
            out.append(Component.translatable("commands.sre.shield.weak.get.entry",
                    e.getName().getString(), c.weakArmor, c.weakArmorTicks, block));
        }
        ctx.getSource().sendSuccess(() -> out, true);
        return 1;
    }

    private static int weakClear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer e : targets) {
            SREWeakArmorPlayerComponent.KEY.get(e).setWeakArmor(0, 0, new HashSet<>(), false);
        }
        feedback(ctx, Component.translatable("commands.sre.shield.weak.clear", targets.size()));
        return 1;
    }

    private static int weakRemove(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        int layers = IntegerArgumentType.getInteger(ctx, "layers");
        for (ServerPlayer e : targets) {
            SREWeakArmorPlayerComponent.KEY.get(e).decreaseWeakArmor(layers);
        }
        feedback(ctx, Component.translatable("commands.sre.shield.weak.remove", targets.size(), layers));
        return 1;
    }

    private static boolean parseDeathReason(CommandContext<CommandSourceStack> ctx, Set<ResourceLocation> reasons) {
        String dr = StringArgumentType.getString(ctx, "deathReason");
        if ("*".equals(dr)) {
            return true;
        }
        ResourceLocation rl = ResourceLocation.tryParse(dr);
        if (rl != null) {
            reasons.add(rl);
        }
        return false;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, Component msg) {
        ctx.getSource().sendSuccess(() -> Component.empty().append(msg).withStyle(ChatFormatting.GREEN), true);
    }
}
