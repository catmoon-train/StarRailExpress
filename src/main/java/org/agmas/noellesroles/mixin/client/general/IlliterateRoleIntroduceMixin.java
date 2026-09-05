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

package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.agmas.noellesroles.client.IlliterateTextClientHandle;
import org.agmas.noellesroles.client.screen.FilterSelectionScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {RoleIntroduceScreen.class, FilterSelectionScreen.class})
public class IlliterateRoleIntroduceMixin {
    @WrapMethod(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    private void noe$keepRoleIntroReadable(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            Operation<Void> original) {
        boolean prev = IlliterateTextClientHandle.scramble;
        IlliterateTextClientHandle.scramble = false;
        try {
            original.call(graphics, mouseX, mouseY, partialTick);
        } finally {
            IlliterateTextClientHandle.scramble = prev;
        }
    }
}
