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

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import io.wifi.utils.client.betterrender.FakeHudRenderCallback;
import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manages the frame lifecycle of OptimizedTextRenderer.
 *
 * Also gates HUD render logic to tick rate:
 * when isTickDirty() is false, the HUD lambda is skipped entirely
 * and the cached entries from the last tick are replayed instead.
 * 
 * <h2>FakeHudRenderCallback Integration:</h2>
 * This mixin now invokes {@link FakeHudRenderCallback} INSIDE the frame
 * lifecycle,
 * guaranteeing that all rendering is properly batched. This fixes the font
 * rendering
 * issue where text would randomly disappear because Fabric's HudRenderCallback
 * could fire outside our frame boundaries.
 */
@Mixin(Gui.class)
public class GuiRenderMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private FakeGuiGraphics sre$fakeGuiGraphics;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics graphics, DeltaTracker partialTick, CallbackInfo ci) {
        OptimizedTextRenderer.INSTANCE.beginFrame(graphics);
        // Create FakeGuiGraphics for this frame - will be used by FakeHudRenderCallback
        sre$fakeGuiGraphics = new FakeGuiGraphics(graphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics graphics, DeltaTracker partialTick, CallbackInfo ci) {
        // Invoke FakeHudRenderCallback BEFORE endFrame() to ensure all callbacks
        // are executed within the frame lifecycle
        if (FakeHudRenderCallback.EVENT.hasCallbacks() && !minecraft.options.hideGui) {
            FakeHudRenderCallback.EVENT.invoke(sre$fakeGuiGraphics, partialTick);
        }

        OptimizedTextRenderer.INSTANCE.endFrame();
        FakeGuiGraphics.trackCount--;
        sre$fakeGuiGraphics = null;
    }
}