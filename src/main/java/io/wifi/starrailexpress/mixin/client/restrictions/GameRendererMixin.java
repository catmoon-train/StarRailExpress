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

import io.wifi.utils.client.betterrender.TextBatchingBuffer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flushes the chat text batch at the end of the GUI pass — after the GUI
 * buffer (chat backdrop / screen background) has flushed, but before the GUI
 * model-view matrix is popped back to the world matrix. This keeps the chat
 * text on top of the backdrop while drawing with the correct GUI transform.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void sre$flushChatText(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        TextBatchingBuffer.CHAT.flush();
    }
}
