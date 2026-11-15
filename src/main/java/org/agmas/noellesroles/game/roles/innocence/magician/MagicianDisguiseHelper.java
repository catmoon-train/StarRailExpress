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

package org.agmas.noellesroles.game.roles.innocence.magician;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.LooseEndRole;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.RoleInitialItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 魔术师「伪装大师」的通用工具集。
 *
 * <p>两件事：</p>
 * <ol>
 * <li><b>伪装候选池</b>：{@link #collectCandidateIds(Player)} / {@link #isDisguisableBase(SRERole)}
 * ——从所有已注册职业里筛出可伪装的杀手职业，口径是需求里写死的四条：
 * 非多狼位、非地图特色职业、无绑定生成关系、非迷失杀手，再叠加「不与场上已有杀手重复」。</li>
 * <li><b>伪装礼包</b>：{@link #grantDisguiseKit(ServerPlayer, SRERole)}——开局发放伪装职业的
 * 初始物品与初始药水效果（无敌效果一律剔除，如怀旧者 / 冤魂）。</li>
 * </ol>
 *
 * <p>只被魔术师使用；同源孪生职业（蓬莱山辉夜）仍然走各自的历史白名单。</p>
 */
public final class MagicianDisguiseHelper {

    private MagicianDisguiseHelper() {
    }

    /** 魔术师当前伪装职业，未伪装 / 已失效时返回 {@code null}。 */
    @Nullable
    public static SRERole resolveDisguisedRole(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        MagicianRoleData data = RoleData.getNullable(MagicianRoleData.class, player);
        if (data == null || data.getDisguiseRoleId() == null) {
            return null;
        }
        SRERole role = TMMRoles.ROLES.get(data.getDisguiseRoleId());
        if (role == null || role.identifier().equals(ModRoles.MAGICIAN_ID)) {
            return null;
        }
        return role;
    }

    /** 伪装候选（职业 ID 形式），供开局随机抽取。 */
    public static List<ResourceLocation> collectCandidateIds(@Nullable Player player) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (SRERole role : collectCandidates(player)) {
            ids.add(role.identifier());
        }
        return ids;
    }

    /**
     * 伪装候选（职业对象形式）。
     *
     * <p>先按「不与场上已有杀手重复」严格筛选；若严格候选为空（例如场上的杀手已经把池子占满），
     * 退化成不排除重复的宽松候选，保证魔术师永远有身份可扮演。</p>
     */
    public static List<SRERole> collectCandidates(@Nullable Player player) {
        List<SRERole> strict = new ArrayList<>();
        List<SRERole> relaxed = new ArrayList<>();
        // IdentityHashMap 语义的去重：职业注册表本身不含重复实例，LinkedHashSet 只用于稳定顺序
        for (SRERole role : new LinkedHashSet<>(TMMRoles.ROLES.values())) {
            if (!isDisguisableBase(role)) {
                continue;
            }
            relaxed.add(role);
            if (!isAssignedOnField(role, player)) {
                strict.add(role);
            }
        }
        return strict.isEmpty() ? relaxed : strict;
    }

    /**
     * 基础可伪装性判定（不看场上已有职业）：必须是杀手职业，且
     * 非多狼位、非地图特色职业、无绑定生成关系、非迷失杀手。
     */
    public static boolean isDisguisableBase(@Nullable SRERole role) {
        if (role == null) {
            return false;
        }
        // 魔术师本体（以及共享同一份职业数据的辉夜）不参与
        if (role.identifier().equals(ModRoles.MAGICIAN_ID)) {
            return false;
        }
        // 迷失杀手：不占杀手位、没有杀手透视，属于特殊规则职业
        if (role.identifier().equals(ModRoles.LOST_KILLER_ID)) {
            return false;
        }
        // 其它模式的专属职业（游客 / 寻找者 / 超级亡命徒等）不属于本模式可扮演的杀手
        if (role.isOtherModeRole()) {
            return false;
        }
        // 必须是杀手职业：只认杀手阵营（{@link RoleTeam#KILLER}），
        // 杀手方中立（{@link RoleTeam#NEUTRAL_KILLER}）不算「杀手职业」
        if (!RoleTeam.isKiller(role)) {
            return false;
        }
        // 多狼位（迪奥 / 布袋鬼等占用 2 个狼位）与不占位（迷失杀手）一律排除
        if (role.getOccupiedRoleCount() != 1) {
            return false;
        }
        // 地图特色职业：只在特定地图 / 地图特性生成
        if (role.hasMapSpawnRestriction()) {
            return false;
        }
        // 绑定生成关系：如毒师 ↔ 医生、纵火犯 → 消防员
        if (role.hasOccupationRoleRelation()) {
            return false;
        }
        return true;
    }

    /** 该职业是否已经出现在场上（按职业 ID 比对，含魔术师自己）。 */
    public static boolean isAssignedOnField(@Nullable SRERole role, @Nullable Player player) {
        if (role == null || player == null || player.level() == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null) {
            return false;
        }
        for (SRERole assigned : game.getRoles().values()) {
            if (assigned != null && assigned.identifier().equals(role.identifier())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 被动——伪装大师：开局发放伪装职业的初始物品与初始药水效果。
     *
     * <p>不含对方职业的技能（魔术师本人职业不变），也不给无敌效果
     * （{@link ModEffects#INVINCIBLE}，例如怀旧者 / 冤魂的初始无敌）。</p>
     */
    public static void grantDisguiseKit(@Nullable ServerPlayer player, @Nullable SRERole disguise) {
        if (player == null || disguise == null) {
            return;
        }
        try {
            for (ItemStack stack : RoleInitialItems.getInitialItemsForRole(disguise)) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                RoleUtils.insertStackInFreeSlot(player, stack.copy());
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("Magician disguise items failed: {}", disguise.identifier(), e);
        }
        for (MobEffectInstance effect : getStartingEffects(disguise)) {
            if (effect == null || isInvincible(effect)) {
                continue;
            }
            player.addEffect(new MobEffectInstance(effect));
        }
    }

    /** 读取职业定义里的初始药水效果（两类自带效果的职业基类都要覆盖）。 */
    public static List<MobEffectInstance> getStartingEffects(@Nullable SRERole role) {
        if (role instanceof ExtraEffectRole extraEffectRole) {
            return new ArrayList<>(extraEffectRole.getEffects());
        }
        if (role instanceof LooseEndRole looseEndRole) {
            return new ArrayList<>(looseEndRole.getEffects());
        }
        return List.of();
    }

    /** 是否为无敌效果（魔术师伪装时不继承）。 */
    public static boolean isInvincible(MobEffectInstance effect) {
        return effect != null && Objects.equals(effect.getEffect(), ModEffects.INVINCIBLE);
    }
}
