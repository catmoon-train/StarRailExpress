package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.item.AdminDrawingBoardItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 管理员模式画板界面
 * 继承普通画板所有功能，并在"确定"按钮下方增加：
 * 1. "查看模版"按钮 — 打开子界面，展示所有 pattern
 * 2. "输出为模版"按钮 — 将当前画作转为代码pattern，输出到聊天栏并复制
 */
@Environment(EnvType.CLIENT)
public class AdminDrawingBoardScreen extends DrawingBoardScreen {

    private Button btnViewTemplates;
    private Button btnExportTemplate;

    public AdminDrawingBoardScreen(ItemStack stack) {
        super(stack);
    }

    @Override
    protected void init() {
        // 调用父类 init 生成基础按钮
        super.init();

        // 需要重新布局：在确定(btnGenerate)下方插入两个新按钮，关闭按钮下移
        // 父类按钮在 super.init() 中已创建，位置基于 btnY + offset*(BUTTON_HEIGHT+5)
        // btnGenerate 在 offset=3, btnClose 在 offset=4
        // 现在：btnGenerate(3) -> btnViewTemplates(4) -> btnExportTemplate(5) -> btnClose(6)

        // 复用父类的颜色面板和布局参数
        int btnX = colorPanelX;
        int btnY = colorPanelY + (4 * 20) + 20; // 与父类计算一致：COLORS_PER_ROW=4, COLOR_BUTTON_SIZE=20

        int BUTTON_HEIGHT = 20;
        int BUTTON_WIDTH = 80;

        // 删除旧的 btnClose，重新放置在更下方的位置
        removeWidget(btnClose);
        if (btnGenerate != null) removeWidget(btnGenerate);

        // 重新添加"确定"按钮
        btnGenerate = Button.builder(Component.translatable("starrailexpress.drawing_board.confirm"), b -> generateItem())
                .bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnGenerate);

        // "查看模版"按钮 — 打开模版选择子界面
        btnViewTemplates = Button.builder(Component.translatable("starrailexpress.admin_drawing_board.view_templates"), b -> {
            assert minecraft != null;
            minecraft.setScreen(new AdminDrawingBoardPatternScreen(this));
        }).bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 4, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnViewTemplates);

        // "输出为模版"按钮 — 将画作转为代码pattern输出到聊天栏并复制
        btnExportTemplate = Button.builder(Component.translatable("starrailexpress.admin_drawing_board.export_template"), b -> {
            exportTemplate();
        }).bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 5, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnExportTemplate);

        // 关闭按钮移到最下方
        btnClose = Button.builder(Component.translatable("starrailexpress.drawing_board.close"), b -> onClose())
                .bounds(btnX, btnY + (BUTTON_HEIGHT + 5) * 6, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(btnClose);
    }

    /**
     * 获取当前画布的16x16像素数据的深拷贝
     */
    private byte[][] getCurrentPixels() {
        byte[][] pixels = new byte[16][16];
        for (int y = 0; y < 16; y++) {
            System.arraycopy(canvas[y], 0, pixels[y], 0, 16);
        }
        return pixels;
    }

    /**
     * 将当前画布内容输出为Java pattern代码，发送到玩家聊天栏并复制到剪贴板
     */
    private void exportTemplate() {
        byte[][] pixels = getCurrentPixels();

        StringBuilder sb = new StringBuilder();
        sb.append("byte[][] p = new byte[16][16];\n");

        // 仅输出非背景(非16)的像素
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = pixels[y][x] & 0xFF;
                if (color != 16) { // BACKGROUND_WHITE
                    sb.append("p[").append(y).append("][").append(x).append("] = ").append(color).append(";\n");
                }
            }
        }

        sb.append("// pattern code end");

        String patternCode = sb.toString();

        // 复制到剪贴板
        assert minecraft != null;
        minecraft.keyboardHandler.setClipboard(patternCode);

        // 输出到聊天栏
        if (minecraft.player != null) {
            // 发送带点击复制功能的聊天消息
            net.minecraft.network.chat.Component header = Component
                    .translatable("starrailexpress.admin_drawing_board.template_copied")
                    .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x00FF00)));
            net.minecraft.network.chat.Component codeComponent = Component.literal(patternCode)
                    .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, patternCode)));

            minecraft.player.displayClientMessage(header, false);
            minecraft.player.displayClientMessage(codeComponent, false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 检查是否仍持有管理员画板
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            if (!(mainHand.getItem() instanceof AdminDrawingBoardItem) &&
                !(offHand.getItem() instanceof AdminDrawingBoardItem)) {
                this.onClose();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
