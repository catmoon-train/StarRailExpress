package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * FakeGuiGraphics — perfect drop-in replacement for GuiGraphics.
 *
 * <p>
 * All methods delegate to the wrapped real GuiGraphics, but with tick-rate
 * caching
 * for optimized rendering. Draw calls are batched and submitted in a single GPU
 * draw call at the end of Gui.render() by GuiRenderMixin.
 *
 * <h2>Usage</h2>
 * 
 * <pre>{@code
 * // Keep ONE instance per HUD/Screen (field, not local variable)
 * private FakeGuiGraphics fakeGraphics;
 *
 * // In your render method:
 * fakeGraphics = new FakeGuiGraphics(guiGraphics); // wrap the real one
 *
 * // Use exactly like GuiGraphics:
 * fakeGraphics.drawString(font, "Hello", 4, 4, 0xFFFFFF);
 * fakeGraphics.fill(0, 0, 100, 20, 0x80000000);
 *
 * // No flush() needed — GuiRenderMixin handles it automatically.
 * }</pre>
 */
public class FakeGuiGraphics {

    public static int trackCount = 0;
    // ── Shared renderer (one per class, not per instance) ─────────────────────
    private static final OptimizedTextRenderer textRenderer = OptimizedTextRenderer.INSTANCE;

    // ── Wrapped real GuiGraphics ───────────────────────────────────────────────
    private final GuiGraphics real;

    public FakeGuiGraphics(GuiGraphics real) {
        this.real = real;
    }

    /** Access the wrapped real GuiGraphics when you need to escape the fake. */
    public GuiGraphics getDefaultGuiGraphics() {
        return real;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TEXT RENDERING — intercepted, batched, throttle-aware
    // ═════════════════════════════════════════════════════════════════════════

    // ── drawString(Font, String, int, int, int[, boolean]) ───────────────────

    public int drawString(Font font, @Nullable String string, int x, int y, int color) {
        return drawString(font, string, x, y, color, true);
    }

    public int drawString(Font font, @Nullable String string, int x, int y, int color, boolean shadow) {
        if (string == null)
            return 0;
        textRenderer.enqueue(real, Component.literal(string), x, y, color, shadow);
        return x + font.width(string);
    }

    // ── drawString(Font, FormattedCharSequence, int, int, int[, boolean]) ────

    public int drawString(Font font, FormattedCharSequence seq, int x, int y, int color) {
        return drawString(font, seq, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence seq, int x, int y, int color, boolean shadow) {
        textRenderer.enqueueSeq(real, seq, x, y, color, shadow);
        return x + font.width(seq);
    }

    // ── drawString(Font, Component, int, int, int[, boolean]) ────────────────

    public int drawString(Font font, Component component, int x, int y, int color) {
        return drawString(font, component, x, y, color, true);
    }

    public int drawString(Font font, Component component, int x, int y, int color, boolean shadow) {
        textRenderer.enqueue(real, component, x, y, color, shadow);
        return x + font.width(component);
    }

    // ── drawCenteredString ────────────────────────────────────────────────────

    public void drawCenteredString(Font font, String string, int cx, int y, int color) {
        drawString(font, string, cx - font.width(string) / 2, y, color);
    }

    public void drawCenteredString(Font font, Component component, int cx, int y, int color) {
        FormattedCharSequence seq = component.getVisualOrderText();
        drawString(font, seq, cx - font.width(seq) / 2, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence seq, int cx, int y, int color) {
        drawString(font, seq, cx - font.width(seq) / 2, y, color);
    }

    // ── drawWordWrap ──────────────────────────────────────────────────────────

    public void drawWordWrap(Font font, FormattedText text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence seq : font.split(text, maxWidth)) {
            drawString(font, seq, x, y, color, false);
            y += 9; // font line height
        }
    }

    // ── drawStringWithBackdrop ────────────────────────────────────────────────

    public int drawStringWithBackdrop(Font font, Component component, int x, int y, int width, int color) {
        int bgColor = Minecraft.getInstance().options.getBackgroundColor(0.0F);
        if (bgColor != 0) {
            fill(x - 2, y - 2, x + width + 2, y + 9 + 2,
                    net.minecraft.util.FastColor.ARGB32.multiply(bgColor, color));
        }
        return drawString(font, component, x, y, color, true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ALL OTHER GuiGraphics METHODS — cached via OptimizedTextRenderer
    // ═════════════════════════════════════════════════════════════════════════

    // ── Geometry ──────────────────────────────────────────────────────────────

    public PoseStack pose() {
        if (trackCount > 0) {
            SRE.LOGGER.info("[Track] Pose ", new Throwable());
        }
        return real.pose();
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return real.bufferSource();
    }

    public int guiWidth() {
        return real.guiWidth();
    }

    public int guiHeight() {
        return real.guiHeight();
    }

    public void hLine(int x1, int x2, int y, int color) {
        textRenderer.enqueueHLine(real, x1, x2, y, color);
    }

    public void hLine(RenderType rt, int x1, int x2, int y, int color) {
        textRenderer.enqueueHLineWithRenderType(real, rt, x1, x2, y, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        textRenderer.enqueueVLine(real, x, y1, y2, color);
    }

    public void vLine(RenderType rt, int x, int y1, int y2, int color) {
        textRenderer.enqueueVLineWithRenderType(real, rt, x, y1, y2, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        textRenderer.enqueueFill(real, x1, y1, x2, y2, 0, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        textRenderer.enqueueFill(real, x1, y1, x2, y2, z, color);
    }

    public void fill(RenderType rt, int x1, int y1, int x2, int y2, int color) {
        textRenderer.enqueueFillWithRenderType(real, rt, x1, y1, x2, y2, 0, color);
    }

    public void fill(RenderType rt, int x1, int y1, int x2, int y2, int z, int color) {
        textRenderer.enqueueFillWithRenderType(real, rt, x1, y1, x2, y2, z, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int c1, int c2) {
        textRenderer.enqueueFillGradient(real, x1, y1, x2, y2, 0, c1, c2);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int z, int c1, int c2) {
        textRenderer.enqueueFillGradient(real, x1, y1, x2, y2, z, c1, c2);
    }

    public void fillGradient(RenderType rt, int x1, int y1, int x2, int y2, int c1, int c2, int z) {
        textRenderer.enqueueFillGradientWithRenderType(real, rt, x1, y1, x2, y2, c1, c2, z);
    }

    public void fillRenderType(RenderType rt, int x1, int y1, int x2, int y2, int z) {
        textRenderer.enqueueFillRenderType(real, rt, x1, y1, x2, y2, z);
    }

    public void renderOutline(int x, int y, int w, int h, int color) {
        textRenderer.enqueueRenderOutline(real, x, y, w, h, color);
    }

    // ── Scissor ───────────────────────────────────────────────────────────────

    public void enableScissor(int x1, int y1, int x2, int y2) {
        textRenderer.enqueueEnableScissor(real, x1, y1, x2, y2);
    }

    public void disableScissor() {
        textRenderer.enqueueDisableScissor(real);
    }

    public boolean containsPointInScissor(int x, int y) {
        // This needs real-time check, cannot be cached
        return real.containsPointInScissor(x, y);
    }

    // ── Color & state ─────────────────────────────────────────────────────────

    public void setColor(float r, float g, float b, float a) {
        textRenderer.enqueueSetColor(real, r, g, b, a);
    }

    // ── Blit / Sprites ────────────────────────────────────────────────────────

    public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        textRenderer.enqueueBlitTexAtlas(real, x, y, z, w, h, sprite);
    }

    public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite, float r, float g, float b, float a) {
        textRenderer.enqueueBlitTexAtlasColor(real, x, y, z, w, h, sprite, r, g, b, a);
    }

    public void blit(ResourceLocation loc, int x, int y, int z, float u, float v, int w, int h, int tw, int th) {
        textRenderer.enqueueBlitResource(real, loc, x, y, z, u, v, w, h, tw, th);
    }

    public void blit(ResourceLocation loc, int x, int y, float u, float v, int w, int h, int tw, int th) {
        textRenderer.enqueueBlitResourceSimple(real, loc, x, y, u, v, w, h, tw, th);
    }

    /**
     * Blit with width/height and UV region - used by PlayerFaceRenderer.
     * Signature: blit(texture, x, y, width, height, u, v, uWidth, vHeight,
     * texWidth, texHeight)
     */
    public void blit(ResourceLocation loc, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw,
            int th) {
        textRenderer.enqueueBlitResourceRegion(real, loc, x, y, w, h, u, v, uw, vh, tw, th);
    }

    public void blitSprite(ResourceLocation loc, int x, int y, int w, int h) {
        textRenderer.enqueueBlitSprite(real, loc, x, y, w, h);
    }

    public void blitSprite(ResourceLocation loc, int x, int y, int z, int w, int h) {
        textRenderer.enqueueBlitSpriteZ(real, loc, x, y, z, w, h);
    }

    public void blitSprite(ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int w, int h) {
        textRenderer.enqueueBlitSpriteRegion(real, loc, tw, th, u, v, x, y, w, h);
    }

    public void blitSprite(ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int z, int w, int h) {
        textRenderer.enqueueBlitSpriteRegionZ(real, loc, tw, th, u, v, x, y, z, w, h);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    public void renderItem(ItemStack stack, int x, int y) {
        textRenderer.enqueueRenderItem(real, stack, x, y);
    }

    public void renderItem(ItemStack stack, int x, int y, int seed) {
        textRenderer.enqueueRenderItemSeed(real, stack, x, y, seed);
    }

    public void renderItem(ItemStack stack, int x, int y, int seed, int z) {
        textRenderer.enqueueRenderItemSeedZ(real, stack, x, y, seed, z);
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        textRenderer.enqueueRenderFakeItem(real, stack, x, y);
    }

    public void renderFakeItem(ItemStack stack, int x, int y, int seed) {
        textRenderer.enqueueRenderFakeItemSeed(real, stack, x, y, seed);
    }

    public void renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        textRenderer.enqueueRenderItemEntity(real, entity, stack, x, y, seed);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        textRenderer.enqueueRenderItemDecorations(real, font, stack, x, y, null);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, @Nullable String label) {
        textRenderer.enqueueRenderItemDecorations(real, font, stack, x, y, label);
    }

    // ── Tooltips ──────────────────────────────────────────────────────────────

    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        textRenderer.enqueueRenderTooltipItem(real, font, stack, x, y);
    }

    public void renderTooltip(Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
        textRenderer.enqueueRenderTooltipLines(real, font, lines, image, x, y);
    }

    public void renderTooltip(Font font, Component component, int x, int y) {
        textRenderer.enqueueRenderTooltipComponent(real, font, component, x, y);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        textRenderer.enqueueRenderComponentTooltip(real, font, lines, x, y);
    }

    public void renderTooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        textRenderer.enqueueRenderTooltipSeq(real, font, lines, x, y);
    }

    public void renderComponentHoverEffect(Font font, @Nullable Style style, int x, int y) {
        textRenderer.enqueueRenderComponentHoverEffect(real, font, style, x, y);
    }

    // ── Managed block (deprecated in vanilla but still used internally) ────────

    @Deprecated
    public void drawManaged(Runnable runnable) {
        textRenderer.enqueueDrawManaged(real, runnable);
    }

    // ── innerBlit — intercepted, batched for tick-rate optimization ────────────

    public void innerBlit(ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
            float u0, float u1, float v0, float v1, float r, float g, float b, float a) {
        textRenderer.enqueueBlit(real, texture, x1, x2, y1, y2, z, u0, u1, v0, v1, r, g, b, a);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PLAYER FACE RENDERING — optimized implementation of PlayerFaceRenderer
    // ═════════════════════════════════════════════════════════════════════════

    private static final int SKIN_HEAD_U = 8;
    private static final int SKIN_HEAD_V = 8;
    private static final int SKIN_HEAD_WIDTH = 8;
    private static final int SKIN_HEAD_HEIGHT = 8;
    private static final int SKIN_HAT_U = 40;
    private static final int SKIN_HAT_V = 8;
    private static final int SKIN_TEX_WIDTH = 64;
    private static final int SKIN_TEX_HEIGHT = 64;

    /**
     * Draw a player face (head + hat layer) - optimized replacement for
     * PlayerFaceRenderer.draw().
     * 
     * <p>
     * Usage:
     * 
     * <pre>{@code
     * // Instead of:
     * PlayerFaceRenderer.draw(guiGraphics, skinTexture, x, y, size);
     * 
     * // Use:
     * fakeGraphics.drawPlayerFace(skinTexture, x, y, size);
     * }</pre>
     * 
     * @param skinTexture the player skin texture
     * @param x           the x position
     * @param y           the y position
     * @param size        the size (width and height) of the face
     */
    public void drawPlayerFace(ResourceLocation skinTexture, int x, int y, int size) {
        drawPlayerFace(skinTexture, x, y, size, true, false);
    }

    /**
     * Draw a player face with options for hat layer and mirroring.
     * 
     * @param skinTexture the player skin texture
     * @param x           the x position
     * @param y           the y position
     * @param size        the size (width and height) of the face
     * @param drawHat     whether to draw the hat layer
     * @param upsideDown  whether to flip the face vertically
     */
    public void drawPlayerFace(ResourceLocation skinTexture, int x, int y, int size,
            boolean drawHat, boolean upsideDown) {
        int vOffset = SKIN_HEAD_V + (upsideDown ? SKIN_HEAD_HEIGHT : 0);
        int vHeight = SKIN_HEAD_HEIGHT * (upsideDown ? -1 : 1);

        // Draw head base layer
        blit(skinTexture, x, y, size, size,
                (float) SKIN_HEAD_U, (float) vOffset,
                SKIN_HEAD_WIDTH, vHeight,
                SKIN_TEX_WIDTH, SKIN_TEX_HEIGHT);

        // Draw hat overlay layer
        if (drawHat) {
            int hatVOffset = SKIN_HAT_V + (upsideDown ? SKIN_HEAD_HEIGHT : 0);
            int hatVHeight = SKIN_HEAD_HEIGHT * (upsideDown ? -1 : 1);

            // Note: The hat layer uses blend, handled by the blit action
            blit(skinTexture, x, y, size, size,
                    (float) SKIN_HAT_U, (float) hatVOffset,
                    SKIN_HEAD_WIDTH, hatVHeight,
                    SKIN_TEX_WIDTH, SKIN_TEX_HEIGHT);
        }
    }

    /**
     * Draw a player face from PlayerSkin - convenience method.
     * 
     * @param skin the player skin
     * @param x    the x position
     * @param y    the y position
     * @param size the size (width and height) of the face
     */
    public void drawPlayerFace(net.minecraft.client.resources.PlayerSkin skin, int x, int y, int size) {
        drawPlayerFace(skin.texture(), x, y, size);
    }
}