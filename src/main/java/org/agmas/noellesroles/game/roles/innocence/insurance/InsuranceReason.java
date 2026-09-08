package org.agmas.noellesroles.game.roles.innocence.insurance;

import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Death-cause categories that can be selected on an insurance contract. */
public enum InsuranceReason {
    KNIFE("knife", GameConstants.DeathReasons.KNIFE),
    REVOLVER("revolver", GameConstants.DeathReasons.REVOLVER),
    BAT("bat", GameConstants.DeathReasons.BAT),
    GRENADE("grenade", GameConstants.DeathReasons.GRENADE),
    SHORT_SHOTGUN("short_shotgun", GameConstants.DeathReasons.SHORT_SHOTGUN),
    HEART_ATTACK("heart_attack", GameConstants.DeathReasons.HEART_ATTACK),
    THROWING_KNIFE("throwing_knife", GameConstants.DeathReasons.THROWING_KNIFE_HIT),
    OTHER("other", null);

    private static final Set<ResourceLocation> NAMED_REASONS = Set.of(
            GameConstants.DeathReasons.KNIFE,
            GameConstants.DeathReasons.REVOLVER,
            GameConstants.DeathReasons.BAT,
            GameConstants.DeathReasons.GRENADE,
            GameConstants.DeathReasons.SHORT_SHOTGUN,
            GameConstants.DeathReasons.HEART_ATTACK,
            GameConstants.DeathReasons.THROWING_KNIFE_HIT);

    private final String id;
    private final ResourceLocation deathReason;

    InsuranceReason(String id, ResourceLocation deathReason) {
        this.id = id;
        this.deathReason = deathReason;
    }

    public String id() {
        return id;
    }

    public static InsuranceReason fromId(String id) {
        if (id == null) return null;
        for (InsuranceReason reason : values()) {
            if (reason.id.equals(id)) return reason;
        }
        return null;
    }

    public boolean matches(ResourceLocation reason) {
        if (reason == null || reason.equals(GameConstants.DeathReasons.SHOT_INNOCENT)) return false;
        if (this == OTHER) return !NAMED_REASONS.contains(reason);
        return deathReason.equals(reason);
    }
}
