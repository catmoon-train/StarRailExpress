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

package io.wifi.starrailexpress.customitem;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.network.CustomItemServerNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 自定义列车物品重载命令：{@code sre:reload custom_items}
 */
public class CustomItemReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:reload")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("custom_items").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    try {
                        reload(source.getServer());
                        source.sendSuccess(
                                () -> Component.translatable("sre.custom_item.reload.success")
                                        .withStyle(s -> s.withColor(0x55FF55)),
                                true);
                        SRE.LOGGER.info("[CustomItem] Reloaded custom items by {}", source.getTextName());
                        return 1;
                    } catch (Exception e) {
                        source.sendFailure(Component.translatable("sre.custom_item.reload.failed", e.getMessage()));
                        SRE.LOGGER.error("[CustomItem] Reload failed", e);
                        return 0;
                    }
                })));
    }

    /** 重载服务端索引并同步给所有在线玩家（供本命令与其它需要刷新物品配置的地方复用）。 */
    public static void reload(MinecraftServer server) {
        CustomItemLoader.reload(server);
        CustomItemServerNetwork.clearCache();
        CustomItemServerNetwork.syncToAllPlayers(server);
    }
}
