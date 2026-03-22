package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.widget.SelectedRoleIntroTextWidget;
import org.agmas.noellesroles.packet.GamblerSelectRoleC2SPacket;
import org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;

public class GamblerScreen extends Screen {
    private final GamblerPlayerComponent component;
    private final List<SRERole> availableRoles = new ArrayList<>();
    private SRERole selectedRole;
    private int CARDS_PER_ROW = 5;
    private int ROWS_PER_PAGE = 1;
    private int CARDS_PER_PAGE = 5;
    // 搜索框
    private EditBox searchWidget = null;
    private String searchContent = null;
    private int totalPages = 0;
    private static final int marginBottomY = 32;

    private List<RoleCardWidget> roleCardWidgets = new ArrayList<>();

    // 卡牌尺寸
    private int CARD_WIDTH = 60;
    private int CARD_HEIGHT = 80;
    private int CARD_SPACING_X = 15;
    private int CARD_SPACING_Y = 15;

    private int currentRolePage = 0; // 当前角色页码
    private Button prevPageButton;
    private Button nextPageButton;
    private SelectedRoleIntroTextWidget selectedRoleIntroWidget;

    /**
     * 选择阶段：搜索职业
     */
    private void onRoleSearch(String text) {
        if (text.isEmpty()) {
            searchContent = null;
        } else {
            searchContent = text;
        }
        currentRolePage = 0;
        totalPages = 0;
        refreshRoleSelection();
    }

    public GamblerScreen(Player player) {
        super(Component.translatable("gui.noellesroles.gambler.title"));
        this.component = GamblerPlayerComponent.KEY.get(player);

        // 加载可用角色
        for (ResourceLocation roleId : component.availableRoles) {
            for (SRERole role : Noellesroles.getAllRoles()) {
                if (role.identifier().equals(roleId)) {
                    availableRoles.add(role);
                    break;
                }
            }
        }

        if (component.selectedRole != null) {
            for (SRERole role : availableRoles) {
                if (role.identifier().equals(component.selectedRole)) {
                    selectedRole = role;
                    break;
                }
            }
        }
    }

    /**
     * 初始化角色选择阶段
     */
    private void initRoleSelection() {
        if (availableRoles.isEmpty()) {
            onClose();
            return;
        }
        if (selectedRoleIntroWidget != null) {
            selectedRoleIntroWidget = null;
            this.removeWidget(selectedRoleIntroWidget);
        }
        // 过滤搜索结果
        List<SRERole> filteredRoles = new ArrayList<>();
        for (SRERole role : availableRoles) {
            String roleName = RoleUtils.getRoleName(role).getString();
            if (searchContent == null || roleName.toLowerCase().contains(searchContent.toLowerCase())
                    || role.identifier().toString().contains(searchContent.toLowerCase())) {
                filteredRoles.add(role);
            }
        }

        // 翻页相关
        // 动态定义
        CARDS_PER_ROW = 5; // 每行最多5个卡牌
        ROWS_PER_PAGE = 1; // 每页最多1行
        CARDS_PER_PAGE = CARDS_PER_ROW * ROWS_PER_PAGE; // 每页最多8个卡牌

        CARD_WIDTH = 100;
        CARD_HEIGHT = 120;
        CARD_SPACING_X = 15;
        CARD_SPACING_Y = 15;
        // BIG: 854/492
        // SMALL: 427/240
        boolean isSmallUI = false;
        if (this.height <= 400)
            isSmallUI = true;
        if (isSmallUI) {
            CARD_WIDTH = 60;
            CARD_HEIGHT = 60;
        }
        // LoggerFactory.getLogger("gamblerScreen").info("[W/H] " + this.width + "/" +
        // this.height);
        totalPages = (int) Math.ceil(filteredRoles.size() / (double) CARDS_PER_PAGE);

        // 确保当前页码有效
        if (totalPages > 0) {
            currentRolePage = Mth.clamp(currentRolePage, 0, totalPages - 1);
        } else {
            currentRolePage = 0;
        }

        // 计算当前页的角色范围
        int startIndex = currentRolePage * CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + CARDS_PER_PAGE, filteredRoles.size());

        // 计算布局 - 居中显示卡牌
        int totalCardsWidth = CARDS_PER_ROW * CARD_WIDTH + (CARDS_PER_ROW - 1) * CARD_SPACING_X;
        int totalCardsHeight = ROWS_PER_PAGE * CARD_HEIGHT + (ROWS_PER_PAGE - 1) * CARD_SPACING_Y;
        int startX = (width - totalCardsWidth) / 2;
        int startY = (height - totalCardsHeight) / 2 - (isSmallUI ? 20 : 30); // 向上偏移给下部分提示留空间

        // 清空旧的卡牌组件
        for (RoleCardWidget widget : roleCardWidgets) {
            this.removeWidget(widget);
        }
        roleCardWidgets.clear();

        // 添加当前页的角色卡牌
        for (int i = startIndex; i < endIndex; i++) {
            SRERole role = filteredRoles.get(i);
            int indexOnPage = i - startIndex;
            int col = indexOnPage % CARDS_PER_ROW;
            int row = indexOnPage / CARDS_PER_ROW;

            int x = startX + col * (CARD_WIDTH + CARD_SPACING_X);
            int y = startY + row * (CARD_HEIGHT + CARD_SPACING_Y);

            RoleCardWidget card = new RoleCardWidget(x, y, CARD_WIDTH, CARD_HEIGHT, role, i);
            roleCardWidgets.add(card);
            addRenderableWidget(card);
        }

        // 添加翻页按钮
        int buttonWidth = 80;
        int buttonHeight = 25;
        int buttonY = startY + totalCardsHeight + (isSmallUI ? 10 : 20);

        // 移除旧的翻页按钮
        if (prevPageButton != null) {
            this.removeWidget(prevPageButton);
        }
        if (nextPageButton != null) {
            this.removeWidget(nextPageButton);
        }

        // 上一页按钮
        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.gambler.prev_page"),
                button -> {
                    if (currentRolePage > 0) {
                        currentRolePage--;
                        refreshRoleSelection();
                    }
                })
                .bounds(width / 2 - buttonWidth - 30, buttonY, buttonWidth, buttonHeight)
                .build();
        prevPageButton.active = currentRolePage > 0;
        addRenderableWidget(prevPageButton);

        // 下一页按钮
        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.gambler.next_page"),
                button -> {
                    if (currentRolePage < totalPages - 1) {
                        currentRolePage++;
                        refreshRoleSelection();
                    }
                })
                .bounds(width / 2 + 30, buttonY, buttonWidth, buttonHeight)
                .build();
        nextPageButton.active = currentRolePage < totalPages - 1;
        addRenderableWidget(nextPageButton);

        if (selectedRoleIntroWidget == null) {
            selectedRoleIntroWidget = createRoleIntroWidget(isSmallUI);
            addRenderableWidget(selectedRoleIntroWidget);

        }
        int searchWidth = isSmallUI ? 200 : 400;
        int searchX = (width - searchWidth) / 2;
        int searchY = startY - (isSmallUI ? 30 : 50);
        // 添加搜索框（如果不存在）
        if (searchWidget == null) {

            searchWidget = new EditBox(font, searchX, searchY, searchWidth, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder(this::onRoleSearch);
            addRenderableWidget(searchWidget);
        } else {
            searchWidget.setPosition(searchX, searchY);
            searchWidget.setWidth(searchWidth);
            this.removeWidget(searchWidget);
            this.addRenderableWidget(searchWidget);
            // 解决奇怪问题
        }
        // 根据搜索结果设置搜索框颜色
        if (filteredRoles.isEmpty() && !searchContent.isEmpty()) {
            searchWidget.setTextColor(0xFFAA0000); // 红色，表示没有搜索结果
        } else {
            searchWidget.setTextColor(0xFFFFFFFF); // 白色
        }
    }

    /**
     * 刷新角色选择界面
     */
    private void refreshRoleSelection() {
        roleCardWidgets.forEach(this::removeWidget);
        if (prevPageButton != null)
            removeWidget(prevPageButton);
        if (nextPageButton != null)
            removeWidget(nextPageButton);
        roleCardWidgets.clear();
        initRoleSelection();
    }

    /**
     * 角色被选中时调用
     */
    public void onRoleSelected(SRERole role) {
        if (minecraft == null || minecraft.player == null)
            return;

        this.selectedRole = role;
        if (this.selectedRole == null)
            return;

        ClientPlayNetworking.send(new GamblerSelectRoleC2SPacket(this.selectedRole.identifier()));
        onClose();
    }

    @Override
    protected void init() {
        super.init();
        initRoleSelection();
    }

    private SelectedRoleIntroTextWidget createRoleIntroWidget(boolean isSmallUI) {
        // 绘制已选中的角色信息
        var widget = new SelectedRoleIntroTextWidget(Component.nullToEmpty(""), font);
        if (selectedRole != null) {
            MutableComponent selectedText = Component.translatable("gui.noellesroles.gambler.selected",
                    RoleUtils.getRoleName(selectedRole).withColor(selectedRole.getColor()))
                    .withStyle(ChatFormatting.GREEN);
            MutableComponent introTip = Component.translatable("gui.noellesroles.gambler.selected.intro")
                    .withStyle(ChatFormatting.GRAY);

            int widgetWidthSelText = font.width(selectedText);
            int widgetWidthIntroTip = font.width(introTip);
            int widgetWidth = 0;
            if (!isSmallUI) {
                selectedText = selectedText.append("\n");
                widgetWidth = Math.max(widgetWidthSelText, widgetWidthIntroTip);
            } else {
                selectedText = selectedText.append(" ");
                widgetWidth = (widgetWidthSelText + widgetWidthIntroTip) + font.width(Component.literal(" "));
            }
            selectedText = selectedText.append(introTip);

            MutableComponent roleDescription = Component
                    .translatable("screen.noellesroles.gambler.selected_desc",
                            RoleUtils.getRoleName(selectedRole).withColor(selectedRole.getColor()),
                            RoleUtils.getRoleDescription(selectedRole).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD);
            widget.setTooltip(Tooltip.create(roleDescription));
            widget.setMessage(selectedText);
            widget.setCentered(true);
            widget.setHeight(font.lineHeight * 2);
            widget.setWidth(Math.max(200, widgetWidth));
            widget.setX(width / 2 - widget.getWidth() / 2);
            widget.setY(height - font.lineHeight - marginBottomY - widget.getHeight() - 10);
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

        // 绘制页码信息
        if (totalPages > 0) {
            Component pageInfo = Component.translatable("screen.noellesroles.gambler.page_info",
                    currentRolePage + 1, totalPages)
                    .withStyle(ChatFormatting.YELLOW);
            guiGraphics.drawCenteredString(font, pageInfo, width / 2, 60, 0xFFFFFF);
        }

        // 绘制提示
        Component hint = Component.translatable("screen.noellesroles.gambler.hint")
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

    /**
     * 角色卡牌小部件
     */
    private class RoleCardWidget extends AbstractWidget {
        private final SRERole role;
        private boolean hovered;
        private float hoverAnimation = 0f;

        public RoleCardWidget(int x, int y, int width, int height, SRERole role, int index) {
            super(x, y, width, height, RoleUtils.getRoleName(role));
            this.role = role;
            // 设置工具提示
            Component tooltip = Component
                    .translatable("screen.noellesroles.gambler.click_to_select",
                            RoleUtils.getRoleName(role).withColor(role.getColor()),
                            RoleUtils.getRoleDescription(role).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD);
            this.setTooltip(Tooltip.create(tooltip));
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.hovered = isMouseOver(mouseX, mouseY);

            // 更新悬停动画
            float targetHover = hovered ? 1f : 0f;
            hoverAnimation = Mth.lerp(0.2f, hoverAnimation, targetHover);

            // 计算卡牌位置和大小（带有悬停效果）
            int renderX = getX();
            int renderY = getY();
            int renderWidth = width;
            int renderHeight = height;

            if (hoverAnimation > 0) {
                float scale = 1 + hoverAnimation * 0.1f;
                renderWidth = (int) (width * scale);
                renderHeight = (int) (height * scale);
                renderX = getX() - (renderWidth - width) / 2;
                renderY = getY() - (renderHeight - height) / 2;
            }

            // 绘制卡牌背景
            drawCardBackground(guiGraphics, renderX, renderY, renderWidth, renderHeight);

            // 绘制角色图标（如果有）
            drawRoleIcon(guiGraphics, renderX, renderY, renderWidth, renderHeight);

            // 绘制角色名称
            drawRoleName(guiGraphics, renderX, renderY, renderWidth, renderHeight);

            // 绘制边框和效果
            drawCardEffects(guiGraphics, renderX, renderY, renderWidth, renderHeight);
        }

        private void drawCardBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            // 主背景（渐变）
            int topColor = 0xFF2D3047; // 深蓝紫色
            int bottomColor = 0xFF1C1E2E; // 更深的紫色

            // 如果悬停，改变颜色
            if (hoverAnimation > 0) {
                topColor = blendColors(topColor, 0xFF3D4077, hoverAnimation);
                bottomColor = blendColors(bottomColor, 0xFF2C2E5E, hoverAnimation);
            }

            // 绘制圆角矩形背景
            // int cornerRadius = 10;
            guiGraphics.fill(x, y, x + width, y + height, 0xFF000000); // 黑色边框

            // 主背景填充
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, topColor);

            // 底部渐变
            int gradientHeight = height / 3;
            for (int i = 0; i < gradientHeight; i++) {
                float progress = (float) i / gradientHeight;
                int color = blendColors(topColor, bottomColor, progress);
                guiGraphics.fill(x + 1, y + height - gradientHeight + i - 1,
                        x + width - 1, y + height - gradientHeight + i, color);
            }
        }

        private void drawRoleIcon(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            // 这里可以绘制角色的图标
            // 示例：绘制一个简单的占位符图标
            int iconSize = Math.min(width, height) / 2;
            int iconX = x + (width - iconSize) / 2;
            int iconY = y + height / 4 - iconSize / 4;

            // 图标背景（圆形）
            guiGraphics.fill(iconX - 2, iconY - 2, iconX + iconSize + 2, iconY + iconSize + 2, 0x80000000);

            // 绘制一个简单的职业图标
            int iconColor = getRoleColor();
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, iconColor);

            // 图标边框
            guiGraphics.renderOutline(iconX, iconY, iconSize, iconSize, 0xFFFFFFFF);

            // 绘制职业首字母
            String initial = getMessage().getString().substring(0, 1);
            int textX = iconX + iconSize / 2 - font.width(initial) / 2;
            int textY = iconY + iconSize / 2 - font.lineHeight / 2;
            guiGraphics.drawString(font, initial, textX, textY, 0xFFFFFF, true);
        }

        private void drawRoleName(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            // 名称背景条
            int nameBarY = y + height * 2 / 3;
            int nameBarHeight = height / 3;
            guiGraphics.fill(x, nameBarY, x + width, y + height, 0x80000000);

            // 绘制角色名称
            Component name = getMessage();
            List<FormattedCharSequence> wrappedName = font.split(name, width - 10);

            int nameY = nameBarY + (nameBarHeight - wrappedName.size() * font.lineHeight) / 2;

            for (FormattedCharSequence line : wrappedName) {
                int lineWidth = font.width(line);
                int lineX = x + (width - lineWidth) / 2;
                guiGraphics.drawString(font, line, lineX, nameY, 0xFFFFFF, true);
                nameY += font.lineHeight;
            }

            // 如果有选中状态，显示选中标记
            if (role.equals(selectedRole)) {
                int checkSize = 8;
                int checkX = x + width - checkSize - 5;
                int checkY = nameBarY + 5;
                guiGraphics.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, 0xFF00FF00);
                guiGraphics.drawCenteredString(font, "✓", checkX + checkSize / 2, checkY - 1, 0x000000);
            }
        }

        private void drawCardEffects(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            // 边框
            int borderColor = 0xFF444488;
            if (hoverAnimation > 0) {
                borderColor = blendColors(borderColor, 0xFF8888FF, hoverAnimation);
            }
            guiGraphics.renderOutline(x, y, width, height, borderColor);

            // 悬停时的发光效果
            if (hoverAnimation > 0) {
                int glowColor = (int) (hoverAnimation * 100) << 24 | 0x8888FF;
                for (int i = 1; i <= 3; i++) {
                    guiGraphics.renderOutline(x - i, y - i, width + i * 2, height + i * 2, glowColor);
                }
            }

            // 顶部装饰条
            int topBarHeight = 5;
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + topBarHeight, getRoleColor());
        }

        private int getRoleColor() {
            // 根据职业类型返回不同的颜色
            // 这里只是一个示例，您可以根据实际的职业属性返回不同的颜色
            switch (role.identifier().getPath()) {
                case "knight":
                    return 0xFF4A90E2; // 蓝色
                default:
                    // 使用hashCode生成稳定但随机的颜色
                    return role.getColor();
            }
        }

        private int blendColors(int color1, int color2, float ratio) {
            int r1 = (color1 >> 16) & 0xFF;
            int g1 = (color1 >> 8) & 0xFF;
            int b1 = color1 & 0xFF;

            int r2 = (color2 >> 16) & 0xFF;
            int g2 = (color2 >> 8) & 0xFF;
            int b2 = color2 & 0xFF;

            int r = (int) (r1 + (r2 - r1) * ratio);
            int g = (int) (g1 + (g2 - g1) * ratio);
            int b = (int) (b1 + (b2 - b1) * ratio);

            return (0xFF << 24) | (r << 16) | (g << 8) | b;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            GamblerScreen.this.onRoleSelected(role);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            // 无障碍功能支持
            narrationElementOutput.add(NarratedElementType.TITLE, getMessage());
            if (role.equals(selectedRole)) {
                narrationElementOutput.add(NarratedElementType.HINT,
                        Component.translatable("screen.noellesroles.gambler.narration.selected"));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.active && this.visible && this.isValidClickButton(button) && this.clicked(mouseX, mouseY)) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onClick(mouseX, mouseY);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 支持键盘导航
        if (keyCode == 265) { // 上箭头
            navigateCards(-CARDS_PER_ROW);
            return true;
        } else if (keyCode == 264) { // 下箭头
            navigateCards(CARDS_PER_ROW);
            return true;
        } else if (keyCode == 263) { // 左箭头
            navigateCards(-1);
            return true;
        } else if (keyCode == 262) { // 右箭头
            navigateCards(1);
            return true;
        } else if (keyCode == 257 || keyCode == 335) { // Enter 或 Numpad Enter
            if (selectedRole != null && roleCardWidgets.stream().anyMatch(card -> card.role.equals(selectedRole))) {
                onRoleSelected(selectedRole);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void navigateCards(int delta) {
        if (roleCardWidgets.isEmpty())
            return;

        // 查找当前选中卡牌的索引
        int currentIndex = -1;
        for (int i = 0; i < roleCardWidgets.size(); i++) {
            if (roleCardWidgets.get(i).role.equals(selectedRole)) {
                currentIndex = i;
                break;
            }
        }

        // 计算新索引
        int newIndex = currentIndex + delta;
        if (newIndex < 0) {
            if (prevPageButton.isActive()) {
                prevPageButton.onClick(0, 0);
                newIndex = roleCardWidgets.size() - 1;
            } else {
                newIndex = 0;
            }
        }
        if (newIndex >= roleCardWidgets.size()) {
            if (nextPageButton.isActive()) {
                newIndex = 0;
                nextPageButton.onClick(0, 0);
            } else {
                newIndex = roleCardWidgets.size() - 1;
            }
        }
        if (newIndex >= 0 && newIndex < roleCardWidgets.size()) {
            selectedRole = roleCardWidgets.get(newIndex).role;
        }
    }
}