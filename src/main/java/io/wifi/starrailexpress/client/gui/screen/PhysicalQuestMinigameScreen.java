package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

public class PhysicalQuestMinigameScreen extends Screen {
    public enum Kind {
        EXTINGUISH("extinguish_fire"),
        PACHINKO("pachinko"),
        MIX_DRINK("mix_drink"),
        BALLOON_PUMP("balloon_pump"),
        THROW_BALL("throw_ball");

        final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    private static final int W = 430;
    private static final int H = 270;
    private static final int HEADER_H = 24;
    private static final int WHITE = MinigameUI.WHITE;
    private static final int YELLOW = 0xFFFFD166;
    private static final int GREEN = MinigameUI.GREEN;
    private static final int INTRO_TICKS = 7;

    private final Runnable onSuccess;
    private final Kind kind;
    private int ticks;
    private int introTicks;
    private int successTicks = -1;

    // --- 灭火 ---
    private float fireSize = 1f;
    private boolean extinguisherHeld;
    private double lastMouseX, lastMouseY;

    // --- 弹球 ---
    private float plunger;
    private boolean plungerHeld;
    private boolean ballActive;
    private float bx, by, bvx, bvy;
    private int score;
    // 阻碍物布局：相对于弹球区域中心的列偏移
    private static final int[][] PEGS = {
            {0, 0},
            {-1, 1}, {1, 1},
            {-2, 2}, {0, 2}, {2, 2},
            {-3, 3}, {-1, 3}, {1, 3}, {3, 3},
    };

    // --- 调酒 ---
    private final List<DragItem> ingredients = new ArrayList<>();
    private DragItem dragging;
    private boolean cupHeld;
    private float cupX, cupY;
    private boolean cupLoaded;
    private int shakeCount;
    private float lastShakeY;
    private boolean pouring;
    private float pourProgress;

    // --- 打气 ---
    private float balloon = 0.12f;
    private boolean pumpHeld;
    private float pumpHandleY;
    private static final float PUMP_TOP = 78;
    private static final float PUMP_BOT = 186;

    // --- 投球 ---
    private boolean birdHeld;
    private boolean birdFlying;
    private float birdX, birdY, birdVelX, birdVelY;
    private int stableInBucket;
    private float dragStartX, dragStartY;

    public PhysicalQuestMinigameScreen(BlockPos pos, Runnable onSuccess, Kind kind) {
        super(Component.translatable("minigame.starrailexpress." + kind.id));
        this.onSuccess = onSuccess;
        this.kind = kind;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        ingredients.clear();
        dragging = null;
        cupHeld = false;
        cupLoaded = false;
        shakeCount = 0;
        lastShakeY = 0;
        pouring = false;
        pourProgress = 0;
        fireSize = 1f;
        score = 0;
        ballActive = false;
        plunger = 0;
        balloon = 0.12f;
        pumpHandleY = t + PUMP_TOP + 10;
        birdHeld = false;
        birdFlying = false;
        stableInBucket = 0;
        introTicks = 0;

        ingredients.add(new DragItem(new ItemStack(Items.ICE), l + 28, t + 210));
        ingredients.add(new DragItem(new ItemStack(Items.KELP), l + 78, t + 210));
        ingredients.add(new DragItem(new ItemStack(Items.GOLDEN_APPLE), l + 128, t + 210));

        cupX = l + 260;
        cupY = t + 100;
        birdX = l + W - 80;
        birdY = t + 100;
    }

    private int left() { return (width - W) / 2; }
    private int top()  { return (height - H) / 2; }

    // ==================== tick ====================

    @Override
    public void tick() {
        ticks++;
        if (introTicks < INTRO_TICKS) introTicks++;
        if (successTicks >= 0 && ++successTicks > 14) {
            minecraft.setScreen(null);
            return;
        }
        switch (kind) {
            case PACHINKO -> tickPachinko();
            case MIX_DRINK -> { if (pouring && (pourProgress += 0.04f) >= 1f) complete(); }
            case THROW_BALL -> tickThrowBall();
            default -> {}
        }
    }

    private void tickPachinko() {
        if (!ballActive) return;
        bx += bvx; by += bvy; bvy += 0.25f;
        int l = left(), t = top();
        int pegCx = l + 140;
        for (int[] peg : PEGS) {
            float px = pegCx + peg[0] * 48;
            float py = t + 72 + peg[1] * 40;
            float d = dist(bx, by, px, py);
            if (d < 16) { bvx += (bx - px) * 0.04f; bvy = -Math.abs(bvy) * 0.55f; }
        }
        if (bx < l + 15 || bx > l + W - 60) bvx *= -0.7f;
        if (bx < l + 15) bx = l + 16;
        if (by > t + 225) {
            int slot = Mth.clamp((int)((bx - (l + 40)) / 58), 0, 5);
            score += new int[]{5,10,15,15,10,5}[slot];
            ballActive = false;
            if (score >= 20) complete();
        }
    }

    private void tickThrowBall() {
        if (!birdFlying) return;
        birdX += birdVelX; birdY += birdVelY;
        birdVelY += 0.28f; birdVelX *= 0.996f; birdVelY *= 0.996f;
        int l = left(), t = top();
        boolean inBucket = birdX > l + 5 && birdX < l + 135 && birdY > t + 80 && birdY < t + 210;
        if (inBucket) {
            birdVelX *= 0.82f; birdVelY *= -0.35f;
            stableInBucket = Math.abs(birdVelX) + Math.abs(birdVelY) < 0.8f ? stableInBucket + 1 : 0;
            if (stableInBucket > 35) complete();
        }
        if (birdY > t + H + 50 || birdX < l - 60 || birdX > l + W + 60) {
            birdFlying = false; birdX = l + W - 80; birdY = t + 100;
        }
    }

    // ==================== render ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();

        float intro = MinigameUI.easeOut((introTicks + partialTick) / INTRO_TICKS);
        float scale = 0.82f + 0.18f * intro;
        float cx = width / 2f, cy = t + H / 2f;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0);

        MinigameUI.panel(g, l, t, l + W, t + H, HEADER_H);
        g.drawCenteredString(font, title, width / 2, t + 8, WHITE);

        switch (kind) {
            case EXTINGUISH -> renderExtinguish(g, l, t, mouseX, mouseY);
            case PACHINKO -> renderPachinko(g, l, t);
            case MIX_DRINK -> renderMixDrink(g, l, t);
            case BALLOON_PUMP -> renderBalloon(g, l, t);
            case THROW_BALL -> renderThrowBall(g, l, t, mouseX, mouseY);
        }
        g.pose().popPose();

        // success overlay (outside scale transform)
        if (successTicks >= 0) {
            g.fill(l, t, l + W, t + H, 0xAA102414);
            g.drawCenteredString(font, Component.translatable("minigame.starrailexpress.common.done"),
                    width / 2, t + 128, GREEN);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ==================== 灭火 ====================

    private void renderExtinguish(GuiGraphics g, int l, int t, int mx, int my) {
        int fcx = l + 160, fcy = t + 145;
        float flick = 1f + 0.08f * Mth.sin(ticks * 0.5f);
        float base = fireSize * flick;
        MinigameUI.filledCircle(g, fcx, fcy - (int)(6*base), (int)(52*base), 0xFFFF6020);
        MinigameUI.filledCircle(g, fcx, fcy, (int)(36*base), 0xFFFF9020);
        MinigameUI.filledCircle(g, fcx, fcy + (int)(6*base), (int)(20*base), 0xFFFFD040);
        MinigameUI.filledCircle(g, fcx, fcy + (int)(12*base), (int)(9*base), 0xFFFFF5E0);

        // 灭火器 5x 放大
        ItemStack ext = new ItemStack(ModItems.EXTINGUISHER);
        int exX = l + 326, exY = t + 96;
        g.pose().pushPose();
        g.pose().translate(exX + 8, exY + 8, 0);
        g.pose().scale(5f, 5f, 1f);
        g.renderItem(ext, -8, -8);
        g.pose().popPose();
        g.drawString(font, Component.translatable("item.noellesroles.extinguisher"), exX - 4, exY + 52, 0x99AABBCC);

        if (extinguisherHeld) {
            g.pose().pushPose();
            g.pose().translate(mx, my, 0);
            g.pose().scale(5f, 5f, 1f);
            g.renderItem(ext, -8, -8);
            g.pose().popPose();
            for (int i = 0; i < 6; i++) {
                float pct = i / 5f;
                int sprayX = (int)(mx + (fcx - mx) * pct + Mth.sin(ticks * 0.3f + i) * 8);
                int sprayY = (int)(my + (fcy - my) * pct + Mth.cos(ticks * 0.3f + i) * 8);
                MinigameUI.filledCircle(g, sprayX, sprayY, 5 - (int)(pct * 3),
                        ((int)(0x60*(1f - pct*0.5f)) << 24) | 0xDDEEFF);
            }
        }
        MinigameUI.progressBar(g, l + 100, t + 232, 230, 10, fireSize, 0xFFFF6B22);
        g.drawString(font, Component.translatable("minigame.starrailexpress.extinguish.hint"),
                l + 100, t + 250, 0xFF8899AA);
    }

    // ==================== 弹球 ====================

    private void renderPachinko(GuiGraphics g, int l, int t) {
        g.drawString(font, score + " / 20", l + 20, t + 40, YELLOW);
        int pegCx = l + 140; // 阻碍物区域中心在左侧
        // 画弧线
        drawArcLine(g, pegCx - 48, t + 110, pegCx + 48, t + 110, 16, 0x4488AACC);
        drawArcLine(g, pegCx - 96, t + 150, pegCx + 96, t + 150, 20, 0x4488AACC);
        drawArcLine(g, pegCx - 144, t + 190, pegCx + 144, t + 190, 24, 0x4488AACC);
        // 阻碍物
        for (int[] peg : PEGS) {
            int px = pegCx + peg[0] * 48;
            int py = t + 72 + peg[1] * 40;
            MinigameUI.filledCircle(g, px, py, 8, 0xFF526170);
            MinigameUI.ring(g, px, py, 8, 1, 0xFF7A8FA0);
        }
        // 得分区
        int[] scores = {5,10,15,15,10,5};
        for (int i = 0; i < scores.length; i++) {
            int sx = l + 30 + i * 58;
            int sy = t + 238;
            g.fill(sx, sy, sx + 48, sy + 20, 0xCC283040);
            g.drawCenteredString(font, Component.literal(String.valueOf(scores[i])), sx + 24, sy + 6, YELLOW);
        }
        // 发射管（右侧，不重叠）
        int tubeX = l + W - 68;
        g.fill(tubeX - 5, t + 56, tubeX + 5, t + 215, 0xFF3C4652);
        g.renderOutline(tubeX - 5, t + 56, 10, 159, 0xFF5A6A80);
        // 拉杆
        int handleTop = (int)(t + 80 + plunger * 100);
        g.fill(tubeX - 4, handleTop, tubeX + 4, handleTop + 26, 0xFFB58B4A);
        g.fill(tubeX - 9, handleTop - 3, tubeX + 9, handleTop, 0xFFD4A85C);
        if (!ballActive) {
            MinigameUI.filledCircle(g, tubeX, t + 68, 10, 0xFFE8F0F8);
            MinigameUI.ring(g, tubeX, t + 68, 10, 1, 0xFFC0D0E0);
        } else {
            MinigameUI.filledCircle(g, Math.round(bx), Math.round(by), 10, 0xFFE8F0F8);
        }
    }

    private void drawArcLine(GuiGraphics g, int x1, int y1, int x2, int y2, int sag, int color) {
        int steps = 20;
        int midX = (x1 + x2) / 2;
        int midY = y1 + sag;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float)steps;
            float t1 = (i + 1) / (float)steps;
            float t0inv = 1f - t0, t1inv = 1f - t1;
            int ax = Math.round(t0inv*t0inv*x1 + 2*t0inv*t0*midX + t0*t0*x2);
            int ay = Math.round(t0inv*t0inv*y1 + 2*t0inv*t0*midY + t0*t0*y2);
            int bx = Math.round(t1inv*t1inv*x1 + 2*t1inv*t1*midX + t1*t1*x2);
            int by = Math.round(t1inv*t1inv*y1 + 2*t1inv*t1*midY + t1*t1*y2);
            g.fill(Math.min(ax,bx), Math.min(ay,by), Math.max(ax,bx)+1, Math.max(ay,by)+1, color);
        }
    }

    // ==================== 调酒 ====================

    private void renderMixDrink(GuiGraphics g, int l, int t) {
        g.drawString(font, Component.translatable("minigame.starrailexpress.mix_drink.desk"),
                l + 30, t + 195, 0xFF8899AA);

        for (DragItem item : ingredients) {
            if (!item.used) {
                g.renderItem(item.stack, Math.round(item.x), Math.round(item.y));
                g.renderItemDecorations(font, item.stack, Math.round(item.x), Math.round(item.y));
            }
        }
        // 调酒杯（可拖拽跟随鼠标）
        int cX = (int) cupX, cY = (int) cupY;
        drawCocktailGlass(g, cX, cY, shakeCount >= 3 ? 0x88FFFF60 : 0x00000000);
        if (cupHeld) g.renderOutline(cX - 2, cY - 2, 52, 62, 0x66FFFFFF);

        // 高脚杯
        int gX = l + 345, gY = t + 116;
        drawWineGlass(g, gX, gY);
        if (pouring) {
            float p = pourProgress;
            int streamH = Math.round(p * 36);
            g.fill(cX + 22, cY + 58, cX + 26, cY + 58 + streamH, 0xCCFFD040);
            int liquidH = Math.round(p * 22);
            g.fill(gX + 10, gY + 44 - liquidH, gX + 22, gY + 44, 0xCCFFD040);
        }
        g.drawCenteredString(font, shakeCount + " / 3", cX + 22, cY - 14, YELLOW);

        if (dragging != null) {
            g.renderItem(dragging.stack, (int) lastMouseX - 8, (int) lastMouseY - 8);
        }
    }

    private void drawCocktailGlass(GuiGraphics g, int x, int y, int liquid) {
        g.fill(x, y, x + 48, y + 4, 0xFFD0D8E0);
        g.fill(x + 2, y + 4, x + 46, y + 8, 0xFFD0D8E0);
        for (int row = 0; row < 46; row++) {
            int inset = 2 + row * 10 / 46;
            g.fill(x + inset, y + 8 + row, x + 48 - inset, y + 8 + row + 1, 0x44D0D8E0);
        }
        g.fill(x + 18, y + 54, x + 30, y + 58, 0xFFB0B8C0);
        if (liquid != 0) {
            // 液体限制在杯壁内
            for (int row = 20; row < 40; row++) {
                int inset = 2 + row * 10 / 46 + 2;
                g.fill(x + inset, y + 8 + row, x + 48 - inset, y + 8 + row + 1, liquid);
            }
        }
    }

    private void drawWineGlass(GuiGraphics g, int x, int y) {
        g.fill(x + 10, y, x + 22, y + 3, 0xFFE8EEF8);
        g.fill(x + 12, y + 3, x + 20, y + 5, 0xFFE8EEF8);
        for (int row = 0; row < 34; row++) {
            int inset = 12 - row * 5 / 34;
            g.fill(x + inset, y + 5 + row, x + 32 - inset, y + 5 + row + 1, 0x33E8EEF8);
        }
        g.fill(x + 14, y + 39, x + 18, y + 44, 0xFFD0D8E0);
        g.fill(x + 10, y + 44, x + 22, y + 46, 0xFFD0D8E0);
    }

    // ==================== 打气 ====================

    private void renderBalloon(GuiGraphics g, int l, int t) {
        int bcx = l + 145, bcy = t + 140;
        // 虚线目标框
        for (int angle = 0; angle < 360; angle += 15) {
            float rad = (float) Math.toRadians(angle);
            int dx = Math.round(48 * Mth.cos(rad));
            int dy = Math.round(48 * Mth.sin(rad));
            int dx2 = Math.round(48 * Mth.cos((float) Math.toRadians(angle + 8)));
            int dy2 = Math.round(48 * Mth.sin((float) Math.toRadians(angle + 8)));
            if (angle % 30 == 0) {
                g.fill(bcx + dx - 1, bcy + dy - 1, bcx + dx2 + 1, bcy + dy2 + 1, 0x88FF8DA0);
            }
        }
        // 气球
        float r = 48 * balloon;
        float pulse = 1f + 0.03f * Mth.sin(ticks * 0.3f);
        int cr = Math.round(r * pulse);
        MinigameUI.filledCircle(g, bcx, bcy, cr, 0xFFFF5C8A);
        MinigameUI.filledCircle(g, bcx - (int)(cr*0.28f), bcy - (int)(cr*0.28f), (int)(cr*0.2f), 0x44FFFFFF);
        g.fill(bcx - 4, bcy + cr, bcx + 4, bcy + cr + 10, 0xFF9A3A5A);

        // 打气筒：正T把手 + 稍矮矩形气筒
        int pTop = (int)(t + PUMP_TOP);
        int pBot = (int)(t + PUMP_BOT);
        int pX = l + 248;
        int pW = 40;
        int pMid = pX + pW / 2;
        // 气筒身（矮矩形）
        g.fill(pX, pTop + 28, pX + pW, pBot, 0xFF3A4452);
        g.renderOutline(pX, pTop + 28, pW, pBot - pTop - 28, 0xFF5A6A80);
        // T把手
        int hY = (int) pumpHandleY;
        int barW = pW + 16;
        g.fill(pMid - barW/2, hY, pMid + barW/2, hY + 10, 0xFFD8E0E8);
        // 连杆（T的竖线）
        g.fill(pMid - 3, hY + 10, pMid + 3, pTop + 28, 0xFFB8C0C8);
        // 气管
        g.fill(pX - 14, pTop + 44, pX, pTop + 50, 0xFF6A7A90);

        MinigameUI.progressBar(g, l + 90, t + 232, 220, 10, balloon, 0xFFFF5C8A);
        g.drawString(font, Component.translatable("minigame.starrailexpress.balloon.hint"),
                l + 90, t + 250, 0xFF8899AA);
    }

    // ==================== 投球 ====================

    private void renderThrowBall(GuiGraphics g, int l, int t, int mx, int my) {
        // 桶：镜像翻转（开口朝右→朝左），加深
        g.pose().pushPose();
        g.pose().translate(l + 70, t + 150, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(50));
        // 桶底
        g.fill(-45, -32, 45, -28, 0xFFB08048);
        // 桶身
        g.fill(-45, -28, 45, 32, 0xFF8A5A32);
        g.renderOutline(-45, -28, 90, 60, 0xFFB08048);
        // 桶沿
        g.fill(-50, -36, 50, -30, 0xFFC09058);
        g.pose().popPose();

        // 球篮（加深，开口朝左）
        int basketX = l + W - 78;
        int basketY = t + 155;
        g.fill(basketX, basketY, basketX + 60, basketY + 6, 0xFFA07040);
        g.fill(basketX, basketY + 6, basketX + 4, basketY + 48, 0xFF6A4A2A);
        g.fill(basketX + 56, basketY + 6, basketX + 60, basketY + 48, 0xFF6A4A2A);
        g.fill(basketX + 4, basketY + 42, basketX + 56, basketY + 48, 0xFF6A4A2A);
        g.drawString(font, Component.translatable("minigame.starrailexpress.throw_ball.basket"),
                basketX + 8, basketY - 14, 0xFF8899AA);

        // 鸟球
        float rx = birdHeld ? mx : birdX;
        float ry = birdHeld ? my : birdY;
        int cbr = 16;
        MinigameUI.filledCircle(g, Math.round(rx), Math.round(ry), cbr, 0xFFFF4E45);
        MinigameUI.filledCircle(g, Math.round(rx - 6), Math.round(ry - 5), 6, 0x44FFFFFF);
        MinigameUI.filledCircle(g, Math.round(rx + 5), Math.round(ry - 6), 5, WHITE);
        MinigameUI.filledCircle(g, Math.round(rx + 6), Math.round(ry - 6), 2, 0xFF111111);
        g.fill(Math.round(rx + cbr - 3), Math.round(ry - 2), Math.round(rx + cbr + 5), Math.round(ry + 3), 0xFFFFCC00);

        if (birdHeld) {
            float dx = mx - dragStartX, dy = my - dragStartY;
            for (int i = 0; i < 12; i++) {
                float pct = i / 12f;
                int dotX = Math.round(dragStartX + dx * pct);
                int dotY = Math.round(dragStartY + dy * pct);
                if (i % 2 == 0) g.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, 0x88FFFFFF);
            }
            float simX = dragStartX, simY = dragStartY;
            float simVx = -dx * 0.18f, simVy = -dy * 0.18f;
            for (int i = 0; i < 30; i++) {
                simX += simVx; simY += simVy; simVy += 0.28f;
                if (i % 2 == 0) g.fill(Math.round(simX), Math.round(simY),
                        Math.round(simX)+1, Math.round(simY)+1, 0x44FFAA40);
            }
        }
    }

    // ==================== 鼠标事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || successTicks >= 0) return super.mouseClicked(mouseX, mouseY, button);
        int l = left(), t = top();
        switch (kind) {
            case EXTINGUISH -> {
                extinguisherHeld = inRect(mouseX, mouseY, l + 320, t + 90, 60, 60);
                lastMouseX = mouseX; lastMouseY = mouseY;
            }
            case PACHINKO -> plungerHeld = inRect(mouseX, mouseY, l + W - 78, t + 56, 24, 170);
            case MIX_DRINK -> {
                // 先检查是否点击调酒杯
                if (inRect(mouseX, mouseY, cupX - 4, cupY - 4, 56, 66)) {
                    cupHeld = true; lastMouseX = mouseX; lastMouseY = mouseY; return true;
                }
                for (DragItem item : ingredients) {
                    if (!item.used && inRect(mouseX, mouseY, item.x - 4, item.y - 4, 28, 28)) {
                        dragging = item; lastMouseX = mouseX; lastMouseY = mouseY; break;
                    }
                }
            }
            case BALLOON_PUMP -> {
                int pMid = l + 248 + 20;
                pumpHeld = inRect(mouseX, mouseY, pMid - 28, pumpHandleY - 4, 56, 20);
            }
            case THROW_BALL -> {
                if (!birdFlying && dist((float)mouseX, (float)mouseY, birdX, birdY) < 24) {
                    birdHeld = true; dragStartX = (float)mouseX; dragStartY = (float)mouseY;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        int l = left(), t = top();
        switch (kind) {
            case EXTINGUISH -> {
                if (extinguisherHeld && mouseX < l + 240) {
                    fireSize = Math.max(0f, fireSize - 0.018f);
                    if (fireSize <= 0f) complete();
                }
            }
            case PACHINKO -> {
                if (plungerHeld) plunger = Mth.clamp(((float)mouseY - (t + 80)) / 100f, 0f, 1f);
            }
            case MIX_DRINK -> {
                if (cupHeld) { cupX = (float)mouseX - 24; cupY = (float)mouseY - 30; }
                if (cupHeld && cupLoaded) {
                    if (Math.abs((float)mouseY - lastShakeY) > 36) { shakeCount++; lastShakeY = (float)mouseY; }
                }
            }
            case BALLOON_PUMP -> {
                if (pumpHeld) {
                    float oldY = pumpHandleY;
                    pumpHandleY = Mth.clamp((float)mouseY, t + PUMP_TOP - 10, t + PUMP_BOT - 30);
                    if (pumpHandleY - oldY > 5) { // 向下拉→充气
                        balloon = Math.min(1f, balloon + 0.07f);
                        if (balloon >= 0.98f) complete();
                    }
                }
            }
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int l = left(), t = top();
        switch (kind) {
            case PACHINKO -> {
                if (plungerHeld && !ballActive) {
                    ballActive = true; bx = l + W - 68; by = t + 68;
                    bvx = -(2.8f + plunger * 4f); bvy = -(1f + plunger * 2.5f); plunger = 0;
                }
            }
            case MIX_DRINK -> {
                if (dragging != null) {
                    if (inRect(mouseX, mouseY, cupX - 8, cupY - 8, 64, 74)) {
                        dragging.used = true;
                        cupLoaded = ingredients.stream().allMatch(i -> i.used);
                    }
                    dragging = null;
                }
                if (cupHeld) {
                    cupHeld = false;
                    // 摇晃满3次后放到高脚杯上方→倒酒
                    if (cupLoaded && shakeCount >= 3 && inRect(mouseX, mouseY, l + 335, t + 80, 60, 100)) {
                        pouring = true;
                    }
                }
            }
            case THROW_BALL -> {
                if (birdHeld) {
                    birdHeld = false; birdFlying = true;
                    birdVelX = (birdX - (float)mouseX) * 0.2f;
                    birdVelY = (birdY - (float)mouseY) * 0.2f;
                }
            }
            default -> {}
        }
        extinguisherHeld = false; plungerHeld = false; pumpHeld = false;
        return true;
    }

    // ==================== 工具 ====================

    private void complete() { if (successTicks >= 0) return; successTicks = 0; onSuccess.run(); }
    private static boolean inRect(double x, double y, double rx, double ry, double rw, double rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by; return (float)Math.sqrt(dx*dx + dy*dy);
    }

    private static final class DragItem {
        final ItemStack stack; final float x; final float y; boolean used;
        DragItem(ItemStack stack, float x, float y) { this.stack = stack; this.x = x; this.y = y; }
    }
}
