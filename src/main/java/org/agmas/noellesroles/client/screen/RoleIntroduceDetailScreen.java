package org.agmas.noellesroles.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.widget.SelectedRoleIntroTextWidget;
import org.agmas.noellesroles.utils.RoleUtils;

public class RoleIntroduceDetailScreen extends Screen {
    private final Object role;
    private final Screen parent;

    protected RoleIntroduceDetailScreen(Object role, Screen parent) {
        super(Component.translatable("gui.roleintroduce.details.title", RoleUtils.getRoleOrModifierTypeName(role),
                RoleUtils.getRoleOrModifierNameWithColor(role)));
        this.role = role;
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private SelectedRoleIntroTextWidget createRoleIntroWidget(boolean isSmallUI) {
        // 绘制已选中的角色信息
        var widget = new SelectedRoleIntroTextWidget(Component.nullToEmpty(""), font);
        var selectedRole = role;
        if (selectedRole != null) {
            MutableComponent selectedText = Component
                    .translatable("gui.roleintroduce.details.tip",
                            RoleUtils.getRoleOrModifierTypeName(role),RoleUtils.getRoleOrModifierNameWithColor(selectedRole),
                            RoleUtils.getRoleOrModifierDescription(selectedRole).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD);
            String[] texts = selectedText.getString().split("\n");
            int wwidth = 200;
            for (var te : texts) {
                wwidth = Math.max(wwidth, font.width(te));
            }
            widget.setMessage(selectedText);
            widget.setCentered(true);
            widget.setHeight(font.lineHeight * Math.max(12, texts.length));
            widget.setWidth(wwidth);
            widget.setX(width / 2 - wwidth / 2);
            widget.setY(height / 2 - widget.getHeight() / 2);

            // List<FormattedCharSequence> wrappedDescription = font.split(roleDescription,
            // 280); // 使用适当的宽度

            // // 计算文本占用的垂直空间
            // int textHeight = 10; // selectedText 的高度
            // textHeight += wrappedDescription.size() * 10 + 2; // 每行描述 + 间隔

            // // 计算合适的 Y 坐标，确保不超出屏幕
            // int margin = 30; // 底部边距
            // int infoY = Math.max(height - margin - textHeight - 20, 100); // 最低在 y=100
            // 以上显示

            // // 计算合适的 X 和宽度，确保不超出屏幕
            // int infoWidth = Math.min(300, width - 2 * margin); // 最大宽度不超过屏幕减去边距
            // int infoX = (width - infoWidth) / 2; // 居中显示

            // int infoHeight = textHeight + 20; // 加上边框和间距

            // // 绘制选中角色文本
            // // guiGraphics.drawCenteredString(font, selectedText, width / 2, infoY,
            // 0x00FF00);

            // // 显示已选择角色的描述（如果空间足够）
            // for (int i = 0; i < wrappedDescription.size(); i++) {
            // FormattedCharSequence line = wrappedDescription.get(i);
            // guiGraphics.drawCenteredString(font, line, width / 2, infoY + 12 + i * 10,
            // 0xAAAAAA);
            // }
        }
        return widget;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染渐变背景
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 绘制标题背景
        int titleBgY = 20;
        int titleBgHeight = 40;
        guiGraphics.fillGradient(0, titleBgY, width, titleBgY + titleBgHeight,
                0x80000000, 0x00000000);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 绘制标题
        guiGraphics.drawCenteredString(font, this.title, width / 2, 30, 0xFFFFFF);

        // 绘制提示
        Component hint = Component.translatable("screen.roleintroduce.hint")
                .withStyle(ChatFormatting.GRAY);
        guiGraphics.drawCenteredString(font, hint, width / 2, height - 30, 0x888888);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 创建更现代化的渐变背景
        int topColor = 0xFF1A1A2E; // 深蓝色
        int bottomColor = 0xFF16213E; // 更深的蓝色
        guiGraphics.fillGradient(0, 0, width, height, topColor, bottomColor);

        // 添加一些装饰性粒子效果（可选）
        if (minecraft.level != null) {
            long time = minecraft.level.getGameTime();
            for (int i = 0; i < 20; i++) {
                float x = (float) ((time * 0.5 + i * 50) % width);
                float y = (float) ((Math.sin(time * 0.02 + i) * 20 + height / 2 + i * 10) % height);
                float size = 2 + (float) Math.sin(time * 0.1 + i) * 1;
                int alpha = (int) (100 + 155 * Math.sin(time * 0.05 + i));
                int starColor = (alpha << 24) | 0xFFFFFF;
                guiGraphics.fill((int) x, (int) y, (int) (x + size), (int) (y + size), starColor);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        boolean isSmallUI = false;
        if (this.height <= 400)
            isSmallUI = true;
        if (isSmallUI) {
        }
        var intro = createRoleIntroWidget(isSmallUI);
        addRenderableWidget(intro);
        var button = Button.builder(Component.translatable("gui.roleintroduce.details.close"), btn -> {
            this.onClose();
        }).bounds(this.width / 2 - 50, this.height / 2 + intro.getHeight() / 2 + 20, 100, 20).build();
        addRenderableWidget(button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 支持键盘导航
        if (keyCode == 257 || keyCode == 335) { // Enter 或 Numpad Enter
            this.onClose();
            return true;
        } else if (keyCode == 27) {// ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

}