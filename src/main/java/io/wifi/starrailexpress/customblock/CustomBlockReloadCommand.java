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

package io.wifi.starrailexpress.customblock;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.synccontent.ContentChannel;
import io.wifi.starrailexpress.synccontent.ContentSyncServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 自定义方块重载命令：{@code sre:reload custom_blocks}
 *
 * <p>
 * 重载后只重新握手（几十字节的一个包）：客户端本地缓存里已经有同一份内容时，
 * 服务端不会再下发全文。
 */
public class CustomBlockReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:reload")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("custom_blocks").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    try {
                        reload(source.getServer());
                        source.sendSuccess(
                                () -> Component.translatable("sre.custom_block.reload.success")
                                        .withStyle(s -> s.withColor(0x55FF55)),
                                true);
                        SRE.LOGGER.info("[CustomBlock] Reloaded custom blocks by {}", source.getTextName());
                        return 1;
                    } catch (Exception e) {
                        source.sendFailure(Component.translatable("sre.custom_block.reload.failed", e.getMessage()));
                        SRE.LOGGER.error("[CustomBlock] Reload failed", e);
                        return 0;
                    }
                })));
    }

    /** 重载服务端索引并重新握手（供本命令与其它需要刷新方块配置的地方复用）。 */
    public static void reload(MinecraftServer server) {
        io.wifi.starrailexpress.customcontent.CustomContentReload.withDependents(server, ContentChannel.CUSTOM_BLOCK);
    }
}
