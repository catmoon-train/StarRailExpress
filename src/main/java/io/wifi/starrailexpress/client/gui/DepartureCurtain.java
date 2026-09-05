package io.wifi.starrailexpress.client.gui;

/** Keeps the departure screen alive until a fully covered frame has actually been drawn. */
public final class DepartureCurtain {
    private float previous;
    private float opacity;
    private boolean coveredFrame;
    private boolean releasing;

    public void tick(float gameFade, boolean nextStageReady) {
        previous = opacity;
        if (releasing) {
            opacity = Math.max(0.0F, opacity - 0.1F);
        } else {
            float target = Math.max(opacity, Math.clamp(gameFade, 0.0F, 1.0F));
            if (nextStageReady) target = 1.0F;
            opacity = Math.min(target, opacity + 0.1F);
        }
    }

    public float opacity(float partialTick) {
        return previous + (opacity - previous) * Math.clamp(partialTick, 0.0F, 1.0F);
    }

    public void frameRendered(float partialTick) {
        if (!releasing && opacity(partialTick) >= 0.999F) coveredFrame = true;
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
