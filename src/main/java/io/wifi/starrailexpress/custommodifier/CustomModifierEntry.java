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

package io.wifi.starrailexpress.custommodifier;

import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.Locale;

/**
 * 自定义修饰符的运行时实例（注册进 {@link org.agmas.harpymodloader.modifiers.HMLModifiers#MODIFIERS}）。
 *
 * <p>
 * 与自定义职业的 {@code CustomNormalRole} 对应：持有配置数据，方便运行时读取触发条件 / 触发内容。
 */
public class CustomModifierEntry extends SREModifier {

    /** 自定义修饰符标记 flag。 */
    public static final String FLAG = "inner.custom_modifier";

    private final CustomModifierData data;

    public CustomModifierEntry(CustomModifierData data) {
        super(id(data.englishId), data.getColor(), null, null, false, false);
        this.data = data;
        this.addFlag(FLAG);
    }

    public static ResourceLocation id(String englishId) {
        return ResourceLocation.fromNamespaceAndPath(CustomModifierData.NAMESPACE,
                englishId == null ? "unknown" : englishId.toLowerCase(Locale.ROOT));
    }

    /** 是否为自定义修饰符（按实例类型或命名空间判断）。 */
    public static boolean isCustomModifier(SREModifier modifier) {
        if (modifier == null)
            return false;
        if (modifier instanceof CustomModifierEntry)
            return true;
        return modifier.identifier() != null
                && CustomModifierData.NAMESPACE.equals(modifier.identifier().getNamespace());
    }

    public CustomModifierData getData() {
        return data;
    }
}
