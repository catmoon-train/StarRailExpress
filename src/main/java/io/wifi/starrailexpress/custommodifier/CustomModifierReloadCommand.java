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

package io.wifi.starrailexpress.custommodifier;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.network.CustomModifierServerNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 自定义修饰符重载命令：{@code sre:reload custom_modifiers}
 *
 * <p>
 * 不带子命令的 {@code sre:reload} 一键重载全部（见
 * {@link io.wifi.starrailexpress.content.command.SREReloadCommand}）。
 */
public class CustomModifierReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:reload")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("custom_modifiers")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            try {
                                reload(source.getServer());
                                source.sendSuccess(
                                        () -> Component.translatable("sre.custom_modifier.reload.success")
                                                .withStyle(s -> s.withColor(0x55FF55)),
                                        true);
                                SRE.LOGGER.info("[CustomModifier] Reloaded custom modifiers by {}",
                                        source.getTextName());
                                return 1;
                            } catch (Exception e) {
                                source.sendFailure(Component
                                        .translatable("sre.custom_modifier.reload.failed", e.getMessage()));
                                SRE.LOGGER.error("[CustomModifier] Reload failed", e);
                                return 0;
                            }
                        })));
    }

    /** 重载服务端索引并同步给所有在线玩家（供本命令与一键重载复用）。 */
    public static void reload(MinecraftServer server) {
        CustomModifierLoader.reload(server);
        CustomModifierServerNetwork.clearCache();
        CustomModifierServerNetwork.syncToAllPlayers(server);
    }
}
