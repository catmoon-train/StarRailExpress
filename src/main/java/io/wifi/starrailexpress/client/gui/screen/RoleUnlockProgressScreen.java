package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.unlock.RoleUnlockManager;
import io.wifi.starrailexpress.unlock.RoleUnlockManager.RoleUnlockEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 职业解锁进度界面
 * <p>
 * 展示全局已游玩场次与每个有解锁阈值的职业的解锁进度。
 * 可切换到「展示动画」模式，以卡片轮播动画逐一展示所有已解锁职业。
 * </p>
 */
public class RoleUnlockProgressScreen extends Screen {

    // ─── 布局常量 ────────────────────────────────────────────────────────────
    private static final int ROW_H       = 26;
    private static final int ROW_STRIDE  = ROW_H + 3;
    private static final int HEADER_H    = 64;  // 标题+进度说明区高度
    private static final int FOOTER_H    = 36;  // 底部按钮区高度
    private static final int SCROLL_W    = 8;
    private static final int BAR_W       = 80;  // 进度条宽度
    private static final int PANEL_PAD   = 10;
    private static final int COLOR_DOT_R = 5;   // 职业色点半径

    // ─── 动画常量 ────────────────────────────────────────────────────────────
    /** 每张卡片完整动画时长（ms） */
    private static final long ANIM_CARD_MS   = 2000L;
    /** 进入阶段占比 */
    private static final float ANIM_IN_FRAC  = 0.20f;
    /** 静止阶段占比 */
    private static final float ANIM_HOLD_FRAC = 0.60f;
    // 退出阶段 = 1 - IN - HOLD = 0.20f

    // ─── 数据 ────────────────────────────────────────────────────────────────
    private List<RoleUnlockEntry> allEntries = new ArrayList<>();
    private List<RoleUnlockEntry> unlockedEntries = new ArrayList<>();

    // ─── 列表视图状态 ────────────────────────────────────────────────────────
    private int scrollOffset   = 0;
    private int maxScroll      = 0;
    private int rowsPerPage    = 1;
    private boolean draggingScroll = false;
    private double dragStartY  = 0;
    private int    dragStartOff = 0;

    // ─── 动画视图状态 ────────────────────────────────────────────────────────
    private boolean showingAnimation = false;
    /** 当前正在展示的已解锁职业索引 */
    private int     animIndex    = 0;
    /** 动画视图启动时间（ms） */
    private long    animStartMs  = 0L;

    // ─── 布局缓存 ────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int listAreaY, listAreaH;
    private int scrollBarX;

    // ─── 按钮 ────────────────────────────────────────────────────────────────
    /** 切换到展示动画 / 返回列表 */
    private Button toggleAnimBtn;
    /** 关闭界面 */
    private Button closeBtn;
    /** 动画中：上一张 */
    private Button prevBtn;
    /** 动画中：下一张 */
    private Button nextBtn;
    /** 动画中：自动播放/暂停 */
    private Button autoPlayBtn;
    private boolean autoPlay = true;

    // ─────────────────────────────────────────────────────────────────────────

    public RoleUnlockProgressScreen() {
        super(Component.literal("职业解锁进度"));
    }

    // ─── 初始化 ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        allEntries     = RoleUnlockManager.getInstance().buildClientEntries();
        unlockedEntries = allEntries.stream().filter(RoleUnlockEntry::isUnlocked).toList();

        computeLayout();
        addButtons();
    }

    private void computeLayout() {
        panelW     = Math.min(Math.max(280, width - 60), 560);
        panelH     = Math.min(Math.max(200, height - 40), 480);
        panelX     = (width  - panelW) / 2;
        panelY     = (height - panelH) / 2;

        listAreaY  = panelY + HEADER_H;
        listAreaH  = panelH - HEADER_H - FOOTER_H;
        rowsPerPage = Math.max(1, listAreaH / ROW_STRIDE);
        maxScroll  = Math.max(0, allEntries.size() - rowsPerPage);
        scrollBarX = panelX + panelW - PANEL_PAD - SCROLL_W;
    }

    private void addButtons() {
        clearWidgets();

        int btnY = panelY + panelH - FOOTER_H + (FOOTER_H - 20) / 2;
        int cx   = panelX + panelW / 2;

        // 列表模式按钮
        toggleAnimBtn = addRenderableWidget(
                ModernButton.builder(Component.literal("▶ 展示动画"), btn -> startAnimation())
                        .bounds(cx - 110, btnY, 100, 20)
                        .accentColor(0xFF3A5A8A)
                        .build());

        closeBtn = addRenderableWidget(
                ModernButton.builder(Component.literal("关闭"), btn -> onClose())
                        .bounds(cx + 10, btnY, 100, 20)
                        .accentColor(0xFF553333)
                        .build());

        // 动画模式按钮（初始隐藏）
        prevBtn = addRenderableWidget(
                ModernButton.builder(Component.literal("◀"), btn -> animStep(-1))
                        .bounds(cx - 160, btnY, 40, 20)
                        .accentColor(0xFF2B3A55)
                        .build());
        nextBtn = addRenderableWidget(
                ModernButton.builder(Component.literal("▶"), btn -> animStep(1))
                        .bounds(cx + 120, btnY, 40, 20)
                        .accentColor(0xFF2B3A55)
                        .build());
        autoPlayBtn = addRenderableWidget(
                ModernButton.builder(Component.literal("⏸ 暂停"), btn -> toggleAutoPlay())
                        .bounds(cx - 50, btnY, 100, 20)
                        .accentColor(0xFF2B5A3A)
                        .build());

        setAnimButtonsVisible(false);
    }

    private void setAnimButtonsVisible(boolean visible) {
        prevBtn.visible     = visible;
        nextBtn.visible     = visible;
        autoPlayBtn.visible = visible;
        toggleAnimBtn.setMessage(visible
                ? Component.literal("☰ 返回列表")
                : Component.literal("▶ 展示动画"));
    }

    // ─── 渲染 ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 背景遮罩
        g.fill(0, 0, width, height, 0xAA000000);
        // 面板背景
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101820);
        // 面板边框
        drawBorder(g, panelX, panelY, panelW, panelH, 0xFF3A4A6A);

        if (showingAnimation) {
            renderAnimationView(g, mouseX, mouseY, partialTick);
        } else {
            renderListView(g, mouseX, mouseY, partialTick);
        }


    }

    // ─── 列表视图渲染 ────────────────────────────────────────────────────────

    private void renderListView(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int games = RoleUnlockManager.getInstance().getClientGlobalGamesPlayed();
        int total = RoleUnlockManager.UNLOCK_THRESHOLDS.size();
        int unlocked = (int) allEntries.stream().filter(RoleUnlockEntry::isUnlocked).count();

        // ── 标题区 ────────────────────────────────────────────────────────────
        g.drawCenteredString(font, "§l职业解锁进度",
                panelX + panelW / 2, panelY + 10, 0xFFFFDD55);
        g.drawCenteredString(font,
                "§7已游玩场次：§e" + games + " §7场",
                panelX + panelW / 2, panelY + 24, 0xFFFFFFFF);
        g.drawCenteredString(font,
                "§7已解锁职业：§a" + unlocked + " §7/ §f" + total,
                panelX + panelW / 2, panelY + 36, 0xFFFFFFFF);

        // 整体解锁进度条
        int barTotalW = panelW - PANEL_PAD * 4;
        int barX = panelX + PANEL_PAD * 2;
        int barY = panelY + 50;
        drawProgressBar(g, barX, barY, barTotalW, 8,
                total > 0 ? (float) unlocked / total : 0f,
                0xFF44CC66, 0xFF224433);

        // ── 分隔线 ────────────────────────────────────────────────────────────
        g.fill(panelX + 4, listAreaY - 1, panelX + panelW - 4, listAreaY, 0xFF3A4A6A);

        // ── 剪裁并渲染行列表 ──────────────────────────────────────────────────
        enableScissor(g, panelX, listAreaY, panelX + panelW - SCROLL_W - 4, listAreaY + listAreaH);

        int startIdx = scrollOffset;
        int endIdx   = Math.min(startIdx + rowsPerPage + 1, allEntries.size());
        int rowY     = listAreaY - (scrollOffset * ROW_STRIDE) % ROW_STRIDE;
        // 精确计算起始 Y
        rowY = listAreaY;

        for (int i = startIdx; i < endIdx; i++) {
            RoleUnlockEntry entry = allEntries.get(i);
            int ry = listAreaY + (i - startIdx) * ROW_STRIDE;
            if (ry + ROW_STRIDE < listAreaY || ry > listAreaY + listAreaH) continue;
            renderRow(g, entry, panelX + PANEL_PAD, ry,
                    panelW - PANEL_PAD * 2 - SCROLL_W - 6,
                    mouseX, mouseY);
        }

        disableScissor(g);

        // ── 滚动条 ────────────────────────────────────────────────────────────
        if (allEntries.size() > rowsPerPage) {
            renderScrollBar(g, mouseX, mouseY);
        }
    }

    /** 渲染单行职业解锁条目 */
    private void renderRow(GuiGraphics g, RoleUnlockEntry entry,
            int x, int y, int w, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w
                && mouseY >= y && mouseY <= y + ROW_H;
        int bgColor = entry.isUnlocked()
                ? (hovered ? 0x30AAFFAA : 0x20446644)
                : (hovered ? 0x30AAAAFF : 0x18334488);
        g.fill(x, y, x + w, y + ROW_H, bgColor);

        // 职业色点
        int dotX = x + 10;
        int dotY = y + ROW_H / 2;
        int dotColor = entry.isUnlocked() ? (entry.roleColor() | 0xFF000000) : 0xFF666666;
        g.fill(dotX - COLOR_DOT_R, dotY - COLOR_DOT_R,
               dotX + COLOR_DOT_R, dotY + COLOR_DOT_R, dotColor);

        // 解锁/锁定图标
        String icon = entry.isUnlocked() ? "§a✔" : "§c✘";
        g.drawString(font, icon, x + 22, y + (ROW_H - 8) / 2, 0xFFFFFFFF);

        // 职业名
        String nameKey = "role." + entry.roleId().getNamespace() + "." + entry.roleId().getPath();
        String roleName = net.minecraft.locale.Language.getInstance().getOrDefault(nameKey,
                entry.roleId().getPath());
        int nameColor = entry.isUnlocked() ? 0xFFFFFFFF : 0xFFAAAAAA;
        g.drawString(font, roleName, x + 36, y + (ROW_H - font.lineHeight) / 2, nameColor);

        // 进度条 + 阈值文字（右侧）
        int threshold = entry.threshold();
        int games     = entry.currentGames();
        float progress = threshold > 0 ? Math.min(1f, (float) games / threshold) : 1f;
        int barX = x + w - BAR_W - 60;
        int barY = y + (ROW_H - 6) / 2;
        drawProgressBar(g, barX, barY, BAR_W, 6, progress,
                entry.isUnlocked() ? 0xFF44CC88 : 0xFF4477CC, 0xFF222222);

        // 场次文字
        String countStr;
        if (entry.isUnlocked()) {
            countStr = "§a已解锁";
        } else {
            countStr = "§e" + games + "§7/§f" + threshold;
        }
        g.drawString(font, countStr, x + w - 58, y + (ROW_H - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    // ─── 动画视图渲染 ────────────────────────────────────────────────────────

    private void renderAnimationView(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = panelX + panelW / 2;
        int cy = panelY + panelH / 2 - FOOTER_H / 2;

        if (unlockedEntries.isEmpty()) {
            g.drawCenteredString(font, "§7暂无已解锁职业", cx, cy - 8, 0xFFAAAAAA);
            return;
        }

        // 推进自动播放
        if (autoPlay) {
            long elapsed = System.currentTimeMillis() - animStartMs;
            int targetIdx = (int) (elapsed / ANIM_CARD_MS) % unlockedEntries.size();
            if (targetIdx != animIndex) {
                animIndex = targetIdx;
            }
        }

        long elapsed   = System.currentTimeMillis() - animStartMs;
        long cardTime  = elapsed % ANIM_CARD_MS;
        float cardFrac = (float) cardTime / ANIM_CARD_MS;

        // 计算进入/静止/退出阶段的平移 & 透明度
        float alphaFrac;
        float xOffset;
        if (cardFrac < ANIM_IN_FRAC) {
            float t = cardFrac / ANIM_IN_FRAC;
            float ease = easeOutQuart(t);
            xOffset   = (1f - ease) * (panelW * 0.6f);
            alphaFrac = ease;
        } else if (cardFrac < ANIM_IN_FRAC + ANIM_HOLD_FRAC) {
            xOffset   = 0f;
            alphaFrac = 1f;
        } else {
            float t = (cardFrac - ANIM_IN_FRAC - ANIM_HOLD_FRAC) / (1f - ANIM_IN_FRAC - ANIM_HOLD_FRAC);
            float ease = easeInQuart(t);
            xOffset   = -ease * (panelW * 0.6f);
            alphaFrac = 1f - ease;
        }

        int alpha = (int) (alphaFrac * 255);
        if (alpha <= 0) return;

        RoleUnlockEntry entry = unlockedEntries.get(animIndex);

        int cardW = (int) (panelW * 0.65f);
        int cardH = 100;
        int cardX = cx - cardW / 2 + (int) xOffset;
        int cardY = cy - cardH / 2;

        int roleColor = entry.roleColor() | 0xFF000000;
        int cardBg    = blendAlpha(0xFF101820, roleColor, 0.15f);
        int borderClr = blendAlpha(roleColor, 0xFF000000, 0.4f);

        // 卡片背景
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH,
                applyAlpha(cardBg, alpha));
        drawBorder(g, cardX, cardY, cardW, cardH,
                applyAlpha(borderClr, alpha));

        // 顶部色带
        g.fill(cardX, cardY, cardX + cardW, cardY + 6,
                applyAlpha(roleColor, alpha));

        // 职业名
        String roleName = I18n.get("announcement.star.role."+entry.roleId().getPath());
        float scale = 1.6f;
        g.pose().pushPose();
        g.pose().translate(cx + xOffset, cardY + 30, 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(font, "§l" + roleName, 0, 0,
                applyAlpha(roleColor, alpha));
        g.pose().popPose();

        // 状态标签
        g.drawCenteredString(font, "§a§l★ 已解锁",
                cx + (int) xOffset, cardY + 55, applyAlpha(0xFFAAFFAA, alpha));

        // 阈值说明
        String desc = "§7所需场次：§e" + entry.threshold() + " §7场";
        g.drawCenteredString(font, desc,
                cx + (int) xOffset, cardY + 68, applyAlpha(0xFFCCCCCC, alpha));

        // 序号指示器
        String idxStr = "§7(" + (animIndex + 1) + " / " + unlockedEntries.size() + ")";
        g.drawCenteredString(font, idxStr, cx, panelY + panelH - FOOTER_H - 16, 0xFF888888);

        // 标题
        g.drawCenteredString(font, "§l§6已解锁职业展示",
                cx, panelY + 12, 0xFFFFDD44);
    }

    // ─── 动画控制 ────────────────────────────────────────────────────────────

    private void startAnimation() {
        showingAnimation = true;
        animIndex   = 0;
        animStartMs = System.currentTimeMillis();
        autoPlay    = true;
        setAnimButtonsVisible(true);
        autoPlayBtn.setMessage(Component.literal("⏸ 暂停"));
    }

    private void stopAnimation() {
        showingAnimation = false;
        setAnimButtonsVisible(false);
    }

    private void animStep(int delta) {
        if (unlockedEntries.isEmpty()) return;
        autoPlay  = false;
        animIndex = Math.floorMod(animIndex + delta, unlockedEntries.size());
        animStartMs = System.currentTimeMillis(); // 重置动画计时
        autoPlayBtn.setMessage(Component.literal("▶ 自动"));
    }

    private void toggleAutoPlay() {
        autoPlay = !autoPlay;
        if (autoPlay) {
            animStartMs = System.currentTimeMillis() - (long) animIndex * ANIM_CARD_MS;
            autoPlayBtn.setMessage(Component.literal("⏸ 暂停"));
        } else {
            autoPlayBtn.setMessage(Component.literal("▶ 自动"));
        }
    }

    // ─── 事件处理 ────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showingAnimation) {
            if (keyCode == GLFW.GLFW_KEY_LEFT)  { animStep(-1); return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { animStep(1);  return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { stopAnimation(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!showingAnimation) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!showingAnimation && allEntries.size() > rowsPerPage) {
            // 检查点击滚动条区域
            int sbH = getScrollBarHeight();
            int sbY = getScrollBarY();
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + SCROLL_W
                    && mouseY >= sbY && mouseY <= sbY + sbH) {
                draggingScroll = true;
                dragStartY     = mouseY;
                dragStartOff   = scrollOffset;
                return true;
            }
        }
        if (showingAnimation && button == 0) {
            // 点击卡片区域切换
            int cx = panelX + panelW / 2;
            int cardW = (int) (panelW * 0.65f);
            if (mouseX >= cx - cardW / 2.0 && mouseX <= cx + cardW / 2.0) {
                animStep(mouseX < cx ? -1 : 1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            int trackH  = listAreaH - getScrollBarHeight();
            if (trackH > 0) {
                double delta = mouseY - dragStartY;
                int newOff   = (int) (dragStartOff + delta * maxScroll / trackH);
                scrollOffset = Mth.clamp(newOff, 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (showingAnimation) {
            stopAnimation();
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── 滚动条辅助 ──────────────────────────────────────────────────────────

    private void renderScrollBar(GuiGraphics g, int mouseX, int mouseY) {
        // 轨道
        g.fill(scrollBarX, listAreaY, scrollBarX + SCROLL_W, listAreaY + listAreaH, 0xFF1A2A3A);
        // 滑块
        int sbH = getScrollBarHeight();
        int sbY = getScrollBarY();
        boolean hovered = mouseX >= scrollBarX && mouseX <= scrollBarX + SCROLL_W
                && mouseY >= sbY && mouseY <= sbY + sbH;
        int thumbColor = (draggingScroll || hovered) ? 0xFF7799CC : 0xFF445577;
        g.fill(scrollBarX + 1, sbY, scrollBarX + SCROLL_W - 1, sbY + sbH, thumbColor);
    }

    private int getScrollBarHeight() {
        if (maxScroll <= 0) return listAreaH;
        return Math.max(20, listAreaH * rowsPerPage / allEntries.size());
    }

    private int getScrollBarY() {
        if (maxScroll <= 0) return listAreaY;
        int trackH = listAreaH - getScrollBarHeight();
        return listAreaY + trackH * scrollOffset / maxScroll;
    }

    // ─── 绘制工具 ────────────────────────────────────────────────────────────

    private void drawProgressBar(GuiGraphics g,
            int x, int y, int w, int h,
            float progress, int fillColor, int bgColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        int fillW = (int) (w * Mth.clamp(progress, 0f, 1f));
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, fillColor);
        }
        // 高光
        g.fill(x, y, x + fillW, y + 1, 0x40FFFFFF);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color); // top
        g.fill(x,         y + h - 1, x + w,     y + h,     color); // bottom
        g.fill(x,         y,         x + 1,     y + h,     color); // left
        g.fill(x + w - 1, y,         x + w,     y + h,     color); // right
    }

    /** 使用 GuiGraphics 的 enableScissor 进行矩形裁剪 */
    private void enableScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    private void disableScissor(GuiGraphics g) {
        g.disableScissor();
    }

    // ─── 颜色工具 ────────────────────────────────────────────────────────────

    private static int applyAlpha(int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int gv = (rgb >> 8)  & 0xFF;
        int b = rgb        & 0xFF;
        return (alpha << 24) | (r << 16) | (gv << 8) | b;
    }

    /** 将 base 与 tint 混合，tint 权重 = tintWeight（0..1） */
    private static int blendAlpha(int base, int tint, float tintWeight) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
        int r  = (int) (br + (tr - br) * tintWeight);
        int gv = (int) (bg + (tg - bg) * tintWeight);
        int b  = (int) (bb + (tb - bb) * tintWeight);
        return 0xFF000000 | (r << 16) | (gv << 8) | b;
    }

    // ─── 缓动函数 ────────────────────────────────────────────────────────────

    private static float easeOutQuart(float t) {
        float v = 1f - t;
        return 1f - v * v * v * v;
    }

    private static float easeInQuart(float t) {
        return t * t * t * t;
    }
}
