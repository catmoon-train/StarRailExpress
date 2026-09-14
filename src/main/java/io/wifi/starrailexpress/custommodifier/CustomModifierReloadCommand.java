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

/**
 * 自定义修饰符重载命令：{@code sre:reloadModifierConfig}
 */
public class CustomModifierReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:reloadModifierConfig")
                .requires(source -> source.hasPermission(3))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    try {
                        CustomModifierLoader.reload(source.getServer());
                        CustomModifierServerNetwork.clearCache();
                        CustomModifierServerNetwork.syncToAllPlayers(source.getServer());
                        source.sendSuccess(
                                () -> Component.literal("[CustomModifier] 自定义修饰符配置已重新加载")
                                        .withStyle(s -> s.withColor(0x55FF55)),
                                true);
                        SRE.LOGGER.info("[CustomModifier] Reloaded custom modifiers by {}", source.getTextName());
                        return 1;
                    } catch (Exception e) {
                        source.sendFailure(Component.literal("[CustomModifier] 重载失败: " + e.getMessage()));
                        SRE.LOGGER.error("[CustomModifier] Reload failed", e);
                        return 0;
                    }
                }));
    }
}
