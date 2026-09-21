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

package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.*;
import java.util.function.Consumer;

public class TMMRoles {
    public static final Map<String, SRERole> ROLES_BY_PATH = new HashMap<>();
    public static final Map<ResourceLocation, SRERole> ROLES = new HashMap<>();
    private static final HashSet<String> CACHED_VERSIONS_LIST = new HashSet<>();
    /**
     * 声明过专属随机事件（{@link SRERole#setEventEnableChance(java.util.function.BiConsumer, int)}）的职业。
     * <p>
     * 每局开局掷骰（{@link SRERole#rollAllEventEnableChances}）与局末清理
     * （{@link SRERole#resetAllEventEnableStates}）只遍历这一份列表，未声明事件的职业完全不参与，
     * 派发开销只与事件数量有关而不是职业总数。
     * <p>
     * 生命周期由注册流程维护：{@link #registerRole} 收录注册时已声明事件的职业，
     * {@link SRERole#setEventEnableChance(java.util.function.BiConsumer, int)} 收录注册之后再声明的职业，
     * {@link #unregisterCustomRole} 注销时把该实例移出列表。
     * 用 {@link IdentityHashMap} 支持的身份集合，避免职业实现自定义 {@code equals} 时互相顶替。
     */
    private static final Set<SRERole> EVENT_ROLES = Collections.newSetFromMap(new IdentityHashMap<>());
    public static final int CIVILIAN_MAX_SPRINT_TICKS = GameConstants.getInTicks(0, 10);
    public static final List<ComponentKey<? extends RoleComponent>> COMPONENT_KEYS = new ArrayList<>();
    public static final SRERole DISCOVERY_CIVILIAN = registerRole(
            new OriginalRole(SRE.id("discovery_civilian"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true))
            .setCanPickUpRevolver(false).setNeutrals(true).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true)
            .setAddedVersion("original");
    public static final SRERole CIVILIAN = registerRole(new OriginalRole(SRE.id("civilian"), 0x36E51B, true, false,
            SRERole.MoodType.REAL, CIVILIAN_MAX_SPRINT_TICKS, false)).setAddedVersion("original");
    public static final SRERole VIGILANTE = registerRole(new OriginalRole(SRE.id("vigilante"), 0x1B8AE5, true, false,
            SRERole.MoodType.REAL, CIVILIAN_MAX_SPRINT_TICKS, false) {
        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of(new ItemStack(TMMItems.REVOLVER).copy());
        }
    }.setVigilanteTeam(true).setDefaultMax(0).setCanSetSpawnInfoInConfig(false)).setAddedVersion("original");
    public static final SRERole KILLER = registerRole(
            new OriginalRole(SRE.id("killer"), 0xC13838, false, true, SRERole.MoodType.FAKE, -1, true))
            .setAddedVersion("original");
    public static final SRERole LOOSE_END = registerRole(
            new LooseEndRole(SRE.id("loose_end"), 0x9F0000, false, false, SRERole.MoodType.NONE, -1, false,
                    List.of(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            30 * 20, // 持续时间
                            1, // 等级（0 = 速度 I）速度 II
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ), new MobEffectInstance(
                            MobEffects.WATER_BREATHING,
                            30 * 20, // 持续时间 60s（tick）
                            2, // 等级（0 = 速度 I）
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ), new MobEffectInstance(
                            MobEffects.DOLPHINS_GRACE,
                            30 * 20, // 持续时间 60s（tick）
                            1, // 等级（0 = 速度 I）
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ))))
            .setCanSeeTime(true).setCanUseInstinctAndNightVision(true).setCanBeRandomedByOtherRoles(false)
            .setToggledOnInstinctType(InstinctType.OBSERVER_ROLE_COLOR).setAddedVersion("original");

    public static class CACHE {
        public static final ArrayList<SRERole> MAFIA_ROLES = new ArrayList<>();
    };

    public static SRERole registerRole(SRERole role, String... flags) {
        return registerRole(role.addFlag(flags));
    }

    public static SRERole registerCustomRole(SRERole role) {
        if (ROLES_BY_PATH.containsKey(role.identifier.getPath())) {
            SRE.LOGGER.error("[ROLE REGISTERER] Duplicated role identifier path found: {} and {}. Ignore the new one.",
                    ROLES_BY_PATH.get(role.identifier.getPath()).identifier().toString(), role.identifier().toString());
            return null;
        }
        return registerRole(role);
    }

    public static boolean unregisterCustomRole(SRERole role) {
        if (role == null)
            return false;
        ROLES_BY_PATH.remove(role.identifier.getPath());
        ROLES.remove(role.identifier());
        EVENT_ROLES.remove(role);
        return true;
    }

    public static SRERole registerRole(SRERole role) {
        if (ROLES_BY_PATH.containsKey(role.identifier.getPath())) {
            // 拒绝注册
            throw new IllegalArgumentException(String.format(
                    "[ROLE REGISTERER] Duplicated role identifier path found: %s and %s. Ignore the new one.",
                    ROLES_BY_PATH.get(role.identifier.getPath()).identifier().toString(),
                    role.identifier().toString()));
        }
        ROLES.put(role.identifier(), role);

        ROLES_BY_PATH.put(role.identifier().getPath(), role);
        if (role.isMafiaTeam()) {
            CACHE.MAFIA_ROLES.add(role);
        }
        if (role.getComponentKey() != null) {
            COMPONENT_KEYS.add(role.getComponentKey());
        }
        // 先声明事件再注册的写法（setEventEnableChance 在 registerRole 之前调用）在这里补收录
        if (role.hasRoundEvent()) {
            EVENT_ROLES.add(role);
        }
        return role;
    }

    /**
     * 把一个刚声明了专属随机事件的职业收录进 {@link #EVENT_ROLES}。
     * <p>
     * 由 {@link SRERole#setEventEnableChance(java.util.function.BiConsumer, int)} 回调，只收录当前注册表中的那个实例；
     * 尚未注册（或已被注销）的职业对象不会入列，避免只存在于内存中的实例参与每局掷骰。
     *
     * @param role 声明了专属随机事件的职业实例
     */
    static void markEventRole(SRERole role) {
        if (role != null && ROLES.get(role.identifier()) == role) {
            EVENT_ROLES.add(role);
        }
    }

    /**
     * 遍历所有声明过专属随机事件的职业（顺序不保证）。
     * <p>
     * 内部先做快照，遍历过程中注销职业不会抛 {@link java.util.ConcurrentModificationException}，
     * 已注销的实例也不会再被访问。
     *
     * @param action 对每个事件职业执行的操作，例如开局掷骰 / 局末清理
     */
    static void forEachEventRole(Consumer<SRERole> action) {
        for (SRERole role : List.copyOf(EVENT_ROLES)) {
            action.accept(role);
        }
    }

    public static void addRoleComponents(ComponentKey<? extends RoleComponent> componentKeyToAdd) {
        COMPONENT_KEYS.add(componentKeyToAdd);
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : ROLES.values()) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }

    public static SRERole getRole(ResourceLocation id) {
        return ROLES.getOrDefault(id, null);
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
        for (var t : ROLES.values()) {
            CACHED_VERSIONS_LIST.add(t.getAddedVersion());
        }
        return new HashSet<>(CACHED_VERSIONS_LIST);
    }

    public static SRERole getRoleByPath(String rolePath) {
        return ROLES_BY_PATH.getOrDefault(rolePath, null);
    }
}
