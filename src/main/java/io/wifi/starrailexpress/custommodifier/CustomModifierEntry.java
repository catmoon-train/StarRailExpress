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

    /**
     * 构造时的配置数据。
     *
     * <p>
     * <b>只用来记 {@code englishId}</b>：运行时读取一律按 id 到 {@link CustomModifierLoader}
     * 实时解析（见 {@link #getData()}），不把这里当数据源。
     */
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

    /**
     * 当前生效的配置数据。
     *
     * <p>
     * 必须走「实时解析」而不是构造时捕获的对象：修饰符实例在开局分配后就固定住了，
     * 而工具里改完配置会重载并生成<b>新的</b>数据对象；旧实例若继续用旧对象，就会出现
     * 「条件 / 指令明明填了却永远不触发」。
     */
    public CustomModifierData getData() {
        return liveData();
    }

    // ==================== 名称 / 介绍（直连配置，不走翻译键） ====================

    /**
     * 当前生效的配置数据：服务端优先用加载器里的权威数据，
     * 客户端在拿不到时回退到网络同步副本（与 {@code CustomNormalRole} 同一套处理）。
     */
    private CustomModifierData liveData() {
        // 构造尚未完成时（父类构造里的虚调用）没有 id 可用
        if (this.data == null || this.data.englishId == null) {
            return null;
        }
        // 忽略大小写：identifier 是小写的，配置里改过 id 大小写时旧实例也能查到新数据
        CustomModifierData live = CustomModifierLoader.getCustomModifierDataIgnoreCase(this.data.englishId);
        if (live != null) {
            return live;
        }
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            CustomModifierData synced = CustomModifierClientNetwork.getSyncedModifier(this.data.englishId);
            if (synced != null) {
                return synced;
            }
        }
        // 加载器与同步副本都查不到 = 该 id 已从配置里删除 / 改名。
        // 这里绝不能退回构造时的 this.data：那正是「改完配置重载后仍按旧数据跑」的来源。
        // 返回 null 让调用方停用该修饰符（名称自动回退成 id）。
        return null;
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

    /**
     * 颜色也实时取自配置。
     *
     * <p>
     * 构造时 {@code super(id, data.getColor(), ...)} 把颜色存成了字段，改完颜色重载后
     * 玩家身上的旧实例还会是旧颜色，所以这里覆盖掉，直接读当前配置。
     */
    @Override
    public int color() {
        CustomModifierData live = liveData();
        return live != null ? live.getColor() : super.color();
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
