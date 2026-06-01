package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.item.DrawingBoardItem;
import io.wifi.starrailexpress.network.DrawingBoardPayload;
import io.wifi.starrailexpress.utils.ai.DrawingBoardRecognizer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 画板界面
 * 16x16 像素画布，带 16 种颜色选择和图像识别功能
 */
@Environment(EnvType.CLIENT)
public class DrawingBoardScreen extends Screen {

    private static final int CANVAS_SIZE = 16;
    private static final int PIXEL_SIZE = 16;
    private static final int CANVAS_PIXEL_SIZE = CANVAS_SIZE * PIXEL_SIZE;

    private static final int COLOR_PANEL_WIDTH = 180;
    private static final int COLOR_BUTTON_SIZE = 20;
    private static final int COLORS_PER_ROW = 4;

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;

    private final byte[][] canvas = new byte[CANVAS_SIZE][CANVAS_SIZE];
    private int selectedColor = 0;
    private int selectedTool = 0;
    private ItemStack boardStack;

    private static final int[] PALETTE = {
        0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00,
        0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF,
        0xFFFF8000, 0xFF8000FF, 0xFF808080, 0xFFC0C0C0,
        0xFF800000, 0xFF008000, 0xFF000080, 0xFF804000,
    };

    // 背景白色ID - 纯白色，画布默认使用，不出现在调色盘中
    private static final int BACKGROUND_WHITE = 16;
    // 背景白色实际颜色值
    private static final int BACKGROUND_WHITE_COLOR = 0xFFFFFFFF;

    private int canvasX, canvasY;
    private int colorPanelX, colorPanelY;

    private Button btnClear;
    private Button btnGenerate;
    private Button btnBrush;
    private Button btnEraser;
    private Button btnClose;

    private boolean isDrawing = false;
    private int lastRecognizeResult = DrawingBoardRecognizer.UNKNOWN;
    private String lastRecognizeMessage = "";
    private String lastHint = "";  // 提示信息
    private double lastSimilarityPercent = 0.0; // 上次识别的相似度百分比

    // 保底机制：追踪连续识别次数
    private static final int FALLBACK_THRESHOLD = 4;  // 连续4次相同类别触发保底
    private int consecutiveSameCategoryCount = 0;
    private int lastClosestCategory = DrawingBoardRecognizer.UNKNOWN;
    private boolean canvasModifiedSinceLastRecognize = false;  // 追踪画作是否被修改
    private int displayClosestCategory = DrawingBoardRecognizer.UNKNOWN;  // 显示给玩家的最近类别

    public DrawingBoardScreen() {
        super(Component.translatable("starrailexpress.drawing_board.title"));
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                canvas[y][x] = (byte) BACKGROUND_WHITE;
            }
        }
    }

    public DrawingBoardScreen(ItemStack stack) {
        this();
        this.boardStack = stack;

        if (stack != null && stack.getItem() instanceof DrawingBoardItem) {
            byte[][] savedPixels = DrawingBoardItem.getPixelData(stack);
            for (int y = 0; y < CANVAS_SIZE; y++) {
                for (int x = 0; x < CANVAS_SIZE; x++) {
                    canvas[y][x] = savedPixels[y][x];
                }
            }
            this.selectedColor = DrawingBoardItem.getSelectedColor(stack);
        }
    }

    @Override
    protected void init() {
        super.init();

        int totalWidth = CANVAS_PIXEL_SIZE + COLOR_PANEL_WIDTH + 20;
        canvasX = (width - totalWidth) / 2;
        canvasY = (height - CANVAS_PIXEL_SIZE) / 2;

        colorPanelX = canvasX + CANVAS_PIXEL_SIZE + 20;
        colorPanelY = canvasY;

        int btnX = colorPanelX;
        int btnY = colorPanelY + (COLORS_PER_ROW * COLOR_BUTTON_SIZE) + 20;

        btnBrush = Button.builder(Component.translatable("starrailexpress.drawing_board.brush"), b -> {
            selectedTool = 0;
            updateToolButtons();
        }).bounds(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnBrush);

        btnEraser = Button.builder(Component.translatable("starrailexpress.drawing_board.eraser"), b -> {
            selectedTool = 1;
            updateToolButtons();
        }).bounds(btnX, btnY + BUTTON_HEIGHT + 5, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnEraser);

        btnClear = Button.builder(Component.translatable("starrailexpress.drawing_board.clear"), b -> clearCanvas())
                .bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnClear);

        btnGenerate = Button.builder(Component.translatable("starrailexpress.drawing_board.confirm"), b -> generateItem())
                .bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnGenerate);

        btnClose = Button.builder(Component.translatable("starrailexpress.drawing_board.close"), b -> onClose())
                .bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 4, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnClose);

        updateToolButtons();
    }

    private void updateToolButtons() {
        btnBrush.setMessage(Component.translatable(selectedTool == 0 ?
                "starrailexpress.drawing_board.brush_selected" :
                "starrailexpress.drawing_board.brush"));
        btnEraser.setMessage(Component.translatable(selectedTool == 1 ?
                "starrailexpress.drawing_board.eraser_selected" :
                "starrailexpress.drawing_board.eraser"));
    }

    private void clearCanvas() {
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                canvas[y][x] = (byte) BACKGROUND_WHITE;
            }
        }
        lastRecognizeResult = DrawingBoardRecognizer.UNKNOWN;
        lastRecognizeMessage = "";
        lastHint = "";
        // 重置保底计数器
        consecutiveSameCategoryCount = 0;
        lastClosestCategory = DrawingBoardRecognizer.UNKNOWN;
        canvasModifiedSinceLastRecognize = false;
    }

    private void generateItem() {
        // 识别（带保底机制）
        DrawingBoardRecognizer.RecognizeResult result = DrawingBoardRecognizer.getInstance().recognizeWithHint(canvas);
        int recognizedCategory = result.category;

        if (recognizedCategory == DrawingBoardRecognizer.UNKNOWN) {
            // 识别失败，检查保底机制
            int closestCategory = result.closestCategory;
            displayClosestCategory = closestCategory;

            if (closestCategory != DrawingBoardRecognizer.UNKNOWN) {
                if (closestCategory == lastClosestCategory && canvasModifiedSinceLastRecognize) {
                    // 只有画作被修改过，且类别相同时才增加保底计数
                    consecutiveSameCategoryCount++;
                    if (consecutiveSameCategoryCount >= FALLBACK_THRESHOLD) {
                        // 保底成功！
                        recognizedCategory = closestCategory;
                    }
                } else if (closestCategory != lastClosestCategory) {
                    // 换了类别，重置计数器
                    consecutiveSameCategoryCount = 1;
                    lastClosestCategory = closestCategory;
                    canvasModifiedSinceLastRecognize = false;
                }
            } else {
                consecutiveSameCategoryCount = 0;
                lastClosestCategory = DrawingBoardRecognizer.UNKNOWN;
                canvasModifiedSinceLastRecognize = false;
            }
        } else {
            // 识别成功，重置保底计数器
            consecutiveSameCategoryCount = 0;
            lastClosestCategory = DrawingBoardRecognizer.UNKNOWN;
            canvasModifiedSinceLastRecognize = false;
            displayClosestCategory = DrawingBoardRecognizer.UNKNOWN;
        }

        lastRecognizeResult = recognizedCategory;
        lastHint = result.hint;  // 保存提示信息
        lastSimilarityPercent = result.similarity;

        if (recognizedCategory == DrawingBoardRecognizer.UNKNOWN) {
            // 识别失败，只显示消息，不关闭界面
            lastRecognizeMessage = Component.translatable("starrailexpress.drawing_board.recognize.fail").getString();
        } else {
            // 识别成功，显示消息并关闭界面
            lastRecognizeMessage = Component.translatable("starrailexpress.drawing_board.recognize.success").getString();
            // 发送识别请求到服务端（会消耗画板并给予物品），携带识别结果
            ClientPlayNetworking.send(new DrawingBoardPayload.DrawBoardRecognizePayload(recognizedCategory));
            // 关闭界面
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.fillGradient(0, 0, width, height, 0xC0102030, 0xD0081018);

        graphics.drawCenteredString(font, title, width / 2, 10, 0xEEEEFF);

        graphics.fill(canvasX - 2, canvasY - 2, canvasX + CANVAS_PIXEL_SIZE + 2, canvasY + CANVAS_PIXEL_SIZE + 2, 0xFF333333);

        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                int colorIndex = canvas[y][x] & 0xFF;
                int color;
                if (colorIndex == BACKGROUND_WHITE) {
                    // 背景白色使用纯白
                    color = BACKGROUND_WHITE_COLOR;
                } else {
                    color = colorIndex < PALETTE.length ? PALETTE[colorIndex] : 0xFF000000;
                }
                graphics.fill(
                        canvasX + x * PIXEL_SIZE,
                        canvasY + y * PIXEL_SIZE,
                        canvasX + (x + 1) * PIXEL_SIZE,
                        canvasY + (y + 1) * PIXEL_SIZE,
                        color
                );
            }
        }

        for (int i = 0; i <= CANVAS_SIZE; i++) {
            int pos = canvasX + i * PIXEL_SIZE;
            graphics.fill(pos, canvasY, pos + 1, canvasY + CANVAS_PIXEL_SIZE, 0x44000000);
            pos = canvasY + i * PIXEL_SIZE;
            graphics.fill(canvasX, pos, canvasX + CANVAS_PIXEL_SIZE, pos + 1, 0x44000000);
        }

        graphics.drawString(font, Component.translatable("starrailexpress.drawing_board.color_palette").getString(), colorPanelX, colorPanelY - 15, 0xFFFFFF);

        for (int i = 0; i < PALETTE.length; i++) {
            int row = i / COLORS_PER_ROW;
            int col = i % COLORS_PER_ROW;
            int bx = colorPanelX + col * (COLOR_BUTTON_SIZE + 4);
            int by = colorPanelY + row * (COLOR_BUTTON_SIZE + 4);

            graphics.fill(bx - 2, by - 2, bx + COLOR_BUTTON_SIZE + 2, by + COLOR_BUTTON_SIZE + 2,
                    i == selectedColor ? 0xFFFFFFFF : 0xFF888888);
            graphics.fill(bx, by, bx + COLOR_BUTTON_SIZE, by + COLOR_BUTTON_SIZE, PALETTE[i]);
        }

        int hoverColor = getColorAtMouse(mouseX, mouseY);
        if (hoverColor >= 0) {
            String tooltip = "Pixel: (" + hoverColor + ") Color: #" + String.format("%06X", PALETTE[hoverColor] & 0xFFFFFF);
            graphics.drawString(font, tooltip, mouseX + 10, mouseY - 10, 0xFFFFFF);
        }

        int infoY = canvasY + CANVAS_PIXEL_SIZE + 10;
        graphics.drawString(font, Component.translatable("starrailexpress.drawing_board.tool", selectedTool == 0 ?
                Component.translatable("starrailexpress.drawing_board.brush").getString() :
                Component.translatable("starrailexpress.drawing_board.eraser").getString()), canvasX, infoY, 0xAAAAAA);

        if (!lastRecognizeMessage.isEmpty()) {
            int color = lastRecognizeResult != DrawingBoardRecognizer.UNKNOWN ? 0x00FF00 : 0xFF6060;
            graphics.drawString(font, lastRecognizeMessage, canvasX, infoY + 15, color);

            // 识别失败时显示提示信息
            if (lastRecognizeResult == DrawingBoardRecognizer.UNKNOWN && !lastHint.isEmpty()) {
                String hintText = Component.translatable(lastHint).getString();
                graphics.drawString(font, hintText, canvasX, infoY + 45, 0xFFAA00);

                // 如果有最近类别，再显示类别名
                if (displayClosestCategory != DrawingBoardRecognizer.UNKNOWN) {
                    String categoryName = Component.translatable(DrawingBoardRecognizer.getClosestCategoryTranslationKey(displayClosestCategory)).getString();
                    graphics.drawString(font, categoryName, canvasX, infoY + 60, 0xFFAA00);
                }
                // 显示相似度百分比（0%失败，100%成功）
                String simText = String.format("相似度: %.0f%%", lastSimilarityPercent);
                graphics.drawString(font, simText, canvasX, infoY + 75, 0xFFAA00);
            }
        }

        graphics.drawString(font, Component.translatable("starrailexpress.drawing_board.hint").getString(), canvasX, infoY + 30, 0x888888);
    }

    private int getColorAtMouse(int mouseX, int mouseY) {
        if (mouseX >= colorPanelX && mouseX < colorPanelX + COLORS_PER_ROW * (COLOR_BUTTON_SIZE + 4)) {
            int col = (mouseX - colorPanelX) / (COLOR_BUTTON_SIZE + 4);
            int row = (mouseY - colorPanelY) / (COLOR_BUTTON_SIZE + 4);
            if (row >= 0 && col >= 0 && row < 4 && col < 4) {
                return row * COLORS_PER_ROW + col;
            }
        }
        return -1;
    }

    private int getPixelAtMouse(int mouseX, int mouseY) {
        int px = (mouseX - canvasX) / PIXEL_SIZE;
        int py = (mouseY - canvasY) / PIXEL_SIZE;
        if (px >= 0 && px < CANVAS_SIZE && py >= 0 && py < CANVAS_SIZE) {
            return py * CANVAS_SIZE + px;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int colorIndex = getColorAtMouse((int) mouseX, (int) mouseY);
        if (colorIndex >= 0) {
            selectedColor = colorIndex;
            return true;
        }

        int pixelIndex = getPixelAtMouse((int) mouseX, (int) mouseY);
        if (pixelIndex >= 0) {
            int x = pixelIndex % CANVAS_SIZE;
            int y = pixelIndex / CANVAS_SIZE;

            if (button == 0) {
                if (selectedTool == 0) {
                    canvas[y][x] = (byte) selectedColor;
                } else {
                    canvas[y][x] = (byte) BACKGROUND_WHITE;
                }
                canvasModifiedSinceLastRecognize = true;
                isDrawing = true;
                return true;
            } else if (button == 1) {
                canvas[y][x] = (byte) BACKGROUND_WHITE;
                canvasModifiedSinceLastRecognize = true;
                isDrawing = true;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDrawing) {
            isDrawing = false;
            saveCanvas();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int pixelIndex = getPixelAtMouse((int) mouseX, (int) mouseY);
        if (pixelIndex >= 0) {
            int x = pixelIndex % CANVAS_SIZE;
            int y = pixelIndex / CANVAS_SIZE;

            if (button == 0) {
                if (selectedTool == 0) {
                    canvas[y][x] = (byte) selectedColor;
                } else {
                    canvas[y][x] = (byte) BACKGROUND_WHITE;
                }
            } else if (button == 1) {
                canvas[y][x] = (byte) BACKGROUND_WHITE;
            }
            canvasModifiedSinceLastRecognize = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = (int) verticalAmount;
        if (delta > 0) {
            selectedColor = (selectedColor + 1) % 16;
        } else if (delta < 0) {
            selectedColor = (selectedColor + 15) % 16;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            saveCanvas();
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveCanvas() {
        // 保存到本地物品
        if (boardStack != null && boardStack.getItem() instanceof DrawingBoardItem) {
            DrawingBoardItem.savePixelData(boardStack, canvas);
            DrawingBoardItem.setSelectedColor(boardStack, selectedColor);
        }

        // 同步到服务端
        byte[] pixels = new byte[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[y * 16 + x] = canvas[y][x];
            }
        }
        ClientPlayNetworking.send(new DrawingBoardPayload.DrawBoardSavePayload(selectedColor, pixels));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
