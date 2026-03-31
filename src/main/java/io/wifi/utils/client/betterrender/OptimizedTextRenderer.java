package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Frame-level text batch renderer with tick-rate computation.
 *
 * <p>
 * Lifecycle (managed by GuiRenderMixin):
 * 
 * <pre>
 *   ClientTickEvent  →  markTickDirty()    ← called every game tick
 *   Gui.render HEAD  →  beginFrame()       ← opens batch window
 *     FakeGuiGraphics →  enqueue(...)      ← only runs if tick is dirty
 *   Gui.render RETURN →  endFrame()        ← submits single GPU draw call
 * </pre>
 *
 * <p>
 * When the tick has NOT changed since last frame, {@link #isTickDirty()}
 * returns false. GuiRenderMixin skips re-invoking HUD render logic entirely
 * and endFrame() replays the cached entries from the last tick directly.
 */
public class OptimizedTextRenderer {

    public static final OptimizedTextRenderer INSTANCE = new OptimizedTextRenderer();

    private OptimizedTextRenderer() {
    }

    // ── Tick-rate gate ─────────────────────────────────────────────────────────

    /** Set to true every game tick by ClientTickMixin. */
    private boolean tickDirty = true;

    /**
     * The pending entries computed on the LAST dirty tick — replayed every frame.
     */
    private final List<PendingEntry> tickCache = new ArrayList<>(64);

    /** Entries accumulated during the current frame's enqueue pass. */
    private final List<PendingEntry> pending = new ArrayList<>(64);

    private GuiGraphics frameGraphics = null;
    private boolean inFrame = false;

    // ── Tick lifecycle (called by ClientTickMixin) ─────────────────────────────

    /** Called once per game tick. Marks HUD for recomputation. */
    public void markTickDirty() {
        tickDirty = true;
    }

    /** True if HUD render logic should run this frame (tick changed). */
    public boolean isTickDirty() {
        return tickDirty;
    }

    // ── Frame lifecycle (called by GuiRenderMixin) ─────────────────────────────

    public void beginFrame(GuiGraphics graphics) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // ← 这行
        frameGraphics = graphics;
        inFrame = true;
        pending.clear();
    }

    public void endFrame() {
        if (!inFrame)
            return;

        // If the tick was dirty, the HUD ran and filled `pending` with fresh entries.
        // Promote them to tickCache and clear the dirty flag.
        if (tickDirty && !pending.isEmpty()) {
            tickCache.clear();
            tickCache.addAll(pending);
            tickDirty = false;
        }

        // Always flush from tickCache (either freshly computed or last tick's replay)
        flushCache();

        pending.clear();
        inFrame = false;
        frameGraphics = null;
    }

    private void flushCache() {
        if (tickCache.isEmpty() || frameGraphics == null)
            return;

        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource bufferSource = frameGraphics.bufferSource();

        for (PendingEntry e : tickCache) {
            if (e.seq() != null) {
                font.drawInBatch(e.seq(), e.x(), e.y(), e.color(), e.shadow(),
                        e.matrix(), bufferSource, Font.DisplayMode.NORMAL, 0,
                        LightTexture.FULL_BRIGHT);
            } else {
                font.drawInBatch(e.text(), e.x(), e.y(), e.color(), e.shadow(),
                        e.matrix(), bufferSource, Font.DisplayMode.NORMAL, 0,
                        LightTexture.FULL_BRIGHT);
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // ← 这行
        RenderSystem.disableDepthTest();
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
    }

    // ── Enqueue API (called by FakeGuiGraphics) ────────────────────────────────

    public void enqueue(GuiGraphics graphics, Component text,
            float x, float y, int color, boolean shadow) {
        if (!inFrame) {
            graphics.drawString(Minecraft.getInstance().font, text, (int) x, (int) y, color, shadow);
            return;
        }
        pending.add(new PendingEntry(null, text, x, y, color, shadow,
                new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueSeq(GuiGraphics graphics, FormattedCharSequence seq,
            float x, float y, int color, boolean shadow) {
        if (!inFrame) {
            graphics.drawString(Minecraft.getInstance().font, seq, (int) x, (int) y, color, shadow);
            return;
        }
        pending.add(new PendingEntry(seq, null, x, y, color, shadow,
                new Matrix4f(graphics.pose().last().pose())));
    }

    // ── Internal record ────────────────────────────────────────────────────────

    private record PendingEntry(
            FormattedCharSequence seq,
            Component text,
            float x, float y,
            int color, boolean shadow,
            Matrix4f matrix) {
    }
}