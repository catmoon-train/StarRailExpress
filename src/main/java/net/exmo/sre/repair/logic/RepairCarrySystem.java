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

package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;

/** 修机模式倒地与背人状态的每 tick 维护（位置同步、减益、状态失效清理）。 */
public final class RepairCarrySystem {
    private RepairCarrySystem() {
    }

    public static void tick(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (component.carryBlockedTicks > 0) {
                component.carryBlockedTicks--;
                if (component.carryBlockedTicks == 0) {
                    component.sync();
                }
            }
            if (component.carrying != null) {
                if (serverWorld.getPlayerByUUID(component.carrying) instanceof ServerPlayer carried
                        && ModComponents.REPAIR_ROLES.get(carried).downed
                        && !GameUtils.isPlayerEliminated(carried)
                        && !carried.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                    carried.teleportTo(player.getX(), player.getY() + 2.15D, player.getZ());
                    carried.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    carried.resetFallDistance();
                    carried.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 20, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 4, false, false, true));
                } else {
                    component.carrying = null;
                    component.sync();
                }
            }
            if (component.carriedBy != null) {
                player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 10, 0, false, false, true));
                if (!(serverWorld.getPlayerByUUID(component.carriedBy) instanceof ServerPlayer carrier)
                        || !player.getUUID().equals(ModComponents.REPAIR_ROLES.get(carrier).carrying)) {
                    component.carriedBy = null;
                    component.sync();
                }
            }
            if (component.downed) {
                player.setHealth(Math.max(1.0F, player.getHealth()));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 8, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 3, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));
            }
        }
    }
}
