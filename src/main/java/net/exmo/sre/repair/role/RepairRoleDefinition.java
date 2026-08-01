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

package net.exmo.sre.repair.role;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.state.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import net.exmo.sre.repair.role.RepairRoles;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum RepairRoleDefinition {
    MECHANIC("mechanic", Faction.SURVIVOR, true),
    MEDIC("medic", Faction.SURVIVOR, false),
    RUNNER("runner", Faction.SURVIVOR, false),
    WARDEN("warden", Faction.HUNTER, true),
    BRUTE("brute", Faction.HUNTER, false),
    TRACKER("tracker", Faction.HUNTER, false),
    ARCHIVIST("archivist", Faction.NEUTRAL, true),
    SABOTEUR("saboteur", Faction.NEUTRAL, false),
    COLLECTOR("collector", Faction.NEUTRAL, false);

    public static final int UNLOCK_PRICE = 5000;
    public final String id;
    public final Faction faction;
    public final boolean starter;

    RepairRoleDefinition(String id, Faction faction, boolean starter) {
        this.id = id;
        this.faction = faction;
        this.starter = starter;
    }

    public ResourceLocation identifier() {
        return Noellesroles.id("repair_" + id);
    }

    public Component displayName() {
        return Component.translatable("role.noellesroles.repair." + id);
    }

    public Component description() {
        return Component.translatable("role.noellesroles.repair." + id + ".desc");
    }

    public SRERole sreRole() {
        return switch (this) {
            case MECHANIC -> RepairRoles.REPAIR_MECHANIC;
            case MEDIC -> RepairRoles.REPAIR_MEDIC;
            case RUNNER -> RepairRoles.REPAIR_RUNNER;
            case WARDEN -> RepairRoles.REPAIR_WARDEN;
            case BRUTE -> RepairRoles.REPAIR_BRUTE;
            case TRACKER -> RepairRoles.REPAIR_TRACKER;
            case ARCHIVIST -> RepairRoles.REPAIR_ARCHIVIST;
            case SABOTEUR -> RepairRoles.REPAIR_SABOTEUR;
            case COLLECTOR -> RepairRoles.REPAIR_COLLECTOR;
        };
    }

    public static List<RepairRoleDefinition> byFaction(Faction faction) {
        return Arrays.stream(values()).filter(role -> role.faction == faction).toList();
    }

    public static Optional<RepairRoleDefinition> byId(String id) {
        return Arrays.stream(values()).filter(role -> role.id.equals(id)).findFirst();
    }

    public enum Faction {
        SURVIVOR, HUNTER, NEUTRAL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("role.noellesroles.repair.faction." + id());
        }
    }
}
