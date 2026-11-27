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

package io.wifi.starrailexpress.roster.pool;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

/**
 * 一个「职业池」——本局允许生成的职业（以及修饰符）预设。
 *
 * <p>
 * 池子只描述<b>内容</b>，不描述玩法：选中后由
 * {@link RolePoolRegistry#applyToRoster(java.util.Collection)} 写入职业轮换名单
 * （{@link io.wifi.starrailexpress.roster.RoleRosterManager}），之后的职业生成、
 * 数量与概率仍走原有的注册配置。
 *
 * <p>
 * 职业用 <b>path</b>（如 {@code telegrapher}）而不是完整 id 记录：
 * 展示名（{@code announcement.star.role.<path>}）与阵营（{@link io.wifi.starrailexpress.api.RoleTeam}）
 * 都在运行时解析，翻译或阵营调整后池子数据不会失真，也方便策划直接改这里。
 *
 * <p>
 * {@link #rolePaths()} 为空表示「全部已注册职业」；{@link #modifierPaths()}
 * 为空表示「全部修饰符」（内置池子目前都是全修饰符）。
 */
public record RolePool(String id, String fallbackName, List<String> rolePaths, List<String> modifierPaths) {

    /** 投票选项 resultId 的前缀，完整格式为 {@code role_pool:<池 id>}。 */
    public static final String RESULT_PREFIX = "role_pool:";

    /** 只有职业、修饰符全开（常用写法）。 */
    public static RolePool of(String id, String fallbackName, List<String> rolePaths) {
        return new RolePool(id, fallbackName, List.copyOf(rolePaths), List.of());
    }

    public static RolePool of(String id, String fallbackName, List<String> rolePaths, List<String> modifierPaths) {
        return new RolePool(id, fallbackName, List.copyOf(rolePaths), List.copyOf(modifierPaths));
    }

    /** 池子名称的翻译键（缺失时回退到 {@link #fallbackName()}）。 */
    public String nameKey() {
        return "gui.sre.role_pool.name." + id;
    }

    public Component displayName() {
        return Component.translatableWithFallback(nameKey(), fallbackName);
    }

    /** 是否表示「全部职业」（没有显式列出职业）。 */
    public boolean allRoles() {
        return rolePaths.isEmpty();
    }

    /** 是否表示「全部修饰符」。 */
    public boolean allModifiers() {
        return modifierPaths.isEmpty();
    }

    /** 投票选项中代表该池子的 resultId。 */
    public String voteResultId() {
        return RESULT_PREFIX + id;
    }

    /** 从投票 resultId 反查池子 id；不是职业池选项时返回 {@code null}。 */
    @Nullable
    public static String idOfResult(@Nullable String resultId) {
        if (resultId == null || !resultId.startsWith(RESULT_PREFIX)) {
            return null;
        }
        String id = resultId.substring(RESULT_PREFIX.length());
        return id.isBlank() ? null : id;
    }
}
