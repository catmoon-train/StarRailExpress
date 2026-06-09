package io.wifi.starrailexpress.content.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class ShieldCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sre:shield")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(context -> executeAdd(context.getSource(),
                                        ImmutableList.of(context.getSource().getEntityOrException()),
                                        IntegerArgumentType.getInteger(context, "amount")))
                                .then(
                                        Commands.argument("targets", EntityArgument.entities())
                                                .executes(context -> executeAdd(context.getSource(),
                                                        EntityArgument.getEntities(context, "targets"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(context -> executeSet(context.getSource(),
                                        ImmutableList.of(context.getSource().getEntityOrException()),
                                        IntegerArgumentType.getInteger(context, "amount")))
                                .then(
                                        Commands.argument("targets", EntityArgument.entities())
                                                .executes(context -> executeSet(context.getSource(),
                                                        EntityArgument.getEntities(context, "targets"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("get").executes(context -> executeGet(context.getSource(),
                                ImmutableList.of(context.getSource().getEntityOrException())))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> executeGet(context.getSource(),
                                                EntityArgument.getEntities(context, "targets"))))));
    }

    private static int executeSet(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
        int total = 0;

        for (Entity target : targets) {
            int finalAmount = Math.max(amount, 0);
            SREArmorPlayerComponent.KEY.get(target).armor = finalAmount;
            SREArmorPlayerComponent.KEY.get(target).sync();
            total += finalAmount;
        }

        if (targets.size() == 1) {
            Entity target = targets.iterator().next();
            source.sendSuccess(
                    () -> Component
                            .translatable("commands.sre.setshield", target.getName().getString(), amount)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        } else {
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.setshield.multiple", targets.size(), amount)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        }
        return total;
    }

    private static int executeAdd(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
        int total = 0;
        for (Entity target : targets) {
            SREArmorPlayerComponent comp = SREArmorPlayerComponent.KEY.get(target);
            comp.armor = Math.max(0, comp.armor + amount);
            comp.sync();
            total += comp.armor;
        }

        if (targets.size() == 1) {
            Entity target = targets.iterator().next();
            int shield = SREArmorPlayerComponent.KEY.get(target).armor;
            source.sendSuccess(
                    () -> Component
                            .translatable("commands.sre.addshield", target.getName().getString(), amount, shield)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        } else {
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.addshield.multiple", targets.size(), amount)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        }
        return total;
    }

    private static int executeGet(CommandSourceStack source, Collection<? extends Entity> targets) {
        final int total = targets.stream().mapToInt(target -> {
            var comp = SREArmorPlayerComponent.KEY.maybeGet(target).orElse(null);
            if (comp != null) {
                return comp.armor;
            }
            return 0;
        }).sum();
        source.sendSuccess(
                () -> Component
                        .translatable("commands.sre.getshield", total)
                        .withStyle(style -> style.withColor(0x00FF00)),
                true);
        return total;
    }
}
