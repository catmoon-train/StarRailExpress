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

public class TMMRoles {
    public static final Map<ResourceLocation, SRERole> ROLES = new HashMap<>();
    private static final HashSet<String> CACHED_VERSIONS_LIST = new HashSet<>();
    public static final int CIVILIAN_MAX_SPRINT_TICKS = GameConstants.getInTicks(0, 10);
    public static final List<ComponentKey<? extends RoleComponent>> COMPONENT_KEYS = new ArrayList<>();
    public static final SRERole DISCOVERY_CIVILIAN = registerRole(
            new OriginalRole(SRE.id("discovery_civilian"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true))
            .setCanPickUpRevolver(false).setNeutrals(true).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true).setAddedVersion("original");
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
            new OriginalRole(SRE.id("killer"), 0xC13838, false, true, SRERole.MoodType.FAKE, -1, true)).setAddedVersion("original");
    public static final SRERole LOOSE_END = registerRole(
            new LooseEndRole(SRE.id("loose_end"), 0x9F0000, false, false, SRERole.MoodType.NONE, -1, false,
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            30 * 20, // 持续时间 60s（tick）
                            2, // 等级（0 = 速度 I）
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    )))
            .setCanSeeTime(true).setCanUseInstinctAndNightVision(true).setCanBeRandomedByOtherRoles(false)
            .setToggledOnInstinctType(InstinctType.OBSERVER_ROLE_COLOR).setAddedVersion("original");

    public static class CACHE {
        public static final ArrayList<SRERole> MAFIA_ROLES = new ArrayList<>();
    };

    public static SRERole registerRole(SRERole role, String... flags) {
        return registerRole(role.addFlag(flags));
    }

    public static SRERole registerRole(SRERole role) {
        ROLES.put(role.identifier(), role);
        if (role.isMafiaTeam()) {
            CACHE.MAFIA_ROLES.add(role);
        }
        if (role.getComponentKey() != null) {
            COMPONENT_KEYS.add(role.getComponentKey());
        }
        return role;
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
}
