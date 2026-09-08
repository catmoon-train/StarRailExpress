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

package io.wifi.starrailexpress.client.gui;

/** Keeps the departure screen alive until a fully covered frame has actually been drawn. */
public final class DepartureCurtain {
    private float previous;
    private float opacity;
    private boolean coveredFrame;
    private boolean releasing;

    public void tick(float cover, boolean nextStageReady) {
        previous = opacity;
        if (releasing) {
            opacity = Math.max(0.0F, opacity - 0.12F);
            return;
        }
        float target = Math.max(Math.clamp(cover, 0.0F, 1.0F), nextStageReady ? 1.0F : 0.0F);
        if (target > opacity) {
            opacity = Math.min(target, opacity + 0.12F);
        } else {
            opacity = target;
        }
    }

    public float opacity() {
        return Math.max(previous, opacity);
    }

    public float opacity(float partialTick) {
        return previous + (opacity - previous) * Math.clamp(partialTick, 0.0F, 1.0F);
    }

    public void frameRendered(float partialTick) {
        if (!releasing && opacity(partialTick) >= 0.999F) coveredFrame = true;
    }

    public boolean isFullyCovered() {
        return !releasing && coveredFrame && opacity() >= 0.999F;
    }

    public boolean canHandoff(boolean nextStageReady) {
        return nextStageReady && coveredFrame && !releasing;
    }

    public void release() {
        releasing = true;
        previous = opacity = 1.0F;
    }

    public boolean isReleasing() {
        return releasing;
    }

    public boolean isVisible() {
        return Math.max(previous, opacity) > 0.0F;
    }

    public void clear() {
        previous = opacity = 0.0F;
        coveredFrame = releasing = false;
    }
}
