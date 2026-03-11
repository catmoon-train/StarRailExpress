package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.Role;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class SetRoleMaxCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var setmaxCommand = Commands.literal("noellesroles").requires((commandSourceStack) -> {
                return commandSourceStack.hasPermission(1);
            })
                    .then(Commands.literal("setmax")
                            .then(Commands.argument("role", ResourceLocationArgument.id())
                                    .suggests((context, builder) -> {
                                        for (Role role : TMMRoles.ROLES.values()) {
                                            ResourceLocation id = role.identifier();
                                            builder.suggest(id.toString());
                                        }
                                        return builder.buildFuture();
                                    })
                                    .then(Commands.argument("value", IntegerArgumentType.integer(0, 10))
                                            .executes(context -> {
                                                ResourceLocation roleId = ResourceLocationArgument.getId(context,
                                                        "role");
                                                int value = IntegerArgumentType.getInteger(context, "value");

                                                Role roleObj = null;
                                                for (Role role : TMMRoles.ROLES.values()) {
                                                    if (role.identifier().equals(roleId)) {
                                                        roleObj = role;
                                                        break;
                                                    }
                                                }
                                                if (roleObj != null) {
                                                    Harpymodloader.setRoleMaximum(roleObj, value);
                                                } else {

                                                    Harpymodloader.setRoleMaximum(roleId, value);
                                                }

                                                NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
                                                boolean configUpdated = false;
                                                String rolePath = roleId.getPath();
                                                try {

                                                    Field field = getField(rolePath);
                                                    field.set(config, value);
                                                    configUpdated = true;
                                                } catch (Exception e) {
                                                }
                                                if (configUpdated) {
                                                    NoellesRolesConfig.HANDLER.save();
                                                }

                                                context.getSource().sendSystemMessage(
                                                        Component.literal("Set max " + roleId + " to " + value));
                                                return 1;
                                            }))));
            dispatcher.register(setmaxCommand);
        });
    }

    private static @NotNull Field getField(String rolePath) throws NoSuchFieldException {
        String[] parts = rolePath.split("_");
        StringBuilder fieldNameBuilder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i > 0) {
                // Capitalize first letter of subsequent parts
                part = part.substring(0, 1).toUpperCase() + part.substring(1);
            }
            fieldNameBuilder.append(part);
        }
        fieldNameBuilder.append("Max");
        String fieldName = fieldNameBuilder.toString();

        // Use reflection to set the field
        return NoellesRolesConfig.class.getField(fieldName);
    }
}