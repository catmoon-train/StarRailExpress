package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.UpdateNameTagSelectedPayload;
import io.wifi.starrailexpress.network.UpdateSkinSelectedPayload;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public class SkinManagementScreen extends Screen {
    private static final class CategoryTabData {
        private final String id;
        private final Component label;
        private final Item iconItem;

        private CategoryTabData(String id, Component label, Item iconItem) {
            this.id = id;
            this.label = label;
            this.iconItem = iconItem;
        }

        private boolean isHatTab() {
            return "hat".equals(id);
        }
    }

    private final SREPlayerSkinsComponent skinsComponent;
    private final Player player;
    private SkinSelectionList skinList;
    private Button backButton;
    private Button refreshButton;
    private Button prevCategoryPageButton;
    private Button nextCategoryPageButton;
    private Button prevNameTagButton;
    private Button nextNameTagButton;

    // 分类标签按钮列表
    private final List<CategoryButton> categoryButtons = new ArrayList<>();
    private List<CategoryTabData> categories = new ArrayList<>();
    private int selectedCategory = 0;
    private int categoryPage = 0;
    private final List<String> availableNameTags = new ArrayList<>();
    private int selectedNameTagIndex = 0;

    // 当前选中的帽子皮肤
    private String selectedHat = "default";

    // 颜色定义
    private static final int BACKGROUND_COLOR_TOP = 0xFF1A1A2E;
    private static final int BACKGROUND_COLOR_BOTTOM = 0xFF16213E;
    private static final int PANEL_COLOR = 0x90303030;
    private static final int MODEL_SCALE_DIVISOR = 3;
    private static final int CATEGORY_PAGE_SIZE = 6;
    private static final int NAME_TAG_COLOR = 0xFFF5DFA8;
    private static final int NAME_TAG_HINT_COLOR = 0xFF9FAABC;

    // 右侧面板布局
    private int rightPanelX;
    private int rightPanelWidth;
    private int rightPanelY;
    private int rightPanelHeight;

    public SkinManagementScreen() {
        super(Component.translatable("screen.sre.skins.title"));
        this.player = Minecraft.getInstance().player;
        this.skinsComponent = SREPlayerSkinsComponent.KEY.get(this.player);
        // 加载当前装备的帽子
        this.selectedHat = skinsComponent.getEquippedSkins().getOrDefault("hat", "default");
    }

    @Override
    protected void init() {
        super.init();

        // 清除旧组件
        this.clearWidgets();
        categoryButtons.clear();

        int screenWidth = this.width;
        int screenHeight = this.height;

        // 新布局：左侧皮肤列表 + 右侧玩家预览
        int titleHeight = 40;
        int categoryHeight = 22;
        int listTop = 104;
        int listHeight = screenHeight - 160;
        int buttonAreaHeight = 60;

        // 左右分栏比例: 左侧60%皮肤列表, 右侧40%玩家预览
        int totalContentWidth = Math.min(screenWidth - 40, 800);
        int contentStartX = (screenWidth - totalContentWidth) / 2;
        int leftPanelWidth = (int) (totalContentWidth * 0.58);
        rightPanelWidth = totalContentWidth - leftPanelWidth - 10;
        rightPanelX = contentStartX + leftPanelWidth + 10;
        rightPanelY = listTop;
        rightPanelHeight = listHeight;

        categories = buildCategories();
        refreshNameTagState();
        if (categories.isEmpty()) {
            selectedCategory = 0;
            categoryPage = 0;
        } else {
            selectedCategory = Mth.clamp(selectedCategory, 0, categories.size() - 1);
            categoryPage = selectedCategory / CATEGORY_PAGE_SIZE;
        }

        // 1. 标题区域
        initTitleArea(screenWidth, titleHeight);

        // 2. 分类标签区域
        initCategoryArea(contentStartX, leftPanelWidth, listTop, categoryHeight);

        // 3. 左侧皮肤列表区域
        initSkinListArea(contentStartX, leftPanelWidth, listTop, listHeight);

        // 4. 右侧玩家预览区域和帽子按钮
        initPlayerPreviewArea();

        // 5. 底部按钮区域
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

    private void initCategoryArea(int listX, int listWidth, int listTop, int categoryHeight) {
        if (categories.isEmpty()) {
            return;
        }

        int categoryY = listTop - categoryHeight - 4;
        int arrowWidth = 20;
        int arrowSpacing = 6;
        int categorySpacing = 5;
        int maxPage = Math.max(0, (categories.size() - 1) / CATEGORY_PAGE_SIZE);
        categoryPage = Mth.clamp(categoryPage, 0, maxPage);

        int pageStart = categoryPage * CATEGORY_PAGE_SIZE;
        int pageEndExclusive = Math.min(categories.size(), pageStart + CATEGORY_PAGE_SIZE);
        int tabsPerPage = Math.max(1, pageEndExclusive - pageStart);

        int tabsAreaWidth = listWidth - arrowWidth * 2 - arrowSpacing * 2;
        int categoryWidth = Math.max(64, (tabsAreaWidth - (tabsPerPage - 1) * categorySpacing) / tabsPerPage);
        int tabsStartX = listX + arrowWidth + arrowSpacing;

        prevCategoryPageButton = Button.builder(Component.literal("<"), button -> {
            if (categoryPage > 0) {
                categoryPage--;
                int newPageStart = categoryPage * CATEGORY_PAGE_SIZE;
                int newPageEnd = Math.min(categories.size(), newPageStart + CATEGORY_PAGE_SIZE) - 1;
                selectedCategory = Mth.clamp(selectedCategory, newPageStart, newPageEnd);
                refreshSkinPanels();
            }
        }).pos(listX, categoryY)
                .size(arrowWidth, categoryHeight)
                .build();
        prevCategoryPageButton.active = categoryPage > 0;
        addRenderableWidget(prevCategoryPageButton);

        nextCategoryPageButton = Button.builder(Component.literal(">"), button -> {
            if (categoryPage < maxPage) {
                categoryPage++;
                selectedCategory = categoryPage * CATEGORY_PAGE_SIZE;
                refreshSkinPanels();
            }
        }).pos(listX + listWidth - arrowWidth, categoryY)
                .size(arrowWidth, categoryHeight)
                .build();
        nextCategoryPageButton.active = categoryPage < maxPage;
        addRenderableWidget(nextCategoryPageButton);

        for (int i = pageStart; i < pageEndExclusive; i++) {
            CategoryTabData tab = categories.get(i);
            int finalI = i;
            int localIndex = i - pageStart;

            CategoryButton button = new CategoryButton(
                    tabsStartX + localIndex * (categoryWidth + categorySpacing),
                    categoryY,
                    categoryWidth,
                    categoryHeight,
                    tab.label,
                    tab.iconItem,
                    button1 -> {
                        selectedCategory = finalI;
                        refreshSkinPanels();
                    },
                    i == selectedCategory);

            categoryButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void initSkinListArea(int listX, int listWidth, int listTop, int listHeight) {
        if (categories.isEmpty() || selectedCategory >= categories.size()) {
            // 显示空状态
            addRenderableWidget(new CenteredText(
                    listX + listWidth / 2, listTop + listHeight / 2,
                    Component.translatable("screen.sre.skins.no_items"),
                    0xFFAAAAAA));
            return;
        }

        CategoryTabData selectedTab = categories.get(selectedCategory);
        if (selectedTab.isHatTab()) {
            addRenderableWidget(new CenteredText(
                    listX + listWidth / 2,
                    listTop + listHeight / 2,
                    Component.translatable("screen.sre.skins.hat_title"),
                    0xFFCCCCCC));
            return;
        }

        Item selectedItem = selectedTab.iconItem;
        ItemStack itemStack = new ItemStack(selectedItem);

        // 创建皮肤列表（放在左侧面板）
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

    private List<CategoryTabData> buildCategories() {
        List<CategoryTabData> result = new ArrayList<>();
        for (Item item : getSkinnableItemTypes()) {
            String itemTypeName = BuiltInRegistries.ITEM.getKey(item).toString();
            result.add(new CategoryTabData(itemTypeName, Component.literal(getItemShortName(item)), item));
        }
        result.add(new CategoryTabData("hat", Component.translatable("screen.sre.skins.hat_title"), Items.LEATHER_HELMET));
        return result;
    }

    private void initPlayerPreviewArea() {
        // 右侧预览面板背景
        addRenderableWidget(new SimplePanel(
                rightPanelX, rightPanelY, rightPanelWidth, rightPanelHeight,
                0x80000000, 0xFF444455));

        // 预览标题
        addRenderableWidget(new CenteredText(
                rightPanelX + rightPanelWidth / 2, rightPanelY + 10,
                Component.translatable("screen.sre.skins.preview"),
                0xFFFFFFFF));

        int nameTagY = rightPanelY + 30;
        addRenderableWidget(new CenteredText(
            rightPanelX + rightPanelWidth / 2,
            nameTagY - 10,
            Component.translatable("screen.sre.skins.title_selector"),
            NAME_TAG_HINT_COLOR));

        prevNameTagButton = Button.builder(Component.literal("<"), button -> shiftNameTagSelection(-1))
            .pos(rightPanelX + 8, nameTagY - 7)
            .size(18, 14)
            .build();
        prevNameTagButton.active = availableNameTags.size() > 1;
        addRenderableWidget(prevNameTagButton);

        nextNameTagButton = Button.builder(Component.literal(">"), button -> shiftNameTagSelection(1))
            .pos(rightPanelX + rightPanelWidth - 26, nameTagY - 7)
            .size(18, 14)
            .build();
        nextNameTagButton.active = availableNameTags.size() > 1;
        addRenderableWidget(nextNameTagButton);
    }

    private void initButtonArea(int screenWidth, int screenHeight, int buttonAreaHeight) {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonY = screenHeight - 40;
        int buttonSpacing = 20;

        // 刷新按钮
        refreshButton = org.agmas.noellesroles.client.widget.custom_button.ModernButton.builder(
                Component.translatable("screen.sre.skins.refresh"),
                button -> refreshSkinPanels()).pos((screenWidth - buttonWidth * 2 - buttonSpacing) / 2, buttonY)
                .size(buttonWidth, buttonHeight)
                .build();

        refreshButton.setTooltip(Tooltip.create(
                Component.translatable("screen.sre.skins.refresh_tooltip")));

        addRenderableWidget(refreshButton);

        // 返回按钮
        backButton = org.agmas.noellesroles.client.widget.custom_button.ModernButton.builder(
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
            if (TMMItems.SkinableItem != null) {
                skinnableItems.addAll(TMMItems.SkinableItem);
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

        // 渲染右侧玩家模型预览
        renderPlayerPreview(graphics, mouseX, mouseY);

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

    private void renderPlayerPreview(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (player == null) return;

        // 在右侧面板中渲染玩家模型预览
        int previewX1 = rightPanelX + 5;
        int previewY1 = rightPanelY + 25;
        int previewX2 = rightPanelX + rightPanelWidth - 5;
        int previewY2 = rightPanelY + rightPanelHeight - 10;

        int previewSize = Math.min(previewX2 - previewX1, previewY2 - previewY1);
        int modelScale = previewSize / MODEL_SCALE_DIVISOR;
        ItemStack previewStack = getPreviewStackForCurrentTab();
        if (!previewStack.isEmpty()) {
            player.setItemSlot(EquipmentSlot.MAINHAND, previewStack);

        }
        // 使用InventoryScreen的renderEntityInInventoryFollowsMouse方法渲染玩家模型
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                previewX1, previewY1, previewX2, previewY2,
                modelScale,
                0.0625F,
                mouseX, mouseY,
                player);

        Component selectedNameTagText = getSelectedNameTagDisplayText();
        int titleX = rightPanelX + rightPanelWidth / 2;
        int titleY = previewY1 + 4;
        guiGraphics.drawCenteredString(font, selectedNameTagText, titleX, titleY, NAME_TAG_COLOR);


    }

    private NameTagInventoryComponent getNameTagComponent() {
        if (player == null)
            return null;
        return NameTagInventoryComponent.KEY.get(player);
    }

    private void refreshNameTagState() {
        availableNameTags.clear();
        NameTagInventoryComponent component = getNameTagComponent();
        if (component == null)
            return;

        availableNameTags.addAll(component.nameTags);
        if (availableNameTags.isEmpty()) {
            selectedNameTagIndex = 0;
            return;
        }

        String current = component.getCurrentNameTag();
        int idx = availableNameTags.indexOf(current);
        selectedNameTagIndex = idx >= 0 ? idx : 0;
    }

    private void shiftNameTagSelection(int delta) {
        if (availableNameTags.isEmpty())
            return;
        int size = availableNameTags.size();
        int nextIndex = (selectedNameTagIndex + delta) % size;
        if (nextIndex < 0)
            nextIndex += size;
        selectedNameTagIndex = nextIndex;
        String selectedTag = availableNameTags.get(selectedNameTagIndex);
        NameTagInventoryComponent component = getNameTagComponent();
        if (component != null)
            component.CurrentNameTag = selectedTag;
        ClientPlayNetworking.send(new UpdateNameTagSelectedPayload(selectedTag));
        refreshSkinPanels();
    }

    private Component getSelectedNameTagDisplayText() {
        if (availableNameTags.isEmpty())
            return Component.translatable("screen.sre.skins.no_title");

        String selectedTag = availableNameTags.get(Mth.clamp(selectedNameTagIndex, 0, availableNameTags.size() - 1));
        ResourceLocation tagRl = ResourceLocation.tryParse(selectedTag);
        if (tagRl != null)
            return Component.translatableWithFallback(selectedTag, tagRl.getPath());
        return Component.translatableWithFallback(selectedTag, selectedTag);
    }

    private ItemStack getPreviewStackForCurrentTab() {
        if (categories.isEmpty() || selectedCategory < 0 || selectedCategory >= categories.size())
            return ItemStack.EMPTY;

        CategoryTabData selectedTab = categories.get(selectedCategory);
        if (selectedTab.isHatTab()) {
            ItemStack hatPreview = new ItemStack(Items.LEATHER_HELMET);
            hatPreview.set(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN, selectedHat);
            return hatPreview;
        }

        ItemStack preview = new ItemStack(selectedTab.iconItem);
        String equippedSkin = skinsComponent.getEquippedSkin(selectedTab.id);
        preview.set(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN, equippedSkin);
        return preview;
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
                categoryPage = selectedCategory / CATEGORY_PAGE_SIZE;
                refreshSkinPanels();
                return true;
            }
        } else if (keyCode == 262) { // 右箭头
            if (selectedCategory < categories.size() - 1) {
                selectedCategory++;
                categoryPage = selectedCategory / CATEGORY_PAGE_SIZE;
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
        private final Component label;

        public CategoryButton(int x, int y, int width, int height, Component label, Item item,
                OnPress onPress, boolean selected) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.selected = selected;
            this.item = new ItemStack(item);
            this.label = label;
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
                int textX = getX() + width / 2 - font.width(label) / 2;
            int textY = getY() + (height - 8) / 2;
            // 文字
            graphics.renderFakeItem(item, textX - 17, textY - 4);
            int textColor = selected ? 0xFF00FF00 : (isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFCCCCCC);
            graphics.drawString(
                    font,
                    label,
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
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false; // 装饰性组件，不拦截鼠标事件
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

//            // 物品图标
//            graphics.renderFakeItem(item, getX() + 5, getY() + (height - 16) / 2);

//            // 物品名称
//            graphics.drawString(
//                    Minecraft.getInstance().font,
//                    getMessage(),
//                    getX() + 25,
//                    getY() + (height - 8) / 2,
//                    0xFFFFFF,
//                    false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

}