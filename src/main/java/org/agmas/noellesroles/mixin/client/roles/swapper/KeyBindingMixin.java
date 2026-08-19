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

package org.agmas.noellesroles.mixin.client.roles.swapper;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Shadow
    public abstract boolean same(KeyMapping other);

    @Unique
    private boolean shouldSuppressKey() {
        if (SRE.isLobby)
            return false;
        final var instance = Minecraft.getInstance();
        if (instance == null)
            return false;
        final var player = instance.player;
        if (player == null)
            return false;
        final var options = instance.options;
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning() && SREClient.isPlayerAliveAndInSurvival() && SREClient.gameComponent.isRole(player, ModRoles.SWAPPER)) {
            if (instance.screen != null) {
                return this.same(options.keySwapOffhand) ||
                        this.same(options.keyJump) ||
                        this.same(options.keyTogglePerspective) ||
                        this.same(options.keyDrop) ||
                        this.same(options.keyAttack) ||
                        this.same(options.keyUse) ||
                        this.same(options.keyAdvancements);
            }
        }
        return false;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean noe$restrainWasPressedKeys(boolean original) {
        return original && !this.shouldSuppressKey();
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean noe$restrainIsPressedKeys(boolean original) {
        return original && !this.shouldSuppressKey();
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean noe$restrainMatchesKey(boolean original) {
        return original && !this.shouldSuppressKey();
    }
}
