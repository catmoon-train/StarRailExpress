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
import io.wifi.starrailexpress.game.ElevatedBlockCommandPermission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /sre:block_cmd_perm [on|off] — 将命令方块与实体交互方块的执行权限提升为 3，并写入配置。
 */
public final class ElevatedBlockCommandPermissionCommand {

    private ElevatedBlockCommandPermissionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:block_cmd_perm")
                .requires(source -> source.hasPermission(4))
                .executes(ctx -> {
                    boolean enabled = ElevatedBlockCommandPermission.isEnabled();
                    ctx.getSource().sendSuccess(() -> Component.translatable(enabled
                            ? "commands.sre.block_cmd_perm.status.on"
                            : "commands.sre.block_cmd_perm.status.off"), true);
                    return enabled ? 1 : 0;
                })
                .then(Commands.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false))));
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        ElevatedBlockCommandPermission.setEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "commands.sre.block_cmd_perm.enabled"
                : "commands.sre.block_cmd_perm.disabled"), true);
        return enabled ? 1 : 0;
    }
}
