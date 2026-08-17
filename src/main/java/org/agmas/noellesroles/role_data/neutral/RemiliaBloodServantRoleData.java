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

import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;

public class RemiliaBloodServantRoleData extends SimpleRoleData {
    public final static int MAX_ALIVE = 20 * 180;
    public int ticks = MAX_ALIVE;

    public RemiliaBloodServantRoleData(RoleDataContext context) {
        super(context);
    }

    public void clientTick() {
        if (GameUtils.isPlayerAliveAndSurvival(player)) {

            if (ticks > 0) {
                ticks--;
            }
        }
    }

    public void serverTick() {
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            if (player.level().getGameTime() % 40 == 0) {
                envCheck();
            }
            if (ticks > 0) {
                ticks--;
                if (ticks % 600 == 0) {
                    sync();
                }
                if (ticks == 0) {
                    death();
                }
            }
        }
    }

    private void envCheck() {
        if (player.isInWaterOrRain()) {
            // 惧怕水，处于水或雨中时行动缓慢且无法攻击、使用道具。
            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
            player.addEffect(ModEffects.of(ModEffects.USED_BANED, 60, 1, false, false, true));
        } else {
            final var level = player.level();
            if (SREWorldBlackoutComponent.KEY.get(level).isBlackoutActive()) {
                return;
            }
            if (level.getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())) > 6
                    || (level.getBrightness(LightLayer.SKY,
                            BlockPos.containing(player.getEyePosition())) > 13
                            && level.getDayTime() < 13000)) {

                // 惧怕阳光和灯光，在光比较亮时行动异常缓慢。
                player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
            }
        }
    }

    private void death() {
        GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.TIMEOUT);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registryLookup) {
        tag.putInt("ticks", ticks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registryLookup) {
        ticks = getIntTag(tag, "ticks", 0);
    }
}
