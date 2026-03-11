package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.agmas.noellesroles.screen.DetectiveInspectScreenHandler;

/**
 * 私家侦探审查界面的客户端屏幕
 * 
 * 只读界面，显示目标玩家的物品栏内容。
 * 玩家无法与物品进行交互。
 */
public class DetectiveInspectScreen extends AbstractContainerScreen<DetectiveInspectScreenHandler> {
    
    // 背景纹理 - 使用原版箱子界面纹理
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");
    
    // 背景尺寸
    private static final int BACKGROUND_WIDTH = 176;
    @SuppressWarnings("unused")
    private static final int BACKGROUND_HEIGHT = 166;
    
    public DetectiveInspectScreen(DetectiveInspectScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        
        // 设置背景尺寸（4行物品栏）
        this.imageWidth = BACKGROUND_WIDTH;
        this.imageHeight = 89; // 4行 + 边框
        
        // 调整标题位置
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94;
    }
    
    @Override
    protected void init() {
        super.init();
        // 隐藏玩家物品栏标题（因为这是只读界面，不显示查看者的物品栏）
        this.inventoryLabelY = -9999; // 移出屏幕
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        // 渲染物品提示
        this.renderTooltip(context, mouseX, mouseY);
    }
    
    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // 绘制背景 - 使用通用箱子纹理的上半部分（4行）
        // 上边框
        context.blit(TEXTURE, x, y, 0, 0, this.imageWidth, 17);
        // 物品槽区域（4行）
        for (int row = 0; row < 4; row++) {
            context.blit(TEXTURE, x, y + 17 + row * 18, 0, 17, this.imageWidth, 18);
        }
        // 下边框
        context.blit(TEXTURE, x, y + 17 + 4 * 18, 0, 215, this.imageWidth, 7);
    }
    
    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        // 绘制标题
        context.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        
        // 绘制提示信息 - "只读模式"
        Component readOnlyText = Component.translatable("screen.noellesroles.detective.read_only");
        int textWidth = this.font.width(readOnlyText);
        context.drawString(this.font, readOnlyText, 
            this.imageWidth - textWidth - 8, this.titleLabelY, 0x808080, false);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 禁止所有点击操作（除了关闭界面）
        // 允许关闭按钮点击
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // 禁止拖拽操作
        return false;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 禁止所有鼠标释放操作
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 只允许关闭界面的按键
        if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}