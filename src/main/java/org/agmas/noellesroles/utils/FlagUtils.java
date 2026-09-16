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

package org.agmas.noellesroles.utils;

import org.agmas.harpymodloader.modifiers.HMLModifiers;

import io.wifi.starrailexpress.api.SREAbstractInfoClass;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.network.chat.Component;

import java.text.Collator;
import java.util.*;

public class FlagUtils {
    public static HashSet<String> getAllFlags() {
        HashSet<String> set = new HashSet<String>();
        set.addAll(getAllRoleFlags());
        set.addAll(getAllModifierFlags());
        return set;
    }

    public static Collator COLLATOR = Collator.getInstance();
    public static Comparator<String> comparator = Comparator.comparingInt((String s) -> {
        if (s.startsWith("inner.version"))
            return 2; // 排最后
        if (s.startsWith("inner."))
            return 0; // 排最前
        return 1; // 中间
    }).thenComparing((a, b) -> COLLATOR.compare(getFlagName(a).getString(), getFlagName(b).getString()));

    public static LinkedHashSet<String> getAllFlagsSorted() {
        ArrayList<String> list = new ArrayList<>(getAllFlags());
        list.sort(comparator);
        return new LinkedHashSet<>(list);
    }

    public static Component getFlagName(String flag) {
        if (flag.isBlank())
            return Component.empty();
        if (flag.startsWith("inner.version.")) {
            return Component.translatable("screen.roleintroduce.flag.inner.version.prefix",
                    flag.substring("inner.version.".length()));
        }
        String path = "screen.roleintroduce.flag." + flag;
        return Component.translatableWithFallback(path, flag.toUpperCase().replaceAll("_", " "));
    }

    /**
     * 把配置里的自定义标签挂成 flags。
     *
     * <p>
     * 自定义职业 / 修饰符的编辑器允许作者写若干标签；挂到 flags 上之后，介绍页的分类筛选
     * （{@code screen.roleintroduce.flag.<flag>}）与 {@code inner.*} 语义旗标都会自动生效。
     * 没写翻译的标签由 {@link #getFlagName} 回退成「大写下划线转空格」的原文，不会显示成原始键。
     *
     * @param info 运行时职业或修饰符（两者都继承自 {@code SREAbstractInfoClass}）
     */
    public static void applyCustomFlags(SREAbstractInfoClass info, List<String> tags) {
        if (info == null || tags == null) {
            return;
        }
        for (String tag : tags) {
            String value = tag == null ? "" : tag.trim();
            if (!value.isEmpty()) {
                info.getFlags().add(value);
            }
        }
    }

    public static HashSet<String> getAllRoleFlags() {
        return TMMRoles.getAllFlags();
    }

    public static HashSet<String> getAllModifierFlags() {
        return HMLModifiers.getAllFlags();
    }
}
