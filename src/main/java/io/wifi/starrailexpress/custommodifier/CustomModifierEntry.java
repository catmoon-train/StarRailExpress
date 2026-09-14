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

import io.wifi.starrailexpress.client.network.CustomModifierClientNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.Locale;

/**
 * 自定义修饰符的运行时实例（注册进 {@link org.agmas.harpymodloader.modifiers.HMLModifiers#MODIFIERS}）。
 *
 * <p>
 * 与自定义职业的 {@code CustomNormalRole} 对应：持有配置数据，方便运行时读取触发条件 / 触发内容。
 *
 * <p>
 * 名称与介绍<b>直接来自配置</b>（{@code displayName} / {@code description}），不走翻译键
 * —— 否则介绍页面会显示成 {@code announcement.star.modifier.xxx} 这样的原始键名。
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

    // ==================== 名称 / 介绍（直连配置，不走翻译键） ====================

    /**
     * 当前生效的配置数据：服务端优先用加载器里的权威数据，
     * 客户端在拿不到时回退到网络同步副本（与 {@code CustomNormalRole} 同一套处理）。
     */
    private CustomModifierData liveData() {
        CustomModifierData live = CustomModifierLoader.getCustomModifierData(this.data.englishId);
        if (live != null) {
            return live;
        }
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            CustomModifierData synced = CustomModifierClientNetwork.getSyncedModifier(this.data.englishId);
            if (synced != null) {
                return synced;
            }
        }
        return this.data;
    }

    /** 配置里的显示名称（未填写时回退到完整 id）。 */
    private String configuredName() {
        CustomModifierData data = liveData();
        if (data != null && data.displayName != null && !data.displayName.isBlank()) {
            return data.displayName;
        }
        return identifier().toString();
    }

    /** 配置里的介绍（未填写时返回空串）。 */
    private String configuredDescription() {
        CustomModifierData data = liveData();
        if (data != null && data.description != null && !data.description.isEmpty()) {
            return fixNewlines(data.description);
        }
        return "";
    }

    @Override
    public Component getName() {
        return getName(false);
    }

    @Override
    public MutableComponent getName(boolean color) {
        MutableComponent text = Component.literal(configuredName());
        return color ? text.withColor(color()) : text;
    }

    @Override
    public Component getDescription() {
        return Component.literal(configuredDescription());
    }

    @Override
    public Component getSimpleDescription() {
        return getDescription();
    }

    @Override
    public boolean hasSimpleDescription() {
        return !configuredDescription().isEmpty();
    }

    /** 允许配置里用 {@code \n} 写换行（与自定义职业一致）。 */
    private static String fixNewlines(String text) {
        return text == null ? "" : text.replace("\\n", "\n");
    }
}
