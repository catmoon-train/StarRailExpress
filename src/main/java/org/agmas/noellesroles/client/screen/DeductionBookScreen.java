/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveClue;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveFlavor;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData.DeathNote;
import org.agmas.noellesroles.packet.GreatDetectiveRevealC2SPacket;

import java.util.List;
import java.util.UUID;

/**
 * 推理之书界面 &mdash; 复古书页风格，参考 NewspaperScreen 的设计。
 *
 * <p>每页对应一名嫌疑人，列出死亡时间与已掌握的碎片线索。线索 &ge; 3 条时
 * 可选择查明「方位」或「生死」，每名凶手只能查明一项。
 * 左右方向键或翻页按钮切换嫌疑人页。
 */
public class DeductionBookScreen extends Screen {

    // ---------- 纹理与布局常量 ----------
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation
            .parse("noellesroles:textures/gui/deduction_book.png");
    private static final int ORIG_IMG_WIDTH = 300;
    private static final int ORIG_IMG_HEIGHT = 200;

    // 内容区域在原始纹理中的相对坐标
    private static final int CONTENT_REL_X = 30;
    private static final int CONTENT_REL_Y = 55;
    private static final int CONTENT_REL_WIDTH = 240;
    private static final int CONTENT_REL_HEIGHT = 110;

    // 标题和嫌疑人标签的相对 Y 坐标
    private static final int TITLE_REL_Y = 20;
    private static final int SUSPECT_REL_Y = 40;

    // 翻页按钮在原始纹理中的相对坐标
    private static final int PAGE_BTN_REL_X = 248;
    private static final int PAGE_BTN_REL_Y = 174;

    // 页脚区域 &mdash; 目标情况 / 距离信息
    private static final int FOOTER_REL_Y = 172;

    // 屏幕边距
    private static final int SCREEN_MARGIN = 20;

    // ---------- 页面状态 ----------
    private int page = 0;

    // 当前帧的「方位 / 生死」可点击区域
    private boolean distBtnVisible = false;
    private boolean vitalBtnVisible = false;
    private int distBtnX, distBtnY, distBtnW, distBtnH;
    private int vitalBtnX, vitalBtnY, vitalBtnW, vitalBtnH;
    private UUID currentKiller = null;

    // ---------- 布局计算值 ----------
    private int bookX, bookY, bookWidth, bookHeight;
    private int contentX, contentY, contentWidth, contentHeight;
    private int titleY, suspectY, footerY;
    private float scale;

    // ---------- 翻页按钮 ----------
    private PageButton backButton;
    private PageButton forwardButton;

    public DeductionBookScreen() {
        super(GameNarrator.NO_TITLE);
    }

    private GreatDetectiveRoleData component() {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        return RoleData.getNullable(GreatDetectiveRoleData.class, minecraft.player);
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        calculateBookSize();
        createButtons();
        clampPage();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        calculateBookSize();
        this.clearWidgets();
        createButtons();
        clampPage();
    }

    private void calculateBookSize() {
        int availW = width - 2 * SCREEN_MARGIN;
        int availH = height - 2 * SCREEN_MARGIN;
        double ratio = (double) ORIG_IMG_WIDTH / ORIG_IMG_HEIGHT; // 300/200 = 1.5
        int w, h;
        if (availW / (double) availH > ratio) {
            h = availH;
            w = (int) (h * ratio);
        } else {
            w = availW;
            h = (int) (w / ratio);
        }
        bookWidth = w;
        bookHeight = h;
        bookX = (width - w +15) / 2;
        bookY = (height - h) / 2;

        scale = (float) bookWidth / ORIG_IMG_WIDTH;

        // 计算各区域实际屏幕坐标
        contentX = (int) (bookX + CONTENT_REL_X * scale);
        contentY = (int) (bookY + CONTENT_REL_Y * scale);
        contentWidth = (int) (CONTENT_REL_WIDTH * scale);
        contentHeight = (int) (CONTENT_REL_HEIGHT * scale);

        titleY = (int) (bookY + TITLE_REL_Y * scale);
        suspectY = (int) (bookY + SUSPECT_REL_Y * scale);
        footerY = (int) (bookY + FOOTER_REL_Y * scale);
    }

    private void createButtons() {
        // 翻页按钮：仿照 NewspaperScreen 的定位方式
        int btnX = (int) (bookX + PAGE_BTN_REL_X * scale);
        int btnY = (int) (bookY + PAGE_BTN_REL_Y * scale);
        // 左侧按钮（上一页）
        int leftX = btnX - 28;
        // 右侧按钮（下一页）
        int rightX = btnX;

        this.backButton = this.addRenderableWidget(
                new PageButton(leftX, btnY, false, b -> pageBack(), true));
        this.forwardButton = this.addRenderableWidget(
                new PageButton(rightX, btnY, true, b -> pageForward(), true));

        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        List<UUID> killers = getKillerList();
        if (backButton != null)
            backButton.visible = page > 0;
        if (forwardButton != null)
            forwardButton.visible = page < killers.size() - 1;
    }

    private void clampPage() {
        List<UUID> killers = getKillerList();
        if (killers.isEmpty()) {
            page = 0;
        } else {
            if (page < 0)
                page = 0;
            if (page >= killers.size())
                page = killers.size() - 1;
        }
    }

    private List<UUID> getKillerList() {
        GreatDetectiveRoleData comp = component();
        return comp == null ? List.of() : comp.getKillerOrder();
    }

    // ==================== 翻页 ====================

    private void pageForward() {
        List<UUID> killers = getKillerList();
        if (page < killers.size() - 1) {
            page++;
            updateButtonVisibility();
        }
    }

    private void pageBack() {
        if (page > 0) {
            page--;
            updateButtonVisibility();
        }
    }

    // ==================== 渲染背景 ====================

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super，自行绘制纹理背景
        guiGraphics.blit(BOOK_TEXTURE,
                bookX, bookY, bookWidth, bookHeight,
                0, 0, ORIG_IMG_WIDTH, ORIG_IMG_HEIGHT,
                ORIG_IMG_WIDTH, ORIG_IMG_HEIGHT);
    }

    // ==================== 暂停屏幕 ====================

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 渲染内容 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        distBtnVisible = false;
        vitalBtnVisible = false;
        currentKiller = null;

        // ---- 标题 ----
        Component titleComp = Component.translatable("screen.noellesroles.great_detective.title");
        drawScaledCentered(g, titleComp, titleY, 1.3f, 0xFFD4A344);

        GreatDetectiveRoleData comp = component();
        List<UUID> killers = getKillerList();

        if (comp == null || killers.isEmpty()) {
            Component emptyText = Component.translatable("screen.noellesroles.great_detective.empty")
                    .withStyle(ChatFormatting.GRAY);
            drawScaledCentered(g, emptyText, suspectY + (int) (16 * scale), 1.0f, 0xFF888888);
            return;
        }

        clampPage();
        UUID killer = killers.get(page);
        currentKiller = killer;

        // ---- 嫌疑人标签 ----
        Component suspectLabel = Component.translatable(
                "screen.noellesroles.great_detective.suspect", page + 1, killers.size());
        drawScaledCentered(g, suspectLabel, suspectY, 1.0f, 0xFFEEDDAA);

        // ---- 死亡时间 + 线索列表 ----
        List<DeathNote> notes = comp.getDeathNotes(killer);
        List<DetectiveClue> clues = comp.getClues(killer);
        int lineY = contentY;
        int lineHeight = (int) (9 * scale);

        for (DeathNote note : notes) {
            Component line = Component.literal("\u276F ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("screen.noellesroles.great_detective.death_note",
                            Component.literal(note.victimName()).withStyle(ChatFormatting.WHITE),
                            DetectiveFlavor.formatTime(note.secondsAgo()).copy().withStyle(ChatFormatting.WHITE)));
            drawContentLine(g, line, lineY, 0xFF99AABB);
            lineY += lineHeight;
        }

        for (DetectiveClue clue : clues) {
            Component line = Component.literal("\u276F ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(DeductionClueText.render(clue));
            drawContentLine(g, line, lineY, 0xFFCCBB99);
            lineY += lineHeight;
        }

        // ---- 页脚：方位 / 生死 ----
        if (clues.size() >= 3) {
            if (comp.hasRevealedDistance(killer)) {
                int dist = comp.getRevealedDistance(killer);
                Component distText;
                int distColor;
                if (dist < 0) {
                    distText = Component.translatable("screen.noellesroles.great_detective.distance_unknown");
                    distColor = 0xFFCC4444;
                } else {
                    distText = Component.translatable("screen.noellesroles.great_detective.distance", dist);
                    distColor = 0xFF44CCCC;
                }
                drawScaledCentered(g, distText, footerY, 0.9f, distColor);
            } else if (comp.hasRevealedVital(killer)) {
                boolean alive = comp.getRevealedVitalAlive(killer);
                Component vitalText = Component.translatable(alive
                        ? "screen.noellesroles.great_detective.vital_alive"
                        : "screen.noellesroles.great_detective.vital_dead");
                drawScaledCentered(g, vitalText, footerY, 0.9f, alive ? 0xFF66CC66 : 0xFFAA8888);
            } else {
                layoutChoiceButtons(g, mouseX, mouseY);
            }
        } else {
            Component needMore = Component.translatable(
                    "screen.noellesroles.great_detective.need_more", 3 - clues.size());
            drawScaledCentered(g, needMore, footerY, 0.85f, 0xFF997766);
        }
    }

    // ==================== 辅助绘制方法 ====================

    /**
     * 在书页内容区域左对齐绘制一行文本（带缩放）。
     */
    private void drawContentLine(GuiGraphics g, Component text, int screenY, int color) {
        if (text.getString().isEmpty())
            return;
        g.pose().pushPose();
        g.pose().translate(contentX, screenY, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * 在书页中绘制水平居中、带缩放的文本。
     * @param relScale 相对于 scale 的文字缩放倍率
     */
    private void drawScaledCentered(GuiGraphics g, Component text, int screenY, float relScale, int color) {
        String raw = text.getString();
        if (raw.isEmpty())
            return;
        g.pose().pushPose();
        g.pose().translate(bookX + bookWidth / 2.0f, screenY, 0);
        g.pose().scale(scale * relScale, scale * relScale, 1.0f);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    private void layoutChoiceButtons(GuiGraphics g, int mouseX, int mouseY) {
        Component distBtn = Component.translatable("screen.noellesroles.great_detective.target_distance");
        Component vitalBtn = Component.translatable("screen.noellesroles.great_detective.target_vital");
        float btnScale = 0.95f;
        int distW = (int) (font.width(distBtn) * btnScale * scale);
        int vitalW = (int) (font.width(vitalBtn) * btnScale * scale);
        int btnH = (int) (font.lineHeight * btnScale * scale);
        int gap = (int) (18 * scale);
        int total = distW + gap + vitalW;
        int startX = (int) (bookX + bookWidth / 2.0f - total / 2.0f);
        int btnY = (int) (footerY - btnH / 2.0f);

        distBtnX = startX;
        distBtnY = btnY;
        distBtnW = distW;
        distBtnH = btnH;
        distBtnVisible = true;

        vitalBtnX = startX + distW + gap;
        vitalBtnY = btnY;
        vitalBtnW = vitalW;
        vitalBtnH = btnH;
        vitalBtnVisible = true;

        drawFooterChoice(g, distBtn, distBtnX + distW / 2.0f, mouseX, mouseY,
                distBtnX, distBtnY, distBtnW, distBtnH, btnScale);
        drawFooterChoice(g, vitalBtn, vitalBtnX + vitalW / 2.0f, mouseX, mouseY,
                vitalBtnX, vitalBtnY, vitalBtnW, vitalBtnH, btnScale);
    }

    private void drawFooterChoice(GuiGraphics g, Component label, float centerX, int mouseX, int mouseY,
            int bx, int by, int bw, int bh, float btnScale) {
        boolean hover = hit(mouseX, mouseY, bx, by, bw, bh);
        g.pose().pushPose();
        g.pose().translate(centerX, footerY, 0);
        g.pose().scale(scale * btnScale, scale * btnScale, 1.0f);
        g.drawCenteredString(font, label, 0, 0, hover ? 0xFF66FF66 : 0xFFCCDDAA);
        g.pose().popPose();
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        if (button == 0 && currentKiller != null) {
            if (distBtnVisible && hit(mouseX, mouseY, distBtnX, distBtnY, distBtnW, distBtnH)) {
                ClientPlayNetworking.send(new GreatDetectiveRevealC2SPacket(
                        currentKiller, GreatDetectiveRevealC2SPacket.MODE_DISTANCE));
                return true;
            }
            if (vitalBtnVisible && hit(mouseX, mouseY, vitalBtnX, vitalBtnY, vitalBtnW, vitalBtnH)) {
                ClientPlayNetworking.send(new GreatDetectiveRevealC2SPacket(
                        currentKiller, GreatDetectiveRevealC2SPacket.MODE_VITAL));
                return true;
            }
        }
        return false;
    }

    // ==================== 键盘交互 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (keyCode == 263 || keyCode == 266) { // 左方向键 或 Page Up
            pageBack();
            return true;
        }
        if (keyCode == 262 || keyCode == 267) { // 右方向键 或 Page Down
            pageForward();
            return true;
        }
        return false;
    }
}
