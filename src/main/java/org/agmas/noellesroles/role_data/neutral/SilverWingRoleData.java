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

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;
import org.agmas.noellesroles.game.roles.neutral.silver_wing.SilverWingRules;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.UUID;

public class SilverWingRoleData extends SimpleRoleData {
    private UUID activeBirdId;
    /** 下次发放被动金币的游戏内 tick（会议/时停会暂停）。 */
    private long nextPassiveGoldTick;

    public SilverWingRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean hasActiveBird() {
        if (activeBirdId == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity entity = level.getEntity(activeBirdId);
        return entity instanceof MechanicalBirdEntity bird && !bird.isRemoved();
    }

    public void setActiveBird(MechanicalBirdEntity bird) {
        this.activeBirdId = bird.getUUID();
    }

    public void clearActiveBird() {
        this.activeBirdId = null;
    }

    @Override
    public void clear() {
        if (activeBirdId != null && player.level() instanceof ServerLevel level) {
            Entity entity = level.getEntity(activeBirdId);
            if (entity instanceof MechanicalBirdEntity bird) {
                bird.despawnQuietly();
            }
        }
        activeBirdId = null;
    }

    @Override
    public void serverTick() {
        tickPassiveGold();
        if (activeBirdId == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(activeBirdId);
        if (!(entity instanceof MechanicalBirdEntity bird) || bird.isRemoved()) {
            activeBirdId = null;
        }
    }

    private void tickPassiveGold() {
        if (!(player instanceof ServerPlayer) || !GameUtils.isGameRunning(player)
                || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        if (nextPassiveGoldTick <= 0L) {
            nextPassiveGoldTick = SilverWingRules.nextPassiveGoldTick(now);
            return;
        }
        if (!SilverWingRules.isPassiveGoldDue(now, nextPassiveGoldTick)) {
            return;
        }
        MoneyUtils.addToBalance(player, SilverWingRules.PASSIVE_GOLD_AMOUNT);
        nextPassiveGoldTick = SilverWingRules.nextPassiveGoldTick(now);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
