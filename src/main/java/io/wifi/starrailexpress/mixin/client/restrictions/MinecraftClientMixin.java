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

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1))
    private void tmm$replaceInventoryScreenWithLimitedInventoryScreen(Minecraft instance, Screen screen,
            Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(instance, screen);
            return;
        }

        SREGameWorldComponent gameComponent = SREClient.gameComponent;
        if (gameComponent != null) {

            if (gameComponent.getFade() > 0) {
                return;
            }

        }
        boolean flag = GameUtils.isPlayerAliveAndSurvival(player);
        if (!flag && checkOnOpenInventory(player, screen)) {
            flag = true;
        }

        original.call(instance,
                flag ? new LimitedInventoryScreen(this.player) : screen);
    }

    private static boolean checkOnOpenInventory(LocalPlayer player, Screen screen) {
        return io.wifi.starrailexpress.event.OnOpenInventory.EVENT.invoker().needOpenLimittedInventory(player, screen);
    }
}
