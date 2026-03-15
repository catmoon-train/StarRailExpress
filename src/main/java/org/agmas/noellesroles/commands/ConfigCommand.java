package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.config.NoellesRolesConfig;

public class ConfigCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var configCommand = Commands.literal("noellesroles")
                    .then(Commands.literal("config")
                            .requires(source -> source.hasPermission(2)) // 需要OP权限
                            .then(Commands.literal("reload")
                                    .executes(context -> {
                                        NoellesRolesConfig.HANDLER.load();
                                        context.getSource().sendSystemMessage(
                                                Component.literal("NoellesRoles configuration reloaded successfully"));
                                        return 1;
                                    }))
                            .then(Commands.literal("reset")
                                    .executes(context -> {
                                        // 创建默认配置实例
                                        NoellesRolesConfig.HANDLER.reset();
                                        return 1;
                                    }))
                            .then(Commands.literal("accidentalKillPunishment")
                                    .then(Commands.argument("value", BoolArgumentType.bool())
                                            .executes(context -> {
                                                boolean value = BoolArgumentType.getBool(context, "value");
                                                NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
                                                config.accidentalKillPunishment = value;
                                                NoellesRolesConfig.HANDLER.save();

                                                String statusText = value ? "Enabled" : "Disabled";
                                                context.getSource().sendSystemMessage(
                                                        Component
                                                                .literal("Innocent Punishment" + statusText
                                                                        + " (accidentalKillPunishment = " + value + ")")
                                                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                                                return 1;
                                            }))));
            dispatcher.register(configCommand);
        });
    }
}