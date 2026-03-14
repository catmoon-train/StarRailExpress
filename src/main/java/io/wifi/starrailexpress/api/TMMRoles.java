package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.SRE;
import net.minecraft.resources.ResourceLocation;

import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TMMRoles {
    public static final Map<ResourceLocation, SRERole> ROLES = new HashMap<>();
    public static final List<ComponentKey<? extends RoleComponent>> COMPONENT_KEYS = new ArrayList<>();
    public static final SRERole DISCOVERY_CIVILIAN = registerRole(
            new NormalRole(SRE.id("discovery_civilian"), 0x36E51B, true, false, SRERole.MoodType.NONE, -1, true));
    public static final SRERole CIVILIAN = registerRole(new NormalRole(SRE.id("civilian"), 0x36E51B, true, false,
            SRERole.MoodType.REAL, GameConstants.getInTicks(0, 10), false));
    public static final SRERole VIGILANTE = registerRole(new NormalRole(SRE.id("vigilante"), 0x1B8AE5, true, false,
            SRERole.MoodType.REAL, GameConstants.getInTicks(0, 10), false).setVigilanteTeam(true));
    public static final SRERole KILLER = registerRole(
            new NormalRole(SRE.id("killer"), 0xC13838, false, true, SRERole.MoodType.FAKE, -1, true));
    public static final SRERole LOOSE_END = registerRole(
            new NormalRole(SRE.id("loose_end"), 0x9F0000, false, false, SRERole.MoodType.NONE, -1, false)).setCanSeeTime(true).setCanUseInstinct(true);

    public static SRERole registerRole(SRERole role) {
        ROLES.put(role.identifier(), role);
        if (role.getComponentKey() != null) {
            COMPONENT_KEYS.add(role.getComponentKey());
        }
        return role;
    }

    public static void addRoleComponents(ComponentKey<? extends RoleComponent> componentKeyToAdd) {
        COMPONENT_KEYS.add(componentKeyToAdd);
    }
}
