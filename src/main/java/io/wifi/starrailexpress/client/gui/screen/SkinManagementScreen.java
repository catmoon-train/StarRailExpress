/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.gui.anim.GuiAnim;
import io.wifi.starrailexpress.client.hat.ClientHatEquipmentCache;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.UpdateNameTagSelectedPayload;
import io.wifi.starrailexpress.network.UpdateSkinSelectedPayload;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 皮肤装备界面（复古列车风格重写版）。
 * <p>
 * 遵循 docs/ui_style.md 风格约定：
 * <ul>
 * <li>深棕红渐变背景 + 棕褐色描边 + 金色点缀；</li>
 * <li>分类标签栏带平滑滑动的金色选中指示条；</li>
 * <li>切换分类时内容区淡入过渡，打开界面时面板滑入；</li>
 * <li>"帽子"页使用 {@link HatSkinGridPanel} 卡片网格；</li>
 * <li>"名片"页使用 {@link NameTagList} 列表选择；</li>
 * <li>所有动画基于真实帧间隔驱动（{@link GuiAnim}）。</li>
 * </ul>
 */
public class SkinManagementScreen extends Screen {

    // ─── 分类标签数据 ────────────────────────────────────────────────────────
    private static final class CategoryTabData {
        final String id;
        final Component label;
        final Item iconItem;

        CategoryTabData(String id, Component label, Item iconItem) {
            this.id = id;
            this.label = label;
            this.iconItem = iconItem;
        }

        boolean isHatTab() {
            return "hat".equals(id);
        }

        boolean isNameTagTab() {
            return "name_tag".equals(id);
        }
    }

    // ─── 主题色（遵循 ui_style.md 复古列车配色） ──────────────────────────
    private static final int ACCENT = 0xFFD4AF37;        // GOLD 强调色
    private static final int ACCENT_SOFT = 0xFFC9A84C;   // 棕金色
    private static final int BG_TOP = 0xD81A1008;        // 深棕红（面板上）
    private static final int BG_BOTTOM = 0xD820140A;     // 棕黑（面板下）
    private static final int SCREEN_BG_TOP = 0xF018120A;  // 全屏背景上
    private static final int SCREEN_BG_BOTTOM = 0xF0061018; // 全屏背景下
    private static final int PANEL_BORDER = 0xFF8B6914;  // 棕褐色描边
    private static final int DECOR_LINE = 0x33FFE8C0;    // 顶部装饰线
    private static final int TEXT_MAIN = 0xFFFFF4DC;     // 主文字 浅奶油色
    private static final int TEXT_TITLE = 0xFFF5E8C8;    // 标题文字 浅米色
    private static final int TEXT_DIM = 0xFF9E8B6E;      // 次要文字 土褐色
    private static final int NAME_TAG_COLOR = 0xFFF5DFA8; // 称号色

    private static final int MIN_CATEGORY_WIDTH = 52;
    private static final int MODEL_SCALE_DIVISOR = 3;

    private final SREPlayerSkinsComponent skinsComponent;
    private final Player player;
    private SkinSelectionList skinList;
    private HatSkinGridPanel hatGrid;
    private NameTagList nameTagList;
    private EditBox searchBox;
    private ToggleCheckbox hideAllHatsCheck;
    private ToggleCheckbox showOwnHatCheck;
    private Button backButton;
    private Button refreshButton;

    public Screen parentScreen = null;

    private final List<CategoryTabData> categories = new ArrayList<>();
    private final List<CategoryButton> categoryButtons = new ArrayList<>();
    private int selectedCategory = 0;
    private int categoryPage = 0;
    private int categoryPageSize = 6;
    private String searchFilter = "";

    // ─── 动画状态 ───────────────────────────────────────────────────────────
    private long openTimeMs = -1L;
    private float contentAlpha = 1f;
    private float indicatorX = 0f;
    private float indicatorWidth = 0f;
    private boolean indicatorInitialized = false;

    // 布局缓存
    private int contentX;
    private int leftPanelWidth;
    private int listTop;
    private int listHeight;
    private int rightPanelX;
    private int rightPanelWidth;

    public SkinManagementScreen() {
        this(null);
    }

    public SkinManagementScreen(Screen parentScreen) {
        super(Component.translatable("screen.sre.skins.title"));
        this.player = Minecraft.getInstance().player;
        this.skinsComponent = SREPlayerSkinsComponent.KEY.get(this.player);
        this.parentScreen = parentScreen;
    }

    // ─── 初始化 ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        categoryButtons.clear();
        if (openTimeMs < 0) {
            openTimeMs = System.currentTimeMillis();
            contentAlpha = 0f;
        }

        boolean isCompact = this.height < 420 || this.width < 560;
        int titleHeight = isCompact ? 24 : 32;
        int titleMarginT = isCompact ? 6 : 10;
        int categoryHeight = isCompact ? 20 : 24;
        int categoryMarginT = isCompact ? 5 : 8;
        int gapBelowCategory = isCompact ? 5 : 8;
        this.listTop = titleMarginT + titleHeight + categoryMarginT + categoryHeight + gapBelowCategory;
        int bottomPadding = isCompact ? 30 : 48;
        this.listHeight = this.height - listTop - bottomPadding;

        int totalContentWidth = Math.min(this.width - 20, 820);
        this.contentX = (this.width - totalContentWidth) / 2;
        this.leftPanelWidth = (int) (totalContentWidth * 0.58);
        this.rightPanelWidth = totalContentWidth - leftPanelWidth - 10;
        this.rightPanelX = contentX + leftPanelWidth + 10;

        rebuildCategories();
        if (categories.isEmpty()) {
            selectedCategory = 0;
            categoryPage = 0;
        } else {
            selectedCategory = Mth.clamp(selectedCategory, 0, categories.size() - 1);
        }

        initCategoryArea(categoryHeight, categoryMarginT, titleMarginT + titleHeight);
        initSearchBox(categoryHeight, titleMarginT + titleHeight);
        initHatConfigCheckboxes(titleMarginT, titleMarginT + titleHeight);
        initContentArea();
        initButtonArea(isCompact);
    }

    private void rebuildCategories() {
        categories.clear();
        if (player != null && TMMItems.SkinableItem != null) {
            for (Item item : TMMItems.SkinableItem) {
                String itemTypeName = BuiltInRegistries.ITEM.getKey(item).toString();
                categories.add(new CategoryTabData(itemTypeName,
                        Component.literal(getItemShortName(item)), item));
            }
        }
        categories.add(new CategoryTabData("hat",
                Component.translatable("screen.sre.skins.hat_title"), Items.LEATHER_HELMET));
        // 名片（称号）标签
        categories.add(new CategoryTabData("name_tag",
                Component.translatable("screen.sre.skins.name_tag_title"), Items.NAME_TAG));
    }

    private void initCategoryArea(int categoryHeight, int categoryMarginT, int categoryY) {
        if (categories.isEmpty()) {
            return;
        }
        int arrowWidth = 18;
        int arrowSpacing = 4;
        int categorySpacing = 4;

        int tabsAreaWidth = leftPanelWidth - arrowWidth * 2 - arrowSpacing * 2;
        categoryPageSize = Math.max(1, (tabsAreaWidth + categorySpacing) / (MIN_CATEGORY_WIDTH + categorySpacing));
        int maxPage = Math.max(0, (categories.size() - 1) / categoryPageSize);
        categoryPage = Mth.clamp(categoryPage, 0, maxPage);

        int pageStart = categoryPage * categoryPageSize;
        int pageEndExcl = Math.min(categories.size(), pageStart + categoryPageSize);
        int tabsThisPage = Math.max(1, pageEndExcl - pageStart);
        int categoryWidth = Math.max(MIN_CATEGORY_WIDTH,
                (tabsAreaWidth - (tabsThisPage - 1) * categorySpacing) / tabsThisPage);
        int tabsStartX = contentX + arrowWidth + arrowSpacing;

        Button prevPage = Button.builder(Component.literal("<"), b -> {
            if (categoryPage > 0) {
                categoryPage--;
                int newStart = categoryPage * categoryPageSize;
                int newEnd = Math.min(categories.size(), newStart + categoryPageSize) - 1;
                selectedCategory = Mth.clamp(selectedCategory, newStart, newEnd);
                onCategoryChanged(false);
            }
        }).pos(contentX, categoryY).size(arrowWidth, categoryHeight).build();
        prevPage.active = categoryPage > 0;
        addRenderableWidget(prevPage);

        int finalMaxPage = maxPage;
        Button nextPage = Button.builder(Component.literal(">"), b -> {
            if (categoryPage < finalMaxPage) {
                categoryPage++;
                selectedCategory = categoryPage * categoryPageSize;
                onCategoryChanged(false);
            }
        }).pos(contentX + leftPanelWidth - arrowWidth, categoryY).size(arrowWidth, categoryHeight).build();
        nextPage.active = categoryPage < maxPage;
        addRenderableWidget(nextPage);

        for (int i = pageStart; i < pageEndExcl; i++) {
            CategoryTabData tab = categories.get(i);
            int finalI = i;
            CategoryButton button = new CategoryButton(
                    tabsStartX + (i - pageStart) * (categoryWidth + categorySpacing),
                    categoryY, categoryWidth, categoryHeight,
                    tab.label, tab.iconItem,
                    b -> selectCategory(finalI),
                    i == selectedCategory);
            categoryButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void initSearchBox(int categoryHeight, int categoryY) {
        searchBox = new EditBox(font, rightPanelX, categoryY, rightPanelWidth, categoryHeight,
                Component.translatable("screen.sre.skins.search"));
        searchBox.setMaxLength(50);
        searchBox.setResponder(text -> {
            searchFilter = text.toLowerCase();
            refreshSkinPanels();
        });
        searchBox.setHint(Component.translatable("screen.sre.skins.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(searchBox);
    }

    private void initHatConfigCheckboxes(int titleY, int totalTitleH) {
        SREClientConfig config = SREClientConfig.instance();
        int checkH = 14;
        int checkW = 100;
        int gap = 4;
        int checkY = titleY + (totalTitleH - checkH) / 2;
        int startX = width - 10 - checkW * 2 - gap;

        hideAllHatsCheck = new ToggleCheckbox(startX, checkY, checkW, checkH,
                Component.translatable("screen.sre.skins.hide_all_hats"),
                config.hideAllHats,
                v -> {
                    config.hideAllHats = v;
                    config.reload();
                });
        addRenderableWidget(hideAllHatsCheck);

        showOwnHatCheck = new ToggleCheckbox(startX + checkW + gap, checkY, checkW, checkH,
                Component.translatable("screen.sre.skins.show_own_hat_only"),
                config.showOwnHatOnly,
                v -> {
                    config.showOwnHatOnly = v;
                    config.reload();
                });
        addRenderableWidget(showOwnHatCheck);
    }

    private void selectCategory(int index) {
        if (index == selectedCategory && contentAlpha > 0.95f) {
            return;
        }
        selectedCategory = index;
        categoryPage = index / categoryPageSize;
        onCategoryChanged(true);
    }

    private void onCategoryChanged(boolean playSound) {
        if (playSound) {
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.7f));
        }
        contentAlpha = 0f;
        refreshSkinPanels();
    }

    private void initContentArea() {
        if (categories.isEmpty() || selectedCategory >= categories.size()) {
            addRenderableWidget(new CenteredText(contentX + leftPanelWidth / 2, listTop + listHeight / 2,
                    Component.translatable("screen.sre.skins.no_items"), TEXT_DIM));
            return;
        }
        CategoryTabData selectedTab = categories.get(selectedCategory);
        if (selectedTab.isHatTab()) {
            hatGrid = new HatSkinGridPanel(contentX, listTop, leftPanelWidth, listHeight,
                    skinsComponent, this::equipHat, searchFilter);
            addRenderableWidget(hatGrid);
            return;
        }
        hatGrid = null;

        if (selectedTab.isNameTagTab()) {
            nameTagList = new NameTagList(this, minecraft,
                    contentX, leftPanelWidth, listHeight, listTop, searchFilter);
            addRenderableWidget(nameTagList);
            return;
        }
        nameTagList = null;

        ItemStack itemStack = new ItemStack(selectedTab.iconItem);
        skinList = new SkinSelectionList(this, Minecraft.getInstance(),
                contentX, leftPanelWidth, listHeight, listTop, itemStack, skinsComponent,
                skinName -> ClientPlayNetworking.send(
                        new UpdateSkinSelectedPayload(ItemSkinManager.getItemTypeName(itemStack), skinName)),
                searchFilter);
        addRenderableWidget(skinList);

        addRenderableWidget(new ItemInfoPanel(rightPanelX, listTop - 25, rightPanelWidth, 20, itemStack));
    }

    /** 装备帽子：发送到服务端并做本地乐观更新（预览即时生效） */
    private void equipHat(String skinName) {
        ClientPlayNetworking.send(new UpdateSkinSelectedPayload("hat", skinName));
        if (player != null) {
            ClientHatEquipmentCache.setLocalOptimistic(player.getUUID(), skinName);
        }
    }

    // ─── 名片数据 ────────────────────────────────────────────────────────────

    List<String> getNameTags() {
        List<String> tags = new ArrayList<>();
        NameTagInventoryComponent component = player == null ? null
                : NameTagInventoryComponent.KEY.get(player);
        if (component != null) {
            tags.addAll(component.nameTags);
        }
        return tags;
    }

    String getCurrentNameTag() {
        NameTagInventoryComponent component = player == null ? null
                : NameTagInventoryComponent.KEY.get(player);
        return component != null ? component.getCurrentNameTag() : "";
    }

    void selectNameTag(String tag) {
        NameTagInventoryComponent component = player == null ? null
                : NameTagInventoryComponent.KEY.get(player);
        if (component != null) {
            component.CurrentNameTag = tag;
        }
        ClientPlayNetworking.send(new UpdateNameTagSelectedPayload(tag));
    }

    Component getNameTagDisplayText(String tagId) {
        ResourceLocation tagRl = ResourceLocation.tryParse(tagId);
        if (tagRl != null) {
            return Component.translatableWithFallback(tagId, tagRl.getPath());
        }
        return Component.translatableWithFallback(tagId, tagId);
    }

    // ─── 按钮区 ─────────────────────────────────────────────────────────────

    private void initButtonArea(boolean isCompact) {
        int buttonWidth = isCompact ? 84 : 110;
        int buttonHeight = isCompact ? 16 : 20;
        int buttonY = this.height - (isCompact ? 24 : 38);
        int buttonSpacing = 16;

        refreshButton = Button.builder(Component.translatable("screen.sre.skins.refresh"),
                b -> refreshSkinPanels())
                .pos((this.width - buttonWidth * 2 - buttonSpacing) / 2, buttonY)
                .size(buttonWidth, buttonHeight)
                .build();
        refreshButton.setTooltip(Tooltip.create(Component.translatable("screen.sre.skins.refresh_tooltip")));
        addRenderableWidget(refreshButton);

        backButton = Button.builder(Component.translatable("screen.sre.skins.back"), b -> this.onClose())
                .pos(refreshButton.getX() + buttonWidth + buttonSpacing, buttonY)
                .size(buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(backButton);
    }

    // ─── 渲染（遵循 ui_style.md 第3节渲染范式） ──────────────────────────

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super，绘制自定义全屏背景
        float dt = GuiAnim.frameDeltaSeconds();
        if (hatGrid != null) {
            hatGrid.setFrameDelta(dt);
        }
        contentAlpha = GuiAnim.approach(contentAlpha, 1f, 10f, dt);
        float openProgress = GuiAnim.easeOutCubic((System.currentTimeMillis() - openTimeMs) / 400f);

        renderAnimatedBackground(graphics, dt);
        renderPanelBackgrounds(graphics, openProgress);
        renderTitle(graphics, openProgress);
        renderTabIndicator(graphics, dt, openProgress);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先调用 super.render 绘制组件（内部自动调用 renderBackground）
        super.render(graphics, mouseX, mouseY, partialTick);

        // 在组件上方绘制附加元素
        float openProgress = GuiAnim.easeOutCubic((System.currentTimeMillis() - openTimeMs) / 400f);
        float dt = GuiAnim.frameDeltaSeconds();
        renderPreviewPanel(graphics, mouseX, mouseY, dt, openProgress);
        renderFooterStats(graphics);
    }

    // ─── 背景渲染 ───────────────────────────────────────────────────────────

    private void renderAnimatedBackground(GuiGraphics graphics, float dt) {
        // 全屏深棕渐变背景
        graphics.fillGradient(0, 0, width, height, SCREEN_BG_TOP, SCREEN_BG_BOTTOM);

        // 金色微粒（替代之前的蓝色粒点）
        long time = System.currentTimeMillis();
        for (int i = 0; i < 24; i++) {
            float x = (time * 0.015f * (1 + i % 3) + i * 67f) % (width + 20) - 10;
            float y = height / 2f + (float) Math.sin(time * 0.0008 + i * 1.7) * (height * 0.45f);
            float twinkle = 0.5f + 0.5f * (float) Math.sin(time * 0.0012 + i * 2.3);
            int alpha = (int) (15 + 45 * twinkle);
            graphics.fill((int) x, (int) y, (int) x + 2, (int) y + 2, GuiAnim.withAlpha(0xD4AF37, alpha));
        }
        // 顶部金色氛围光
        graphics.fillGradient(0, 0, width, 60, 0x252A1A08, 0x002A1A08);
    }

    /** 左面板 + 右面板的盒子背景 */
    private void renderPanelBackgrounds(GuiGraphics graphics, float openProgress) {
        float slide = (1f - openProgress) * -12f;
        graphics.pose().pushPose();
        graphics.pose().translate(0, slide, 0);

        // 左面板
        drawPanelBox(graphics, contentX, listTop, leftPanelWidth, listHeight, openProgress);
        // 右面板
        drawPanelBox(graphics, rightPanelX, listTop, rightPanelWidth, listHeight, openProgress);

        graphics.pose().popPose();
    }

    /** 单一面板盒子：渐变背景 + 棕褐色描边 + 顶部装饰线 */
    private void drawPanelBox(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int bgTop = GuiAnim.withAlpha(BG_TOP, GuiAnim.alphaOf(alpha));
        int bgBot = GuiAnim.withAlpha(BG_BOTTOM, GuiAnim.alphaOf(alpha));
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBot);
        // 棕褐色描边
        int borderColor = GuiAnim.withAlpha(PANEL_BORDER, GuiAnim.alphaOf(alpha));
        g.fill(x, y, x + w, y + 1, borderColor);
        g.fill(x, y + h - 1, x + w, y + h, borderColor);
        g.fill(x, y, x + 1, y + h, borderColor);
        g.fill(x + w - 1, y, x + w, y + h, borderColor);
        // 顶部装饰线
        int decorAlpha = Math.min(0x33, GuiAnim.alphaOf(alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, decorAlpha | 0x00FFE8C0);
    }

    private void renderTitle(GuiGraphics graphics, float openProgress) {
        int titleY = this.height < 420 ? 6 : 10;
        int titleHeight = this.height < 420 ? 18 : 24;
        float slide = (1f - openProgress) * -18f;

        graphics.pose().pushPose();
        graphics.pose().translate(0, slide, 0);

        int titleW = Math.min(240, (int) ((width - 40) * 0.75));
        int titleX = (width - titleW) / 2;

        // 标题背景面板
        graphics.fillGradient(titleX, titleY, titleX + titleW, titleY + titleHeight,
                GuiAnim.withAlpha(BG_TOP, GuiAnim.alphaOf(openProgress)),
                GuiAnim.withAlpha(BG_BOTTOM, GuiAnim.alphaOf(openProgress)));
        // 棕褐色上下边
        int borderColor = GuiAnim.withAlpha(PANEL_BORDER, GuiAnim.alphaOf(openProgress));
        graphics.fill(titleX, titleY, titleX + titleW, titleY + 1, borderColor);
        graphics.fill(titleX, titleY + titleHeight - 1, titleX + titleW, titleY + titleHeight, borderColor);
        graphics.fill(titleX, titleY, titleX + 1, titleY + titleHeight, borderColor);
        graphics.fill(titleX + titleW - 1, titleY, titleX + titleW, titleY + titleHeight, borderColor);
        // 顶部装饰线
        int decorAlpha = Math.min(0x33, GuiAnim.alphaOf(openProgress));
        graphics.fill(titleX + 1, titleY + 1, titleX + titleW - 1, titleY + 2, decorAlpha | 0x00FFE8C0);

        graphics.drawCenteredString(font, this.title,
                width / 2, titleY + (titleHeight - 8) / 2,
                GuiAnim.withAlpha(TEXT_TITLE, GuiAnim.alphaOf(openProgress)));

        // 标题下金色短线（随打开动画延展）
        int underlineW = (int) (64 * openProgress);
        int underlineColor = GuiAnim.withAlpha(ACCENT, GuiAnim.alphaOf(openProgress));
        graphics.fill(width / 2 - underlineW / 2, titleY + titleHeight + 2,
                width / 2 + underlineW / 2, titleY + titleHeight + 4, underlineColor);

        graphics.pose().popPose();
    }

    /** 分类标签栏下的滑动选中指示条 */
    private void renderTabIndicator(GuiGraphics graphics, float dt, float openProgress) {
        CategoryButton selected = null;
        for (CategoryButton button : categoryButtons) {
            if (button.selected) {
                selected = button;
                break;
            }
        }
        if (selected == null) {
            indicatorInitialized = false;
            return;
        }
        if (!indicatorInitialized) {
            indicatorX = selected.getX();
            indicatorWidth = selected.getWidth();
            indicatorInitialized = true;
        }
        indicatorX = GuiAnim.approach(indicatorX, selected.getX(), 18f, dt);
        indicatorWidth = GuiAnim.approach(indicatorWidth, selected.getWidth(), 18f, dt);

        int y = selected.getY() + selected.getHeight() + 1;
        int x0 = Math.round(indicatorX);
        int x1 = Math.round(indicatorX + indicatorWidth);
        int alpha = GuiAnim.alphaOf(openProgress);
        graphics.fill(x0, y, x1, y + 2, GuiAnim.withAlpha(ACCENT, alpha));
        graphics.fill(x0, y - 1, x1, y, GuiAnim.withAlpha(ACCENT_SOFT, alpha / 2));
    }

    // ─── 右侧预览面板 ───────────────────────────────────────────────────────

    private void renderPreviewPanel(GuiGraphics graphics, int mouseX, int mouseY, float dt, float openProgress) {
        if (player == null) {
            return;
        }
        float slide = (1f - openProgress) * 24f;
        graphics.pose().pushPose();
        graphics.pose().translate(slide, 0, 0);

        boolean isNameTagTab = !categories.isEmpty() && selectedCategory < categories.size()
                && categories.get(selectedCategory).isNameTagTab();

        // 称号标签（预览面板内显示当前称号）
        if (isNameTagTab) {
            int nameTagY = listTop + 34;
            graphics.drawCenteredString(font, Component.translatable("screen.sre.skins.title_selector"),
                    rightPanelX + rightPanelWidth / 2, nameTagY - 20, TEXT_DIM);
            String current = getCurrentNameTag();
            Component displayText = current.isEmpty()
                    ? Component.translatable("screen.sre.skins.no_title")
                    : getNameTagDisplayText(current);
            graphics.drawCenteredString(font, displayText,
                    rightPanelX + rightPanelWidth / 2, nameTagY - 4, NAME_TAG_COLOR);
        } else {
            // 非名片页：在预览上方显示当前装备的皮肤名
            int labelY = listTop + 30;
            graphics.drawCenteredString(font, Component.translatable("screen.sre.skins.preview"),
                    rightPanelX + rightPanelWidth / 2, labelY, TEXT_DIM);
        }


        // 玩家预览
        int nameTagY = listTop + 34;
        int previewX1 = rightPanelX + 6;
        int previewY1 = nameTagY + 8;
        int previewX2 = rightPanelX + rightPanelWidth - 6;
        int previewY2 = listTop + listHeight - 12;
        int previewSize = Math.min(previewX2 - previewX1, previewY2 - previewY1);
        int modelScale = previewSize / MODEL_SCALE_DIVISOR;

        ItemStack previewStack = getPreviewStackForCurrentTab();
        ItemStack originalMainhand = player.getMainHandItem().copy();
        if (!previewStack.isEmpty()) {
            player.setItemSlot(EquipmentSlot.MAINHAND, previewStack);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400F);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                previewX1, previewY1, previewX2, previewY2, modelScale, 0.0625F, mouseX, mouseY, player);
        graphics.pose().popPose();
        player.setItemSlot(EquipmentSlot.MAINHAND, originalMainhand);

        graphics.pose().popPose();
    }

    private ItemStack getPreviewStackForCurrentTab() {
        if (categories.isEmpty() || selectedCategory < 0 || selectedCategory >= categories.size()) {
            return ItemStack.EMPTY;
        }
        CategoryTabData selectedTab = categories.get(selectedCategory);
        if (selectedTab.isHatTab() || selectedTab.isNameTagTab()) {
            return ItemStack.EMPTY;
        }
        ItemStack preview = new ItemStack(selectedTab.iconItem);
        preview.set(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN,
                skinsComponent.getEquippedSkin(selectedTab.id));
        return preview;
    }

    private void renderFooterStats(GuiGraphics graphics) {
        graphics.drawCenteredString(font, Component.translatable("screen.sre.skins.instructions"),
                width / 2, height - 10, TEXT_DIM);

        int totalSkins = 0;
        int unlockedSkins = 0;
        if (player != null && TMMItems.SkinableItem != null) {
            for (Item item : TMMItems.SkinableItem) {
                var skins = skinsComponent.getUnlockedSkins(new ItemStack(item));
                totalSkins += skins.size() + 1;
                unlockedSkins += (int) skins.values().stream().filter(b -> b).count() + 1;
            }
        }
        var hatSkins = ItemSkinManager.getSkins("hat");
        for (String hatName : hatSkins.keySet()) {
            if ("default".equals(hatName)) continue;
            totalSkins++;
            if (skinsComponent.isSkinUnlockedForItemType("hat", hatName)) unlockedSkins++;
        }
        if (totalSkins > 0) {
            graphics.drawString(font, Component.translatable("screen.sre.skins.stats", unlockedSkins, totalSkins),
                    10, 4, TEXT_DIM, false);
        }
    }

    // ─── 输入 ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        if (keyCode == 82) { // R — 刷新
            refreshSkinPanels();
            return true;
        }
        if (keyCode == 263 && selectedCategory > 0) { // ←
            selectCategory(selectedCategory - 1);
            return true;
        }
        if (keyCode == 262 && selectedCategory < categories.size() - 1) { // →
            selectCategory(selectedCategory + 1);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    public void refreshSkinPanels() {
        skinList = null;
        hatGrid = null;
        nameTagList = null;
        this.init();
    }

    private static String getItemShortName(Item item) {
        String name = item.getDescription().getString();
        return name.length() <= 12 ? name : name.substring(0, 10) + "...";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 内部控件
    // ═══════════════════════════════════════════════════════════════════════════

    // ─── CategoryButton ──────────────────────────────────────────────────────

    private static class CategoryButton extends Button {
        final boolean selected;
        private final ItemStack item;
        private final Component label;
        private float hoverAnim = 0f;
        private float selectAnim = 0f;

        // 复古主题分类按钮色
        private static final int TAB_BG = 0x991A1008;
        private static final int TAB_BG_HOVER = 0x992A1A08;
        private static final int TAB_BG_SELECT = 0x99302010;
        private static final int TAB_BORDER = 0x558B6914;
        private static final int TAB_BORDER_ACTIVE = 0xFFD4AF37;
        private static final int TAB_TEXT = 0xFFC8B898;
        private static final int TAB_TEXT_ACTIVE = 0xFFFFF4DC;

        CategoryButton(int x, int y, int width, int height, Component label, Item item,
                OnPress onPress, boolean selected) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.selected = selected;
            this.item = new ItemStack(item);
            this.label = label;
            this.selectAnim = selected ? 1f : 0f;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float dt = GuiAnim.currentDelta();
            hoverAnim = GuiAnim.toggle(hoverAnim, isHoveredOrFocused(), 14f, dt);
            selectAnim = GuiAnim.toggle(selectAnim, selected, 12f, dt);

            int bg = GuiAnim.blend(TAB_BG, TAB_BG_HOVER, hoverAnim);
            bg = GuiAnim.blend(bg, TAB_BG_SELECT, selectAnim);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);

            int border = GuiAnim.blend(TAB_BORDER, TAB_BORDER_ACTIVE,
                    Math.max(hoverAnim * 0.6f, selectAnim));
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, border);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, border);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);

            var font = Minecraft.getInstance().font;
            int textColor = GuiAnim.blend(TAB_TEXT, TAB_TEXT_ACTIVE,
                    Math.max(hoverAnim, selectAnim));

            int iconSize = 16;
            int iconTextGap = 3;
            int textWidth = font.width(label);
            boolean showText = iconSize + iconTextGap + textWidth + 4 <= width;
            int contentW = showText ? iconSize + iconTextGap + textWidth : iconSize;
            int iconY = getY() + (height - iconSize) / 2;
            int textY = getY() + (height - 8) / 2;
            int startX = getX() + (width - contentW) / 2;

            graphics.renderFakeItem(item, startX, iconY);
            if (showText) {
                graphics.drawString(font, label, startX + iconSize + iconTextGap, textY, textColor);
            }
        }
    }

    // ─── CenteredText ────────────────────────────────────────────────────────

    private static class CenteredText extends AbstractWidget {
        private final Component text;
        private final int color;

        CenteredText(int x, int y, Component text, int color) {
            super(x, y, 0, 0, text);
            this.text = text;
            this.color = color;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, text, getX() - font.width(text) / 2,
                    getY() - font.lineHeight / 2, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, text);
        }
    }

    // ─── ToggleCheckbox ──────────────────────────────────────────────────────

    private static class ToggleCheckbox extends AbstractWidget {
        private final Component label;
        private boolean toggled;
        private final java.util.function.Consumer<Boolean> onToggle;
        private float hoverAnim = 0f;

        private static final int BG_OFF = 0x991A1008;
        private static final int BG_ON = 0x992A2010;
        private static final int BG_HOVER = 0x992A1A08;
        private static final int BORDER = 0x558B6914;
        private static final int BORDER_ACTIVE = 0xFFD4AF37;
        private static final int CHECK_ON = 0xFFD4AF37;

        ToggleCheckbox(int x, int y, int width, int height, Component label, boolean initial,
                java.util.function.Consumer<Boolean> onToggle) {
            super(x, y, width, height, label);
            this.label = label;
            this.toggled = initial;
            this.onToggle = onToggle;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            float dt = GuiAnim.currentDelta();
            hoverAnim = GuiAnim.toggle(hoverAnim, isHoveredOrFocused(), 14f, dt);

            int bg = GuiAnim.blend(toggled ? BG_ON : BG_OFF, BG_HOVER, hoverAnim);
            int border = GuiAnim.blend(BORDER,
                    toggled ? BORDER_ACTIVE : GuiAnim.blend(0x558B6914, 0xFFC9A84C, hoverAnim),
                    Math.max(toggled ? 1f : 0f, hoverAnim * 0.5f));

            // 背景
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            // 边框
            g.fill(getX(), getY(), getX() + width, getY() + 1, border);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            g.fill(getX(), getY(), getX() + 1, getY() + height, border);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);

            // 勾选框
            int boxSize = Math.min(12, height - 4);
            int boxX = getX() + 3;
            int boxY = getY() + (height - boxSize) / 2;
            g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, toggled ? CHECK_ON : 0x553A3020);
            g.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0x558B6914);
            g.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0x558B6914);
            g.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0x558B6914);
            g.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0x558B6914);

            // 勾号
            if (toggled) {
                var font = Minecraft.getInstance().font;
                g.drawString(font, "\u2713", boxX + 1, boxY - 1, 0xFF1A1008, false);
            }

            // 标签
            int textColor = GuiAnim.blend(0xFF9E8B6E, 0xFFFFF4DC, Math.max(toggled ? 0.6f : 0f, hoverAnim));
            var font = Minecraft.getInstance().font;
            String clipped = font.plainSubstrByWidth(label.getString(), width - boxSize - 8);
            g.drawString(font, clipped, boxX + boxSize + 5, getY() + (height - 8) / 2, textColor, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOver(mouseX, mouseY)) {
                toggled = !toggled;
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                SoundEvents.UI_BUTTON_CLICK, 0.7f));
                if (onToggle != null) onToggle.accept(toggled);
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, label);
        }
    }

    // ─── ItemInfoPanel ───────────────────────────────────────────────────────

    private static class ItemInfoPanel extends AbstractWidget {
        private final ItemStack item;

        ItemInfoPanel(int x, int y, int width, int height, ItemStack item) {
            super(x, y, width, height, item.getDisplayName());
            this.item = item;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fillGradient(getX(), getY(), getX() + width, getY() + height,
                    0x991A1008, 0x9920140A);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0x558B6914);
            graphics.renderFakeItem(item, getX() + 5, getY() + (height - 16) / 2);
            graphics.drawString(Minecraft.getInstance().font, getMessage(),
                    getX() + 25, getY() + (height - 8) / 2, 0xFFFFF4DC, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 名片列表（称号选择）
    // ═══════════════════════════════════════════════════════════════════════════

    public static class NameTagList extends ObjectSelectionList<NameTagList.NameTagEntry> {
        private static final int ENTRY_HEIGHT = 32;
        private static final int SCROLLBAR_WIDTH = 7;

        private final SkinManagementScreen parentScreen;
        private boolean draggingScrollbar = false;

        // 复古主题色
        private static final int BG_COLOR = 0x801A1008;
        private static final int BORDER_COLOR = 0x558B6914;
        private static final int ENTRY_BG = 0x991A1008;
        private static final int ENTRY_BG_HOVER = 0x992A1A08;
        private static final int ENTRY_BG_SELECTED = 0xB0302010;
        private static final int TEXT_COLOR = 0xFFC8B898;
        private static final int TEXT_SELECTED = 0xFFF5E8C8;
        private static final int SELECTED_MARKER = 0xFFD4AF37;

        public NameTagList(SkinManagementScreen parentScreen, Minecraft mc,
                int x, int width, int height, int y, String searchFilter) {
            super(mc, width, height, y, ENTRY_HEIGHT);
            this.setX(x);
            this.parentScreen = parentScreen;
            rebuild(searchFilter);
        }

        public void rebuild() {
            rebuild("");
        }

        public void rebuild(String filter) {
            clearEntries();
            List<String> tags = parentScreen.getNameTags();
            String current = parentScreen.getCurrentNameTag();
            String f = filter != null ? filter.toLowerCase() : "";
            for (String tag : tags) {
                if (!f.isEmpty()) {
                    Component display = parentScreen.getNameTagDisplayText(tag);
                    if (!tag.toLowerCase().contains(f) && !display.getString().toLowerCase().contains(f)) {
                        continue;
                    }
                }
                addEntry(new NameTagEntry(tag, tag.equals(current)));
            }
            if (tags.isEmpty() || (filter != null && !filter.isEmpty() && children().isEmpty())) {
                addEntry(new NameTagEntry("__empty__", false));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + width - SCROLLBAR_WIDTH - 2;
        }

        @Override
        public int getRowWidth() {
            return width - SCROLLBAR_WIDTH - 10;
        }

        @Override
        protected void renderListBackground(@NotNull GuiGraphics g) {
            int x0 = getX(), y0 = getY();
            int x1 = x0 + width, y1 = y0 + height;
            g.fillGradient(x0, y0, x1, y1, BG_COLOR, BG_COLOR);
            g.fill(x0, y0, x1, y0 + 1, BORDER_COLOR);
            g.fill(x0, y1 - 1, x1, y1, BORDER_COLOR);
            g.fill(x0, y0, x0 + 1, y1, BORDER_COLOR);
            g.fill(x1 - 1, y0, x1, y1, BORDER_COLOR);
        }

        @Override
        protected void renderHeader(GuiGraphics g, int i, int j) {}

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderListBackground(g);
            g.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
            super.renderWidget(g, mouseX, mouseY, partialTick);
            g.disableScissor();
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingScrollbar) {
                double scrollAmount = getMaxScroll();
                if (scrollAmount > 0) {
                    double trackH = height - 6;
                    double thumbH = Math.max(18, (int) (height * (height / (double) (getMaxPosition() + height))));
                    double scrollPerPx = scrollAmount / (trackH - thumbH);
                    setScrollAmount(getScrollAmount() + dragY * scrollPerPx);
                }
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && getMaxScroll() > 0) {
                int trackX = getScrollbarPosition() + 1;
                int trackW = SCROLLBAR_WIDTH - 2;
                double thumbH = Math.max(18, (int) (height * (height / (double) (getMaxPosition() + height))));
                double thumbY = getY() + (height - thumbH) * (getScrollAmount() / (double) getMaxScroll());
                if (mouseX >= trackX && mouseX <= trackX + trackW
                        && mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    draggingScrollbar = true;
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            draggingScrollbar = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void renderSelection(GuiGraphics g, int top, int w, int h, int outer, int inner) {
            // 自定义选中行背景
            int y0 = top;
            int y1 = top + h;
            g.fillGradient(getX() + 2, y0, getX() + getRowWidth(), y1,
                    0x401A1008, 0x4020140A);
            // 左侧金色标记
            g.fill(getX() + 2, y0 + 2, getX() + 5, y1 - 2, SELECTED_MARKER);
        }

        public class NameTagEntry extends ObjectSelectionList.Entry<NameTagEntry> {
            final String tagId;
            final boolean isCurrent;
            float hoverAnim = 0f;

            NameTagEntry(String tagId, boolean isCurrent) {
                this.tagId = tagId;
                this.isCurrent = isCurrent;
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x,
                    int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                float dt = GuiAnim.currentDelta();
                hoverAnim = GuiAnim.toggle(hoverAnim, hovered, 14f, dt);

                if ("__empty__".equals(tagId)) {
                    g.drawCenteredString(Minecraft.getInstance().font,
                            Component.translatable("screen.sre.skins.no_title"),
                            x + entryWidth / 2, y + entryHeight / 2 - 4, 0xFF9E8B6E);
                    return;
                }

                // 背景
                int bg = GuiAnim.blend(ENTRY_BG, ENTRY_BG_HOVER, hoverAnim);
                if (isCurrent) bg = GuiAnim.blend(bg, ENTRY_BG_SELECTED, 0.5f + hoverAnim * 0.3f);
                g.fill(x + 2, y + 2, x + entryWidth - 4, y + entryHeight - 2, bg);

                // 边框
                int border = GuiAnim.blend(0x308B6914, 0xFFD4AF37, hoverAnim);
                if (isCurrent) border = GuiAnim.blend(border, 0xFFD4AF37, 0.6f);
                g.fill(x + 2, y + 2, x + entryWidth - 4, y + 3, border);
                g.fill(x + 2, y + entryHeight - 3, x + entryWidth - 4, y + entryHeight - 2, border);
                g.fill(x + 2, y + 2, x + 3, y + entryHeight - 2, border);
                g.fill(x + entryWidth - 5, y + 2, x + entryWidth - 4, y + entryHeight - 2, border);

                // 当前选中标记
                if (isCurrent) {
                    g.fill(x + 4, y + 4, x + 8, y + entryHeight - 4, SELECTED_MARKER);
                }

                // 文字
                Component display = parentScreen.getNameTagDisplayText(tagId);
                int textColor = isCurrent ? TEXT_SELECTED
                        : (int) GuiAnim.blend(0xFF9E8B6E, 0xFFFFF4DC, hoverAnim);
                var font = Minecraft.getInstance().font;
                g.drawString(font, font.plainSubstrByWidth(display.getString(), entryWidth - 30),
                        x + (isCurrent ? 14 : 6), y + (entryHeight - 8) / 2, textColor, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if ("__empty__".equals(tagId)) return false;
                if (button == 0) {
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    parentScreen.selectNameTag(tagId);
                    parentScreen.refreshSkinPanels();
                    return true;
                }
                return false;
            }

            @Override
            public @NotNull Component getNarration() {
                return parentScreen.getNameTagDisplayText(tagId);
            }
        }
    }
}
