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

package io.wifi.starrailexpress.content.command.data;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;

import java.util.function.Function;

/**
 * 把「伪装实体的 NBT」注册成原版 {@code /data} 与 {@code /execute if data} 的一个数据源：
 * <pre>
 * /data get sre:disguise &lt;player&gt; [path] [scale]
 * /data merge sre:disguise &lt;player&gt; &lt;nbt&gt;
 * /data modify sre:disguise &lt;player&gt; &lt;path&gt; set|merge|append|insert|prepend|from ...
 * /data remove sre:disguise &lt;player&gt; &lt;path&gt;
 * /execute if data sre:disguise &lt;player&gt; &lt;path&gt;        （unless 取反）
 * /execute store ... data sre:disguise &lt;player&gt; &lt;path&gt;  （写回伪装 NBT）
 * ... set from sre:disguise &lt;player&gt; &lt;path&gt;             （作为 NBT 来源）
 * </pre>
 * 语法与语义完全跟着原版走：路径用原版 {@code NbtPathArgument}、取值 / 写回走原版
 * {@link DataAccessor} 协议，因此 {@code /execute if data} 只判「路径是否存在」（与
 * {@code if data entity} 一致），要比较具体数值就用 {@code /execute store} 或 {@code /data get}。
 * <p>
 * 与 {@code EntityDataAccessor.PROVIDER} 同形：{@code Function} 的入参是**目标参数名**
 * （原版对「写入目标」用 {@code target}、对「NBT 来源」用 {@code source}），所以同一个
 * 数据源在两种位置都能用，参数名不会打架。
 */
public final class DisguiseDataProvider implements DataCommands.DataProvider {

    /** 数据源名（出现在原版 block / entity / storage 旁边）。 */
    public static final String NAME = "sre:disguise";

    /** 注册进 {@code DataCommands} 的三张 provider 表用。 */
    public static final Function<String, DataCommands.DataProvider> PROVIDER = DisguiseDataProvider::new;

    private final String targetArg;

    private DisguiseDataProvider(String targetArg) {
        this.targetArg = targetArg;
    }

    @Override
    public DataAccessor access(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return new DisguiseDataAccessor(EntityArgument.getPlayer(context, this.targetArg));
    }

    @Override
    public ArgumentBuilder<CommandSourceStack, ?> wrap(ArgumentBuilder<CommandSourceStack, ?> builder,
            Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> action) {
        // 与原版 entity provider 同形：<名字> <目标参数> → 交给 action 追加 path / 各种操作子树。
        return builder.then(Commands.literal(NAME)
                .then(action.apply(Commands.argument(this.targetArg, EntityArgument.player()))));
    }
}
