package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.UpdateSkinSelectedPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SkinManagementScreen extends Screen {
    private final SREPlayerSkinsComponent skinsComponent;
    private final Player player;
    private SkinSelectionList skinList;
    private Button backButton;
    private Button refreshButton;

    // 分类标签按钮列表
    private final List<CategoryButton> categoryButtons = new ArrayList<>();
    private int selectedCategory = 0;

    // 颜色定义
    private static final int BACKGROUND_COLOR_TOP = 0xFF1A1A2E;
    private static final int BACKGROUND_COLOR_BOTTOM = 0xFF16213E;
    private static final int PANEL_COLOR = 0x90303030;

    public SkinManagementScreen() {
        super(Component.translatable("screen.sre.skins.title"));
        this.player = Minecraft.getInstance().player;
        this.skinsComponent = SREPlayerSkinsComponent.KEY.get(this.player);
    }

    @Override
    protected void init() {
        super.init();

        // 清除旧组件
        this.clearWidgets();
        categoryButtons.clear();

        int screenWidth = this.width;
        int screenHeight = this.height;

        // 计算布局区域
        int titleHeight = 40;
        int categoryHeight = 25;
        int listTop = 104;
        int listHeight = screenHeight - 160; // 为底部按钮留出空间
        int buttonAreaHeight = 60;

        // 1. 标题区域
        initTitleArea(screenWidth, titleHeight);

        // 2. 分类标签区域
        initCategoryArea(screenWidth, titleHeight, categoryHeight);

        // 3. 皮肤列表区域
        initSkinListArea(screenWidth, listTop, listHeight);

        // 4. 底部按钮区域
        initButtonArea(screenWidth, screenHeight, buttonAreaHeight);
    }

    private void initTitleArea(int screenWidth, int titleHeight) {
        int titleWidth = 300;
        int titleX = (screenWidth - titleWidth) / 2;
        int startY = 10;

        // 标题面板
        addRenderableWidget(new SimplePanel(
                titleX, startY, titleWidth, titleHeight,
                PANEL_COLOR, 0xFF555555));

        // 标题文字
        addRenderableWidget(new CenteredText(
                titleX + titleWidth / 2, startY + titleHeight / 2,
                this.title, 0xFFFFFFFF));
    }

    private void initCategoryArea(int screenWidth, int titleY, int categoryHeight) {
        // 获取可换肤物品类型
        // if(true){
        // return;
        // }
        List<Item> skinnableItems = getSkinnableItemTypes();

        if (skinnableItems.isEmpty()) {
            return;
        }

        int categoryWidth = 100;
        int categorySpacing = 5;
        int totalWidth = skinnableItems.size() * categoryWidth + (skinnableItems.size() - 1) * categorySpacing;
        int startX = (screenWidth - totalWidth) / 2;
        int categoryY = titleY + 12;

        for (int i = 0; i < skinnableItems.size(); i++) {
            Item item = skinnableItems.get(i);
            int finalI = i;

            CategoryButton button = new CategoryButton(
                    startX + i * (categoryWidth + categorySpacing),
                    categoryY,
                    categoryWidth,
                    categoryHeight,
                    item,
                    button1 -> {
                        selectedCategory = finalI;
                        refreshSkinPanels();
                    },
                    i == selectedCategory);

            categoryButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void initSkinListArea(int screenWidth, int listTop, int listHeight) {
        List<Item> skinnableItems = getSkinnableItemTypes();

        if (skinnableItems.isEmpty() || selectedCategory >= skinnableItems.size()) {
            // 显示空状态
            addRenderableWidget(new CenteredText(
                    screenWidth / 2, listTop + listHeight / 2,
                    Component.translatable("screen.sre.skins.no_items"),
                    0xFFAAAAAA));
            return;
        }

        Item selectedItem = skinnableItems.get(selectedCategory);
        ItemStack itemStack = new ItemStack(selectedItem);

        // 计算列表区域
        int listWidth = Math.min(screenWidth - 100, 600);
        int listX = (screenWidth - listWidth) / 2;

        // 创建皮肤列表
        skinList = new SkinSelectionList(
                this,
                Minecraft.getInstance(),
                listX,
                listWidth,
                listHeight,
                listTop,
                itemStack,
                skinsComponent,
                skinName -> {
                    // 当选择皮肤时更新玩家的皮肤设置
                    String itemTypeName = getItemTypeName(itemStack);
                    skinsComponent.setEquippedSkinForItemType(itemTypeName, skinName);
                    ClientPlayNetworking.send(new UpdateSkinSelectedPayload(itemTypeName, skinName));
                });

        addRenderableWidget(skinList);

        // 显示物品信息
        addRenderableWidget(new ItemInfoPanel(
                listX,
                listTop - 25,
                listWidth,
                20,
                itemStack));
    }

    private void initButtonArea(int screenWidth, int screenHeight, int buttonAreaHeight) {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonY = screenHeight - 40;
        int buttonSpacing = 20;

        // 刷新按钮
        refreshButton = Button.builder(
                Component.translatable("screen.sre.skins.refresh"),
                button -> refreshSkinPanels()).pos((screenWidth - buttonWidth * 2 - buttonSpacing) / 2, buttonY)
                .size(buttonWidth, buttonHeight)
                .build();

        refreshButton.setTooltip(Tooltip.create(
                Component.translatable("screen.sre.skins.refresh_tooltip")));

        addRenderableWidget(refreshButton);

        // 返回按钮
        backButton = Button.builder(
                Component.translatable("screen.sre.skins.back"),
                button -> this.onClose()).pos(refreshButton.getX() + buttonWidth + buttonSpacing, buttonY)
                .size(buttonWidth, buttonHeight)
                .build();

        addRenderableWidget(backButton);
    }

    private static String getItemShortName(Item item) {
        String name = item.getDescription().getString();
        if (name.length() <= 12)
            return name;
        return name.substring(0, 10) + "...";
    }

    private String getItemTypeName(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return BuiltInRegistries.ITEM.getKey(item).toString();

    }

    private List<Item> getSkinnableItemTypes() {
        List<Item> skinnableItems = new ArrayList<>();

        // 添加支持皮肤的物品
        if (player != null) {
            if (TMMItems.SkinnableItem != null) {
                skinnableItems.addAll(TMMItems.SkinnableItem);
            }
            // 可以添加更多物品
        }
        return skinnableItems;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渲染渐变背景
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // 渲染子组件
        super.render(graphics, mouseX, mouseY, partialTick);

        // 渲染底部说明
        renderInstructions(graphics);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渐变背景
        graphics.fillGradient(0, 0, width, height,
                BACKGROUND_COLOR_TOP, BACKGROUND_COLOR_BOTTOM);

        // 添加一些装饰粒子
        long time = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            float x = (float) ((time * 0.2 + i * 50) % width);
            float y = (float) ((Math.sin(time * 0.001 + i) * 30 + height / 2) % height);
            float size = 1 + (float) Math.sin(time * 0.002 + i);
            int alpha = (int) (50 + 100 * Math.sin(time * 0.0005 + i));
            int starColor = (alpha << 24) | 0xFFFFFF;
            graphics.fill((int) x, (int) y, (int) (x + size), (int) (y + size), starColor);
        }
    }

    private void renderInstructions(GuiGraphics graphics) {
        // 底部说明文本
        Component instructions = Component.translatable("screen.sre.skins.instructions");
        graphics.drawCenteredString(font, instructions, width / 2, height - 12, 0xFF888888);

        // 皮肤统计
        int totalSkins = 0;
        int unlockedSkins = 0;
        List<Item> skinnableItems = getSkinnableItemTypes();

        for (Item item : skinnableItems) {
            ItemStack stack = new ItemStack(item);
            var skins = skinsComponent.getUnlockedSkins(stack);
            totalSkins += skins.size() + 1; // 包括默认皮肤
            unlockedSkins += (int) skins.values().stream().filter(b -> b).count() + 1;
        }

        if (totalSkins > 0) {
            Component stats = Component.translatable("screen.sre.skins.stats",
                    unlockedSkins, totalSkins);
            graphics.drawString(font, stats, 10, 10, 0xFFAAAAAA, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 256) { // ESC键
            this.onClose();
            return true;
        }

        if (keyCode == 82) { // R键刷新
            refreshSkinPanels();
            return true;
        }

        // 方向键切换分类
        if (keyCode == 263) { // 左箭头
            if (selectedCategory > 0) {
                selectedCategory--;
                refreshSkinPanels();
                return true;
            }
        } else if (keyCode == 262) { // 右箭头
            List<Item> items = getSkinnableItemTypes();
            if (selectedCategory < items.size() - 1) {
                selectedCategory++;
                refreshSkinPanels();
                return true;
            }
        }

        return false;
    }

    public void refreshSkinPanels() {
        this.init();
    }

    // 分类按钮
    private static class CategoryButton extends Button {
        private final boolean selected;
        private final ItemStack item;

        public CategoryButton(int x, int y, int width, int height, Item item,
                OnPress onPress, boolean selected) {
            super(x, y, width, height, Component.literal(getItemShortName(item)), onPress, DEFAULT_NARRATION);
            this.selected = selected;
            this.item = new ItemStack(item);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int backgroundColor;
            int borderColor;

            if (selected) {
                backgroundColor = 0x8040AA40; // 绿色
                borderColor = 0xFF00FF00;
            } else if (isHoveredOrFocused()) {
                backgroundColor = 0x804488CC; // 蓝色
                borderColor = 0xFF6688CC;
            } else {
                backgroundColor = 0x80404040; // 灰色
                borderColor = 0xFF555555;
            }

            // 背景
            graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);

            // 边框
            graphics.fill(getX(), getY(), getX() + width, getY() + 2, borderColor);
            graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, borderColor);
            graphics.fill(getX(), getY(), getX() + 2, getY() + height, borderColor);
            graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + height, borderColor);
            var font = Minecraft.getInstance().font;
            int textX = getX() + width / 2 - font.width(getMessage()) / 2;
            int textY = getY() + (height - 8) / 2;
            // 文字
            graphics.renderFakeItem(item, textX - 17, textY - 4);
            int textColor = selected ? 0xFF00FF00 : (isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFCCCCCC);
            graphics.drawString(
                    font,
                    getMessage(),
                    textX + 9,
                    textY,
                    textColor);
        }
    }

    // 简单面板组件
    private static class SimplePanel extends AbstractWidget {
        private final int backgroundColor;
        private final int borderColor;

        public SimplePanel(int x, int y, int width, int height, int backgroundColor, int borderColor) {
            super(x, y, width, height, Component.empty());
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // 背景
            graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);

            // 边框
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            // 装饰性组件，无需旁白
        }
    }

    // 居中文本组件
    private static class CenteredText extends AbstractWidget {
        private final Component text;
        private final int color;

        public CenteredText(int x, int y, Component text, int color) {
            super(x, y, 0, 0, text);
            this.text = text;
            this.color = color;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int textWidth = Minecraft.getInstance().font.width(text);
            int textHeight = Minecraft.getInstance().font.lineHeight;

            int renderX = getX() - textWidth / 2;
            int renderY = getY() - textHeight / 2;

            graphics.drawString(Minecraft.getInstance().font, text, renderX, renderY, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, text);
        }
    }

    // 物品信息面板
    private static class ItemInfoPanel extends AbstractWidget {
        private final ItemStack item;

        public ItemInfoPanel(int x, int y, int width, int height, ItemStack item) {
            super(x, y, width, height, item.getDisplayName());
            this.item = item;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // 背景
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80404040);

            // 物品图标
            graphics.renderFakeItem(item, getX() + 5, getY() + (height - 16) / 2);

            // 物品名称
            graphics.drawString(
                    Minecraft.getInstance().font,
                    getMessage(),
                    getX() + 25,
                    getY() + (height - 8) / 2,
                    0xFFFFFF,
                    false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}