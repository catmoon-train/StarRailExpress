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

package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
                                        for (SRERole role : TMMRoles.ROLES.values()) {
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

                                                SRERole roleObj = null;
                                                for (SRERole role : TMMRoles.ROLES.values()) {
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