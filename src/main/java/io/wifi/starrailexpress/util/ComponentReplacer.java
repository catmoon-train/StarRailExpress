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

package io.wifi.starrailexpress.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 工具类，用于将 Component 中的占位符替换为指定值（不递归）
 */
public class ComponentReplacer {

    /**
     * 替换 component 中所有出现的模式（key）为对应的值（value）
     * 
     * @param component    原始组件
     * @param replacements 模式->替换值 映射
     * @return 新的组件（不会修改原组件）
     */
    public static Component replacePlaceholders(Component component, Map<String, String> replacements) {
        if (component == null || replacements == null || replacements.isEmpty()) {
            return component;
        }

        // 1. 按长度降序排序，避免短模式误匹配长模式的前缀
        List<String> patterns = new ArrayList<>(replacements.keySet());
        patterns.sort((a, b) -> Integer.compare(b.length(), a.length()));

        // 2. 构建正则表达式（转义特殊字符）
        String regex = patterns.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile(regex);

        // 3. 递归处理
        return replaceRecursively(component, replacements, pattern);
    }

    private static Component replaceRecursively(Component component,
            Map<String, String> replacements,
            Pattern pattern) {
        // 处理所有子组件
        List<Component> newSiblings = new ArrayList<>();
        for (Component sibling : component.getSiblings()) {
            newSiblings.add(replaceRecursively(sibling, replacements, pattern));
        }

        // 处理当前组件的内容
        if (component.getContents() instanceof PlainTextContents plain) {
            String originalText = plain.text();
            String newText = replacePatterns(originalText, pattern, replacements);

            // 如果文本发生了变化，创建新组件并保留样式
            if (!newText.equals(originalText)) {
                MutableComponent newComp = Component.literal(newText)
                        .withStyle(component.getStyle());
                // 添加替换后的子组件
                for (Component sib : newSiblings) {
                    newComp.append(sib);
                }
                return newComp;
            }
        }

        // 内容未变（或非纯文本），仅替换子组件
        MutableComponent newComp = component.copy();
        newComp.getSiblings().clear();
        newComp.getSiblings().addAll(newSiblings);
        return newComp;
    }

    /**
     * 在给定文本中一次性替换所有匹配的模式（非递归）
     */
    private static String replacePatterns(String text,
            Pattern pattern,
            Map<String, String> replacements) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            String replacement = replacements.getOrDefault(matched, matched);
            // 转义替换中的特殊字符，确保字面替换
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}