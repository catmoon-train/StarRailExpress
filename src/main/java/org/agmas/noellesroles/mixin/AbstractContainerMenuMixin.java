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
import io.wifi.starrailexpress.content.gui.CustomInventoryMenu;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import org.agmas.noellesroles.client.screen.DetectiveInspectScreenHandler;
import org.agmas.noellesroles.client.screen.PostmanScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    public void doClick(int i, int j, ClickType clickType, Player player, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        final var instance1 = (AbstractContainerMenu) (Object) this;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (instance1 instanceof CustomInventoryMenu)
            return;
        if (!(instance1 instanceof InventoryMenu || instance1 instanceof PostmanScreenHandler
                || instance1 instanceof DetectiveInspectScreenHandler  ||instance1.getClass().getPackage().getName().contains("supplementaries"))) {
            ci.cancel();
        }
    }
}
