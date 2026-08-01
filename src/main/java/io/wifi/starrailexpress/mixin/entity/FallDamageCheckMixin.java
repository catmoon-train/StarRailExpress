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

package io.wifi.starrailexpress.mixin.entity;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FallDamageCheckMixin {
    @Inject(method = "checkFallDamage", at = @At("HEAD"), cancellable = true)
    public void checkFallDamage(double y, boolean onGround, BlockState blockState, BlockPos blockPos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (onGround) {
            // 落地了
            if (self instanceof ServerPlayer player) {
                // 是玩家（服务端检测）
                if (player.isSpectator() || player.isCreative())
                    return;
                var cca = AreasWorldComponent.KEY.get(player.level());
                if (cca.areasSettings.fallToDeathHeight > 0) {
                    if (self.fallDistance >= cca.areasSettings.fallToDeathHeight) {
                        var role = RoleUtils.getPlayerRole(player);
                        // 免疫摔落致死的职业不会因高度限制摔死
                        if (role == null)
                            return;
                        if (!role.isFallDamageImmune()) {
                            GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FALL_DAMAGE);
                        }
                    }
                    self.resetFallDistance();
                    ci.cancel();
                }
            }
        }
    }
}
