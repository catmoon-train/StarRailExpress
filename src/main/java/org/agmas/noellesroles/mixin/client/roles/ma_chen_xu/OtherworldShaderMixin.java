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

package org.agmas.noellesroles.mixin.client.roles.ma_chen_xu;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.OtherworldShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.client.OtherworldShader.instance;

@Mixin(GameRenderer.class)
public class OtherworldShaderMixin {
//    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
//    private void onGetFov(CallbackInfoReturnable<Double> cir) {
//        if (Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.hasEffect(ModEffects.OTHERWORLD_AURA)){
//            cir.setReturnValue(100d);
//        }
//    }
    @SuppressWarnings("resource")
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void renderOtherworld(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (renderer != null && bl && renderer.getMinecraft().level != null) {
            OtherworldShader shader = instance;
            shader.initPostProcessor();
            shader.renderPostProcess(deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    @Inject(method = "resize(II)V", at = @At("TAIL"))
    private void resizeOtherworld(int pWidth, int pHeight, CallbackInfo ci) {
        if (instance != null)
            instance.resize(pWidth, pHeight);
    }
}
