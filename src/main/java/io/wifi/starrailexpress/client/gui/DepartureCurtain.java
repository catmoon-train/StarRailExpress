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
