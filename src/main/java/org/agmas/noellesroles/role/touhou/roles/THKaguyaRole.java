package org.agmas.noellesroles.role.touhou.roles;

import java.util.List;

import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

public class THKaguyaRole extends TouhouRole {

    public static final int NORMAL_DEATH_THRESHOLD = 8;

    public THKaguyaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean canIncreaseSurvivingKillers() {
        return true;
    }

    @Override
    public boolean winWithKiller() {
        return false;
    }

    @Override
    public boolean winWithInnocent() {
        return false;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return ShopContent.getDefaultKnifeEntries();
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (!(victim instanceof ServerPlayer serverVictim))
            return;
        int remaningPlayerCount = RoleUtils.getAlivePlayers(serverVictim.serverLevel()).size();
        if (remaningPlayerCount <= NORMAL_DEATH_THRESHOLD)
            return;
        if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN))
            return;
        if (deathReason.equals(GameConstants.DeathReasons.BROKEN_HEART)
                || (killer != null && !SREGameWorldComponent.isKillerTeamStatic(killer) && !forceDeath)) {

            var lover = LoversComponent.KEY.get(victim).getLoverAsPlayer();
            if (lover != null) {
                if (!GameUtils.isPlayerAliveAndSurvival(lover)
                        && DefibrillatorComponent.KEY.get(lover).resurrectionTime <= 0) {
                    return;
                }
            }
            DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(victim);
            component.triggerDeath(30 * 20, null, victim.position());
            SREPlayerShopComponent.KEY.get(victim).addToBalance(50);
        }
    }
}
