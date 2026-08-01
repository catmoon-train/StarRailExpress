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

package io.wifi.mixins.cca;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.function.Consumer;

@Mixin(targets = "org.ladysnake.cca.internal.base.asm.StaticComponentPluginBase", remap = false)
public class CCAWatheBlockMixin {

    @Inject(method = "processInitializers", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skipWathe(Collection<?> entrypoints, Consumer<?> consumer, CallbackInfo ci) {
        // 检查 entrypoints 里是否包含 wathe 的类，有则跳过
        for (Object ep : entrypoints) {
            if (ep.getClass().getName().startsWith("dev.doctor4t.wathe.")) {
                ci.cancel();
                return;
            }
        }
    }
}