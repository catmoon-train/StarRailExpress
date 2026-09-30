package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

/**
 * 虚拟血量（Dream 体系）管理指令。
 *
 * <p>{@code /sre:virtual_health get <玩家>}：查询当前虚拟血量。
 * <p>{@code /sre:virtual_health add <玩家> <量>}：增减虚拟血量，支持负数；结果夹在 [0, 上限]，不会致死。
 * <p>{@code /sre:virtual_health set <玩家> <值>}：直接设置虚拟血量；结果夹在 [0, 上限]，不会致死。
 */
public final class VirtualHealthCommand {
    private VirtualHealthCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:virtual_health")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(VirtualHealthCommand::get)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(VirtualHealthCommand::add))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(VirtualHealthCommand::set))))));
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
        int current = health.currentHealth();
        int max = DreamHealthComponent.maxHealth();
        context.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " 的虚拟血量："
                + current + " / " + max), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
        int result = health.addHealth(amount);
        if (result < 0) {
            context.getSource().sendFailure(Component.literal("无法调整 " + target.getName().getString() + " 的虚拟血量"));
            return 0;
        }
        int max = DreamHealthComponent.maxHealth();
        int finalResult = result;
        context.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                + " 的虚拟血量已" + (amount >= 0 ? "增加 " : "减少 ") + Math.abs(amount)
                + " 点，当前：" + finalResult + " / " + max), true);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");
        DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
        int result = health.setHealth(value);
        if (result < 0) {
            context.getSource().sendFailure(Component.literal("无法设置 " + target.getName().getString() + " 的虚拟血量"));
            return 0;
        }
        int max = DreamHealthComponent.maxHealth();
        int finalResult = result;
        context.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                + " 的虚拟血量已设为 " + finalResult + " / " + max), true);
        return 1;
    }
}
