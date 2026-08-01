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

package org.agmas.noellesroles.mixin.client.roles.youlu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.agmas.noellesroles.client.YouluFreeCamClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幽露球烟：本地玩家处于球烟内（带视野迷雾效果）时，将雾色与背景清屏色染黑。
 * 雾的可见距离仍由既有的 VISION_FOG 渲染逻辑控制（见 WorldRendererMixin），
 * 本 mixin 只负责把雾"变为黑色"。
 */
@Mixin(FogRenderer.class)
public abstract class YouluFogColorMixin {

    /** 黑雾亮度（纯 0 会显得过于死黑，保留一丝灰度）。 */
    private static final float NR$BLACK = 0.015f;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void nr$blackenClearColor(Camera camera, float partialTick, ClientLevel level,
            int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        if (YouluFreeCamClient.shouldBlackenFog()) {
            RenderSystem.clearColor(NR$BLACK, NR$BLACK, NR$BLACK, 0.0F);
        }
    }

    @Inject(method = "levelFogColor", at = @At("TAIL"))
    private static void nr$blackenFogColor(CallbackInfo ci) {
        if (YouluFreeCamClient.shouldBlackenFog()) {
            RenderSystem.setShaderFogColor(NR$BLACK, NR$BLACK, NR$BLACK);
        }
    }
}
