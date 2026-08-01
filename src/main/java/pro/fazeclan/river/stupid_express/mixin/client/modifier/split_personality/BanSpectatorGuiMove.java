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

package pro.fazeclan.river.stupid_express.mixin.client.modifier.split_personality;

import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

@Mixin(SpectatorGui.class)
public abstract class BanSpectatorGuiMove {
    @Inject(method = "onHotbarSelected", at = @At("HEAD"), cancellable = true)
    private void se$onHotbarSelected(int i, CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        try {
            final var worldModifierComponent = WorldModifierComponent.KEY.get(Minecraft.getInstance().player.level());
            if (worldModifierComponent.isModifier(Minecraft.getInstance().player, SEModifiers.SPLIT_PERSONALITY))
                {
                    ci.cancel();
                }

        }catch (Exception ignored){

        }
    }
    @Inject(method = "onMouseMiddleClick", at = @At("HEAD"), cancellable = true)
    private void se$onMouseMiddleClick(CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        try {
            final var worldModifierComponent = WorldModifierComponent.KEY.get(Minecraft.getInstance().player.level());
            if (worldModifierComponent.isModifier(Minecraft.getInstance().player, SEModifiers.SPLIT_PERSONALITY))
                {
                    ci.cancel();
                }

        }catch (Exception ignored){

        }
    }
}
