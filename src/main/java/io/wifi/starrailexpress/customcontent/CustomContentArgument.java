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
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 自定义内容的 id 参数：{@code <id>[<组件>]}，写法、报错与补全都跟着原版的物品参数走。
 *
 * <p>
 * 例：{@code my_sword[minecraft:custom_name="Excalibur",minecraft:unbreakable={}]}，
 * 与原版 {@code /give @s minecraft:stone[...]} 完全同一套语法（含 {@code !} 移除组件、
 * 引号、转义、嵌套复合标签、组件名省略 {@code minecraft:} 前缀等）。
 *
 * <p>
 * <b>实现方式</b>：本类自己不解析组件、也不自己摆补全候选，而是把「当前光标处的文本」
 * 换成 {@link #SHADOW_ITEM 占位物品 id + 原样尾部} 后交给原版的 {@link ItemParser} /
 * {@link ItemArgument}：
 * <ul>
 * <li>解析：用原版解析器读完整块后取其中的 {@link DataComponentPatch}，并把解析器消费的
 * 字符数换算回真实输入（真实 id 长度与占位 id 不同，所以只核对组件块的长度）；</li>
 * <li>补全：把同一个「占位 id + 尾部」交给原版，取它给出的建议<b>文本</b>，再按真实输入里
 * 当前片段（{@code [} / {@code ,} / {@code =} 之后）的位置重新生成建议，
 * 于是组件名、值、乃至 {@code !} 屏蔽列表的补全都与原版一致。</li>
 * </ul>
 *
 * <p>
 * 注册时必须用 {@code SingletonArgumentInfo.contextAware}（参数需要 {@link CommandBuildContext}
 * 来查注册表），两端各自用自己的 build context 重建实例，与原版物品参数完全相同的机制。
 */
public class CustomContentArgument implements ArgumentType<CustomContentArgument.Value> {

    /** 占位物品：只是为了让原版解析器有个起点，真正发给玩家的是自定义内容。 */
    private static final String SHADOW_ITEM = "minecraft:stone";

    /** 这个 id 属于哪张自定义内容表（只影响候选来源与后续校验的提示）。 */
    public enum Kind {
        BLOCK,
        ITEM
    }

    /** 解析结果：自定义 id + 原版组件补丁（未写组件时是 {@link DataComponentPatch#EMPTY}）。 */
    public record Value(String id, DataComponentPatch components) {
        /** 是否写了组件。 */
        public boolean hasComponents() {
            return components != null && !components.isEmpty();
        }
    }

    private static final SimpleCommandExceptionType ERROR_EXPECTED_ID = new SimpleCommandExceptionType(
            Component.translatable("sre.custom_content.error.expected_id"));

    private final Kind kind;
    /** 借原版解析 {@code [组件]}（含 {@code !} 移除语法与原版报错）。 */
    private final ItemParser parser;
    /** 借原版生成组件块内的补全。 */
    private final ItemArgument suggestionDelegate;

    public CustomContentArgument(Kind kind, CommandBuildContext buildContext) {
        this.kind = kind;
        this.parser = new ItemParser(buildContext);
        this.suggestionDelegate = ItemArgument.item(buildContext);
    }

    /**
     * 方块 id 参数。
     *
     * <p>
     * 两个 kind 各用一个<b>独立的参数类型类</b>：命令树同步到客户端时，客户端按
     * 「参数类型」重建实例（{@code ArgumentTypeInfos} 的 class → 序列化器映射），
     * 同一个类注册两个 id 会让这个映射变得有歧义，其中一个分支就会拿到错误的补全来源。
     */
    public static final class BlockIdArgument extends CustomContentArgument {
        public BlockIdArgument(CommandBuildContext buildContext) {
            super(Kind.BLOCK, buildContext);
        }
    }

    /** 物品 id 参数（补全自定义列车物品 id）。 */
    public static final class ItemIdArgument extends CustomContentArgument {
        public ItemIdArgument(CommandBuildContext buildContext) {
            super(Kind.ITEM, buildContext);
        }
    }

    /**
     * 注册参数类型。
     *
     * <p>
     * 必须<b>在注册表冻结前</b>调用（本项目的调用点是
     * {@code SRECommandRegister.registerCommandArgumentTypes()}，由 {@code SRE.onInitialize()}
     * 在客户端与服务端<b>共同的</b>初始化流程里触发），否则命令树同步时找不到参数类型。
     * 注册键是 ResourceLocation（按名字对齐，与注册顺序无关），不参与任何版本 / 白名单校验。
     */
    public static void register() {
        ArgumentTypeRegistry.registerArgumentType(SRE.id("custom_block_id"), BlockIdArgument.class,
                SingletonArgumentInfo.contextAware(BlockIdArgument::new));
        ArgumentTypeRegistry.registerArgumentType(SRE.id("custom_item_id"), ItemIdArgument.class,
                SingletonArgumentInfo.contextAware(ItemIdArgument::new));
    }

    /** 方块 id 参数（补全自定义方块 id）。 */
    public static CustomContentArgument block(CommandBuildContext buildContext) {
        return new BlockIdArgument(buildContext);
    }

    /** 物品 id 参数（补全自定义列车物品 id）。 */
    public static CustomContentArgument item(CommandBuildContext buildContext) {
        return new ItemIdArgument(buildContext);
    }

    /** 取参数值。 */
    public static Value getValue(CommandContext<?> context, String name) {
        return context.getArgument(name, Value.class);
    }

    // ==================== 解析 ====================

    @Override
    public Value parse(StringReader reader) throws CommandSyntaxException {
        int idStart = reader.getCursor();
        String id = reader.readUnquotedString();
        if (id.isEmpty()) {
            reader.setCursor(idStart);
            throw ERROR_EXPECTED_ID.createWithContext(reader);
        }
        if (!reader.canRead() || reader.peek() != '[') {
            return new Value(id, DataComponentPatch.EMPTY);
        }
        // 从 '[' 起整段交给原版：占位物品 id + 原样尾部
        String tail = reader.getString().substring(reader.getCursor());
        StringReader shadow = new StringReader(SHADOW_ITEM + tail);
        ItemParser.ItemResult result = parser.parse(shadow);
        // 原版消费了「占位 id + 组件块」，真实输入只消费组件块（长度 = 总消费 - 占位 id 长度）
        reader.setCursor(reader.getCursor() + (shadow.getCursor() - SHADOW_ITEM.length()));
        return new Value(id, result.components());
    }

    // ==================== 补全 ====================

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int bracket = remaining.indexOf('[');
        if (bracket < 0) {
            // 还在写 id：给自定义 id 候选
            String prefix = remaining.toLowerCase(Locale.ROOT);
            for (String id : ids()) {
                if (id.startsWith(prefix)) {
                    builder.suggest(id);
                }
            }
            return builder.buildFuture();
        }
        return suggestComponents(context, builder, bracket);
    }

    /** 组件块内的补全：交给原版物品参数，再把建议文本按真实位置重新摆一遍。 */
    private <S> CompletableFuture<Suggestions> suggestComponents(CommandContext<S> context,
            SuggestionsBuilder builder, int bracketInRemaining) {
        if (!(context.getSource() instanceof SharedSuggestionProvider)) {
            // 拿不到注册表就没法让原版解析组件（正常两端都能走到这里）
            return Suggestions.empty();
        }
        String input = builder.getInput();
        int bracketIndex = builder.getStart() + bracketInRemaining;
        String tail = input.substring(bracketIndex);

        // 原版将在「占位物品 id + 尾部」上解析：start = 0，让原版从参数开头读起
        //（与原版自己的调用方式一致：从参数起点解析，再对光标所在片段给建议）
        SuggestionsBuilder shadowBuilder = new SuggestionsBuilder(SHADOW_ITEM + tail, 0);
        CompletableFuture<Suggestions> shadowFuture = suggestionDelegate.listSuggestions(context, shadowBuilder);

        // 当前片段在真实输入里的起点：最后一个未被引号包住的 [ , = 之后
        int fragmentStart = fragmentStart(input, bracketIndex);
        return shadowFuture.thenApply(shadowSuggestions -> {
            SuggestionsBuilder fragmentBuilder = new SuggestionsBuilder(input, fragmentStart);
            for (Suggestion suggestion : shadowSuggestions.getList()) {
                String text = suggestion.getText();
                if (!text.isEmpty()) {
                    fragmentBuilder.suggest(text);
                }
            }
            return fragmentBuilder.build();
        });
    }

    /**
     * 找当前可替换片段的起点：{@code [} / {@code ,} / {@code =} 之后（跳过引号里的分隔符）。
     *
     * <p>
     * 与原版物品参数的补全范围一致：组件名在 {@code [} 或 {@code ,} 之后，值在 {@code =} 之后。
     */
    private static int fragmentStart(String input, int from) {
        int start = from + 1;
        boolean quoted = false;
        char quoteChar = 0;
        for (int i = from + 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '\\') {
                    i++;
                } else if (c == quoteChar) {
                    quoted = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quoteChar = c;
            } else if (c == '[' || c == ',' || c == '=') {
                start = i + 1;
            }
        }
        return Math.min(start, input.length());
    }

    private List<String> ids() {
        return switch (kind) {
            case BLOCK -> CustomBlockLoader.getAllData().stream().map(data -> data.id).toList();
            case ITEM -> CustomItemLoader.getAllData().stream().map(data -> data.id).toList();
        };
    }
}
