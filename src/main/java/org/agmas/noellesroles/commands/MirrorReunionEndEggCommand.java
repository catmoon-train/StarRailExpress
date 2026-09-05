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

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.MirrorReunionEndEgg;

public final class MirrorReunionEndEggCommand {

    private MirrorReunionEndEggCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("sre:mirror_end_egg")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    MirrorReunionEndEgg.isEnabled()
                                            ? "commands.noellesroles.mirror_end_egg.status.on"
                                            : "commands.noellesroles.mirror_end_egg.status.off"), true);
                            return MirrorReunionEndEgg.isEnabled() ? 1 : 0;
                        })
                        .then(Commands.literal("on").executes(ctx -> {
                            MirrorReunionEndEgg.setEnabled(true);
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("commands.noellesroles.mirror_end_egg.enabled"),
                                    true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            MirrorReunionEndEgg.setEnabled(false);
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("commands.noellesroles.mirror_end_egg.disabled"),
                                    true);
                            return 1;
                        }))));
    }
}
