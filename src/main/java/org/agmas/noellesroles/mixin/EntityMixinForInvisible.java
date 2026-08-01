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

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixinForInvisible {
    /**
     * 原版逻辑：isInvisibleTo() 开头会检测 player.isSpectator()，
     * 若为旁观者则直接 return false（即"对旁观者不隐身"）。
     * 本 Mixin 在 HEAD 处抢先介入，当满足特定条件时强制返回 true（隐身）。
     */
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void preventSpectatorSeeInvisible(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (SRE.isLobby)
            return;
        // 只处理：自身是玩家 && 自身有隐身效果 && 观察者是旁观模式
        if (!(self instanceof Player viewee))
            return;
        if (!viewee.isInvisible())
            return;
        if (viewer.isCreative() && viewer.hasPermissions(2)) {
            // 创造的 op 可以看见
            cir.setReturnValue(false);
            return;
        }

        var gamecca = SREGameWorldComponent.getInstance(viewer.level());
        if (!gamecca.isRunning())
            return;
        if (!viewer.isSpectator()) {
            if (gamecca.isKillerTeam(viewee) && gamecca.isKillerTeam(viewer) && gamecca.canSeeKillerTeammate(viewer)) {
                cir.setReturnValue(false);
            } else {
                cir.setReturnValue(true);
            }
            return;
        }

        var deathPenaltyComponent = ModComponents.DEATH_PENALTY.get(viewer);
        if (deathPenaltyComponent.hasPenalty()) {
            cir.setReturnValue(true);
            return;
        }

        // 示例 C：旁观者与隐身玩家不在同一队伍时才隐藏
        // if (viewer.getTeam() == null || viewer.getTeam() !=
        // invisiblePlayer.getTeam()) {
        // cir.setReturnValue(true);
        // }
        // ──────────────────────────────────────────────
    }
}
