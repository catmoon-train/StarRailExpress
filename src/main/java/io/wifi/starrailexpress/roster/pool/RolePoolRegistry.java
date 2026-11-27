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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.roster.RoleRosterManager;

/**
 * 职业池的查询与落地。
 *
 * <p>
 * <b>查询</b>：{@link #all()} / {@link #byId(String)} / {@link #byResultId(String)}，
 * 以及给界面用的 {@link #rolesOf(RolePool)}（按阵营排好序）与 {@link #groupsOf(RolePool)}。
 *
 * <p>
 * <b>落地</b>：{@link #applyToRoster(Collection)} 把若干池子<b>合并</b>后写入职业轮换名单
 * （只启用池内职业，修饰符默认全开），之后职业是否真的参与生成、数量与概率仍由原有注册配置决定。
 * 服务端与客户端都会用到本类：客户端只用查询部分（展示池内职业），落地只在服务端调用。
 */
public final class RolePoolRegistry {

    /** 池内某一阵营的职业（{@code team} 为 {@code null} 表示未归类的职业）。 */
    public record FactionGroup(@Nullable RoleTeam team, List<SRERole> roles) {
    }

    private static final Map<String, RolePool> BY_ID;

    static {
        Map<String, RolePool> map = new LinkedHashMap<>();
        for (RolePool pool : BuiltinRolePools.ALL) {
            map.put(pool.id(), pool);
        }
        BY_ID = Map.copyOf(map);
    }

    private RolePoolRegistry() {
    }

    /** 全部可用池子，顺序即投票界面顺序。 */
    public static List<RolePool> all() {
        return BuiltinRolePools.ALL;
    }

    @Nullable
    public static RolePool byId(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 从投票 resultId（{@code role_pool:<id>}）反查池子。 */
    @Nullable
    public static RolePool byResultId(@Nullable String resultId) {
        return byId(RolePool.idOfResult(resultId));
    }

    /**
     * 池内职业，按 {@link RoleTeam#factionDisplayOrder(SRERole)}
     * （平民 → 警长 → 杀手 → 中立细分）再按职业 path 排序，保证服务端与客户端顺序一致。
     * 池子未列举职业（{@link RolePool#allRoles()}）时展开为全部已注册职业。
     */
    public static List<SRERole> rolesOf(RolePool pool) {
        List<SRERole> roles = new ArrayList<>();
        if (pool.allRoles()) {
            roles.addAll(TMMRoles.ROLES.values());
        } else {
            for (String path : pool.rolePaths()) {
                SRERole role = TMMRoles.getRoleByPath(path);
                if (role == null) {
                    SRE.LOGGER.warn("[RolePool] 池子 {} 引用了不存在的职业 path：{}", pool.id(), path);
                    continue;
                }
                roles.add(role);
            }
        }
        roles.sort(Comparator.comparingInt(RoleTeam::factionDisplayOrder)
                .thenComparing(role -> role.identifier().getPath()));
        return roles;
    }

    /** 池内职业按阵营分组，顺序与 {@link #rolesOf(RolePool)} 一致。 */
    public static List<FactionGroup> groupsOf(RolePool pool) {
        Map<Integer, List<SRERole>> byOrder = new TreeMap<>();
        Map<Integer, RoleTeam> teamByOrder = new LinkedHashMap<>();
        for (SRERole role : rolesOf(pool)) {
            int order = RoleTeam.factionDisplayOrder(role);
            byOrder.computeIfAbsent(order, key -> new ArrayList<>()).add(role);
            teamByOrder.putIfAbsent(order, RoleTeam.of(role));
        }
        List<FactionGroup> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<SRERole>> entry : byOrder.entrySet()) {
            groups.add(new FactionGroup(teamByOrder.get(entry.getKey()), List.copyOf(entry.getValue())));
        }
        return groups;
    }

    /** 池内修饰符；{@link RolePool#allModifiers()} 时返回全部已注册修饰符。 */
    public static List<SREModifier> modifiersOf(RolePool pool) {
        if (pool.allModifiers()) {
            return List.copyOf(HMLModifiers.MODIFIERS);
        }
        List<SREModifier> modifiers = new ArrayList<>();
        for (String path : pool.modifierPaths()) {
            SREModifier modifier = HMLModifiers.getModifierByPath(path);
            if (modifier == null) {
                SRE.LOGGER.warn("[RolePool] 池子 {} 引用了不存在的修饰符 path：{}", pool.id(), path);
                continue;
            }
            modifiers.add(modifier);
        }
        return modifiers;
    }

    /**
     * 把若干池子合并后写入职业轮换名单（只启用池内职业与修饰符），返回实际启用的职业数量。
     *
     * <p>
     * 只要其中任一池子声明了「全部修饰符」，修饰符就按全开处理；职业则是所有池子的并集，
     * 所以同时选中两个池子会得到更宽的职业范围。
     */
    public static int applyToRoster(Collection<RolePool> pools) {
        if (pools == null || pools.isEmpty()) {
            return 0;
        }
        Map<String, SRERole> roles = new LinkedHashMap<>();
        Map<String, SREModifier> modifiers = new LinkedHashMap<>();
        boolean allModifiers = false;
        List<String> poolNames = new ArrayList<>();
        for (RolePool pool : pools) {
            poolNames.add(pool.displayName().getString());
            for (SRERole role : rolesOf(pool)) {
                roles.putIfAbsent(role.identifier().toString(), role);
            }
            if (pool.allModifiers()) {
                allModifiers = true;
            } else {
                for (SREModifier modifier : modifiersOf(pool)) {
                    modifiers.putIfAbsent(modifier.identifier().toString(), modifier);
                }
            }
        }
        if (allModifiers) {
            modifiers.clear();
            for (SREModifier modifier : HMLModifiers.MODIFIERS) {
                modifiers.put(modifier.identifier().toString(), modifier);
            }
        }
        RoleRosterManager.applyAsPool(roles.values(), modifiers.values());
        SRE.LOGGER.info("[RolePool] 已应用职业池 {}：{} 个职业 / {} 个修饰符",
                String.join(" + ", poolNames), roles.size(), modifiers.size());
        return roles.size();
    }
}
