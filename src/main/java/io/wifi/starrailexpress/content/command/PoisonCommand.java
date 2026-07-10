package io.wifi.starrailexpress.content.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

/**
 * 统一中毒指令 (无旧指令, 直接以 sre: 开头):
 *   sre:poison <normal|fake> <set|get|clear|trigger> [参数...] [目标...]
 *
 * - normal: set <tick> | get | clear | trigger   (真毒)
 * - fake:   set <tick> | get | clear | trigger   (假毒)
 *
 * - set <tick>: 给予目标 tick 数的中毒/假毒 (<tick> 为游戏刻, 1 秒 = 20 tick)。
 * - clear: 直接移除目标的中毒/假毒状态 (清为无)。
 * - trigger: 直接使目标"触发"当前中毒/假毒并立即死亡 (假毒也会致死)。
 *   仅在目标当前存在中毒/假毒 (poisonTicks > 0) 时生效。
 */
public class PoisonCommand {
    private static final String TYPE_NORMAL = "normal";
    private static final String TYPE_FAKE = "fake";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:poison")
                .requires(source -> source.hasPermission(2))
                .then(buildType(TYPE_NORMAL, false))
                .then(buildType(TYPE_FAKE, true)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildType(String type, boolean fake) {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> doSet(ctx, fake, ImmutableList.of(ctx.getSource().getEntityOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> doSet(ctx, fake, EntityArgument.getEntities(ctx, "targets")))));
        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get")
                .executes(ctx -> doGet(ctx, fake, ImmutableList.of(ctx.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> doGet(ctx, fake, EntityArgument.getEntities(ctx, "targets"))));
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
                .executes(ctx -> doClear(ctx, fake, ImmutableList.of(ctx.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> doClear(ctx, fake, EntityArgument.getEntities(ctx, "targets"))));
        LiteralArgumentBuilder<CommandSourceStack> trigger = Commands.literal("trigger")
                .executes(ctx -> doTrigger(ctx, fake, ImmutableList.of(ctx.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> doTrigger(ctx, fake, EntityArgument.getEntities(ctx, "targets"))));
        return Commands.literal(type)
                .then(set)
                .then(get)
                .then(clear)
                .then(trigger);
    }

    private static int doSet(CommandContext<CommandSourceStack> ctx, boolean fake, Collection<? extends Entity> targets) {
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        int applied = 0;
        for (Entity e : targets) {
            SREPlayerPoisonComponent c = SREPlayerPoisonComponent.KEY.get(e);
            if (fake) {
                c.setFakePoisonTicks(ticks, null);
            } else {
                c.setPoisonTicks(ticks, null);
            }
            applied++;
        }
        feedback(ctx, Component.translatable(
                fake ? "commands.sre.poison.fake.set" : "commands.sre.poison.normal.set", applied, ticks));
        return applied;
    }

    private static int doGet(CommandContext<CommandSourceStack> ctx, boolean fake, Collection<? extends Entity> targets) {
        MutableComponent out = Component.translatable(
                fake ? "commands.sre.poison.fake.get.header" : "commands.sre.poison.normal.get.header")
                .withStyle(ChatFormatting.GREEN);
        for (Entity e : targets) {
            SREPlayerPoisonComponent c = SREPlayerPoisonComponent.KEY.get(e);
            boolean isFake = c.fakePoison;
            String kind = isFake ? "commands.sre.poison.fake.label" : "commands.sre.poison.normal.label";
            out.append(Component.translatable("commands.sre.poison.get.entry",
                    e.getName().getString(),
                    Component.translatable(kind),
                    c.poisonTicks));
        }
        ctx.getSource().sendSuccess(() -> out, true);
        return 1;
    }

    private static int doClear(CommandContext<CommandSourceStack> ctx, boolean fake, Collection<? extends Entity> targets) {
        int cleared = 0;
        for (Entity e : targets) {
            SREPlayerPoisonComponent c = SREPlayerPoisonComponent.KEY.get(e);
            // 仅清除与当前类型匹配的中毒状态, 避免误清另一种。
            if (fake == c.fakePoison && c.poisonTicks > 0) {
                c.init();
                cleared++;
            }
        }
        feedback(ctx, Component.translatable(
                fake ? "commands.sre.poison.fake.clear" : "commands.sre.poison.normal.clear", cleared));
        return 1;
    }

    private static int doTrigger(CommandContext<CommandSourceStack> ctx, boolean fake, Collection<? extends Entity> targets) {
        int triggered = 0;
        for (Entity e : targets) {
            SREPlayerPoisonComponent c = SREPlayerPoisonComponent.KEY.get(e);
            if (fake == c.fakePoison && c.poisonTicks > 0) {
                // 强制触发: 取消假毒标记并将剩余刻设为 1, 下次 serverTick 即致死。
                c.fakePoison = false;
                c.poisonTicks = 1;
                c.sync();
                triggered++;
            }
        }
        feedback(ctx, Component.translatable(
                fake ? "commands.sre.poison.fake.trigger" : "commands.sre.poison.normal.trigger", triggered));
        return 1;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, Component msg) {
        ctx.getSource().sendSuccess(() -> Component.empty().append(msg).withStyle(ChatFormatting.GREEN), true);
    }
}
