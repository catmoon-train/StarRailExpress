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

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @WrapMethod(method = "handleDebugKeys")
    private boolean tmm$disableF3Keybinds(int key, Operation<Boolean> original) {
        if (SREClient.isInLobby) {
            return original.call(key);
        }
        if (!SREClient.isPlayerCreative()) {
            return key == 293 ? original.call(key) : false;
        } else {
            return original.call(key);
        }
    }
}
