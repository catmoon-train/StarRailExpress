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

import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /sre:reload}（不带子命令）的执行器：一键重载全部自定义内容。
 *
 * <p>
 * 各内容类型的子命令由各自的类注册（{@code custom_roles} / {@code custom_modifiers} /
 * {@code custom_items} / {@code custom_blocks}）；同名根节点会被 Brigadier 合并，所以这里提供一个
 * 统一的无参执行器，四个注册点都挂它——不管合并时保留哪一个，{@code /sre:reload} 的行为都一致。
 *
 * <p>
 * 顺序固定为「物品 → 方块 → 职业 → 修饰符」：职业的初始物品 / 任务奖励物品支持自定义列车物品，
 * 必须在物品索引就绪之后再解析。
 */
public final class SREReloadCommand {

    private SREReloadCommand() {
    }

    /** 一键重载全部自定义内容（任一项失败不影响其余项，逐项try）。 */
    public static int reloadAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int reloaded = 0;
        reloaded += reloadOne("CustomItem", () -> io.wifi.starrailexpress.customitem.CustomItemReloadCommand.reload(server));
        reloaded += reloadOne("CustomBlock",
                () -> io.wifi.starrailexpress.customblock.CustomBlockReloadCommand.reload(server));
        reloaded += reloadOne("CustomRole",
                () -> io.wifi.starrailexpress.customrole.CustomRoleReloadCommand.reload(server));
        reloaded += reloadOne("CustomModifier",
                () -> io.wifi.starrailexpress.custommodifier.CustomModifierReloadCommand.reload(server));

        final int count = reloaded;
        source.sendSuccess(() -> Component.translatable("sre.custom_content.reload.all", count)
                .withStyle(style -> style.withColor(count == 4 ? 0x72C17B : 0xE06B65)), true);
        SRE.LOGGER.info("[CustomContent] Reloaded {}/4 custom content types by {}", count, source.getTextName());
        return count;
    }

    private static int reloadOne(String what, Runnable action) {
        try {
            action.run();
            return 1;
        } catch (Exception e) {
            SRE.LOGGER.error("[CustomContent] Failed to reload {}", what, e);
            return 0;
        }
    }
}
