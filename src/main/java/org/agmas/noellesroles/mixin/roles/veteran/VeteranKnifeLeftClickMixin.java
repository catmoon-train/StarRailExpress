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

package org.agmas.noellesroles.mixin.roles.veteran;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 对所有 {@code cannotKnifeLeftClick=true} 的职业阻止刀左键攻击。
 * 原仅作用于退伍军人和初学者，现已泛化。
 */
@Mixin(ServerPlayer.class)
public abstract class VeteranKnifeLeftClickMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onCannotKnifeAttack(Entity target, CallbackInfo ci) {
        ServerPlayer attacker = (ServerPlayer) (Object) this;
        if (!(target instanceof Player targetPlayer)) return;
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(targetPlayer)) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        SRERole role = gameWorld.getRole(attacker);
        if (role == null || !role.cannotKnifeLeftClick()) return;

        ItemStack mainHand = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainHand.is(TMMItems.KNIFE)) {
            ci.cancel();
        }
    }
}