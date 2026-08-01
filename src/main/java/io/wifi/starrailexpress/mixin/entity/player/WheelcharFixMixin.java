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

package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class WheelcharFixMixin {
@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void onWantsToStopRiding(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        // 仅在服务端且玩家正在骑乘时阻止主动下座位
        if (!self.level().isClientSide && self.isPassenger() && self.getCooldowns().isOnCooldown(TMMBlocks.ACACIA_BRANCH.asItem())) {
            cir.setReturnValue(false);
        }
    }
}
