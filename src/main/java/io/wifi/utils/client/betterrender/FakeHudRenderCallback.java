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

package io.wifi.utils.client.betterrender;

import net.minecraft.client.DeltaTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Custom HUD render callback event that is invoked during the FakeGuiGraphics frame lifecycle.
 * 
 * <p>This event is guaranteed to be triggered INSIDE the {@link OptimizedTextRenderer#beginFrame()} 
 * and {@link OptimizedTextRenderer#endFrame()} lifecycle, ensuring that all rendering calls are 
 * properly batched and cached.
 * 
 * <h2>Why use this instead of Fabric's HudRenderCallback?</h2>
 * <ul>
 *   <li>Fabric's HudRenderCallback may fire at unpredictable times relative to our frame lifecycle</li>
 *   <li>Using this event ensures text rendering is always properly batched</li>
 *   <li>Prevents font rendering issues where text may disappear randomly</li>
 * </ul>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * FakeHudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
 *     guiGraphics.drawString(font, "Hello", 10, 10, 0xFFFFFF);
 * });
 * }</pre>
 */
public class FakeHudRenderCallback {
    
    /**
     * The singleton event instance.
     */
    public static final FakeHudRenderEvent EVENT = new FakeHudRenderEvent();
    
    /**
     * Event container that manages render callbacks.
     * 
     * <p>Uses ArrayList + Consumer pattern similar to RoleHudRenderCallback for reliable rendering.
     */
    public static class FakeHudRenderEvent {
        private final List<BiConsumer<FakeGuiGraphics, DeltaTracker>> callbacks = new ArrayList<>();
        
        /**
         * Register a render callback.
         * 
         * @param callback the callback to register
         */
        public void register(BiConsumer<FakeGuiGraphics, DeltaTracker> callback) {
            callbacks.add(callback);
        }
        
        /**
         * Unregister a render callback.
         * 
         * @param callback the callback to remove
         * @return true if the callback was found and removed
         */
        public boolean unregister(BiConsumer<FakeGuiGraphics, DeltaTracker> callback) {
            return callbacks.remove(callback);
        }
        
        /**
         * Get all registered callbacks.
         * 
         * @return the list of callbacks (do not modify)
         */
        public List<BiConsumer<FakeGuiGraphics, DeltaTracker>> getCallbacks() {
            return callbacks;
        }
        
        /**
         * Invoke all registered callbacks.
         * Called internally by the frame lifecycle.
         * 
         * @param guiGraphics the FakeGuiGraphics instance for this frame
         * @param deltaTracker the delta tracker for timing
         */
        public void invoke(FakeGuiGraphics guiGraphics, DeltaTracker deltaTracker) {
            for (BiConsumer<FakeGuiGraphics, DeltaTracker> callback : callbacks) {
                try {
                    callback.accept(guiGraphics, deltaTracker);
                } catch (Exception e) {
                    // Log but don't crash - one bad callback shouldn't break everything
                    io.wifi.starrailexpress.SRE.LOGGER.error("[FakeHudRenderCallback] Error in render callback", e);
                }
            }
        }
        
        /**
         * Check if there are any registered callbacks.
         * 
         * @return true if there are callbacks registered
         */
        public boolean hasCallbacks() {
            return !callbacks.isEmpty();
        }
    }
}
