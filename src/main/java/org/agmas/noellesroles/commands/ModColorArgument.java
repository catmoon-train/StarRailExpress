package org.agmas.noellesroles.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.agmas.noellesroles.utils.RoleUtils;

public class ModColorArgument implements ArgumentType<Integer> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0", "123", "red", "dark_green");
    private static final DynamicCommandExceptionType INVALID_COLOR_NAME = new DynamicCommandExceptionType(
            name -> Component.literal("Unknown Color Name: " + name));

    private final int minimum;
    private final int maximum;

    public ModColorArgument(final int minimum, final int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * 创建一个无范围限制的颜色参数（接受任何整数）
     */
    public static ModColorArgument color() {
        return new ModColorArgument(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * 创建一个带范围限制的颜色参数
     */
    public static ModColorArgument colorRange(int min, int max) {
        return new ModColorArgument(min, max);
    }

    public static int getColor(final CommandContext<?> context, final String name) {
        return context.getArgument(name, Integer.class);
    }

    @Override
    public Integer parse(final StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        String input = reader.readString(); // 读取一个单词（不带引号）或带引号的字符串

        // 尝试将输入解析为整数
        try {
            int value = Integer.parseInt(input);
            if (value < minimum || value > maximum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader, value,
                        minimum);
            }
            return value;
        } catch (NumberFormatException ignored) {
            // 不是整数，尝试作为颜色名称处理
        }

        // 尝试匹配颜色名称（忽略大小写）
        String lowerInput = input.toLowerCase(Locale.ROOT);
        if (lowerInput.startsWith("role_") && lowerInput.length() >= 6) {
            lowerInput = lowerInput.substring("role_".length());
            for (SRERole role : TMMRoles.ROLES.values()) {
                Integer color = role.getColor();
                if (color != null && role.identifier().getPath().equalsIgnoreCase(lowerInput)) {
                    if (color < minimum || color > maximum) {
                        reader.setCursor(start);
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader,
                                color,
                                minimum);
                    }
                    return color;
                }
            }
        } else {
            for (ChatFormatting format : ChatFormatting.values()) {
                Integer color = format.getColor();
                if (color != null && format.getName().equalsIgnoreCase(lowerInput)) {
                    if (color < minimum || color > maximum) {
                        reader.setCursor(start);
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader,
                                color,
                                minimum);
                    }
                    return color;
                }
            }
        }

        // 没有匹配的颜色名称，抛出异常
        reader.setCursor(start);
        throw INVALID_COLOR_NAME.create(input);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context,
            final SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        // 1. 建议所有有效的颜色名称
        for (ChatFormatting format : ChatFormatting.values()) {
            Integer color = format.getColor();
            if (color != null) {
                String name = format.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name,
                            Component.literal("Value: " + color).append(Component.literal(" [■]").withColor(color)));
                }
            }
        }
        // 2. 建议所有职业
        for (SRERole role : TMMRoles.ROLES.values()) {
            Integer color = role.getColor();
            if (color != null) {
                String name = "role_" + role.identifier().getPath();
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name,
                            Component.literal("").append(RoleUtils.getRoleOrModifierNameWithColor(role)).append(
                                    Component.literal(" | Value: " + color)));
                }
            }
        }
        // 3. 建议常见数字示例（可选）
        if (remaining.isEmpty() || "16777215".startsWith(remaining)) {
            builder.suggest("16777215",
                    Component.literal("(Also white) ").append(Component.literal("[■]").withColor(16777215)));
        }
        // boolean correct = false;
        // int remaingcolor = 0;
        // try {
        // remaingcolor = Integer.parseInt(remaining);
        // correct = true;
        // } catch (NumberFormatException e) {
        // }
        // if (correct) {
        // builder.suggest(remaining, Component.literal("
        // ").append(Component.literal("[■]").withColor(remaingcolor)));
        // }
        // 可以添加更多常见颜色值

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ModColorArgument))
            return false;

        ModColorArgument that = (ModColorArgument) o;
        return maximum == that.maximum && minimum == that.minimum;
    }

    @Override
    public int hashCode() {
        return 31 * minimum + maximum;
    }

    @Override
    public String toString() {
        if (minimum == Integer.MIN_VALUE && maximum == Integer.MAX_VALUE) {
            return "color()";
        } else if (maximum == Integer.MAX_VALUE) {
            return "color(" + minimum + ")";
        } else {
            return "color(" + minimum + ", " + maximum + ")";
        }
    }
}