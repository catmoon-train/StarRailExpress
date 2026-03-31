package io.wifi.utils.client.betterrender;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * All methods delegate to the wrapped real GuiGraphics.
 * The drawString/drawCenteredString/drawWordWrap family is intercepted:
 * instead of flushing after every call, draw calls are batched and
 * submitted in a single GPU draw call at the end of Gui.render() by
 * GuiRenderMixin.
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
 * fakeGraphics.fill(0, 0, 100, 20, 0x80000000); // non-text: delegates normally
 *
 * // No flush() needed — GuiRenderMixin handles it automatically.
 * }</pre>
 */
public class FakeGuiGraphics {

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
    // ALL OTHER GuiGraphics METHODS — pure delegation to real instance
    // ═════════════════════════════════════════════════════════════════════════

    // ── Geometry ──────────────────────────────────────────────────────────────

    public PoseStack pose() {
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
        real.hLine(x1, x2, y, color);
    }

    public void hLine(RenderType rt, int x1, int x2, int y, int color) {
        real.hLine(rt, x1, x2, y, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        real.vLine(x, y1, y2, color);
    }

    public void vLine(RenderType rt, int x, int y1, int y2, int color) {
        real.vLine(rt, x, y1, y2, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        textRenderer.enqueueFill(real, x1, y1, x2, y2, 0, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        textRenderer.enqueueFill(real, x1, y1, x2, y2, z, color);
    }

    public void fill(RenderType rt, int x1, int y1, int x2, int y2, int color) {
        real.fill(rt, x1, y1, x2, y2, color);
    }

    public void fill(RenderType rt, int x1, int y1, int x2, int y2, int z, int color) {
        real.fill(rt, x1, y1, x2, y2, z, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int c1, int c2) {
        real.fillGradient(x1, y1, x2, y2, c1, c2);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int z, int c1, int c2) {
        real.fillGradient(x1, y1, x2, y2, z, c1, c2);
    }

    public void fillGradient(RenderType rt, int x1, int y1, int x2, int y2, int c1, int c2, int z) {
        real.fillGradient(rt, x1, y1, x2, y2, c1, c2, z);
    }

    public void fillRenderType(RenderType rt, int x1, int y1, int x2, int y2, int z) {
        real.fillRenderType(rt, x1, y1, x2, y2, z);
    }

    public void renderOutline(int x, int y, int w, int h, int color) {
        real.renderOutline(x, y, w, h, color);
    }

    // ── Scissor ───────────────────────────────────────────────────────────────

    public void enableScissor(int x1, int y1, int x2, int y2) {
        real.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        real.disableScissor();
    }

    public boolean containsPointInScissor(int x, int y) {
        return real.containsPointInScissor(x, y);
    }

    // ── Color & state ─────────────────────────────────────────────────────────

    public void setColor(float r, float g, float b, float a) {
        real.setColor(r, g, b, a);
    }

    // ── Blit / Sprites ────────────────────────────────────────────────────────

    public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        real.blit(x, y, z, w, h, sprite);
    }

    public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite, float r, float g, float b, float a) {
        real.blit(x, y, z, w, h, sprite, r, g, b, a);
    }

    // blit(ResourceLocation, int x, int y, int z, float u, float v, int w, int h,
    // int texW, int texH)
    public void blit(ResourceLocation loc, int x, int y, int z, float u, float v, int w, int h, int tw, int th) {
        real.blit(loc, x, y, z, u, v, w, h, tw, th);
    }

    // blit(ResourceLocation, int x, int y, int w, int h, float u, float v, int
    // regionW, int regionH, int texW, int texH)
    public void blit(ResourceLocation loc, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw,
            int th) {
        real.blit(loc, x, y, w, h, u, v, rw, rh, tw, th);
    }

    // blit(ResourceLocation, int x, int y, float u, float v, int w, int h, int
    // texW, int texH)
    public void blit(ResourceLocation loc, int x, int y, float u, float v, int w, int h, int tw, int th) {
        real.blit(loc, x, y, u, v, w, h, tw, th);
    }

    public void blitSprite(ResourceLocation loc, int x, int y, int w, int h) {
        real.blitSprite(loc, x, y, w, h);
    }

    public void blitSprite(ResourceLocation loc, int x, int y, int z, int w, int h) {
        real.blitSprite(loc, x, y, z, w, h);
    }

    public void blitSprite(ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int w, int h) {
        real.blitSprite(loc, tw, th, u, v, x, y, w, h);
    }

    public void blitSprite(ResourceLocation loc, int tw, int th, int u, int v, int x, int y, int z, int w, int h) {
        real.blitSprite(loc, tw, th, u, v, x, y, z, w, h);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    public void renderItem(ItemStack stack, int x, int y) {
        real.renderItem(stack, x, y);
    }

    public void renderItem(ItemStack stack, int x, int y, int seed) {
        real.renderItem(stack, x, y, seed);
    }

    public void renderItem(ItemStack stack, int x, int y, int seed, int z) {
        real.renderItem(stack, x, y, seed, z);
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        real.renderFakeItem(stack, x, y);
    }

    public void renderFakeItem(ItemStack stack, int x, int y, int seed) {
        real.renderFakeItem(stack, x, y, seed);
    }

    public void renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        real.renderItem(entity, stack, x, y, seed);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        real.renderItemDecorations(font, stack, x, y);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, @Nullable String label) {
        real.renderItemDecorations(font, stack, x, y, label);
    }

    // ── Tooltips ──────────────────────────────────────────────────────────────

    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        real.renderTooltip(font, stack, x, y);
    }

    public void renderTooltip(Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
        real.renderTooltip(font, lines, image, x, y);
    }

    public void renderTooltip(Font font, Component component, int x, int y) {
        real.renderTooltip(font, component, x, y);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        real.renderComponentTooltip(font, lines, x, y);
    }

    public void renderTooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        real.renderTooltip(font, lines, x, y);
    }

    public void renderComponentHoverEffect(Font font, @Nullable Style style, int x, int y) {
        real.renderComponentHoverEffect(font, style, x, y);
    }

    // ── Managed block (deprecated in vanilla but still used internally) ────────

    @Deprecated
    public void drawManaged(Runnable runnable) {
        real.drawManaged(runnable);
    }

    // ── innerBlit — intercepted, batched for tick-rate optimization ────────────

    public void innerBlit(ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
            float u0, float u1, float v0, float v1, float r, float g, float b, float a) {
        textRenderer.enqueueBlit(real, texture, x1, x2, y1, y2, z, u0, u1, v0, v1, r, g, b, a);
    }
}