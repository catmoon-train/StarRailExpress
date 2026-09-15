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

package org.agmas.harpymodloader.modifiers;

import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 修饰符互斥的统一处理，与互斥职业（{@link io.wifi.starrailexpress.api.SRERole#opposingRoles}）对应。
 *
 * <p>
 * 互斥关系由 {@link SREModifier#addTwoWayOpposingModifier} /
 * {@link SREModifier#addOpposingModifier} 声明，本类负责让声明真正生效：
 * <ul>
 * <li><b>生成阶段</b>：{@link #hasOpposingModifier} 供分配器
 * （{@code SREMurderGameMode.canAssignModifierToPlayer}）筛选候选玩家，
 * 保证两个互斥修饰符不会被分给同一名玩家；</li>
 * <li><b>运行时</b>：{@link #init()} 监听 {@link ModifierAssigned}，
 * 兜底移除玩家身上与新修饰符互斥的旧修饰符（后到者优先，与旧的手写逻辑一致），
 * 覆盖强制分配、换职、自定义修饰符等绕过分配器的添加路径。</li>
 * </ul>
 *
 * <p>
 * 移除走的是 {@link ModifierRemoved} 事件，所以修饰符自己的属性/状态清理逻辑
 * （例如缩放的 AttributeModifier）会照常执行，声明方无需再手写移除代码。
 */
public final class ModifierOpposingHelper {

    private ModifierOpposingHelper() {
    }

    /**
     * 注册运行时兜底监听。由 {@link HMLModifiers#init()} 调用。
     */
    public static void init() {
        ModifierAssigned.EVENT.register((player, modifier) -> removeOpposingModifiers(player, modifier));
    }

    /**
     * 生成阶段判定：给定集合中是否存在与 {@code modifier} 互斥的修饰符。
     *
     * @param modifier 待分配的修饰符
     * @param existing 该玩家已有的修饰符（本轮已分配 + 已在身上的均可）
     * @return 存在互斥项时返回 {@code true}
     */
    public static boolean hasOpposingModifier(SREModifier modifier, Collection<SREModifier> existing) {
        if (modifier == null || existing == null || existing.isEmpty()) {
            return false;
        }
        for (SREModifier other : existing) {
            if (modifier.isOpposingModifier(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 移除玩家身上所有与 {@code modifier} 互斥的修饰符。
     *
     * <p>
     * 会依次触发 {@link ModifierRemoved} 事件（保证属性清理），最后请求一次同步。
     * 传入的 {@code modifier} 本身不会被移除，调用方无需先把它从组件里排除。
     *
     * @param player   目标玩家，{@code null} 时不做任何事
     * @param modifier 参照修饰符，{@code null} 时不做任何事
     * @return 被移除的修饰符数量
     */
    public static int removeOpposingModifiers(Player player, SREModifier modifier) {
        if (player == null || modifier == null) {
            return 0;
        }
        WorldModifierComponent wmc = WorldModifierComponent.getInstance(player);
        if (wmc == null) {
            return 0;
        }
        // 先收集再移除：getModifiers 返回的是组件内部的实时集合，
        // 且 ModifierRemoved 的监听方（例如双子的配对清理）可能连带移除伙伴的修饰符
        List<SREModifier> conflicts = null;
        for (SREModifier other : wmc.getModifiers(player)) {
            if (modifier.isOpposingModifier(other)) {
                if (conflicts == null) {
                    conflicts = new ArrayList<>();
                }
                conflicts.add(other);
            }
        }
        if (conflicts == null) {
            return 0;
        }
        for (SREModifier other : conflicts) {
            wmc.removeModifier(player.getUUID(), other, false);
            ModifierRemoved.EVENT.invoker().removeModifier(player, other);
        }
        wmc.sync();
        return conflicts.size();
    }
}
