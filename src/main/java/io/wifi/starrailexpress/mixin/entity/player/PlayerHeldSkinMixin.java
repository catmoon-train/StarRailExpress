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

import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 副手物品不参与 {@code PlayerInventory#tick}，因此不会被
 * {@code Item#inventoryTick} 盖上皮肤组件（原版弓/弩常见于副手）。
 * 这里在玩家每 tick 时补盖一次，保证副手物品也能同步正确的皮肤。
 */
@Mixin(Player.class)
public abstract class PlayerHeldSkinMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void sre$stampOffhandSkin(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            ItemSkinManager.applyEquippedSkin(player, player.getOffhandItem());
        }
    }
}
