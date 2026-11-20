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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.index.TrainCommandManualEntries;
import io.wifi.starrailexpress.index.TrainCommandManualEntries.Category;
import io.wifi.starrailexpress.index.TrainCommandManualEntries.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 列车指令手册界面。
 *
 * <p>
 * 左侧为分类（第一项固定为「常用指令」），右侧分页列出指令的名称、用法与功能。
 * 支持滚轮 / 拖拽滚动条上下滚动、PageUp/PageDown 翻页、左右方向键切换分类、
 * 点击任意指令把完整指令（含斜杠）复制到剪贴板。
 * 所有文案来自独立命名空间 {@code train_manual} 的语言文件。
 */
public class TrainCommandManualScreen extends Screen {

    private static final int PAGE_SIZE = 12;
    private static final int COL_PANEL_BG = 0xFFF1E4C6;
    private static final int COL_PANEL_BORDER = 0xFF5F4726;
    private static final int COL_LIST_BG = 0xFFFBF3DF;
    private static final int COL_TITLE = 0xFF3E2B14;
    private static final int COL_TEXT = 0xFF4A3D2C;
    private static final int COL_USAGE = 0xFF8A4B1E;
    private static final int COL_CMD = 0xFF1F5A92;
    private static final int COL_CMD_HOVER = 0xFFC25E12;

    private int categoryIndex = 0;
    private int page = 0;
    private int scroll = 0;
    private int maxScroll = 0;

    private int panelX, panelY, panelW, panelH;
    private int tabX, tabY, tabW, tabH;
    private int listX, listY, listW, listH;
    private int contentX, contentY, contentW;
    private int footerY;
    private int rowHeight;
    private float textScale = 1.0f;

    private final int[] tabXs = new int[TrainCommandManualEntries.CATEGORIES.size()];
    private final int[] tabYs = new int[TrainCommandManualEntries.CATEGORIES.size()];
    private final List<int[]> entryRects = new ArrayList<>();
    private final List<Entry> entryRefs = new ArrayList<>();
    private final List<int[]> usageRects = new ArrayList<>();
    private final List<String> usageRefs = new ArrayList<>();

    private int prevPageX, nextPageX, pageBtnY, pageBtnW, pageBtnH;
    private int closeX, closeY, closeSize;
    private int trackX, trackY, trackH, thumbY, thumbH;

    private boolean draggingScroll = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    private int copiedTimer = 0;
    private String copiedCommand = "";

    public TrainCommandManualScreen() {
        super(Component.translatable("train_manual.screen.title"));
    }

    // ==================== 布局 / 适配 ====================

    @Override
    protected void init() {
        super.init();
        recomputeLayout();
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        super.resize(client, width, height);
        recomputeLayout();
    }

    private void recomputeLayout() {
        int margin = Mth.clamp(width / 36, 8, 24);
        panelX = margin;
        panelY = margin;
        panelW = Math.max(160, width - margin * 2);
        panelH = Math.max(120, height - margin * 2);

        textScale = Mth.clamp(Math.min(width / 900f, height / 620f), 0.72f, 1.0f);
        int headerH = (int) (34 * textScale);
        int footerH = (int) (34 * textScale);

        tabX = panelX + 8;
        tabY = panelY + headerH + 4;
        tabW = Mth.clamp((int) (panelW * 0.25f), 92, 210);
        tabH = Mth.clamp((panelH - headerH - footerH - 20) / (tabYs.length + 1),
                14, (int) (26 * textScale));

        listX = tabX + tabW + 10;
        listY = panelY + headerH + 4;
        listW = Math.max(80, panelX + panelW - listX - 8);
        listH = Math.max(40, panelH - headerH - footerH - 16);

        contentX = listX + 8;
        contentY = listY + 6;
        contentW = Math.max(40, listW - 24);

        rowHeight = (int) (58 * textScale);
        footerY = panelY + panelH - (int) (23 * textScale);

        pageBtnW = (int) (22 * textScale);
        pageBtnH = (int) (15 * textScale);
        nextPageX = panelX + panelW - pageBtnW - 10;
        prevPageX = nextPageX - pageBtnW - 6;
        pageBtnY = footerY - 2;

        closeSize = (int) (16 * textScale);
        closeX = panelX + panelW - closeSize - 8;
        closeY = panelY + 6;

        trackX = listX + listW - 9;
        trackY = listY + 3;
        trackH = Math.max(20, listH - 6);

        clampPage();
        updateScrollBounds();
    }

    private List<Entry> currentEntries() {
        Category category = TrainCommandManualEntries.CATEGORIES.get(categoryIndex);
        return category.entries();
    }

    private int pageCount() {
        return Math.max(1, (currentEntries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void clampPage() {
        page = Mth.clamp(page, 0, pageCount() - 1);
    }

    private void updateScrollBounds() {
        int total = Math.min(PAGE_SIZE, Math.max(0, currentEntries().size() - page * PAGE_SIZE)) * rowHeight;
        maxScroll = Math.max(0, total - listH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        float ratio = (float) listH / (listH + maxScroll);
        thumbH = Mth.clamp((int) (trackH * ratio), 16, trackH);
        thumbY = trackY + (int) ((trackH - thumbH) * (maxScroll == 0 ? 0 : scroll / (float) maxScroll));
    }

    private void selectCategory(int index) {
        if (index < 0 || index >= TrainCommandManualEntries.CATEGORIES.size())
            return;
        categoryIndex = index;
        page = 0;
        scroll = 0;
        updateScrollBounds();
    }

    private void changePage(int delta) {
        int target = Mth.clamp(page + delta, 0, pageCount() - 1);
        if (target == page)
            return;
        page = target;
        scroll = 0;
        updateScrollBounds();
        playClick();
    }

    // ==================== 绘制 ====================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x990D0B09);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        // 纸张面板与金色描边
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xCC211708);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, COL_PANEL_BORDER);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL_BG);

        drawCenteredText(g, Component.translatable("train_manual.screen.title"),
                panelX + panelW / 2, panelY + (int) (9 * textScale), COL_TITLE, 1.25f);
        drawCenteredText(g, Component.translatable("train_manual.screen.hint"),
                panelX + panelW / 2, panelY + (int) (24 * textScale), 0xFF7B6647, 0.85f);

        drawCloseButton(g, mouseX, mouseY);
        drawCategoryTabs(g, mouseX, mouseY);
        drawCommandList(g, mouseX, mouseY);
        drawFooter(g);
    }

    private void drawCloseButton(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = inRect(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize,
                hover ? 0xFFC25E12 : 0xFFD9C8A2);
        drawCenteredText(g, Component.literal("×"), closeX + closeSize / 2,
                closeY + closeSize / 2 - (int) (4 * textScale), hover ? 0xFFFFFFFF : COL_TITLE, 1.0f);
    }

    private void drawCategoryTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < TrainCommandManualEntries.CATEGORIES.size(); i++) {
            int y = tabY + i * (tabH + 3);
            if (y + tabH > listY + listH)
                break;
            tabXs[i] = tabX;
            tabYs[i] = y;
            boolean selected = i == categoryIndex;
            boolean hover = inRect(mouseX, mouseY, tabX, y, tabW, tabH);
            int color = TrainCommandManualEntries.CATEGORIES.get(i).color();
            int bg = selected ? color : (hover ? 0xFFD8C49C : 0xFFE4D5B2);
            g.fill(tabX, y, tabX + tabW, y + tabH, bg);
            g.fill(tabX, y, tabX + 3, y + tabH, selected ? 0xFFF0D68A : color);
            Component label = Component.translatable(TrainCommandManualEntries.NAMESPACE + ".category."
                    + TrainCommandManualEntries.CATEGORIES.get(i).id());
            drawText(g, label, tabX + 7, y + (tabH - (int) (9 * textScale)) / 2,
                    selected ? 0xFFFFF6DC : COL_TEXT);
        }
    }

    private void drawCommandList(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX, listY, listX + listW, listY + listH, COL_PANEL_BORDER);
        g.fill(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1, COL_LIST_BG);

        collectEntryRects();

        g.enableScissor(listX + 2, listY + 2, listX + listW - 2, listY + listH - 2);
        try {
            int from = page * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, currentEntries().size());
            int lineH = Math.max(9, (int) (font.lineHeight * textScale));
            int textMaxW = (int) (contentW / textScale) - 4;

            for (int i = from; i < to; i++) {
                Entry entry = currentEntries().get(i);
                int rowY = contentY + (i - from) * rowHeight - scroll;
                if (rowY + rowHeight < listY || rowY > listY + listH)
                    continue;

                boolean hover = inRect(mouseX, mouseY, contentX - 4, rowY - 2, contentW + 4, rowHeight);
                if (hover) {
                    g.fill(contentX - 4, rowY - 2, contentX + contentW, rowY + rowHeight - 2, 0xFFEAD6A6);
                }

                String base = TrainCommandManualEntries.NAMESPACE + ".entry." + entry.key();
                Component name = Component.translatable(base + ".name");
                if (name.getString().isEmpty())
                    name = Component.literal(entry.command());
                Component usage = Component.translatable(base + ".usage");
                Component desc = Component.translatable(base + ".desc");

                drawScaled(g, Component.literal(entry.command()).append(Component.literal("  "))
                                .append(name).withStyle(style -> style.withUnderlined(hover)),
                        contentX, rowY, hover ? COL_CMD_HOVER : COL_CMD, 1.0f);
                drawScaled(g, Component.translatable("train_manual.screen.usage").append(usage),
                        contentX, rowY + lineH, COL_USAGE, 0.92f);

                int descY = rowY + lineH * 2;
                List<FormattedCharSequence> lines = font.split(
                        Component.translatable("train_manual.screen.desc").append(desc), textMaxW);
                int maxLines = Math.max(1, (rowHeight - lineH * 2 - 2) / lineH);
                for (int l = 0; l < Math.min(maxLines, lines.size()); l++) {
                    drawScaled(g, lines.get(l), contentX, descY + l * lineH, COL_TEXT, 0.92f);
                }
                g.fill(contentX - 4, rowY + rowHeight - 2, contentX + contentW, rowY + rowHeight - 1,
                        0xFFDCCAA4);
            }
        } finally {
            g.disableScissor();
        }

        g.fill(trackX, trackY, trackX + 4, trackY + trackH, 0xFFD2BE96);
        g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH,
                draggingScroll ? 0xFF8A4B1E : 0xFF9C7743);
    }

    private void drawFooter(GuiGraphics g) {
        Component pageText = Component.translatable("train_manual.screen.page", page + 1, pageCount());
        drawCenteredText(g, pageText, (prevPageX + nextPageX + pageBtnW) / 2, footerY + 1, COL_TITLE, 0.95f);

        drawSmallButton(g, prevPageX, pageBtnY, Component.literal("<"), page > 0);
        drawSmallButton(g, nextPageX, pageBtnY, Component.literal(">"), page < pageCount() - 1);

        if (copiedTimer > 0) {
            drawText(g, Component.translatable("train_manual.screen.copied", copiedCommand)
                            .withStyle(ChatFormatting.DARK_GREEN),
                    panelX + 10, footerY + 1, 0xFF2F6B24);
        } else {
            drawText(g, Component.translatable("train_manual.screen.footer_hint"),
                    panelX + 10, footerY + 1, 0xFF7B6647);
        }
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, Component label, boolean active) {
        g.fill(x, y, x + pageBtnW, y + pageBtnH, active ? 0xFF8A6A3A : 0xFFD9C8A2);
        drawCenteredText(g, label, x + pageBtnW / 2, y + 2, active ? 0xFFFFF6DC : 0xFF9C8B6C, 1.0f);
    }

    private void collectEntryRects() {
        entryRects.clear();
        entryRefs.clear();
        usageRects.clear();
        usageRefs.clear();
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, currentEntries().size());
        int lineH = Math.max(9, (int) (font.lineHeight * textScale));
        for (int i = from; i < to; i++) {
            Entry entry = currentEntries().get(i);
            int rowY = contentY + (i - from) * rowHeight - scroll;
            if (rowY + rowHeight < listY || rowY > listY + listH)
                continue;
            entryRects.add(new int[] { contentX - 4, rowY - 2, contentW + 4, rowHeight });
            entryRefs.add(entry);
            usageRects.add(new int[] { contentX - 4, rowY + lineH - 1, contentW + 4, lineH + 2 });
            usageRefs.add(Component.translatable(
                    TrainCommandManualEntries.NAMESPACE + ".entry." + entry.key() + ".usage").getString());
        }
    }

    private void drawText(GuiGraphics g, Component text, int x, int y, int color) {
        drawScaled(g, text, x, y, color, 1.0f);
    }

    private void drawScaled(GuiGraphics g, Component text, int x, int y, int color, float extra) {
        if (text.getString().isEmpty())
            return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(textScale * extra, textScale * extra, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawScaled(GuiGraphics g, FormattedCharSequence text, int x, int y, int color, float extra) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(textScale * extra, textScale * extra, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawCenteredText(GuiGraphics g, Component text, int centerX, int y, int color, float extra) {
        if (text.getString().isEmpty())
            return;
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(textScale * extra, textScale * extra, 1.0f);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    // ==================== 交互 ====================

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void copyText(String text) {
        if (minecraft == null || text.isEmpty())
            return;
        minecraft.keyboardHandler.setClipboard(text);
        copiedCommand = text;
        copiedTimer = 90;
        playClick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);

        if (inRect(mouseX, mouseY, closeX, closeY, closeSize, closeSize)) {
            playClick();
            onClose();
            return true;
        }

        for (int i = 0; i < tabYs.length; i++) {
            if (tabYs[i] != 0 && inRect(mouseX, mouseY, tabXs[i], tabYs[i], tabW, tabH)) {
                if (i != categoryIndex) {
                    selectCategory(i);
                    playClick();
                }
                return true;
            }
        }

        if (inRect(mouseX, mouseY, prevPageX, pageBtnY, pageBtnW, pageBtnH)) {
            changePage(-1);
            return true;
        }
        if (inRect(mouseX, mouseY, nextPageX, pageBtnY, pageBtnW, pageBtnH)) {
            changePage(1);
            return true;
        }

        if (inRect(mouseX, mouseY, trackX - 3, trackY, 12, trackH)) {
            draggingScroll = true;
            dragStartY = (int) mouseY;
            dragStartScroll = scroll;
            if (mouseY < thumbY) {
                scroll = Mth.clamp(scroll - listH, 0, maxScroll);
                updateScrollBounds();
            } else if (mouseY > thumbY + thumbH) {
                scroll = Mth.clamp(scroll + listH, 0, maxScroll);
                updateScrollBounds();
            }
            return true;
        }

        collectEntryRects();
        for (int i = 0; i < usageRects.size(); i++) {
            int[] r = usageRects.get(i);
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                copyText(usageRefs.get(i));
                return true;
            }
        }
        for (int i = 0; i < entryRects.size(); i++) {
            int[] r = entryRects.get(i);
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                copyText(entryRefs.get(i).command());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inRect(mouseX, mouseY, listX, listY, listW, listH) && maxScroll > 0) {
            scroll = Mth.clamp(scroll - (int) (scrollY * rowHeight), 0, maxScroll);
            updateScrollBounds();
            draggingScroll = false;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScroll && maxScroll > 0) {
            int travel = Math.max(1, trackH - thumbH);
            scroll = Mth.clamp(dragStartScroll + (int) ((mouseY - dragStartY) * (maxScroll + listH) / travel),
                    0, maxScroll);
            updateScrollBounds();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (keyCode == 266) { // PageUp
            changePage(-1);
            return true;
        }
        if (keyCode == 267) { // PageDown
            changePage(1);
            return true;
        }
        if (keyCode == 263) { // Left：上一种分类
            selectCategory(categoryIndex - 1);
            return true;
        }
        if (keyCode == 262) { // Right：下一种分类
            selectCategory(categoryIndex + 1);
            return true;
        }
        if (keyCode == 264) { // Up
            scroll = Mth.clamp(scroll - rowHeight, 0, maxScroll);
            updateScrollBounds();
            return true;
        }
        if (keyCode == 259) { // Down
            scroll = Mth.clamp(scroll + rowHeight, 0, maxScroll);
            updateScrollBounds();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (copiedTimer > 0)
            copiedTimer--;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
