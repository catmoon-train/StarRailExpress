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
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
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
    // FIX: categoryPageSize is now dynamic (computed per-init based on available width)
    private int categoryPageSize = 6;
    private final List<String> availableNameTags = new ArrayList<>();
    private int selectedNameTagIndex = 0;

    // 当前选中的帽子皮肤
    private String selectedHat = "default";

    // 颜色定义
    private static final int BACKGROUND_COLOR_TOP = 0xFF1A1A2E;
    private static final int BACKGROUND_COLOR_BOTTOM = 0xFF16213E;
    private static final int PANEL_COLOR = 0x90303030;
    private static final int MODEL_SCALE_DIVISOR = 3;
    private static final int MIN_CATEGORY_WIDTH = 52;
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

        // FIX: Adaptive layout — compress title/padding on small screens
        boolean isCompact = screenHeight < 420 || screenWidth < 560;
        int titleHeight   = isCompact ? 22 : 36;
        int titleMarginT  = isCompact ? 4  : 8;
        int categoryHeight = isCompact ? 18 : 22;
        int categoryMarginT = isCompact ? 4 : 6;
        // listTop = titleMarginT + titleHeight + categoryMarginT + categoryHeight + gap-below-category
        int gapBelowCategory = isCompact ? 4 : 6;
        int listTop = titleMarginT + titleHeight + categoryMarginT + categoryHeight + gapBelowCategory;
        int bottomPadding = isCompact ? 28 : 46;
        int listHeight = screenHeight - listTop - bottomPadding;

        // 左右分栏比例: 左侧~58% 皮肤列表, 右侧~42% 玩家预览
        int totalContentWidth = Math.min(screenWidth - 20, 800);
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
            // FIX: categoryPage is NOT recalculated from selectedCategory here;
            //      it is preserved across refreshSkinPanels() calls.
            //      We only clamp it after computing categoryPageSize in initCategoryArea.
        }

        // 1. 标题区域
        initTitleArea(screenWidth, titleMarginT, titleHeight);

        // 2. 分类标签区域 — also computes categoryPageSize and clamps categoryPage
        initCategoryArea(contentStartX, leftPanelWidth, listTop, categoryHeight, categoryMarginT);

        // 3. 左侧皮肤列表区域
        initSkinListArea(contentStartX, leftPanelWidth, listTop, listHeight);

        // 4. 右侧玩家预览区域和帽子按钮
        initPlayerPreviewArea();

        // 5. 底部按钮区域
        initButtonArea(screenWidth, screenHeight, isCompact);
    }

    private void initTitleArea(int screenWidth, int marginTop, int titleHeight) {
        int titleWidth = Math.min(300, screenWidth - 20);
        int titleX = (screenWidth - titleWidth) / 2;

        addRenderableWidget(new SimplePanel(
                titleX, marginTop, titleWidth, titleHeight,
                PANEL_COLOR, 0xFF555555));

        addRenderableWidget(new CenteredText(
                titleX + titleWidth / 2, marginTop + titleHeight / 2,
                this.title, 0xFFFFFFFF));
    }

    private void initCategoryArea(int listX, int listWidth, int listTop, int categoryHeight, int categoryMarginT) {
        if (categories.isEmpty()) {
            return;
        }

        int categoryY = listTop - categoryHeight - categoryMarginT;
        int arrowWidth = 18;
        int arrowSpacing = 4;
        int categorySpacing = 4;

        // FIX: Compute how many tabs actually fit in the available width
        int tabsAreaWidth = listWidth - arrowWidth * 2 - arrowSpacing * 2;
        // Each tab needs at least MIN_CATEGORY_WIDTH px plus a spacing gap
        categoryPageSize = Math.max(1,
                (tabsAreaWidth + categorySpacing) / (MIN_CATEGORY_WIDTH + categorySpacing));

        int maxPage = Math.max(0, (categories.size() - 1) / categoryPageSize);
        // FIX: clamp categoryPage using the freshly-computed categoryPageSize
        categoryPage = Mth.clamp(categoryPage, 0, maxPage);

        int pageStart       = categoryPage * categoryPageSize;
        int pageEndExcl     = Math.min(categories.size(), pageStart + categoryPageSize);
        int tabsThisPage    = Math.max(1, pageEndExcl - pageStart);

        // Distribute remaining space evenly among tabs (floor), with a sane floor
        int categoryWidth = Math.max(MIN_CATEGORY_WIDTH,
                (tabsAreaWidth - (tabsThisPage - 1) * categorySpacing) / tabsThisPage);

        int tabsStartX = listX + arrowWidth + arrowSpacing;

        prevCategoryPageButton = Button.builder(Component.literal("<"), button -> {
            if (categoryPage > 0) {
                categoryPage--;
                // Move selectedCategory into the new page range
                int newPageStart = categoryPage * categoryPageSize;
                int newPageEnd   = Math.min(categories.size(), newPageStart + categoryPageSize) - 1;
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
                selectedCategory = categoryPage * categoryPageSize;
                refreshSkinPanels();
            }
        }).pos(listX + listWidth - arrowWidth, categoryY)
                .size(arrowWidth, categoryHeight)
                .build();
        nextCategoryPageButton.active = categoryPage < maxPage;
        addRenderableWidget(nextCategoryPageButton);

        for (int i = pageStart; i < pageEndExcl; i++) {
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
                    String itemTypeName = getItemTypeName(itemStack);
                    skinsComponent.setEquippedSkinForItemType(itemTypeName, skinName);
                    ClientPlayNetworking.send(new UpdateSkinSelectedPayload(itemTypeName, skinName));
                });

        addRenderableWidget(skinList);

        addRenderableWidget(new ItemInfoPanel(
                rightPanelX,
                rightPanelY - 25,
                rightPanelWidth,
                20,
                itemStack));
    }

    private List<CategoryTabData> buildCategories() {
        List<CategoryTabData> result = new ArrayList<>();
        for (Item item : getSkinnableItemTypes()) {
            String itemTypeName = BuiltInRegistries.ITEM.getKey(item).toString();
            result.add(new CategoryTabData(itemTypeName, Component.literal(getItemShortName(item)), item));
        }
        result.add(
                new CategoryTabData("hat", Component.translatable("screen.sre.skins.hat_title"), Items.LEATHER_HELMET));
        return result;
    }

    private void initPlayerPreviewArea() {
        addRenderableWidget(new SimplePanel(
                rightPanelX, rightPanelY, rightPanelWidth, rightPanelHeight,
                0x80000000, 0xFF444455));

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

    private void initButtonArea(int screenWidth, int screenHeight, boolean isCompact) {
        int buttonWidth  = isCompact ? 80  : 100;
        int buttonHeight = isCompact ? 16  : 20;
        int buttonY      = screenHeight - (isCompact ? 22 : 36);
        int buttonSpacing = 16;

        refreshButton = ModernButton.builder(
                Component.translatable("screen.sre.skins.refresh"),
                button -> refreshSkinPanels())
                .pos((screenWidth - buttonWidth * 2 - buttonSpacing) / 2, buttonY)
                .size(buttonWidth, buttonHeight)
                .accentBar()
                .build();
        refreshButton.setTooltip(Tooltip.create(
                Component.translatable("screen.sre.skins.refresh_tooltip")));
        addRenderableWidget(refreshButton);

        backButton = ModernButton.builder(
                Component.translatable("screen.sre.skins.back"),
                button -> this.onClose())
                .pos(refreshButton.getX() + buttonWidth + buttonSpacing, buttonY)
                .size(buttonWidth, buttonHeight)
                .accentBar()
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
        if (player != null) {
            if (TMMItems.SkinableItem != null) {
                skinnableItems.addAll(TMMItems.SkinableItem);
            }
        }
        return skinnableItems;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderPlayerPreview(graphics, mouseX, mouseY);
        renderInstructions(graphics);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height,
                BACKGROUND_COLOR_TOP, BACKGROUND_COLOR_BOTTOM);

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
        if (player == null)
            return;

        int previewX1 = rightPanelX + 5;
        int previewY1 = rightPanelY + 25;
        int previewX2 = rightPanelX + rightPanelWidth - 5;
        int previewY2 = rightPanelY + rightPanelHeight - 10;

        int previewSize = Math.min(previewX2 - previewX1, previewY2 - previewY1);
        int modelScale  = previewSize / MODEL_SCALE_DIVISOR;
        ItemStack previewStack   = getPreviewStackForCurrentTab();
        // Save the real mainhand item before temporarily overriding it for rendering
        ItemStack originalMainhand = player.getMainHandItem().copy();
        if (!previewStack.isEmpty()) {
            player.setItemSlot(EquipmentSlot.MAINHAND, previewStack);
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400F);

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                previewX1, previewY1, previewX2, previewY2,
                modelScale,
                0.0625F,
                mouseX, mouseY,
                player);

        guiGraphics.pose().popPose();
        // Always restore the original item — even if previewStack was empty
        player.setItemSlot(EquipmentSlot.MAINHAND, originalMainhand);

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
        int size      = availableNameTags.size();
        int nextIndex = ((selectedNameTagIndex + delta) % size + size) % size;
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

        ItemStack preview    = new ItemStack(selectedTab.iconItem);
        String equippedSkin  = skinsComponent.getEquippedSkin(selectedTab.id);
        preview.set(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN, equippedSkin);
        return preview;
    }

    private void renderInstructions(GuiGraphics graphics) {
        Component instructions = Component.translatable("screen.sre.skins.instructions");
        graphics.drawCenteredString(font, instructions, width / 2, height - 10, 0xFF888888);

        int totalSkins = 0;
        int unlockedSkins = 0;
        List<Item> skinnableItems = getSkinnableItemTypes();
        for (Item item : skinnableItems) {
            ItemStack stack = new ItemStack(item);
            var skins = skinsComponent.getUnlockedSkins(stack);
            totalSkins    += skins.size() + 1;
            unlockedSkins += (int) skins.values().stream().filter(b -> b).count() + 1;
        }

        if (totalSkins > 0) {
            Component stats = Component.translatable("screen.sre.skins.stats", unlockedSkins, totalSkins);
            graphics.drawString(font, stats, 10, 4, 0xFFAAAAAA, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers))
            return true;

        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        if (keyCode == 82) { // R — refresh
            refreshSkinPanels();
            return true;
        }

        // Arrow keys: navigate categories
        if (keyCode == 263) { // left
            if (selectedCategory > 0) {
                selectedCategory--;
                // FIX: use the live categoryPageSize
                categoryPage = selectedCategory / categoryPageSize;
                refreshSkinPanels();
                return true;
            }
        } else if (keyCode == 262) { // right
            if (selectedCategory < categories.size() - 1) {
                selectedCategory++;
                categoryPage = selectedCategory / categoryPageSize;
                refreshSkinPanels();
                return true;
            }
        }

        return false;
    }

    public void refreshSkinPanels() {
        this.init();
    }

    // ─── CategoryButton ──────────────────────────────────────────────────────────

    private static class CategoryButton extends Button {
        private final boolean selected;
        private final ItemStack item;
        private final Component label;

        public CategoryButton(int x, int y, int width, int height, Component label, Item item,
                OnPress onPress, boolean selected) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.selected = selected;
            this.item     = new ItemStack(item);
            this.label    = label;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int backgroundColor, borderColor;
            if (selected) {
                backgroundColor = 0x8040AA40;
                borderColor     = 0xFF00FF00;
            } else if (isHoveredOrFocused()) {
                backgroundColor = 0x804488CC;
                borderColor     = 0xFF6688CC;
            } else {
                backgroundColor = 0x80404040;
                borderColor     = 0xFF555555;
            }

            // Background
            graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);
            // Border
            graphics.fill(getX(),             getY(),              getX() + width,     getY() + 2,              borderColor);
            graphics.fill(getX(),             getY() + height - 2, getX() + width,     getY() + height,         borderColor);
            graphics.fill(getX(),             getY(),              getX() + 2,          getY() + height,         borderColor);
            graphics.fill(getX() + width - 2, getY(),              getX() + width,     getY() + height,         borderColor);

            var font    = Minecraft.getInstance().font;
            int textColor = selected ? 0xFF00FF00 : (isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFCCCCCC);

            // FIX: Centre the icon+label block as a unit, instead of anchoring from text centre.
            // Layout: [icon 16px] [gap 3px] [text]  — all centred horizontally in the button.
            int iconSize    = 16;
            int iconTextGap = 3;
            int textWidth   = font.width(label);
            int totalW      = iconSize + iconTextGap + textWidth;
            // If content is wider than button, fall back to icon-only
            boolean showText = totalW + 4 <= width;

            int contentW    = showText ? totalW : iconSize;
            // FIX: vertical centre — icon is 16px tall, text baseline is ~8px from top of 8px text
            int iconY  = getY() + (height - iconSize) / 2;
            int textY  = getY() + (height - 8) / 2;
            int startX = getX() + (width - contentW) / 2;

            graphics.renderFakeItem(item, startX, iconY);

            if (showText) {
                graphics.drawString(font, label, startX + iconSize + iconTextGap, textY, textColor);
            }
        }
    }

    // ─── SimplePanel ─────────────────────────────────────────────────────────────

    private static class SimplePanel extends AbstractWidget {
        private final int backgroundColor;
        private final int borderColor;

        public SimplePanel(int x, int y, int width, int height, int backgroundColor, int borderColor) {
            super(x, y, width, height, Component.empty());
            this.backgroundColor = backgroundColor;
            this.borderColor     = borderColor;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);
            graphics.fill(getX(), getY(),              getX() + width, getY() + 1,          borderColor);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height,     borderColor);
            graphics.fill(getX(), getY(),              getX() + 1,      getY() + height,     borderColor);
            graphics.fill(getX() + width - 1, getY(), getX() + width,  getY() + height,     borderColor);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    // ─── CenteredText ────────────────────────────────────────────────────────────

    private static class CenteredText extends AbstractWidget {
        private final Component text;
        private final int color;

        public CenteredText(int x, int y, Component text, int color) {
            super(x, y, 0, 0, text);
            this.text  = text;
            this.color = color;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int textWidth  = Minecraft.getInstance().font.width(text);
            int textHeight = Minecraft.getInstance().font.lineHeight;
            graphics.drawString(Minecraft.getInstance().font, text,
                    getX() - textWidth / 2, getY() - textHeight / 2, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, text);
        }
    }

    // ─── ItemInfoPanel ───────────────────────────────────────────────────────────

    private static class ItemInfoPanel extends AbstractWidget {
        private final ItemStack item;

        public ItemInfoPanel(int x, int y, int width, int height, ItemStack item) {
            super(x, y, width, height, item.getDisplayName());
            this.item = item;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80404040);
            graphics.renderFakeItem(item, getX() + 5, getY() + (height - 16) / 2);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, getMessage(),
                    getX() + 25, getY() + (height - 8) / 2, 0xFFFFFF, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}