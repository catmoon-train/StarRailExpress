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

package io.wifi.starrailexpress.customcontent;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 自定义内容的 id 参数：{@code <id>[<组件>]}，写法与原版物品参数一致。
 *
 * <p>
 * 例：{@code my_sword}、{@code my_sword[minecraft:custom_name="Excalibur",minecraft:unbreakable={}]}。
 * 组件块的语法与原版 {@code /give} 相同（SNBT，组件名可省略 {@code minecraft:} 前缀），
 * 由 {@link CustomContentCommands} 用原版的组件编解码器解析后应用到物品栈上。
 *
 * <p>
 * 按注册的 {@link Kind} 给出不同的补全候选（方块 id / 物品 id）。
 */
public class CustomContentArgument implements ArgumentType<CustomContentArgument.Value> {

    /** 这个 id 属于哪张自定义内容表（只影响补全与后续校验的提示）。 */
    public enum Kind {
        BLOCK,
        ITEM
    }

    /** 解析结果：id 与（可选的）组件块内容（不含外层方括号）。 */
    public record Value(String id, String components) {
    }

    private static final SimpleCommandExceptionType ERROR_EXPECTED_ID = new SimpleCommandExceptionType(
            Component.translatable("sre.custom_content.error.expected_id"));
    private static final SimpleCommandExceptionType ERROR_UNCLOSED = new SimpleCommandExceptionType(
            Component.translatable("sre.custom_content.error.unclosed_components"));

    private static final CustomContentArgument BLOCK = new BlockIdArgument();
    private static final CustomContentArgument ITEM = new ItemIdArgument();

    private final Kind kind;

    public CustomContentArgument(Kind kind) {
        this.kind = kind;
    }

    /**
     * 方块 id 参数（补全自定义方块 id）。
     *
     * <p>
     * 两个 kind 各用一个<b>独立的参数类型类</b>：命令树同步到客户端时，客户端按
     * 「参数类型」重建实例（{@code ArgumentTypeInfos} 的 class → 序列化器映射），
     * 同一个类注册两个 id 会让这个映射变得有歧义，其中一个分支就会拿到错误的补全来源。
     */
    public static final class BlockIdArgument extends CustomContentArgument {
        public BlockIdArgument() {
            super(Kind.BLOCK);
        }
    }

    /** 物品 id 参数（补全自定义列车物品 id）。 */
    public static final class ItemIdArgument extends CustomContentArgument {
        public ItemIdArgument() {
            super(Kind.ITEM);
        }
    }

    /**
     * 注册参数类型。
     *
     * <p>
     * 必须<b>在注册表冻结前</b>调用（本项目的调用点是
     * {@code SRECommandRegister.registerCommandArgumentTypes()}，由 {@code SRE.onInitialize()}
     * 在客户端与服务端<b>共同的</b>初始化流程里触发），否则命令树同步时找不到参数类型：
     * 服务端发不出去、客户端也重建不出这个参数。
     *
     * <p>
     * 注册键是 ResourceLocation（按名字对齐，与注册顺序无关），因此服务端与客户端只要装的是
     * 同一份模组，参数类型就是同一个；这里<b>不</b>参与任何版本 / 白名单校验，
     * 客户端照常进服、进服后再同步自定义内容。
     */
    public static void register() {
        ArgumentTypeRegistry.registerArgumentType(SRE.id("custom_block_id"), BlockIdArgument.class,
                SingletonArgumentInfo.contextFree(BlockIdArgument::new));
        ArgumentTypeRegistry.registerArgumentType(SRE.id("custom_item_id"), ItemIdArgument.class,
                SingletonArgumentInfo.contextFree(ItemIdArgument::new));
    }

    /** 方块 id 参数（补全自定义方块 id）。 */
    public static CustomContentArgument block() {
        return BLOCK;
    }

    /** 物品 id 参数（补全自定义列车物品 id）。 */
    public static CustomContentArgument item() {
        return ITEM;
    }

    /** 取参数值。 */
    public static Value getValue(CommandContext<?> context, String name) {
        return context.getArgument(name, Value.class);
    }

    @Override
    public Value parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String id = reader.readUnquotedString();
        if (id.isEmpty()) {
            reader.setCursor(start);
            throw ERROR_EXPECTED_ID.createWithContext(reader);
        }
        String components = null;
        if (reader.canRead() && reader.peek() == '[') {
            components = readComponentBlock(reader);
        }
        return new Value(id, components);
    }

    /** 读一段 {@code [...]}（返回不含外层方括号的内容；支持字符串里的转义与嵌套括号）。 */
    private static String readComponentBlock(StringReader reader) throws CommandSyntaxException {
        reader.skip(); // '['
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        char quoteChar = 0;
        while (reader.canRead()) {
            char c = reader.read();
            if (quoted) {
                if (c == '\\') {
                    out.append(c);
                    if (reader.canRead()) {
                        out.append(reader.read());
                    }
                    continue;
                }
                if (c == quoteChar) {
                    quoted = false;
                }
                out.append(c);
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quoteChar = c;
                out.append(c);
                continue;
            }
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == ']') {
                if (depth == 0) {
                    return out.toString();
                }
                depth--;
            }
            out.append(c);
        }
        throw ERROR_UNCLOSED.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        if (remaining.indexOf('[') >= 0) {
            // 组件块内部不做补全（与原版行为一致）
            return Suggestions.empty();
        }
        String prefix = remaining.toLowerCase(Locale.ROOT);
        for (String id : ids()) {
            if (id.startsWith(prefix)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private List<String> ids() {
        return switch (kind) {
            case BLOCK -> CustomBlockLoader.getAllData().stream().map(data -> data.id).toList();
            case ITEM -> CustomItemLoader.getAllData().stream().map(data -> data.id).toList();
        };
    }
}
