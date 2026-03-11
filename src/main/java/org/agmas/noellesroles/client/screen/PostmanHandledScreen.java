package org.agmas.noellesroles.client.screen;

import org.agmas.noellesroles.component.PostmanPlayerComponent;
import org.agmas.noellesroles.packet.PostmanC2SPacket;
import org.agmas.noellesroles.screen.PostmanScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 邮差传递界面 - 基于 HandledScreen
 *
 * 布局：
 * - 顶部：文字说明
 * - 中间：一个槽位（用于放入物品）
 * - 底部：快捷栏 + 确认按钮
 */
public class PostmanHandledScreen extends AbstractContainerScreen<PostmanScreenHandler> {
    
    // 使用漏斗界面纹理作为基础
    @SuppressWarnings("unused")
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/hopper.png");
    
    private PostmanPlayerComponent postmanComponent;
    private Button confirmButton;
    
    public PostmanHandledScreen(PostmanScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.inventoryLabelY = this.imageHeight - 94;
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.postmanComponent = PostmanPlayerComponent.KEY.get(minecraft.player);
        
        // 确认交换按钮 - 放在槽位右侧
        int buttonWidth = 70;
        int buttonX = this.leftPos + 106;  // 槽位右侧
        int buttonY = this.topPos + 32;   // 与槽位对齐
        
        this.confirmButton = Button.builder(
            Component.translatable("screen.noellesroles.postman.confirm"),
            button -> onConfirm()
        )
        .bounds(buttonX, buttonY, buttonWidth, 20)
        .build();
        
        this.addRenderableWidget(confirmButton);
        
        updateButtonState();
    }
    
    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // 绘制简洁的背景
        // 上半部分：信息和交换区域
        context.fill(x, y, x + this.imageWidth, y + 90, 0xC0101010);
        
        // 下半部分：快捷栏区域
        context.fill(x, y + 90, x + this.imageWidth, y + this.imageHeight, 0xC0202020);
        
        // 绘制分隔线
        context.fill(x, y + 89, x + this.imageWidth, y + 91, 0xFF8B8B8B);
        
        // 绘制中央槽位的边框（高亮显示）
        int slotX = x + 79;
        int slotY = y + 34;
        // 绿色边框
        context.fill(slotX - 1, slotY - 1, slotX + 18, slotY, 0xFF55FF55);
        context.fill(slotX - 1, slotY + 17, slotX + 18, slotY + 18, 0xFF55FF55);
        context.fill(slotX - 1, slotY, slotX, slotY + 17, 0xFF55FF55);
        context.fill(slotX + 17, slotY, slotX + 18, slotY + 17, 0xFF55FF55);
        // 槽位背景
        context.fill(slotX, slotY, slotX + 17, slotY + 17, 0xFF8B8B8B);
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // 每次渲染时重新获取组件，确保读取最新数据
        this.postmanComponent = PostmanPlayerComponent.KEY.get(minecraft.player);
        
        if (postmanComponent == null || !postmanComponent.isDeliveryActive()) {
            this.onClose();
            return;
        }
        
        // 绘制标题
        Component title = Component.translatable("screen.noellesroles.postman.title")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        context.drawString(font, title,
            this.leftPos + this.imageWidth / 2 - font.width(title) / 2,
            this.topPos + 6, 0xFFFFFF, true);
        
        // 绘制目标玩家名称
        String targetName = postmanComponent.targetName;
        Component targetText = Component.translatable("screen.noellesroles.postman.trade_with",Component.literal(targetName).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY);
        context.drawString(font, targetText,
            this.leftPos + this.imageWidth / 2 - font.width(targetText) / 2,
            this.topPos + 20, 0xFFFFFF, true);
        
        // 绘制槽位上方的文字说明
        Component slotLabel = Component.translatable("screen.general.put_item").withStyle(ChatFormatting.WHITE);
        context.drawString(font, slotLabel,
            this.leftPos + 80 + 8 - font.width(slotLabel) / 2,
            this.topPos + 55, 0xAAAAAA, true);
        
        // 绘制确认状态 - 直接读取组件中的最新值
        boolean isReceiver = postmanComponent.isReceiver;
        boolean myConfirmed = isReceiver ? postmanComponent.targetConfirmed : postmanComponent.postmanConfirmed;
        boolean otherConfirmed = isReceiver ? postmanComponent.postmanConfirmed : postmanComponent.targetConfirmed;
        
        // 显示双方确认状态
        Component myStatus = myConfirmed ?
            Component.translatable("screen.noellesroles.postman.you_confirm").withStyle(ChatFormatting.GREEN) :
            Component.translatable("screen.noellesroles.postman.you_not_confirm").withStyle(ChatFormatting.RED);
        Component otherStatus = otherConfirmed ?
            Component.translatable("screen.noellesroles.postman.other_confirm",targetName).withStyle(ChatFormatting.GREEN) :
            Component.translatable("screen.noellesroles.postman.other_not_confirm",targetName).withStyle(ChatFormatting.RED);
        
        context.drawString(font, myStatus,
            this.leftPos + 10, this.topPos + 65, 0xFFFFFF, true);
        context.drawString(font, otherStatus,
            this.leftPos + 10, this.topPos + 77, 0xFFFFFF, true);
        
        // 绘制提示信息 - 在快捷栏下方
        Component hint;
        if (postmanComponent.isBothConfirmed()) {
            hint = Component.translatable("screen.noellesroles.postman.exchanging")
                .withStyle(ChatFormatting.GREEN);
        } else {
            hint = Component.translatable("screen.noellesroles.postman.hint")
                .withStyle(ChatFormatting.GRAY);
        }
        context.drawString(font, hint,
            this.leftPos + this.imageWidth / 2 - font.width(hint) / 2,
            this.topPos + this.imageHeight + 5, 0xFFFFFF, true);
        
        // 更新按钮状态
        updateButtonState();
        
        this.renderTooltip(context, mouseX, mouseY);
    }
    
    /**
     * 更新按钮状态
     */
    private void updateButtonState() {
        if (postmanComponent == null || confirmButton == null) return;
        
        boolean isReceiver = postmanComponent.isReceiver;
        boolean myConfirmed = isReceiver ? postmanComponent.targetConfirmed : postmanComponent.postmanConfirmed;
        boolean bothConfirmed = postmanComponent.isBothConfirmed();
        
        // 如果已经确认或双方都确认，禁用确认按钮
        confirmButton.active = !myConfirmed && !bothConfirmed;
        
        // 更新按钮文字
        if (myConfirmed) {
            confirmButton.setMessage(Component.translatable("screen.noellesroles.postman.confirmed").withStyle(ChatFormatting.GRAY));
        } else {
            confirmButton.setMessage(Component.translatable("screen.noellesroles.postman.confirm"));
        }
    }
    
    /**
     * 确认交换
     */
    private void onConfirm() {
        // 使用组件中的目标玩家 UUID（更可靠）
        if (postmanComponent != null && postmanComponent.deliveryTarget != null) {
            ClientPlayNetworking.send(new PostmanC2SPacket(
                PostmanC2SPacket.Action.CONFIRM,
                postmanComponent.deliveryTarget
            ));
        } else if (menu.getTargetPlayerUuid() != null) {
            // 回退使用 handler 中的 UUID
            ClientPlayNetworking.send(new PostmanC2SPacket(
                PostmanC2SPacket.Action.CONFIRM,
                menu.getTargetPlayerUuid()
            ));
        }
        updateButtonState();
    }
    
    @Override
    public void containerTick() {
        super.containerTick();
        
        // 每次 tick 重新获取组件，确保读取最新同步数据
        this.postmanComponent = PostmanPlayerComponent.KEY.get(minecraft.player);
        
        updateButtonState();
        
        // 检查传递是否仍然激活
        if (postmanComponent != null && !postmanComponent.isDeliveryActive()) {
            this.onClose();
        }
        
        // 如果双方都确认，服务端会处理交换逻辑并关闭界面
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}