/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.dummy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sre:dummy} 假人指令：创建带指定皮肤的傀儡实体。
 *
 * <pre>
 *   /sre:dummy spawn <皮肤来源玩家> <显示名> [invincible]
 *   /sre:dummy remove <显示名>
 *   /sre:dummy list
 * </pre>
 */
public final class DummyCommand {

    private static boolean lifecycleRegistered = false;

    private DummyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!lifecycleRegistered) {
            lifecycleRegistered = true;
            ServerLifecycleEvents.SERVER_STARTED.register(DummyManager::onServerStarted);
        }
        dispatcher.register(Commands.literal("sre:dummy")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("spawn")
                .then(Commands.argument("skin", StringArgumentType.word())
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "skin"),
                            StringArgumentType.getString(ctx, "name"), true))
                        .then(Commands.argument("invincible", BoolArgumentType.bool())
                            .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "skin"),
                                StringArgumentType.getString(ctx, "name"),
                                BoolArgumentType.getBool(ctx, "invincible")))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        boolean removed = DummyManager.remove(name);
                        ctx.getSource().sendSuccess(
                            () -> Component.literal(removed ? "§a假人 " + name + " 已移除。" : "§c找不到假人: " + name), true);
                        return removed ? 1 : 0;
                    })))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    if (DummyManager.all().isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§7当前没有假人。"), false);
                    } else {
                        for (DummyEntity dummy : DummyManager.all()) {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a" + dummy.label() + " §7(皮肤: " + dummy.skinOwner()
                                    + (dummy.invincible() ? ", 无敌" : "") + ")"), false);
                        }
                    }
                    return DummyManager.all().size();
                })));
    }

    private static int spawn(CommandSourceStack source, String skin, String name, boolean invincible) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c该指令只能由玩家执行（需要位置信息）。"));
            return 0;
        }
        DummyManager.spawn(player.serverLevel(), player.position(), player.getYRot(), player.getXRot(),
            skin, name, invincible, true);
        source.sendSuccess(() -> Component.literal(
            "§a正在创建假人 §f" + name + " §a（皮肤: " + skin + (invincible ? "，无敌" : "") + "）…"), true);
        return 1;
    }
}
