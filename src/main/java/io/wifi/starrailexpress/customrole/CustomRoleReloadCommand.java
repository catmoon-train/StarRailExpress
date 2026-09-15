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

package io.wifi.starrailexpress.customrole;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.network.CustomRoleServerNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 自定义职业重载命令：{@code sre:reload custom_roles}
 *
 * <p>
 * 只重载职业。修饰符 / 列车物品 / 方块各有自己的子命令
 * （{@code custom_modifiers} / {@code custom_items} / {@code custom_blocks}），
 * 不带子命令的 {@code sre:reload} 则一键重载全部（见
 * {@link io.wifi.starrailexpress.content.command.SREReloadCommand}）。
 */
public class CustomRoleReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:reload")
                .requires(source -> source.hasPermission(3))
                // 无参 = 一键重载全部内容。四个内容类型各自注册了同名根节点，Brigadier 合并同名节点时
                // 只合并子命令、并且「只有非 null 的执行器才会覆盖已有的」（见 CommandNode#addChild），
                // 所以全项目只在**这一处**挂执行器即可，另外三处不用重复挂。
                .executes(io.wifi.starrailexpress.content.command.SREReloadCommand::reloadAll)
                .then(Commands.literal("custom_roles").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    try {
                        reload(source.getServer());
                        source.sendSuccess(
                                () -> Component.translatable("sre.custom_role.reload.success")
                                        .withStyle(s -> s.withColor(0x55FF55)),
                                true);
                        SRE.LOGGER.info("[CustomRole] Reloaded custom roles by {}", source.getTextName());
                        return 1;
                    } catch (Exception e) {
                        source.sendFailure(Component.translatable("sre.custom_role.reload.failed", e.getMessage()));
                        SRE.LOGGER.error("[CustomRole] Reload failed", e);
                        return 0;
                    }
                })));
    }

    /** 重载服务端索引并同步给所有在线玩家（供本命令与一键重载复用）。 */
    public static void reload(MinecraftServer server) {
        CustomRoleLoader.reload(server);
        CustomRoleServerNetwork.clearCache();
        CustomRoleServerNetwork.syncToAllPlayers(server);
    }
}
