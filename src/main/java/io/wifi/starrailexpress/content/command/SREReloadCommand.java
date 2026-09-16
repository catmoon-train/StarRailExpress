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
 * 顺序由 {@link io.wifi.starrailexpress.customcontent.CustomContentReload#all} 统一决定
 * （物品 → 方块 → 职业 → 修饰符）：职业的初始物品 / 任务奖励 / 商店条目按 id 查自定义物品索引，
 * 必须在物品索引就绪之后再解析；修饰符又依赖已注册的职业。
 */
public final class SREReloadCommand {

    private SREReloadCommand() {
    }

    /** 一键重载全部自定义内容（任一项失败不影响其余项）。 */
    public static int reloadAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int reloaded = io.wifi.starrailexpress.customcontent.CustomContentReload.all(server);

        final int count = reloaded;
        source.sendSuccess(() -> Component.translatable("sre.custom_content.reload.all", count)
                .withStyle(style -> style.withColor(count == 4 ? 0x72C17B : 0xE06B65)), true);
        SRE.LOGGER.info("[CustomContent] Reloaded {}/4 custom content types by {}", count, source.getTextName());
        return count;
    }
}
