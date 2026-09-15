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

import io.wifi.starrailexpress.SRE;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HMLModifiers {

    public static final ArrayList<SREModifier> MODIFIERS = new ArrayList<>();

    /**
     * path -> 修饰符。与 {@code TMMRoles.ROLES_BY_PATH} 对应，用于重复注册校验。
     */
    public static final Map<String, SREModifier> MODIFIERS_BY_PATH = new HashMap<>();

    private static final HashSet<String> CACHED_VERSIONS_LIST = new HashSet<>();

    public static SREModifier getModifier(ResourceLocation res) {
        if (res == null)
            return null;
        for (var m : MODIFIERS) {
            if (m.identifier().equals(res))
                return m;
        }
        return null;
    }

    /**
     * 按 identifier 的 path 取修饰符（忽略命名空间，与注册时的去重键一致）。
     *
     * @param path 修饰符 id 的 path 部分
     * @return 对应修饰符，不存在时返回 {@code null}
     */
    public static SREModifier getModifierByPath(String path) {
        if (path == null)
            return null;
        return MODIFIERS_BY_PATH.get(path);
    }

    public static void init() {
        // 修饰符互斥（SREModifier#addTwoWayOpposingModifier）的运行时兜底
        ModifierOpposingHelper.init();
    }

    public static SREModifier register(SREModifier modifier) {
        return registerModifier(modifier);
    }

    public static SREModifier register(SREModifier modifier, String... flags) {
        return registerModifier(modifier, flags);
    }

    public static SREModifier registerModifier(SREModifier modifier, String... flags) {
        return registerModifier(modifier.addFlag(flags));
    }

    /**
     * 注册修饰符。
     *
     * <p>
     * 与 {@code TMMRoles.registerRole} 一致：同一 path（忽略命名空间）重复注册直接抛异常，
     * 避免重复实例污染 {@link #MODIFIERS}。配置驱动的自定义修饰符请改用
     * {@link #registerCustomModifier(SREModifier)}。
     *
     * @throws IllegalArgumentException 该 path 已被注册
     */
    public static SREModifier registerModifier(SREModifier modifier) {
        String path = pathOf(modifier);
        if (path != null && MODIFIERS_BY_PATH.containsKey(path)) {
            // 拒绝注册
            throw new IllegalArgumentException(String.format(
                    "[MODIFIER REGISTERER] Duplicated modifier identifier path found: %s and %s. Ignore the new one.",
                    MODIFIERS_BY_PATH.get(path).identifier().toString(),
                    modifier.identifier().toString()));
        }
        MODIFIERS.add(modifier);
        if (path != null) {
            MODIFIERS_BY_PATH.put(path, modifier);
        }
        return modifier;
    }

    /**
     * 注册配置驱动的自定义修饰符。
     *
     * <p>
     * 与 {@code TMMRoles.registerCustomRole} 一致：重复注册不抛异常，记录错误并返回 {@code null}，
     * 调用方据此跳过该条配置。判定键与 {@link #registerModifier(SREModifier)} 相同（path 全局去重），
     * 因此自定义修饰符不能与内置修饰符同 path。
     *
     * @return 注册成功返回修饰符本身；该 path 已被占用时返回 {@code null}
     */
    public static SREModifier registerCustomModifier(SREModifier modifier) {
        String path = pathOf(modifier);
        if (path != null && MODIFIERS_BY_PATH.containsKey(path)) {
            SRE.LOGGER.error(
                    "[MODIFIER REGISTERER] Duplicated modifier identifier path found: {} and {}. Ignore the new one.",
                    MODIFIERS_BY_PATH.get(path).identifier().toString(), modifier.identifier().toString());
            return null;
        }
        return registerModifier(modifier);
    }

    /**
     * 注销修饰符，同时清理 {@link #MODIFIERS_BY_PATH} 索引。
     *
     * <p>
     * 索引只在当前确实指向该实例时才移除，避免误删此后注册的同 path 修饰符。
     *
     * @return 是否执行了注销（{@code modifier} 为 {@code null} 时返回 {@code false}）
     */
    public static boolean unregisterModifier(SREModifier modifier) {
        if (modifier == null)
            return false;
        MODIFIERS.remove(modifier);
        String path = pathOf(modifier);
        if (path != null && MODIFIERS_BY_PATH.get(path) == modifier) {
            MODIFIERS_BY_PATH.remove(path);
        }
        return true;
    }

    /** 与 {@code TMMRoles.unregisterCustomRole} 对应的别名。 */
    public static boolean unregisterCustomModifier(SREModifier modifier) {
        return unregisterModifier(modifier);
    }

    private static String pathOf(SREModifier modifier) {
        ResourceLocation id = modifier.identifier();
        return id == null ? null : id.getPath();
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : MODIFIERS) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }

    public static void refreshVersionTags() {
        CACHED_VERSIONS_LIST.clear();
        getAllAddedVersions();
    }

    public static Set<String> getAllAddedVersions() {
        if (!CACHED_VERSIONS_LIST.isEmpty()) {
            return new HashSet<>(CACHED_VERSIONS_LIST);
        }
        CACHED_VERSIONS_LIST.clear();
        for (var t : MODIFIERS) {
            CACHED_VERSIONS_LIST.add(t.getAddedVersion());
        }
        return new HashSet<>(CACHED_VERSIONS_LIST);
    }
}
