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

package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.THLostForestRoles;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

public class THMokouRole extends TouhouRole {
    public static final int XIAONAO_THRESHOLD = 8;
    public static final int NORMAL_DEATH_THRESHOLD = 5;

    public THMokouRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean winWithKiller() {
        return false;
    }

    @Override
    public boolean canIncreaseSurvivingInnocents() {
        return true;
    }

    @Override
    public boolean winWithInnocent() {
        return false;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new ShopEntry(ModItems.FAKE_REVOLVER.getDefaultInstance(),
                50, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(ModItems.ONCE_REVOLVER.getDefaultInstance(),
                150, ShopEntry.Type.WEAPON));
        return SHOP;
    }

    /**
     * 统计"场上存活人数"。
     * <p>
     * 不直接用 {@code RoleUtils.getAlivePlayers(level)}：它只过滤"非旁观且非创造"，
     * 会把观战者、退出本局（opt-out）、中途加入以及尚未分配职业的玩家一并算成存活，
     * 导致实际场上不足 8 人时统计值仍然大于 8，误触发小脑惩罚。
     * <p>
     * 这里额外要求玩家已在本局拿到职业，且保持"含自己"的计数口径
     * （判定发生在被切旁观之前，见 {@code GameMode#killPlayer}），
     * 与角色文案中"在小于等于 8 人时"的含义一致。
     */
    private static int countAliveSurvivors(ServerPlayer victim) {
        if (!(victim.level() instanceof ServerLevel level))
            return 0;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        int count = 0;
        for (var player : level.players()) {
            if (!(player instanceof ServerPlayer serverPlayer))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer))
                continue;
            if (gameWorld.getRole(serverPlayer) == null)
                continue;
            count++;
        }
        return count;
    }

    @Override
    public boolean canBeXiaonao(Player victim, Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer serverVictim))
            return false;
        return countAliveSurvivors(serverVictim) <= XIAONAO_THRESHOLD;
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (!(victim instanceof ServerPlayer serverVictim))
            return;
        // 殉情（链子）死亡时场上人数已因伴侣死亡而减少，若仍按人数阈值判定，
        // 会出现伴侣复活而自己彻底死亡的分裂结果；此时跳过阈值，统一由下方伴侣状态决定去留。
        if (!deathReason.equals(GameConstants.DeathReasons.BROKEN_HEART)) {
            if (countAliveSurvivors(serverVictim) <= NORMAL_DEATH_THRESHOLD) {
                THLostForestRoles.recordImmortalPairRealDeath(serverVictim, THLostForestRoles.KAGUYA_ID);
                return;
            }
        }
        if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
            THLostForestRoles.recordImmortalPairRealDeath(serverVictim, THLostForestRoles.KAGUYA_ID);
            return;
        }
        boolean revived = false;
        if (deathReason.equals(GameConstants.DeathReasons.BROKEN_HEART)
                || (killer != null && !SREGameWorldComponent.isInnocentStatic(killer) && !forceDeath)) {

            var lover = LoversComponent.KEY.get(victim).getLoverAsPlayer();
            if (lover != null) {
                if (!GameUtils.isPlayerAliveAndSurvival(lover)
                        && DefibrillatorComponent.KEY.get(lover).resurrectionTime <= 0) {
                    THLostForestRoles.recordImmortalPairRealDeath(serverVictim, THLostForestRoles.KAGUYA_ID);
                    return;
                }
            }
            DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(victim);
            component.triggerDeath(30 * 20, null, victim.position());
            SREPlayerShopComponent.KEY.get(victim).addToBalance(50);
            revived = true;
        }
        if (!revived) {
            THLostForestRoles.recordImmortalPairRealDeath(serverVictim, THLostForestRoles.KAGUYA_ID);
        }
    }
}
