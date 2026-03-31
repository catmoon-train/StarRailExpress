package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** All render actions cached from the LAST dirty tick — replayed every frame in order. */
    private final List<RenderAction> tickCache = new ArrayList<>(128);

    /** Render actions accumulated during the current frame's enqueue pass. */
    private final List<RenderAction> pending = new ArrayList<>(128);

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
    }

    public void endFrame() {
        if (!inFrame)
            return;

        // If the tick was dirty, the HUD ran and filled pending lists with fresh entries.
        // Promote them to tickCache and clear the dirty flag.
        // IMPORTANT: Always update tickCache when dirty, even if pending is empty (HUD hidden).
        if (tickDirty) {
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
        if (frameGraphics == null || tickCache.isEmpty())
            return;

        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource bufferSource = frameGraphics.bufferSource();

        // Execute all render actions in order
        for (RenderAction action : tickCache) {
            action.execute(frameGraphics, font, bufferSource);
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
        pending.add(new TextAction(null, text, x, y, color, shadow,
                new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueSeq(GuiGraphics graphics, FormattedCharSequence seq,
            float x, float y, int color, boolean shadow) {
        if (!inFrame) {
            graphics.drawString(Minecraft.getInstance().font, seq, (int) x, (int) y, color, shadow);
            return;
        }
        pending.add(new TextAction(seq, null, x, y, color, shadow,
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
        pending.add(new BlitAction(texture,
                x1, x2, y1, y2, z,
                u0, u1, v0, v1,
                r, g, b, a,
                new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueFill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int z, int color) {
        if (!inFrame) {
            graphics.fill(x1, y1, x2, y2, z, color);
            return;
        }
        pending.add(new FillAction(x1, y1, x2, y2, z, color,
                new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueFillWithRenderType(GuiGraphics graphics, RenderType rt, int x1, int y1, int x2, int y2, int z, int color) {
        if (!inFrame) {
            graphics.fill(rt, x1, y1, x2, y2, z, color);
            return;
        }
        pending.add(new FillRenderTypeAction(rt, x1, y1, x2, y2, z, color));
    }

    public void enqueueFillGradient(GuiGraphics graphics, int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo) {
        if (!inFrame) {
            graphics.fillGradient(x1, y1, x2, y2, z, colorFrom, colorTo);
            return;
        }
        pending.add(new FillGradientAction(x1, y1, x2, y2, z, colorFrom, colorTo,
                new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueFillGradientWithRenderType(GuiGraphics graphics, RenderType rt, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, int z) {
        if (!inFrame) {
            graphics.fillGradient(rt, x1, y1, x2, y2, colorFrom, colorTo, z);
            return;
        }
        pending.add(new FillGradientRenderTypeAction(rt, x1, y1, x2, y2, colorFrom, colorTo, z));
    }

    public void enqueueHLine(GuiGraphics graphics, int x1, int x2, int y, int color) {
        if (!inFrame) {
            graphics.hLine(x1, x2, y, color);
            return;
        }
        pending.add(new HLineAction(null, x1, x2, y, color, new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueHLineWithRenderType(GuiGraphics graphics, RenderType rt, int x1, int x2, int y, int color) {
        if (!inFrame) {
            graphics.hLine(rt, x1, x2, y, color);
            return;
        }
        pending.add(new HLineAction(rt, x1, x2, y, color, new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueVLine(GuiGraphics graphics, int x, int y1, int y2, int color) {
        if (!inFrame) {
            graphics.vLine(x, y1, y2, color);
            return;
        }
        pending.add(new VLineAction(null, x, y1, y2, color, new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueVLineWithRenderType(GuiGraphics graphics, RenderType rt, int x, int y1, int y2, int color) {
        if (!inFrame) {
            graphics.vLine(rt, x, y1, y2, color);
            return;
        }
        pending.add(new VLineAction(rt, x, y1, y2, color, new Matrix4f(graphics.pose().last().pose())));
    }

    public void enqueueRenderOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (!inFrame) {
            graphics.renderOutline(x, y, w, h, color);
            return;
        }
        pending.add(new RenderOutlineAction(x, y, w, h, color));
    }

    public void enqueueFillRenderType(GuiGraphics graphics, RenderType rt, int x1, int y1, int x2, int y2, int z) {
        if (!inFrame) {
            graphics.fillRenderType(rt, x1, y1, x2, y2, z);
            return;
        }
        pending.add(new FillRenderTypeOnlyAction(rt, x1, y1, x2, y2, z));
    }

    // ── Scissor operations ─────────────────────────────────────────────────────

    public void enqueueEnableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        if (!inFrame) {
            graphics.enableScissor(x1, y1, x2, y2);
            return;
        }
        pending.add(new EnableScissorAction(x1, y1, x2, y2));
    }

    public void enqueueDisableScissor(GuiGraphics graphics) {
        if (!inFrame) {
            graphics.disableScissor();
            return;
        }
        pending.add(new DisableScissorAction());
    }

    // ── Blit / Sprite operations ───────────────────────────────────────────────

    public void enqueueBlitSprite(GuiGraphics graphics, ResourceLocation loc, int x, int y, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, x, y, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, 0, w, h, -1, -1, -1, -1));
    }

    public void enqueueBlitSpriteZ(GuiGraphics graphics, ResourceLocation loc, int x, int y, int z, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, x, y, z, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, z, w, h, -1, -1, -1, -1));
    }

    public void enqueueBlitSpriteRegion(GuiGraphics graphics, ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, tw, th, u, v, x, y, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, 0, w, h, tw, th, u, v));
    }

    public void enqueueBlitSpriteRegionZ(GuiGraphics graphics, ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int z, int w, int h) {
        if (!inFrame) {
            graphics.blitSprite(loc, tw, th, u, v, x, y, z, w, h);
            return;
        }
        pending.add(new BlitSpriteAction(loc, x, y, z, w, h, tw, th, u, v));
    }

    public void enqueueBlitTexAtlas(GuiGraphics graphics, int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        if (!inFrame) {
            graphics.blit(x, y, z, w, h, sprite);
            return;
        }
        pending.add(new BlitTexAtlasAction(x, y, z, w, h, sprite, 1f, 1f, 1f, 1f));
    }

    public void enqueueBlitTexAtlasColor(GuiGraphics graphics, int x, int y, int z, int w, int h, TextureAtlasSprite sprite, float r, float g, float b, float a) {
        if (!inFrame) {
            graphics.blit(x, y, z, w, h, sprite, r, g, b, a);
            return;
        }
        pending.add(new BlitTexAtlasAction(x, y, z, w, h, sprite, r, g, b, a));
    }

    public void enqueueBlitResource(GuiGraphics graphics, ResourceLocation loc, int x, int y, int z, float u, float v, int w, int h, int tw, int th) {
        if (!inFrame) {
            graphics.blit(loc, x, y, z, u, v, w, h, tw, th);
            return;
        }
        pending.add(new BlitResourceAction(loc, x, y, z, u, v, w, h, w, h, tw, th));
    }

    public void enqueueBlitResourceRegion(GuiGraphics graphics, ResourceLocation loc, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th) {
        if (!inFrame) {
            graphics.blit(loc, x, y, w, h, u, v, rw, rh, tw, th);
            return;
        }
        pending.add(new BlitResourceAction(loc, x, y, 0, u, v, w, h, rw, rh, tw, th));
    }

    public void enqueueBlitResourceSimple(GuiGraphics graphics, ResourceLocation loc, int x, int y, float u, float v, int w, int h, int tw, int th) {
        if (!inFrame) {
            graphics.blit(loc, x, y, u, v, w, h, tw, th);
            return;
        }
        pending.add(new BlitResourceAction(loc, x, y, 0, u, v, w, h, w, h, tw, th));
    }

    // ── Item rendering operations ──────────────────────────────────────────────

    public void enqueueRenderItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!inFrame) {
            graphics.renderItem(stack, x, y);
            return;
        }
        pending.add(new RenderItemAction(stack.copy(), x, y, 0, 0, null));
    }

    public void enqueueRenderItemSeed(GuiGraphics graphics, ItemStack stack, int x, int y, int seed) {
        if (!inFrame) {
            graphics.renderItem(stack, x, y, seed);
            return;
        }
        pending.add(new RenderItemAction(stack.copy(), x, y, seed, 0, null));
    }

    public void enqueueRenderItemSeedZ(GuiGraphics graphics, ItemStack stack, int x, int y, int seed, int z) {
        if (!inFrame) {
            graphics.renderItem(stack, x, y, seed, z);
            return;
        }
        pending.add(new RenderItemAction(stack.copy(), x, y, seed, z, null));
    }

    public void enqueueRenderItemEntity(GuiGraphics graphics, LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        // Item rendering with entity context cannot be easily cached due to entity state
        // Fall back to direct rendering
        graphics.renderItem(entity, stack, x, y, seed);
    }

    public void enqueueRenderFakeItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!inFrame) {
            graphics.renderFakeItem(stack, x, y);
            return;
        }
        pending.add(new RenderFakeItemAction(stack.copy(), x, y, 0));
    }

    public void enqueueRenderFakeItemSeed(GuiGraphics graphics, ItemStack stack, int x, int y, int seed) {
        if (!inFrame) {
            graphics.renderFakeItem(stack, x, y, seed);
            return;
        }
        pending.add(new RenderFakeItemAction(stack.copy(), x, y, seed));
    }

    public void enqueueRenderItemDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, @Nullable String label) {
        if (!inFrame) {
            graphics.renderItemDecorations(font, stack, x, y, label);
            return;
        }
        pending.add(new RenderItemDecorationsAction(stack.copy(), x, y, label));
    }

    // ── Tooltip rendering operations ───────────────────────────────────────────

    public void enqueueRenderTooltipItem(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, stack, x, y);
            return;
        }
        pending.add(new RenderTooltipItemAction(stack.copy(), x, y));
    }

    public void enqueueRenderTooltipLines(GuiGraphics graphics, Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, lines, image, x, y);
            return;
        }
        pending.add(new RenderTooltipLinesAction(new ArrayList<>(lines), image, x, y));
    }

    public void enqueueRenderTooltipComponent(GuiGraphics graphics, Font font, Component component, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, component, x, y);
            return;
        }
        pending.add(new RenderTooltipComponentAction(component, x, y));
    }

    public void enqueueRenderComponentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
        if (!inFrame) {
            graphics.renderComponentTooltip(font, lines, x, y);
            return;
        }
        pending.add(new RenderComponentTooltipAction(new ArrayList<>(lines), x, y));
    }

    public void enqueueRenderTooltipSeq(GuiGraphics graphics, Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        if (!inFrame) {
            graphics.renderTooltip(font, lines, x, y);
            return;
        }
        pending.add(new RenderTooltipSeqAction(new ArrayList<>(lines), x, y));
    }

    public void enqueueRenderComponentHoverEffect(GuiGraphics graphics, Font font, @Nullable Style style, int x, int y) {
        if (!inFrame) {
            graphics.renderComponentHoverEffect(font, style, x, y);
            return;
        }
        pending.add(new RenderComponentHoverEffectAction(style, x, y));
    }

    public void enqueueSetColor(GuiGraphics graphics, float r, float g, float b, float a) {
        if (!inFrame) {
            graphics.setColor(r, g, b, a);
            return;
        }
        pending.add(new SetColorAction(r, g, b, a));
    }

    @SuppressWarnings("deprecation")
    public void enqueueDrawManaged(GuiGraphics graphics, Runnable runnable) {
        // Managed drawing cannot be cached as it may contain arbitrary state changes
        // Execute directly
        graphics.drawManaged(runnable);
    }

    // ── RenderAction interface ─────────────────────────────────────────────────

    @FunctionalInterface
    private interface RenderAction {
        void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource);
    }

    // ── Text rendering action ──────────────────────────────────────────────────

    private record TextAction(
            @Nullable FormattedCharSequence seq,
            @Nullable Component text,
            float x, float y,
            int color, boolean shadow,
            Matrix4f matrix) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (seq != null) {
                font.drawInBatch(seq, x, y, color, shadow,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0,
                        LightTexture.FULL_BRIGHT);
            } else if (text != null) {
                font.drawInBatch(text, x, y, color, shadow,
                        matrix, bufferSource, Font.DisplayMode.NORMAL, 0,
                        LightTexture.FULL_BRIGHT);
            }
        }
    }

    // ── Fill action ────────────────────────────────────────────────────────────

    private record FillAction(
            int x1, int y1, int x2, int y2, int z, int color,
            Matrix4f matrix) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            bufferBuilder.addVertex(matrix, x1, y2, z).setColor(color);
            bufferBuilder.addVertex(matrix, x2, y2, z).setColor(color);
            bufferBuilder.addVertex(matrix, x2, y1, z).setColor(color);
            bufferBuilder.addVertex(matrix, x1, y1, z).setColor(color);
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }

    private record FillRenderTypeAction(
            RenderType rt, int x1, int y1, int x2, int y2, int z, int color) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fill(rt, x1, y1, x2, y2, z, color);
        }
    }

    private record FillRenderTypeOnlyAction(
            RenderType rt, int x1, int y1, int x2, int y2, int z) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fillRenderType(rt, x1, y1, x2, y2, z);
        }
    }

    // ── Fill gradient action ───────────────────────────────────────────────────

    private record FillGradientAction(
            int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo,
            Matrix4f matrix) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fillGradient(x1, y1, x2, y2, z, colorFrom, colorTo);
        }
    }

    private record FillGradientRenderTypeAction(
            RenderType rt, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, int z) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.fillGradient(rt, x1, y1, x2, y2, colorFrom, colorTo, z);
        }
    }

    // ── Line actions ───────────────────────────────────────────────────────────

    private record HLineAction(
            @Nullable RenderType rt, int x1, int x2, int y, int color,
            Matrix4f matrix) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (rt != null) {
                graphics.hLine(rt, x1, x2, y, color);
            } else {
                graphics.hLine(x1, x2, y, color);
            }
        }
    }

    private record VLineAction(
            @Nullable RenderType rt, int x, int y1, int y2, int color,
            Matrix4f matrix) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (rt != null) {
                graphics.vLine(rt, x, y1, y2, color);
            } else {
                graphics.vLine(x, y1, y2, color);
            }
        }
    }

    private record RenderOutlineAction(
            int x, int y, int w, int h, int color) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderOutline(x, y, w, h, color);
        }
    }

    // ── Blit action ────────────────────────────────────────────────────────────

    private record BlitAction(
            ResourceLocation texture,
            int x1, int x2, int y1, int y2, int z,
            float u0, float u1, float v0, float v1,
            float r, float g, float b, float a,
            Matrix4f matrix) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferBuilder.addVertex(matrix, x1, y2, z).setUv(u0, v1).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x2, y2, z).setUv(u1, v1).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x2, y1, z).setUv(u1, v0).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1, y1, z).setUv(u0, v0).setColor(r, g, b, a);
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }

    // ── BlitSprite action ──────────────────────────────────────────────────────

    private record BlitSpriteAction(
            ResourceLocation loc, int x, int y, int z, int w, int h,
            int tw, int th, int u, int v) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (tw < 0) {
                if (z != 0) {
                    graphics.blitSprite(loc, x, y, z, w, h);
                } else {
                    graphics.blitSprite(loc, x, y, w, h);
                }
            } else {
                if (z != 0) {
                    graphics.blitSprite(loc, tw, th, u, v, x, y, z, w, h);
                } else {
                    graphics.blitSprite(loc, tw, th, u, v, x, y, w, h);
                }
            }
        }
    }

    // ── BlitTexAtlas action ────────────────────────────────────────────────────

    private record BlitTexAtlasAction(
            int x, int y, int z, int w, int h, TextureAtlasSprite sprite,
            float r, float g, float b, float a) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (r == 1f && g == 1f && b == 1f && a == 1f) {
                graphics.blit(x, y, z, w, h, sprite);
            } else {
                graphics.blit(x, y, z, w, h, sprite, r, g, b, a);
            }
        }
    }

    // ── BlitResource action ────────────────────────────────────────────────────

    private record BlitResourceAction(
            ResourceLocation loc, int x, int y, int z, float u, float v,
            int w, int h, int rw, int rh, int tw, int th) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (z != 0) {
                graphics.blit(loc, x, y, z, u, v, w, h, tw, th);
            } else if (w != rw || h != rh) {
                graphics.blit(loc, x, y, w, h, u, v, rw, rh, tw, th);
            } else {
                graphics.blit(loc, x, y, u, v, w, h, tw, th);
            }
        }
    }

    // ── Scissor actions ────────────────────────────────────────────────────────

    private record EnableScissorAction(int x1, int y1, int x2, int y2) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.enableScissor(x1, y1, x2, y2);
        }
    }

    private record DisableScissorAction() implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.disableScissor();
        }
    }

    // ── Item rendering actions ─────────────────────────────────────────────────

    private record RenderItemAction(
            ItemStack stack, int x, int y, int seed, int z,
            @Nullable LivingEntity entity) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (z != 0) {
                graphics.renderItem(stack, x, y, seed, z);
            } else if (seed != 0) {
                graphics.renderItem(stack, x, y, seed);
            } else {
                graphics.renderItem(stack, x, y);
            }
        }
    }

    private record RenderFakeItemAction(
            ItemStack stack, int x, int y, int seed) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (seed != 0) {
                graphics.renderFakeItem(stack, x, y, seed);
            } else {
                graphics.renderFakeItem(stack, x, y);
            }
        }
    }

    private record RenderItemDecorationsAction(
            ItemStack stack, int x, int y, @Nullable String label) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            if (label != null) {
                graphics.renderItemDecorations(font, stack, x, y, label);
            } else {
                graphics.renderItemDecorations(font, stack, x, y);
            }
        }
    }

    // ── Tooltip rendering actions ──────────────────────────────────────────────

    private record RenderTooltipItemAction(ItemStack stack, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, stack, x, y);
        }
    }

    private record RenderTooltipLinesAction(
            List<Component> lines, Optional<TooltipComponent> image, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, lines, image, x, y);
        }
    }

    private record RenderTooltipComponentAction(Component component, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, component, x, y);
        }
    }

    private record RenderComponentTooltipAction(List<Component> lines, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderComponentTooltip(font, lines, x, y);
        }
    }

    private record RenderTooltipSeqAction(List<FormattedCharSequence> lines, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderTooltip(font, lines, x, y);
        }
    }

    private record RenderComponentHoverEffectAction(@Nullable Style style, int x, int y) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.renderComponentHoverEffect(font, style, x, y);
        }
    }

    // ── Set color action ───────────────────────────────────────────────────────

    private record SetColorAction(float r, float g, float b, float a) implements RenderAction {
        @Override
        public void execute(GuiGraphics graphics, Font font, MultiBufferSource.BufferSource bufferSource) {
            graphics.setColor(r, g, b, a);
        }
    }
}