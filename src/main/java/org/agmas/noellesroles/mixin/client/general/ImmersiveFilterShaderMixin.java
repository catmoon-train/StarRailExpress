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

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.BlindnessVisionShader;
import org.agmas.noellesroles.client.ImmersiveFilterShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class ImmersiveFilterShaderMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void renderImmersive(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        @SuppressWarnings("resource")
        GameRenderer renderer = (GameRenderer) (Object) this;
        {
            if (renderer != null && bl && renderer.getMinecraft().level != null) {
                ImmersiveFilterShader.instance.initPostProcessor();
                ImmersiveFilterShader.instance.renderPostProcess(deltaTracker.getGameTimeDeltaPartialTick(true));
                // The world pass ran before the hand. Run a hand-only pass here
                // so the hand receives the same outline without overwriting the
                // world image revealed by the first pass.
                BlindnessVisionShader.INSTANCE.initPostProcessor();
                BlindnessVisionShader.INSTANCE.renderPostProcess(
                        deltaTracker.getGameTimeDeltaPartialTick(true), true);
            }
        }
    }

    /**
     * The vanilla first-person hand renderer clears the depth buffer before
     * drawing the hand. Run BlindVision immediately before that clear so its
     * DepthSampler still contains world blocks and entities, not just the hand.
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void renderBlindnessBeforeHand(DeltaTracker deltaTracker, CallbackInfo ci) {
        @SuppressWarnings("resource")
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (renderer != null && renderer.getMinecraft().level != null) {
            BlindnessVisionShader.INSTANCE.initPostProcessor();
            BlindnessVisionShader.INSTANCE.renderPostProcess(deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    @Inject(method = "resize(II)V", at = @At("TAIL"))
    private void resizeImmersive(int w, int h, CallbackInfo ci) {
        ImmersiveFilterShader.instance.resize(w, h);
        BlindnessVisionShader.INSTANCE.resize(w, h);
    }
}
