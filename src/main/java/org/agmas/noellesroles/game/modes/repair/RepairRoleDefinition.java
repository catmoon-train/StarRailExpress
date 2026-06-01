package org.agmas.noellesroles.game.modes.repair;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.api.SRERole;

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
            case MECHANIC -> ModRoles.REPAIR_MECHANIC;
            case MEDIC -> ModRoles.REPAIR_MEDIC;
            case RUNNER -> ModRoles.REPAIR_RUNNER;
            case WARDEN -> ModRoles.REPAIR_WARDEN;
            case BRUTE -> ModRoles.REPAIR_BRUTE;
            case TRACKER -> ModRoles.REPAIR_TRACKER;
            case ARCHIVIST -> ModRoles.REPAIR_ARCHIVIST;
            case SABOTEUR -> ModRoles.REPAIR_SABOTEUR;
            case COLLECTOR -> ModRoles.REPAIR_COLLECTOR;
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
