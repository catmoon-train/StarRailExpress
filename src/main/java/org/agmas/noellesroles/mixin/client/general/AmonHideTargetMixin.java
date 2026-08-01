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

package org.agmas.noellesroles.mixin.client.general;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让阿蒙在附身（夺舍准备）期间看不见被附身目标的渲染：
 * 当本地玩家是正在附身某目标的阿蒙，且被观察实体正是该目标时，使该目标对阿蒙隐形。
 *
 * <p>仅作用于本地观察者（{@code viewer == 本地玩家}），不影响其他人看到该目标。</p>
 */
@Mixin(Entity.class)
public class AmonHideTargetMixin {

    @Inject(method = "isInvisibleTo", at = @At("RETURN"), cancellable = true)
    private void amon$hideFromPossessor(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return; // 已经隐形
        }
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player target)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || viewer != mc.player) {
            return;
        }
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRole(mc.player, ModRoles.AMON)) {
            return;
        }
        AmonPlayerComponent amon = AmonPlayerComponent.KEY.get(mc.player);
        if (amon.clientPossessTarget != null && amon.clientPossessTarget.equals(target.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
