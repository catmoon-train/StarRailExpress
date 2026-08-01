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

package io.wifi.mixins.client;

import net.exmo.sre.loading.StarRailExpressTitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class PanoramaMixin {
    @Mutable
    @Final
    @Shadow
    public static PanoramaRenderer PANORAMA;

    // 在静态初始化块执行完毕后（TAIL）替换字段值
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void replacePanorama(CallbackInfo ci) {
        CubeMap customCubeMap = StarRailExpressTitleScreen.CUBE_MAP;
        PANORAMA = new PanoramaRenderer(customCubeMap);
    }
}
