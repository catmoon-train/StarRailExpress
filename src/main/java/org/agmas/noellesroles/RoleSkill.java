package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.events.OnRoleSkillUse;
import org.agmas.noellesroles.role.ModRoles;

import java.util.HashSet;
import java.util.Set;

public final class RoleSkill {
    private static final Set<ResourceLocation> REGISTERED_ROLE_SKILLS = new HashSet<>();

    static {
        registerDefaults();
    }

    private RoleSkill() {
    }

    public static void register(SRERole role) {
        if (role == null) {
            return;
        }
        REGISTERED_ROLE_SKILLS.add(role.identifier());
    }

    public static boolean isRegistered(SRERole role) {
        return role != null && REGISTERED_ROLE_SKILLS.contains(role.identifier());
    }

    public static boolean beforeUse(ServerPlayer player, SRERole role) {
        if (!isRegistered(role)) {
            return true;
        }
        return OnRoleSkillUse.BEFORE.invoker().onUse(player, role);
    }

    public static void afterUse(ServerPlayer player, SRERole role) {
        if (!isRegistered(role)) {
            return;
        }
        OnRoleSkillUse.AFTER.invoker().onUse(player, role);
    }

    public static SRERole beginUse(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        if (!beforeUse(player, role)) {
            return null;
        }
        return role;
    }

    public static void endUse(ServerPlayer player, SRERole role) {
        if (player == null) {
            return;
        }
        if (role == null) {
            ConfigWorldComponent.onPlayerUsedSkill(player);
            return;
        }
        afterUse(player, role);
        ConfigWorldComponent.onPlayerUsedSkill(player);
    }

    private static void registerDefaults() {
        // AbilityC2SPacket
        register(ModRoles.HOAN_MEIRIN);
        register(ModRoles.MAID_SAKUYA);
        register(ModRoles.JOJO);
        register(ModRoles.DIO);
        register(ModRoles.WIND_YAOSE);
        register(ModRoles.CLEANER);
        register(ModRoles.GLITCH_ROBOT);
        register(ModRoles.DIVER);
        register(ModRoles.MA_CHEN_XU);
        register(ModRoles.WATCHER);
        register(ModRoles.COMMANDER);
        register(ModRoles.ATTENDANT);
        register(ModRoles.EXAMPLER);
        register(ModRoles.BOMBER);
        register(ModRoles.NOISEMAKER);
        register(ModRoles.GHOST);
        register(ModRoles.CANDLE_BEARER);
        register(ModRoles.BLOOD_FEUDIST);
        register(ModRoles.RECALLER);
        register(ModRoles.OLDMAN);
        register(ModRoles.PHANTOM);
        register(ModRoles.NIAN_SHOU);
        register(ModRoles.THIEF);
        register(ModRoles.CLOCKMAKER);
        register(ModRoles.ACCOUNTANT);
        register(ModRoles.ALCHEMIST);
        register(ModRoles.SEA_KING);
        // AbilityWithTargetC2SPacket / other G-key skill packets
        register(ModRoles.FORTUNETELLER);
        register(ModRoles.PUPPETEER);
        register(ModRoles.BOXER);
        register(ModRoles.ATHLETE);
        register(ModRoles.WATER_GHOST);
        register(ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES);
        register(ModRoles.ADMIRER);
        register(ModRoles.STALKER);
        register(ModRoles.DETECTIVE);
        register(ModRoles.TRAPPER);
        register(ModRoles.SUPERSTAR);
        register(ModRoles.PSYCHOLOGIST);
        register(ModRoles.VULTURE);
        register(ModRoles.BROADCASTER);
        register(ModRoles.TELEGRAPHER);
    }
}
