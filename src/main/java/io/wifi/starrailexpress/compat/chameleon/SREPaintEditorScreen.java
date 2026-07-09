package io.wifi.starrailexpress.compat.chameleon;

import com.mecchachameleon.client.ClientPaintState;
import com.mecchachameleon.item.ChameleonArmor;
import com.mecchachameleon.network.SetCanvasPayload;
import com.mecchachameleon.paint.BodyCanvas;
import com.mecchachameleon.paint.BodyPart;
import com.mecchachameleon.paint.PaintAttachments;
import com.mecchachameleon.paint.SkinRegions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
public final class SREPaintEditorScreen extends Screen {
    private static final BodyPart[] MIRROR;
    private static final int[][] PANELS;
    private static final int ANA_W = 16;
    private static final int ANA_H = 32;
    private static final int SWATCH = 12;
    private static final int PAD = 6;
    private static final int MAX_HISTORY = 30;
    private static final int SV_SIZE = 50;
    private static final int HUE_W = 8;
    private static final int COL_W = 62;
    private static final float MAX_ZOOM = 8.0F;
    private static final float SPRAY_HUE_JITTER = 0.012F;
    private static final float SPRAY_SV_JITTER = 0.14F;
    private final int[] canvas;
    private final int enabledMask;
    private final Deque<int[]> undo = new ArrayDeque();
    private final Deque<int[]> redo = new ArrayDeque();
    private final Random rng = new Random();
    private Tool tool;
    private boolean mirror;
    private boolean anatomical;
    private boolean autosave;
    private int brushSize;
    private float opacity;
    private float pickHue;
    private float pickSat;
    private float pickVal;
    private boolean shaping;
    private int shapeX0;
    private int shapeY0;
    private int shapeX1;
    private int shapeY1;
    private int shapeButton;
    private float zoom;
    private float panX;
    private float panY;
    private int hueX;
    private Button toolButton;
    private Button mirrorButton;
    private Button viewButton;
    private Button autosaveButton;
    private int scale;
    private int dim;
    private int canvasX;
    private int canvasY;
    private int paletteX;
    private int titleY;
    private int panelX1;
    private int panelY1;
    private int panelX2;
    private int panelY2;

    private SREPaintEditorScreen(BodyCanvas source, int enabledMask) {
        super(Component.translatable("starrailexpress.paint_editor.title"));
        this.tool = SREPaintEditorScreen.Tool.BRUSH;
        this.brushSize = 1;
        this.opacity = 1.0F;
        this.pickSat = 1.0F;
        this.pickVal = 1.0F;
        this.zoom = 1.0F;
        this.canvas = (int[])source.pixels().clone();
        this.enabledMask = enabledMask;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int mask = ChameleonArmor.enabledMask(mc.player);
            if (mask == 0) {
                mc.player.displayClientMessage(Component.translatable("starrailexpress.paint_editor.equip_armor"), true);
            } else {
                mc.setScreen(new SREPaintEditorScreen((BodyCanvas)mc.player.getAttachedOrCreate(PaintAttachments.BODY_CANVAS), mask));
            }
        }
    }

    protected void init() {
        int fit = Math.min(this.width - 60, this.height - 130) / 64;
        this.scale = Mth.clamp(Math.min(fit, 3), 1, 3);
        this.dim = this.scale * 64;
        int bh = 18;
        int contentW = this.dim + 8 + 62;
        int contentH = 13 + this.dim + 11 + 3 * bh + 6;
        int originX = (this.width - contentW) / 2;
        int originY = (this.height - contentH) / 2;
        this.titleY = originY + 3;
        this.canvasX = originX;
        this.canvasY = originY + 13;
        this.paletteX = this.canvasX + this.dim + 8;
        this.hueX = this.paletteX + 50 + 4;
        this.syncPickerFromColor();
        this.panelX1 = originX - 6;
        this.panelY1 = originY - 6;
        this.panelX2 = originX + contentW + 6;
        this.panelY2 = originY + contentH + 6;
        int row1 = this.canvasY + this.dim + 11;
        int row2 = row1 + bh + 3;
        int row3 = row2 + bh + 3;
        int w3 = (contentW - 8) / 3;
        this.toolButton = Button.builder(this.toolLabel(), (b) -> this.cycleTool()).bounds(originX, row1, w3, bh).build();
        this.addRenderableWidget(this.toolButton);
        this.addRenderableWidget(Button.builder(Component.translatable("starrailexpress.paint_editor.undo"), (b) -> this.undo()).bounds(originX + w3 + 4, row1, w3, bh).build());
        this.addRenderableWidget(Button.builder(Component.translatable("starrailexpress.paint_editor.redo"), (b) -> this.redo()).bounds(originX + 2 * (w3 + 4), row1, w3, bh).build());
        this.mirrorButton = Button.builder(this.mirrorLabel(), (b) -> this.toggleMirror()).bounds(originX, row2, w3, bh).build();
        this.addRenderableWidget(this.mirrorButton);
        this.addRenderableWidget(Button.builder(Component.translatable("starrailexpress.paint_editor.clear"), (b) -> this.clearAll()).bounds(originX + w3 + 4, row2, w3, bh).build());
        this.addRenderableWidget(Button.builder(Component.translatable("starrailexpress.paint_editor.fit"), (b) -> this.resetView()).bounds(originX + 2 * (w3 + 4), row2, w3, bh).build());
        this.autosaveButton = Button.builder(this.autosaveLabel(), (b) -> this.toggleAutosave()).bounds(originX, row3, w3, bh).build();
        this.addRenderableWidget(this.autosaveButton);
        this.viewButton = Button.builder(this.viewLabel(), (b) -> this.toggleView()).bounds(originX + w3 + 4, row3, w3, bh).build();
        this.addRenderableWidget(this.viewButton);
        this.addRenderableWidget(Button.builder(Component.translatable("starrailexpress.paint_editor.done"), (b) -> this.done()).bounds(originX + 2 * (w3 + 4), row3, w3, bh).build());
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        g.fill(this.panelX1, this.panelY1, this.panelX2, this.panelY2, -435153896);
        g.renderOutline(this.panelX1, this.panelY1, this.panelX2 - this.panelX1, this.panelY2 - this.panelY1, -12959672);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(this.font, this.title, (this.panelX1 + this.panelX2) / 2, this.titleY, -1);
        g.fill(this.canvasX - 1, this.canvasY - 1, this.canvasX + this.dim + 1, this.canvasY + this.dim + 1, -14671840);
        g.enableScissor(this.canvasX, this.canvasY, this.canvasX + this.dim, this.canvasY + this.dim);
        if (this.anatomical) {
            this.renderAnatomicalView(g);
        } else {
            this.renderSkinView(g);
        }

        g.disableScissor();
        this.renderPicker(g);
        String bodyPrefix = this.anatomical ? Component.translatable("starrailexpress.paint_editor.hint.body_prefix").getString() : "";
        String mirrorSuffix = this.mirror ? " ⇋" : "";
        String hint = bodyPrefix + this.tool.label() + mirrorSuffix + "  " + Component.translatable("starrailexpress.paint_editor.hint", this.brushSize, Math.round(this.opacity * 100.0F)).getString();
        g.drawString(this.font, hint, this.canvasX, this.canvasY + this.dim + 2, -7826528);
    }

    private void renderSkinView(GuiGraphics g) {
        float cell = this.cell();
        int contentPx = Math.round(64.0F * cell);
        int baseX = this.canvasX + Math.round(this.panX);
        int baseY = this.canvasY + Math.round(this.panY);
        g.fill(baseX, baseY, baseX + contentPx, baseY + contentPx, -1);
        RenderSystem.enableBlend();
        g.setColor(1.0F, 1.0F, 1.0F, 0.5019608F);
        g.blit(DefaultPlayerSkin.getDefaultTexture(), baseX, baseY, contentPx, contentPx, 0.0F, 0.0F, 64, 64, 64, 64);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        for(int ty = 0; ty < 64; ++ty) {
            for(int tx = 0; tx < 64; ++tx) {
                int sx0 = this.canvasX + Math.round(this.panX + (float)tx * cell);
                int sy0 = this.canvasY + Math.round(this.panY + (float)ty * cell);
                int sx1 = this.canvasX + Math.round(this.panX + (float)(tx + 1) * cell);
                int sy1 = this.canvasY + Math.round(this.panY + (float)(ty + 1) * cell);
                if (this.isPaintable(tx, ty)) {
                    int c = this.canvas[SkinRegions.index(tx, ty)];
                    if ((c >>> 24 & 255) != 0) {
                        g.fill(sx0, sy0, sx1, sy1, -16777216 | c & 16777215);
                    }
                } else {
                    g.fill(sx0, sy0, sx1, sy1, -1726999528);
                }
            }
        }

        this.renderShapePreview(g, cell);
    }

    private void renderAnatomicalView(GuiGraphics g) {
        int cell = this.anaCell();
        int ox = this.anaOriginX();

        for(int[] p : PANELS) {
            for(int ly = 0; ly < p[3]; ++ly) {
                for(int lx = 0; lx < p[2]; ++lx) {
                    int sx = p[4] + lx;
                    int sy = p[5] + ly;
                    int px = ox + (p[0] + lx) * cell;
                    int py = this.canvasY + (p[1] + ly) * cell;
                    int color;
                    if (this.isPaintable(sx, sy)) {
                        int c = this.canvas[SkinRegions.index(sx, sy)];
                        color = (c >>> 24 & 255) == 0 ? -1 : -16777216 | c & 16777215;
                    } else {
                        color = -14012360;
                    }

                    g.fill(px, py, px + cell, py + cell, color);
                }
            }
        }

    }

    private int anaCell() {
        return Math.max(2, this.dim / 32);
    }

    private int anaOriginX() {
        return this.canvasX + (this.dim - 16 * this.anaCell()) / 2;
    }

    private void renderShapePreview(GuiGraphics g, float cell) {
        if (this.shaping && (this.tool == SREPaintEditorScreen.Tool.LINE || this.tool == SREPaintEditorScreen.Tool.RECT)) {
            int color = this.shapeButton == 1 ? -2130751408 : -1073741824 | ClientPaintState.currentColor() & 16777215;
            this.forEachShapeTexel((x, y) -> {
                if (this.inCanvas(x, y) && this.isPaintable(x, y)) {
                    int sx0 = this.canvasX + Math.round(this.panX + (float)x * cell);
                    int sy0 = this.canvasY + Math.round(this.panY + (float)y * cell);
                    int sx1 = this.canvasX + Math.round(this.panX + (float)(x + 1) * cell);
                    int sy1 = this.canvasY + Math.round(this.panY + (float)(y + 1) * cell);
                    g.fill(sx0, sy0, sx1, sy1, color);
                }
            });
        }
    }

    private void renderPicker(GuiGraphics g) {
        for(int yy = 0; yy < 50; ++yy) {
            float val = 1.0F - (float)yy / 50.0F;

            for(int xx = 0; xx < 50; ++xx) {
                float sat = (float)xx / 50.0F;
                g.fill(this.paletteX + xx, this.canvasY + yy, this.paletteX + xx + 1, this.canvasY + yy + 1, hsvToRgb(this.pickHue, sat, val));
            }
        }

        int ix = this.paletteX + Math.round(this.pickSat * 50.0F);
        int iy = this.canvasY + Math.round((1.0F - this.pickVal) * 50.0F);
        g.fill(ix - 1, iy - 1, ix + 1, iy + 1, -1);

        for(int yy = 0; yy < 50; ++yy) {
            g.fill(this.hueX, this.canvasY + yy, this.hueX + 8, this.canvasY + yy + 1, hsvToRgb((float)yy / 50.0F, 1.0F, 1.0F));
        }

        int hy = this.canvasY + Math.round(this.pickHue * 50.0F);
        g.fill(this.hueX - 1, hy, this.hueX + 8 + 1, hy + 1, -1);
        int swY = this.canvasY + 50 + 5;
        g.fill(this.paletteX - 1, swY - 1, this.paletteX + 62 + 1, swY + 15, -16777216);
        g.fill(this.paletteX, swY, this.paletteX + 62, swY + 14, ClientPaintState.currentColor());
        int px = this.paletteX;
        int py = swY + 18;

        for(int c : ClientPaintState.palette()) {
            g.fill(px, py, px + 12, py + 12, -16777216);
            g.fill(px + 1, py + 1, px + 12 - 1, py + 12 - 1, c);
            px += 14;
            if (px + 12 > this.paletteX + 62) {
                px = this.paletteX;
                py += 14;
            }
        }

    }

    private boolean isPaintable(int tx, int ty) {
        BodyPart part = SkinRegions.partAt(tx, ty);
        return part != null && (this.enabledMask & 1 << part.ordinal()) != 0;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.handlePicker(mouseX, mouseY)) {
            return true;
        } else if (this.pickPalette(mouseX, mouseY)) {
            return true;
        } else if (!this.anatomical && panHeld()) {
            return true;
        } else {
            int[] t = this.texelAt(mouseX, mouseY);
            int tx = t[0];
            int ty = t[1];
            if (this.inViewport(mouseX, mouseY) && this.inCanvas(tx, ty)) {
                if (button == 2) {
                    this.pickTexel(tx, ty);
                    return true;
                }

                if (this.tool == SREPaintEditorScreen.Tool.LINE || this.tool == SREPaintEditorScreen.Tool.RECT) {
                    this.shaping = true;
                    this.shapeX0 = tx;
                    this.shapeY0 = ty;
                    this.shapeX1 = tx;
                    this.shapeY1 = ty;
                    this.shapeButton = button;
                    return true;
                }

                if (this.isPaintable(tx, ty)) {
                    this.pushUndo();
                    if (button == 0 && this.tool == SREPaintEditorScreen.Tool.BUCKET) {
                        this.bucketFill(tx, ty);
                    } else {
                        this.brushStamp(tx, ty, button);
                    }

                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.anatomical && panHeld()) {
            this.panX += (float)dragX;
            this.panY += (float)dragY;
            this.clampPan();
            return true;
        } else if (this.handlePicker(mouseX, mouseY)) {
            return true;
        } else {
            int[] t = this.texelAt(mouseX, mouseY);
            int tx = t[0];
            int ty = t[1];
            if (this.shaping) {
                this.shapeX1 = tx;
                this.shapeY1 = ty;
                return true;
            } else if ((this.tool == SREPaintEditorScreen.Tool.BRUSH || this.tool == SREPaintEditorScreen.Tool.SPRAY) && this.inViewport(mouseX, mouseY) && this.inCanvas(tx, ty) && this.isPaintable(tx, ty)) {
                this.brushStamp(tx, ty, button);
                return true;
            } else {
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.shaping) {
            int[] t = this.texelAt(mouseX, mouseY);
            this.shaping = false;
            if (t[0] >= 0 && t[1] >= 0) {
                this.shapeX1 = t[0];
                this.shapeY1 = t[1];
                this.pushUndo();
                this.forEachShapeTexel((x, y) -> this.stamp(x, y, this.shapeButton));
            }

            return true;
        } else {
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    private void forEachShapeTexel(TexelOp op) {
        if (this.tool != SREPaintEditorScreen.Tool.RECT) {
            int x0 = this.shapeX0;
            int y0 = this.shapeY0;
            int dx = Math.abs(this.shapeX1 - x0);
            int dy = -Math.abs(this.shapeY1 - y0);
            int sx = x0 < this.shapeX1 ? 1 : -1;
            int sy = y0 < this.shapeY1 ? 1 : -1;
            int err = dx + dy;

            while(true) {
                op.apply(x0, y0);
                if (x0 == this.shapeX1 && y0 == this.shapeY1) {
                    return;
                }

                int e2 = 2 * err;
                if (e2 >= dy) {
                    err += dy;
                    x0 += sx;
                }

                if (e2 <= dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        } else {
            int x0 = Math.min(this.shapeX0, this.shapeX1);
            int x1 = Math.max(this.shapeX0, this.shapeX1);
            int y0 = Math.min(this.shapeY0, this.shapeY1);
            int y1 = Math.max(this.shapeY0, this.shapeY1);

            for(int x = x0; x <= x1; ++x) {
                op.apply(x, y0);
                op.apply(x, y1);
            }

            for(int y = y0; y <= y1; ++y) {
                op.apply(x0, y);
                op.apply(x1, y);
            }

        }
    }

    private boolean handlePicker(double mx, double my) {
        if (mx >= (double)this.paletteX && mx < (double)(this.paletteX + 50) && my >= (double)this.canvasY && my < (double)(this.canvasY + 50)) {
            this.pickSat = (float)Mth.clamp((mx - (double)this.paletteX) / (double)50.0F, (double)0.0F, (double)1.0F);
            this.pickVal = (float)Mth.clamp((double)1.0F - (my - (double)this.canvasY) / (double)50.0F, (double)0.0F, (double)1.0F);
            this.updateColorFromPicker();
            return true;
        } else if (mx >= (double)this.hueX && mx < (double)(this.hueX + 8) && my >= (double)this.canvasY && my < (double)(this.canvasY + 50)) {
            this.pickHue = (float)Mth.clamp((my - (double)this.canvasY) / (double)50.0F, (double)0.0F, (double)1.0F);
            this.updateColorFromPicker();
            return true;
        } else {
            return false;
        }
    }

    private void updateColorFromPicker() {
        ClientPaintState.setCurrentColor(hsvToRgb(this.pickHue, this.pickSat, this.pickVal));
    }

    private void syncPickerFromColor() {
        float[] hsv = rgbToHsv(ClientPaintState.currentColor());
        this.pickHue = hsv[0];
        this.pickSat = hsv[1];
        this.pickVal = hsv[2];
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int)Math.floor((double)(h * 6.0F)) % 6;
        if (i < 0) {
            i += 6;
        }

        float f = h * 6.0F - (float)Math.floor((double)(h * 6.0F));
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (i) {
            case 0:
                r = v;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = v;
                b = p;
                break;
            case 2:
                r = p;
                g = v;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = v;
                break;
            case 4:
                r = t;
                g = p;
                b = v;
                break;
            default:
                r = v;
                g = p;
                b = q;
        }

        return -16777216 | Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = (float)(rgb >> 16 & 255) / 255.0F;
        float g = (float)(rgb >> 8 & 255) / 255.0F;
        float b = (float)(rgb & 255) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float s = max == 0.0F ? 0.0F : d / max;
        float h = 0.0F;
        if (d != 0.0F) {
            if (max == r) {
                h = (g - b) / d % 6.0F;
            } else if (max == g) {
                h = (b - r) / d + 2.0F;
            } else {
                h = (r - g) / d + 4.0F;
            }

            h /= 6.0F;
            if (h < 0.0F) {
                ++h;
            }
        }

        return new float[]{h, s, max};
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!this.inViewport(mx, my)) {
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        } else {
            if (ctrlDown() && !this.anatomical) {
                this.zoomAt(mx, my, scrollY > (double)0.0F ? 1.2F : 0.8333333F);
            } else if (shiftDown()) {
                this.opacity = Mth.clamp(this.opacity + (scrollY > (double)0.0F ? 0.1F : -0.1F), 0.1F, 1.0F);
            } else {
                this.brushSize = Mth.clamp(this.brushSize + (scrollY > (double)0.0F ? 1 : -1), 1, 6);
            }

            return true;
        }
    }

    private void zoomAt(double mx, double my, float factor) {
        float old = this.cell();
        this.zoom = Mth.clamp(this.zoom * factor, 1.0F, 8.0F);
        float now = this.cell();
        double tx = (mx - (double)this.canvasX - (double)this.panX) / (double)old;
        double ty = (my - (double)this.canvasY - (double)this.panY) / (double)old;
        this.panX = (float)(mx - (double)this.canvasX - tx * (double)now);
        this.panY = (float)(my - (double)this.canvasY - ty * (double)now);
        this.clampPan();
    }

    private void clampPan() {
        float content = 64.0F * this.cell();
        float minX = Math.min(0.0F, (float)this.dim - content);
        float minY = Math.min(0.0F, (float)this.dim - content);
        this.panX = Mth.clamp(this.panX, minX, 0.0F);
        this.panY = Mth.clamp(this.panY, minY, 0.0F);
    }

    private void resetView() {
        this.zoom = 1.0F;
        this.panX = 0.0F;
        this.panY = 0.0F;
    }

    private float cell() {
        return (float)this.scale * this.zoom;
    }

    private boolean inViewport(double mx, double my) {
        return mx >= (double)this.canvasX && mx < (double)(this.canvasX + this.dim) && my >= (double)this.canvasY && my < (double)(this.canvasY + this.dim);
    }

    private static boolean shiftDown() {
        long w = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(w, 340) || InputConstants.isKeyDown(w, 344);
    }

    private static boolean ctrlDown() {
        long w = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(w, 341) || InputConstants.isKeyDown(w, 345);
    }

    private static boolean panHeld() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 32);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown()) {
            if (keyCode == 90) {
                this.undo();
                return true;
            }

            if (keyCode == 89) {
                this.redo();
                return true;
            }
        }

        if (keyCode == 77) {
            this.toggleMirror();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private void pickTexel(int tx, int ty) {
        int c = this.canvas[SkinRegions.index(tx, ty)];
        ClientPaintState.setCurrentColor((c >>> 24 & 255) == 0 ? -1 : c);
        this.syncPickerFromColor();
    }

    private void brushStamp(int tx, int ty, int button) {
        int rad = this.brushSize - 1;

        for(int dy = -rad; dy <= rad; ++dy) {
            for(int dx = -rad; dx <= rad; ++dx) {
                this.stamp(tx + dx, ty + dy, button);
            }
        }

    }

    private void stamp(int x, int y, int button) {
        this.paintTexel(x, y, button);
        if (this.mirror) {
            int[] m = this.mirrorOf(x, y);
            if (m != null) {
                this.paintTexel(m[0], m[1], button);
            }
        }

    }

    private void paintTexel(int x, int y, int button) {
        if (this.inCanvas(x, y) && this.isPaintable(x, y)) {
            int i = SkinRegions.index(x, y);
            if (button == 1) {
                this.canvas[i] = 0;
            } else if (this.tool == SREPaintEditorScreen.Tool.SPRAY) {
                if (this.rng.nextFloat() < 0.55F) {
                    this.canvas[i] = this.blend(this.canvas[i], this.sprayColor());
                }
            } else {
                this.canvas[i] = this.blend(this.canvas[i], ClientPaintState.currentColor());
            }

        }
    }

    private int[] mirrorOf(int x, int y) {
        BodyPart part = SkinRegions.partAt(x, y);
        if (part == null) {
            return null;
        } else {
            BodyPart pair = MIRROR[part.ordinal()];
            int[] rs = SkinRegions.region(part);
            int[] rd = SkinRegions.region(pair);
            int lx = x - rs[0];
            int ly = y - rs[1];
            int mx = rd[0] + (rd[2] - 1 - lx);
            int my = rd[1] + ly;
            return new int[]{mx, my};
        }
    }

    private int sprayColor() {
        float[] hsv = rgbToHsv(ClientPaintState.currentColor());
        float h = hsv[0] + (this.rng.nextFloat() * 2.0F - 1.0F) * 0.012F;
        float s = Mth.clamp(hsv[1] + (this.rng.nextFloat() * 2.0F - 1.0F) * 0.14F, 0.0F, 1.0F);
        float v = Mth.clamp(hsv[2] + (this.rng.nextFloat() * 2.0F - 1.0F) * 0.14F, 0.0F, 1.0F);
        h = (h % 1.0F + 1.0F) % 1.0F;
        return hsvToRgb(h, s, v);
    }

    private int blend(int existing, int srcArgb) {
        int base = (existing >>> 24 & 255) == 0 ? 16777215 : existing & 16777215;
        int src = srcArgb & 16777215;
        int r = Math.round((float)(src >> 16 & 255) * this.opacity + (float)(base >> 16 & 255) * (1.0F - this.opacity));
        int g = Math.round((float)(src >> 8 & 255) * this.opacity + (float)(base >> 8 & 255) * (1.0F - this.opacity));
        int b = Math.round((float)(src & 255) * this.opacity + (float)(base & 255) * (1.0F - this.opacity));
        return -16777216 | r << 16 | g << 8 | b;
    }

    private void bucketFill(int tx, int ty) {
        int target = this.canvas[SkinRegions.index(tx, ty)];
        int replacement = ClientPaintState.currentColor();
        if (target != replacement) {
            Deque<int[]> stack = new ArrayDeque();
            stack.push(new int[]{tx, ty});

            while(!stack.isEmpty()) {
                int[] p = (int[])stack.pop();
                int x = p[0];
                int y = p[1];
                if (this.inCanvas(x, y) && this.isPaintable(x, y)) {
                    int i = SkinRegions.index(x, y);
                    if (this.canvas[i] == target) {
                        this.canvas[i] = replacement;
                        stack.push(new int[]{x + 1, y});
                        stack.push(new int[]{x - 1, y});
                        stack.push(new int[]{x, y + 1});
                        stack.push(new int[]{x, y - 1});
                    }
                }
            }

        }
    }

    private boolean pickPalette(double mx, double my) {
        int px = this.paletteX;
        int py = this.canvasY + 50 + 5 + 18;

        for(int c : ClientPaintState.palette()) {
            if (mx >= (double)px && mx < (double)(px + 12) && my >= (double)py && my < (double)(py + 12)) {
                ClientPaintState.setCurrentColor(c);
                this.syncPickerFromColor();
                return true;
            }

            px += 14;
            if (px + 12 > this.paletteX + 62) {
                px = this.paletteX;
                py += 14;
            }
        }

        return false;
    }

    private int[] texelAt(double mx, double my) {
        if (this.anatomical) {
            int cell = this.anaCell();
            int ox = this.anaOriginX();
            int lx = (int)Math.floor((mx - (double)ox) / (double)cell);
            int ly = (int)Math.floor((my - (double)this.canvasY) / (double)cell);

            for(int[] p : PANELS) {
                if (lx >= p[0] && lx < p[0] + p[2] && ly >= p[1] && ly < p[1] + p[3]) {
                    return new int[]{p[4] + (lx - p[0]), p[5] + (ly - p[1])};
                }
            }

            return new int[]{-1, -1};
        } else {
            return new int[]{this.texelX(mx), this.texelY(my)};
        }
    }

    private int texelX(double mx) {
        return (int)Math.floor((mx - (double)this.canvasX - (double)this.panX) / (double)this.cell());
    }

    private int texelY(double my) {
        return (int)Math.floor((my - (double)this.canvasY - (double)this.panY) / (double)this.cell());
    }

    private boolean inCanvas(int tx, int ty) {
        return tx >= 0 && tx < 64 && ty >= 0 && ty < 64;
    }

    private void cycleTool() {
        this.tool = this.tool.next();
        this.toolButton.setMessage(this.toolLabel());
    }

    private Component toolLabel() {
        return Component.translatable("starrailexpress.paint_editor.tool." + this.tool.key());
    }

    private void toggleMirror() {
        this.mirror = !this.mirror;
        if (this.mirrorButton != null) {
            this.mirrorButton.setMessage(this.mirrorLabel());
        }

    }

    private Component mirrorLabel() {
        return Component.translatable(this.mirror ? "starrailexpress.paint_editor.mirror_on" : "starrailexpress.paint_editor.mirror_off");
    }

    private void toggleView() {
        this.anatomical = !this.anatomical;
        this.resetView();
        if (this.viewButton != null) {
            this.viewButton.setMessage(this.viewLabel());
        }

    }

    private Component viewLabel() {
        return Component.translatable(this.anatomical ? "starrailexpress.paint_editor.view_body" : "starrailexpress.paint_editor.view_skin");
    }

    private void toggleAutosave() {
        this.autosave = !this.autosave;
        this.autosaveButton.setMessage(this.autosaveLabel());
    }

    private Component autosaveLabel() {
        return Component.translatable(this.autosave ? "starrailexpress.paint_editor.auto_on" : "starrailexpress.paint_editor.auto_off");
    }

    private void clearAll() {
        this.pushUndo();

        for(int ty = 0; ty < 64; ++ty) {
            for(int tx = 0; tx < 64; ++tx) {
                if (this.isPaintable(tx, ty)) {
                    this.canvas[SkinRegions.index(tx, ty)] = 0;
                }
            }
        }

    }

    private void pushUndo() {
        this.undo.push((int[])this.canvas.clone());

        while(this.undo.size() > 30) {
            this.undo.removeLast();
        }

        this.redo.clear();
    }

    private void undo() {
        if (!this.undo.isEmpty()) {
            this.redo.push((int[])this.canvas.clone());
            System.arraycopy(this.undo.pop(), 0, this.canvas, 0, this.canvas.length);
        }
    }

    private void redo() {
        if (!this.redo.isEmpty()) {
            this.undo.push((int[])this.canvas.clone());
            System.arraycopy(this.redo.pop(), 0, this.canvas, 0, this.canvas.length);
        }
    }

    private void save() {
        BodyCanvas result = new BodyCanvas((int[])this.canvas.clone());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setAttached(PaintAttachments.BODY_CANVAS, result);
        }

        ClientPlayNetworking.send(new SetCanvasPayload(result));
    }

    private void done() {
        this.save();
        super.onClose();
    }

    public void onClose() {
        if (this.autosave) {
            this.save();
        }

        super.onClose();
    }

    public boolean isPauseScreen() {
        return false;
    }

    static {
        MIRROR = new BodyPart[BodyPart.VALUES.length];
        MIRROR[BodyPart.HEAD.ordinal()] = BodyPart.HEAD;
        MIRROR[BodyPart.BODY.ordinal()] = BodyPart.BODY;
        MIRROR[BodyPart.LEFT_ARM.ordinal()] = BodyPart.RIGHT_ARM;
        MIRROR[BodyPart.RIGHT_ARM.ordinal()] = BodyPart.LEFT_ARM;
        MIRROR[BodyPart.LEFT_LEG.ordinal()] = BodyPart.RIGHT_LEG;
        MIRROR[BodyPart.RIGHT_LEG.ordinal()] = BodyPart.LEFT_LEG;
        MIRROR[BodyPart.LEFT_LEG_LOWER.ordinal()] = BodyPart.RIGHT_LEG_LOWER;
        MIRROR[BodyPart.RIGHT_LEG_LOWER.ordinal()] = BodyPart.LEFT_LEG_LOWER;
        PANELS = new int[][]{{4, 0, 8, 8, 8, 8}, {4, 8, 8, 12, 20, 20}, {0, 8, 4, 12, 44, 20}, {12, 8, 4, 12, 36, 52}, {4, 20, 4, 12, 4, 20}, {8, 20, 4, 12, 20, 52}};
    }

    private static enum Tool {
        BRUSH,
        BUCKET,
        LINE,
        RECT,
        SPRAY;

        private Tool() {
        }

        Tool next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        String label() {
            return Component.translatable("starrailexpress.paint_editor.tool." + this.key()).getString();
        }

        String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private interface TexelOp {
        void apply(int var1, int var2);
    }
}