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

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Inventory.class)
public class PlayerInventoryMixin {
    @Shadow
    @Final
    public Player player;

    @WrapMethod(method = "swapPaint")
    private void tmm$invalid(double scrollAmount, @NotNull Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(scrollAmount);
            return;
        }
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());

        int oldSlot = this.player.getInventory().selected;
        original.call(scrollAmount);
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(this.player);

        if (component.getPsychoTicks() > 0) {
            Item psychoItem = TMMItems.BAT;
            SRERole role = gameWorldComponent.getRole(player);
            if (role != null) {
                psychoItem = role.getPsychoItem();
            }
            if (((this.player.getInventory().getItem(oldSlot).is(psychoItem)) &&
                    (!this.player.getInventory().getItem(this.player.getInventory().selected).is(psychoItem))))
                this.player.getInventory().selected = oldSlot;
        }

    }
}