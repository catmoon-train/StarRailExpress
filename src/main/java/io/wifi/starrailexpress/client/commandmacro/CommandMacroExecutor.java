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

package io.wifi.starrailexpress.client.commandmacro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Queue;

public final class CommandMacroExecutor {
    private static final Queue<PendingCommand> QUEUE = new ArrayDeque<>();
    private static long nextRunAt = 0;

    private CommandMacroExecutor() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void execute(CommandMacroStorage.MacroScript script) {
        QUEUE.clear();
        for (CommandMacroStorage.MacroCommand command : script.commands) {
            if (command.command != null && !command.command.trim().isEmpty()) {
                QUEUE.add(new PendingCommand(command.command.trim(), Math.max(0, command.delayMs)));
            }
        }
        nextRunAt = System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || QUEUE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRunAt) {
            return;
        }
        PendingCommand command = QUEUE.poll();
        if (command == null) {
            return;
        }
        String cmd = command.command;
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        client.player.connection.sendCommand(cmd);
        nextRunAt = now + command.delayMs;
    }

    private record PendingCommand(String command, int delayMs) {}
}
