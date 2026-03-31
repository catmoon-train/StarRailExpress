package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Frame-level text batch renderer with tick-rate computation and VertexBuffer caching.
 *
 * <p>
 * Lifecycle (managed by GuiRenderMixin):
 * 
 * <pre>
 *   ClientTickEvent  →  markTickDirty()    ← called every game tick
 *   Gui.render HEAD  →  beginFrame()       ← opens batch window
 *     FakeGuiGraphics →  enqueue(...)      ← only runs if tick is dirty
 *   Gui.render RETURN →  endFrame()        ← submits cached VertexBuffer or rebuilds
 * </pre>
 *
 * <p>
 * When the tick has NOT changed since last frame, {@link #isTickDirty()}
 * returns false. The cached VertexBuffer is replayed directly without
 * re-computing text or blit entries.
 */
public class OptimizedTextRenderer {

    public static final OptimizedTextRenderer INSTANCE = new OptimizedTextRenderer();

    private OptimizedTextRenderer() {
    }

    // ── Tick-rate gate ─────────────────────────────────────────────────────────

    /** Set to true every game tick by ClientTickMixin. */
    private boolean tickDirty = true;

    /**
     * The pending text entries computed on the LAST dirty tick — replayed every frame.
     */
    private final List<PendingEntry> tickCache = new ArrayList<>(64);

    /** Text entries accumulated during the current frame's enqueue pass. */
    private final List<PendingEntry> pending = new ArrayList<>(64);

    /** The pending blit entries computed on the LAST dirty tick — replayed every frame. */
    private final List<BlitEntry> blitTickCache = new ArrayList<>(32);

    /** Blit entries accumulated during the current frame's enqueue pass. */
    private final List<BlitEntry> blitPending = new ArrayList<>(32);

    /** The pending fill entries computed on the LAST dirty tick — replayed every frame. */
    private final List<FillEntry> fillTickCache = new ArrayList<>(32);

    /** Fill entries accumulated during the current frame's enqueue pass. */
    private final List<FillEntry> fillPending = new ArrayList<>(32);

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
        frameGraphics = graphics;
        inFrame = true;
        pending.clear();
        blitPending.clear();
        fillPending.clear();
    }

    public void endFrame() {
        if (!inFrame)
            return;

        // If the tick was dirty, the HUD ran and filled pending lists with fresh entries.
        // Promote them to tickCache and clear the dirty flag.
        if (tickDirty && (!pending.isEmpty() || !blitPending.isEmpty() || !fillPending.isEmpty())) {
            tickCache.clear();
            tickCache.addAll(pending);
            blitTickCache.clear();
            blitTickCache.addAll(blitPending);
            fillTickCache.clear();
            fillTickCache.addAll(fillPending);
            tickDirty = false;
        }

        // Always flush from tickCache (either freshly computed or last tick's replay)
        flushCache();

        pending.clear();
        blitPending.clear();
        fillPending.clear();
        inFrame = false;
        frameGraphics = null;
    }

    private void flushCache() {
        if (frameGraphics == null)
            return;

        // Flush fill entries (solid color rectangles)
        for (FillEntry f : fillTickCache) {
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Matrix4f matrix = f.matrix();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            bufferBuilder.addVertex(matrix, f.x1(), f.y2(), f.z()).setColor(f.color());
            bufferBuilder.addVertex(matrix, f.x2(), f.y2(), f.z()).setColor(f.color());
            bufferBuilder.addVertex(matrix, f.x2(), f.y1(), f.z()).setColor(f.color());
            bufferBuilder.addVertex(matrix, f.x1(), f.y1(), f.z()).setColor(f.color());
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }

        // Flush blit entries (textures like player heads)
        for (BlitEntry b : blitTickCache) {
            RenderSystem.setShaderTexture(0, b.texture());
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend();
            Matrix4f matrix = b.matrix();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferBuilder.addVertex(matrix, b.x1(), b.y2(), 0).setUv(b.u0(), b.v1()).setColor(b.r(), b.g(), b.b(), b.a());
            bufferBuilder.addVertex(matrix, b.x2(), b.y2(), 0).setUv(b.u1(), b.v1()).setColor(b.r(), b.g(), b.b(), b.a());
            bufferBuilder.addVertex(matrix, b.x2(), b.y1(), 0).setUv(b.u1(), b.v0()).setColor(b.r(), b.g(), b.b(), b.a());
            bufferBuilder.addVertex(matrix, b.x1(), b.y1(), 0).setUv(b.u0(), b.v0()).setColor(b.r(), b.g(), b.b(), b.a());
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }

        if (tickCache.isEmpty())
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

    public void enqueueBlit(GuiGraphics graphics, ResourceLocation texture,
            int x1, int x2, int y1, int y2, int z,
            float u0, float u1, float v0, float v1,
            float r, float g, float b, float a) {
        if (!inFrame) {
            graphics.innerBlit(texture, x1, x2, y1, y2, z, u0, u1, v0, v1, r, g, b, a);
            return;
        }
        blitPending.add(new BlitEntry(texture,
                x1, x2, y1, y2,
                u0, u1, v0, v1,
                r, g, b, a,
                new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueFill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int z, int color) {
        if (!inFrame) {
            graphics.fill(x1, y1, x2, y2, z, color);
            return;
        }
        fillPending.add(new FillEntry(x1, y1, x2, y2, z, color,
                new Matrix4f(graphics.pose().last().pose())));
    }

    // ── Internal records ───────────────────────────────────────────────────────

    private record PendingEntry(
            @Nullable FormattedCharSequence seq,
            @Nullable Component text,
            float x, float y,
            int color, boolean shadow,
            Matrix4f matrix) {
    }

    private record BlitEntry(
            ResourceLocation texture,
            int x1, int x2, int y1, int y2,
            float u0, float u1, float v0, float v1,
            float r, float g, float b, float a,
            Matrix4f matrix) {
    }

    private record FillEntry(
            int x1, int y1, int x2, int y2, int z, int color,
            Matrix4f matrix) {
    }
}