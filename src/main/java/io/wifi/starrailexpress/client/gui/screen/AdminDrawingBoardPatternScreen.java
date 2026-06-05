package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.utils.ai.DrawingBoardRecognizer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 管理员画板 — 查看模版子界面
 * 展示所有已定义 pattern，点击按钮即可将对应 pattern 应用到画板上
 */
@Environment(EnvType.CLIENT)
public class AdminDrawingBoardPatternScreen extends Screen {

    private static final int PIXEL_SIZE = 10;
    private static final int CANVAS_SIZE = 16;
    private static final int PREVIEW_SIZE = CANVAS_SIZE * PIXEL_SIZE;
    private static final int GAP = 12;

    // 颜色调色板(与 DrawingBoardScreen 一致)
    private static final int[] PALETTE = {
        0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00,
        0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF,
        0xFFFF8000, 0xFF8000FF, 0xFF808080, 0xFFC0C0C0,
        0xFF800000, 0xFF008000, 0xFF000080, 0xFF804000,
    };
    private static final int BACKGROUND_WHITE = 16;
    private static final int BACKGROUND_WHITE_COLOR = 0xFFFFFFFF;

    private final AdminDrawingBoardScreen parentScreen;
    private final int[] categories;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_ROW = 4;
    private static final int ITEM_WIDTH = PREVIEW_SIZE + 50;  // 预览+名称区域宽度
    private int itemsPerPage;

    private Button btnBack;
    private Button btnPrev;
    private Button btnNext;

    public AdminDrawingBoardPatternScreen(AdminDrawingBoardScreen parentScreen) {
        super(Component.translatable("starrailexpress.admin_drawing_board.view_templates"));
        this.parentScreen = parentScreen;
        this.categories = DrawingBoardRecognizer.getInstance().getAvailableCategories();
    }

    @Override
    protected void init() {
        super.init();

        int totalItemWidth = ITEM_WIDTH + GAP;
        itemsPerPage = Math.max(1, (width - 40) / totalItemWidth * 2); // 每行4个，展示2行

        // 返回按钮
        btnBack = Button.builder(Component.translatable("starrailexpress.admin_drawing_board.back"), b -> {
            assert minecraft != null;
            minecraft.setScreen(parentScreen);
        }).bounds(width / 2 - 40, height - 30, 80, 20).build();
        addRenderableWidget(btnBack);

        // 上一页按钮
        if (categories.length > itemsPerPage) {
            btnPrev = Button.builder(Component.literal("<"), b -> {
                if (scrollOffset > 0) {
                    scrollOffset = Math.max(0, scrollOffset - itemsPerPage);
                    rebuildPatternWidgets();
                }
            }).bounds(10, height / 2 - 10, 20, 20).build();
            addRenderableWidget(btnPrev);

            btnNext = Button.builder(Component.literal(">"), b -> {
                if (scrollOffset + itemsPerPage < categories.length) {
                    scrollOffset += itemsPerPage;
                    rebuildPatternWidgets();
                }
            }).bounds(width - 30, height / 2 - 10, 20, 20).build();
            addRenderableWidget(btnNext);
        }

        rebuildPatternWidgets();
    }

    private void rebuildPatternWidgets() {
        // 清除除导航按钮外的所有widget
        clearWidgets();
        addRenderableWidget(btnBack);
        if (btnPrev != null) addRenderableWidget(btnPrev);
        if (btnNext != null) addRenderableWidget(btnNext);

        int startX = 20;
        int startY = 25;
        int colCount = Math.min(ITEMS_PER_ROW, width / (ITEM_WIDTH + GAP));

        for (int i = 0; i < Math.min(itemsPerPage, categories.length - scrollOffset); i++) {
            int catIndex = scrollOffset + i;
            int categoryId = categories[catIndex];
            String name = Component.translatable(DrawingBoardRecognizer.getClosestCategoryTranslationKey(categoryId)).getString();
            int col = i % colCount;
            int row = i / colCount;
            int x = startX + col * (ITEM_WIDTH + GAP);
            int y = startY + row * (PREVIEW_SIZE + 30);

            Button btnSelect = Button.builder(Component.literal(name), b -> {
                byte[][] pattern = DrawingBoardRecognizer.getInstance().getPatternForCategory(categoryId);
                if (pattern != null && parentScreen != null) {
                    parentScreen.applyPattern(pattern);
                    assert minecraft != null;
                    minecraft.setScreen(parentScreen);
                }
            }).bounds(x + 5, y + PREVIEW_SIZE + 2, PREVIEW_SIZE, 20).build();
            addRenderableWidget(btnSelect);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);

        // 渲染各pattern预览
        int startX = 20;
        int startY = 25;
        int colCount = Math.min(ITEMS_PER_ROW, width / (ITEM_WIDTH + GAP));

        for (int i = 0; i < Math.min(itemsPerPage, categories.length - scrollOffset); i++) {
            int catIndex = scrollOffset + i;
            int categoryId = categories[catIndex];
            byte[][] pattern = DrawingBoardRecognizer.getInstance().getPatternForCategory(categoryId);
            if (pattern == null) continue;

            int col = i % colCount;
            int row = i / colCount;
            int x = startX + col * (ITEM_WIDTH + GAP);
            int y = startY + row * (PREVIEW_SIZE + 30);

            // 画布背景
            graphics.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, 0xFF333333);

            for (int py = 0; py < CANVAS_SIZE; py++) {
                for (int px = 0; px < CANVAS_SIZE; px++) {
                    int colorIndex = pattern[py][px] & 0xFF;
                    int color;
                    if (colorIndex == BACKGROUND_WHITE) {
                        color = BACKGROUND_WHITE_COLOR;
                    } else {
                        color = colorIndex < PALETTE.length ? PALETTE[colorIndex] : 0xFF000000;
                    }
                    graphics.fill(
                        x + px * PIXEL_SIZE,
                        y + py * PIXEL_SIZE,
                        x + (px + 1) * PIXEL_SIZE,
                        y + (py + 1) * PIXEL_SIZE,
                        color
                    );
                }
            }

            // 名称
            String name = Component.translatable(DrawingBoardRecognizer.getClosestCategoryTranslationKey(categoryId)).getString();
            graphics.drawString(font, name, x + PREVIEW_SIZE + 5, y + 5, 0xAAAAAA);
        }

        // 页码信息
        if (categories.length > itemsPerPage) {
            int currentPage = scrollOffset / itemsPerPage + 1;
            int totalPages = (int) Math.ceil((double) categories.length / itemsPerPage);
            graphics.drawCenteredString(font, currentPage + " / " + totalPages, width / 2, height - 45, 0x888888);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
