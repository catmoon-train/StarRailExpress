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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 投掷三叉戟击杀逻辑：仅对 {@code canKillWithTrident=true} 的职业生效。
 * 原为全局行为，现已泛化。
 */
@Mixin(ThrownTrident.class)
public class TridentMixin {
    private ServerPlayer lastHitPlayer = null;

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void noellesroles$onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby) return;
        if (!(entityHitResult.getEntity() instanceof ServerPlayer player)) return;

        ThrownTrident trident = (ThrownTrident) (Object) this;
        if (!(trident.getOwner() instanceof ServerPlayer thrower)) return;
        if (lastHitPlayer == player) return;

        // 仅 canKillWithTrident 的角色才能用投掷三叉戟杀人
        SRERole role = SREGameWorldComponent.KEY.get(thrower.level()).getRole(thrower);
        if (role == null || !role.canKillWithTrident()) return;

        lastHitPlayer = player;
        if (!thrower.isSpectator()) {
            GameUtils.killPlayer(player, true, thrower, SRE.id("trident"));
        }
        thrower.getCooldowns().addCooldown(Items.TRIDENT,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(Items.TRIDENT,
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0)));
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$onHitPlayerBody(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby) return;
        if (entityHitResult.getEntity() instanceof PlayerBodyEntity) {
            ThrownTrident trident = (ThrownTrident) (Object) this;
            if (trident.getOwner() instanceof ServerPlayer killer) {
                killer.getCooldowns().addCooldown(Items.TRIDENT,
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(Items.TRIDENT,
                                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER, 0)));
            }
            trident.discard();
        }
    }
}
