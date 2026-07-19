package io.wifi.starrailexpress.client.gui.screen.ingame;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.client.hud.CommonClientHudRenderer;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.StoreRenderer;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen.StoreItemWidget;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen.WaitingMenuCellButton;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen.WaitingNavButton;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.network.original.StoreBuyPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public class LimitedInventoryScreen extends LimitedHandledScreen<InventoryMenu> {

    public static final ResourceLocation BACKGROUND_TEXTURE = SRE
            .watheId("textures/gui/container/limited_inventory.png");

    public static final ResourceLocation WAITED_INVENTORY_2_TEXTURE = SRE
            .id("textures/gui/waiting_inventory_2.png");

    private static final int SHOP_ITEM_SPACING_X = 38;
    private static final int SHOP_ITEM_SPACING_Y = 52;
    private int SHOP_MAX_ROWS_PER_PAGE = 1;
    private static final int SHOP_TOP_SAFE_Y = 20;

    public final LocalPlayer player;

    public LimitedInventoryScreen(@NotNull LocalPlayer player) {
        super(player.inventoryMenu, player.getInventory(), Component.empty());
        this.player = player;
    }

    public Button menuButton = null;
    public static final int menuButtonHeight = 20;
    public static final int menuButtonWidth = 100;
    public ArrayList<Button> menuSelections = new ArrayList<>();
    public boolean isMenuOpen = false;

    private final ArrayList<StoreItemWidget> shopWidgets = new ArrayList<>();
    private int shopCurrentPage = 0;
    private int shopTotalPages = 1;
    private int shopColumns = 1;
    private int shopRowsOnCurrentPage = 1;

    private int shopGridStartY = 0;
    private int shopNavY = 0;

    private Button shopPrevPageButton = null;
    private Button shopNextPageButton = null;

    // ===== 等待面板（waiting_inventory_2.png）布局 =====
    // 面板尺寸（贴图绘制区域）；内容实际约 174x165，锚定在屏幕中央。
    private static final float PANEL_SCALE = 1.25F;
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 176;
    // 顶部左侧方格（头像）中心
    private static final int HEAD_CX = 33, HEAD_CY = 38, HEAD_SIZE = 32;
    // 顶部右侧宽格（名字 + 状态）文字起点
    private static final int INFO_X = 66, INFO_NAME_Y = 24, INFO_STATUS_Y = 42;
    // 中部 4 个菜单格子的中心 X 与公共中心 Y
    private static final int[] MENU_CELL_CX = { 27, 67, 108, 148 };
    private static final int MENU_CELL_CY = 110;
    private static final int MENU_BTN_W = 38, MENU_BTN_H = 46;
    private static final int MENU_PER_PAGE = MENU_CELL_CX.length;
    private static final String FOUR_CJK_CHARACTERS = "中中中中";
    // 底部 9 个快捷栏槽位
    private static final int HOTBAR_FIRST_X = 7, HOTBAR_Y = 141, HOTBAR_STEP = 18;
    // 翻页栏（中部分隔条）
    private static final int NAV_Y = 70;

    private final ArrayList<WaitingMenuCellButton> waitingMenuButtons = new ArrayList<>();
    private Button waitingPrevPageButton = null;
    private Button waitingNextPageButton = null;
    private int waitingMenuPage = 0;
    private int waitingMenuPages = 1;

    /**
     * 复用同一个包装实例绘制信息行；界面渲染时不在 Gui.render 帧内，FakeGuiGraphics 会直接透传给真实 GuiGraphics。
     */
    private FakeGuiGraphics fakeGraphics = null;

    // 右上角：参与 / 不参与本局游戏切换按钮
    private Button participationButton = null;
    private static final int PARTICIPATION_BTN_W = 40;
    private static final int PARTICIPATION_BTN_H = 20;

    private static int scaled(int value) {
        return Math.round(value * PANEL_SCALE);
    }

    private int panelLeft() {
        return (this.width - scaled(PANEL_W)) / 2;
    }

    private int panelTop() {
        return (this.height - scaled(PANEL_H)) / 2;
    }

    /**
     * 判断游戏是否已开始（gameComponent存在且玩家存活在生存模式）
     */
    private boolean isGameActive() {
        return SREClient.gameComponent != null && SREClient.gameComponent.isRunning();
    }

    /** 当前玩家是否参与下一局游戏（默认参与）。 */
    private boolean isParticipating() {
        if (this.player == null) {
            return true;
        }
        return ParticipationComponent.KEY.get(this.player.level()).isParticipating(this.player);
    }

    /** 切换按钮文案：参与中 -> 显示“不参与”，未参与 -> 显示“参与”。 */
    private Component participationButtonLabel() {
        return Component.translatable("screen.limited_inventory.button.participate.status",
                Component.translatable(!isParticipating()
                        ? "screen.limited_inventory.button.participate.leave"
                        : "screen.limited_inventory.button.participate.join"));
    }

    public void toggleViewMenu(boolean flag) {
        this.isMenuOpen = flag;
        if (menuButton != null) {
            menuButton.setMessage(
                    Component.translatable("screen.limited_inventory.button.menu." + (!isMenuOpen ? "show" : "hide")));
        }
        boolean gameActive = isGameActive();
        for (var ms : menuSelections) {
            ms.visible = this.isMenuOpen && gameActive;
            ms.active = this.isMenuOpen && gameActive;
        }
    }

    public static List<ShopEntry> getRoleShopEntries(SRERole role) {
        if (role == null)
            return List.of();
        final var shopEntries = ShopContent.getShopEntries(role.getIdentifier());
        if (shopEntries != null)
            return shopEntries;
        return List.of();
    }

    public List<ShopEntry> getShopEntries() {
        final var player = Minecraft.getInstance().player;
        var gameWorldComponent = SREClient.gameComponent;
        if (gameWorldComponent == null)
            return List.of();
        if (SREClient.gameComponent != null && SREClient.isPlayerAliveAndInSurvival()) {
            final var role = gameWorldComponent.getRole(player);
            if (role == null)
                return List.of();
            return getRoleShopEntries(role);
        }
        return List.of();
    }

    public static class ShopEntryDisplayItem extends ShopEntry {
        public ShopEntryDisplayItem(ItemStack stack, int price, Type type) {
            super(stack, price, type);
        }

        public ShopEntryDisplayItem(ShopEntry shopEntry, int index) {
            this(shopEntry.stack(), shopEntry.price(), shopEntry.type());
            this.index = index;
        }

        public static ArrayList<ShopEntryDisplayItem> transferArrayList(List<ShopEntry> shopEntries, Player player) {
            ArrayList<ShopEntryDisplayItem> displayAbleEntries = new ArrayList<>();
            int idx = 0;
            for (var entry : shopEntries) {
                if (entry.canDisplay(player)) {
                    displayAbleEntries.add(new ShopEntryDisplayItem(entry, idx));
                }
                idx++;
            }
            return displayAbleEntries;
        }

        public int index = 0;
    }

    @Override
    protected void init() {
        super.init();
        initMenuSelections();

        shopWidgets.clear();
        shopCurrentPage = 0;

        List<ShopEntry> entries = getShopEntries();
        List<ShopEntryDisplayItem> displayAbleEntries = ShopEntryDisplayItem.transferArrayList(entries, player);

        if (!displayAbleEntries.isEmpty()) {
            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                final var role = gameComponent.getRole(player);
                if (role != null && role.getAddChild() != null) {
                    role.getAddChild().accept(this);
                }
            }

            for (var t : displayAbleEntries) {
                var widget = new StoreItemWidget(this, 0, 0, t, t.index);
                shopWidgets.add(widget);
                this.addRenderableWidget(widget);
            }
        }

        shopPrevPageButton = this.addRenderableWidget(
                Button.builder(Component.literal("<"), (btn) -> changeShopPage(-1))
                        .bounds(0, -8, 20, 20)
                        .build());
        shopNextPageButton = this.addRenderableWidget(
                Button.builder(Component.literal(">"), (btn) -> changeShopPage(1))
                        .bounds(0, -8, 20, 20)
                        .build());

        // 参与切换按钮：占据等待面板右上角“参与状态”原文字的位置，实际坐标在 layoutWaitingMenu 中设置
        participationButton = this.addRenderableWidget(
                Button.builder(participationButtonLabel(), b -> {
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.connection.sendCommand("tmm:participate");
                    }
                }).tooltip(Tooltip
                        .create(Component.translatable("screen.limited_inventory.button.participate.hover_text")))
                        .bounds(0, 0, PARTICIPATION_BTN_W, PARTICIPATION_BTN_H).build());
        refreshShopLayout();
        initWaitingMenu();
    }

    /** 创建等待面板中的便捷菜单格子按钮与翻页按钮（复用 GameMenuEntries 的同一组动作）。 */
    private void initWaitingMenu() {
        waitingMenuButtons.clear();
        waitingMenuPage = 0;
        for (var entry : GameMenuEntries.entries_hub(minecraft, this, this::toggleViewMenu)) {
            var btn = new WaitingMenuCellButton(entry.label(), entry.action());
            waitingMenuButtons.add(btn);
            this.addRenderableWidget(btn);
        }
        waitingPrevPageButton = this.addRenderableWidget(
                new WaitingNavButton(Component.literal("<"), (b) -> changeWaitingMenuPage(-1)));
        waitingNextPageButton = this.addRenderableWidget(
                new WaitingNavButton(Component.literal(">"), (b) -> changeWaitingMenuPage(1)));
        layoutWaitingMenu();
    }

    private void changeWaitingMenuPage(int delta) {
        int next = Math.max(0, Math.min(waitingMenuPages - 1, waitingMenuPage + delta));
        if (next != waitingMenuPage) {
            waitingMenuPage = next;
            layoutWaitingMenu();
        }
    }

    /** 重新计算等待菜单按钮位置（依赖屏幕尺寸），并刷新可见性。 */
    private void layoutWaitingMenu() {
        int px = panelLeft();
        int py = panelTop();
        int count = waitingMenuButtons.size();
        waitingMenuPages = Math.max(1, (count + MENU_PER_PAGE - 1) / MENU_PER_PAGE);
        waitingMenuPage = Math.max(0, Math.min(waitingMenuPage, waitingMenuPages - 1));
        int start = waitingMenuPage * MENU_PER_PAGE;
        for (int i = 0; i < count; i++) {
            int slot = i - start;
            var btn = waitingMenuButtons.get(i);
            if (slot >= 0 && slot < MENU_PER_PAGE) {
                int cx = px + scaled(MENU_CELL_CX[slot]);
                btn.setPosition(cx - scaled(MENU_BTN_W) / 2,
                        py + scaled(MENU_CELL_CY) - scaled(MENU_BTN_H) / 2);
            }
        }
        if (waitingPrevPageButton != null && waitingNextPageButton != null) {
            // 让“< 页码 >”整体在导航栏上居中、垂直对齐，与面板背景协调
            int navCenterX = px + scaled(PANEL_W) / 2;
            int navY = py + scaled(NAV_Y);
            int btnW = scaled(12);
            int gap = scaled(4);
            Component pageText = Component.literal((waitingMenuPage + 1) + " / " + waitingMenuPages);
            int textW = Math.round(this.font.width(pageText) * PANEL_SCALE);
            waitingPrevPageButton.setPosition(navCenterX - textW / 2 - gap - btnW, navY);
            waitingNextPageButton.setPosition(navCenterX + textW / 2 + gap, navY);
        }

        // 参与切换按钮：放在右上角信息格内（原参与状态文字所在位置）
        if (participationButton != null) {
            int bw = scaled(PANEL_W - INFO_X - 20);
            int bh = scaled(16);
            participationButton.setWidth(bw);
            participationButton.setHeight(bh);
            participationButton.setPosition(px + scaled(INFO_X), py + scaled(INFO_STATUS_Y) - scaled(2));
        }

        updateWaitingMenuVisibility();
    }

    /** 根据游戏状态/当前页设置等待菜单控件可见性。 */
    private void updateWaitingMenuVisibility() {
        boolean show = !isGameActive();
        int start = waitingMenuPage * MENU_PER_PAGE;
        for (int i = 0; i < waitingMenuButtons.size(); i++) {
            boolean onPage = show && i >= start && i < start + MENU_PER_PAGE;
            var btn = waitingMenuButtons.get(i);
            btn.visible = onPage;
            btn.active = onPage;
        }
        boolean paging = show && waitingMenuPages > 1;
        if (waitingPrevPageButton != null) {
            waitingPrevPageButton.visible = paging;
            waitingPrevPageButton.active = paging && waitingMenuPage > 0;
        }
        if (waitingNextPageButton != null) {
            waitingNextPageButton.visible = paging;
            waitingNextPageButton.active = paging && waitingMenuPage < waitingMenuPages - 1;
        }
    }

    public void initMenuSelections() {
        menuButton = Button
                .builder(Component.translatable("screen.limited_inventory.button.menu"), (btn) -> {
                    toggleViewMenu(!this.isMenuOpen);
                }).bounds(width - menuButtonWidth, height - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                .build();
        this.addRenderableWidget(menuButton);
        menuSelections.clear();
        menuSelections.addAll(GameMenuEntries.register(width, height, minecraft, this, this::toggleViewMenu));
        for (var ms : menuSelections) {
            this.addRenderableWidget(ms);
        }
        toggleViewMenu(false);
    }

    private void changeShopPage(int delta) {
        int next = Math.max(0, Math.min(shopTotalPages - 1, shopCurrentPage + delta));
        if (next != shopCurrentPage) {
            shopCurrentPage = next;
            refreshShopLayout();
        }
    }

    private void refreshShopLayout() {
        SHOP_MAX_ROWS_PER_PAGE = Math.max(1, Math.min((this.y - SHOP_TOP_SAFE_Y - 24) / SHOP_ITEM_SPACING_Y, 4));
        int count = shopWidgets.size();
        if (count <= 0) {
            shopTotalPages = 1;
            shopRowsOnCurrentPage = 1;
            if (shopPrevPageButton != null) {
                shopPrevPageButton.visible = false;
                shopPrevPageButton.active = false;
            }
            if (shopNextPageButton != null) {
                shopNextPageButton.visible = false;
                shopNextPageButton.active = false;
            }
            return;
        }

        int availableWidth = Math.max(1, (int) (this.width * 0.6f));
        shopColumns = Math.max(1, availableWidth / SHOP_ITEM_SPACING_X);

        int totalRows = (count + shopColumns - 1) / shopColumns;
        shopTotalPages = (totalRows + SHOP_MAX_ROWS_PER_PAGE - 1) / SHOP_MAX_ROWS_PER_PAGE;
        shopCurrentPage = Math.max(0, Math.min(shopCurrentPage, shopTotalPages - 1));

        int startRow = shopCurrentPage * SHOP_MAX_ROWS_PER_PAGE;
        int remainingRows = Math.max(0, totalRows - startRow);
        shopRowsOnCurrentPage = Math.max(1, Math.min(SHOP_MAX_ROWS_PER_PAGE, remainingRows));

        int baseY = this.y;

        if (remainingRows <= 0) {
            baseY += 6;
        }
        int offsetUp = (shopRowsOnCurrentPage) * SHOP_ITEM_SPACING_Y;
        shopGridStartY = Math.max(SHOP_TOP_SAFE_Y, baseY - offsetUp);
        for (int i = 0; i < count; i++) {
            var widget = shopWidgets.get(i);
            int globalRow = i / shopColumns;
            int col = i % shopColumns;

            boolean visible = globalRow >= startRow && globalRow < startRow + shopRowsOnCurrentPage;
            widget.visible = visible;
            widget.active = visible;

            if (!visible)
                continue;

            int localRow = globalRow - startRow;
            int itemsInThisRow = Math.min(shopColumns, count - globalRow * shopColumns);

            int rowStartX = this.width / 2 - ((itemsInThisRow) * SHOP_ITEM_SPACING_X) / 2 + 10;
            int x = rowStartX + col * SHOP_ITEM_SPACING_X;
            int y = shopGridStartY + localRow * SHOP_ITEM_SPACING_Y;

            widget.setPosition(x, y);
        }

        shopNavY = Math.min(this.height - 24, shopGridStartY + shopRowsOnCurrentPage * SHOP_ITEM_SPACING_Y - 16) - 8;

        boolean needPaging = shopTotalPages > 1;
        if (shopPrevPageButton != null && shopNextPageButton != null) {
            if (needPaging) {
                Component pageText = Component.literal((shopCurrentPage + 1) + " / " + shopTotalPages);
                int textW = this.font.width(pageText);
                int gap = 6;
                int btnW = 20;

                int centerX = this.width / 2;
                int prevX = centerX - (textW / 2) - gap - btnW;
                int nextX = centerX + (textW / 2) + gap;

                shopPrevPageButton.setPosition(prevX, shopNavY);
                shopNextPageButton.setPosition(nextX, shopNavY);
            }

            shopPrevPageButton.visible = needPaging;
            shopNextPageButton.visible = needPaging;
            shopPrevPageButton.active = needPaging && shopCurrentPage > 0;
            shopNextPageButton.active = needPaging && shopCurrentPage < shopTotalPages - 1;
        }
    }

    @Override
    protected void drawBackground(@NotNull GuiGraphics context, float delta, int mouseX, int mouseY) {
        if (isGameActive()) {
            context.blit(BACKGROUND_TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
        } else {
            renderWaitingPanel(context);
        }
    }

    /** 绘制等待面板背景；菜单按钮与真实快捷栏槽位由各自的原生组件绘制。 */
    private void renderWaitingPanel(GuiGraphics context) {
        int px = panelLeft();
        int py = panelTop();
        context.blit(WAITED_INVENTORY_2_TEXTURE, px, py, scaled(PANEL_W), scaled(PANEL_H),
                0, 0, PANEL_W, PANEL_H, 256, 256);

        // 顶部左格：玩家头像
        PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(player.getUUID());
        if (info != null && info.getSkin().texture() != null) {
            PlayerFaceRenderer.draw(context, info.getSkin().texture(),
                    px + scaled(HEAD_CX - HEAD_SIZE / 2), py + scaled(HEAD_CY - HEAD_SIZE / 2), scaled(HEAD_SIZE));
        }

        // 顶部右格：玩家名（参与状态由覆盖在下方的切换按钮承担，由原生组件绘制）
        drawPanelString(context, player.getName(), px + scaled(INFO_X), py + scaled(INFO_NAME_Y), 0xFFFFFFFF);

        // 翻页页码：与左右箭头按钮在导航栏上居中、垂直对齐
        if (waitingMenuPages > 1) {
            Component pageText = Component.literal((waitingMenuPage + 1) + " / " + waitingMenuPages);
            int textX = px + scaled(PANEL_W) / 2 - Math.round(this.font.width(pageText) * PANEL_SCALE / 2);
            int textY = py + scaled(NAV_Y) + (scaled(12) - Math.round(this.font.lineHeight * PANEL_SCALE)) / 2;
            drawPanelString(context, pageText, textX, textY, 0xFFFFFFFF);
        }
    }

    private void drawPanelString(GuiGraphics context, Component text, int x, int y, int color) {
        context.pose().pushPose();
        context.pose().translate(x, y, 0);
        context.pose().scale(PANEL_SCALE, PANEL_SCALE, 1.0F);
        context.drawString(this.font, text, 0, 0, color, false);
        context.pose().popPose();
    }

    @Override
    protected boolean shouldRenderHotbarSlots() {
        return true;
    }

    @Override
    protected int getSlotRenderX(Slot slot) {
        if (!isGameActive() && slot.index >= 36 && slot.index <= 44) {
            return panelLeft() + scaled(HOTBAR_FIRST_X + (slot.index - 36) * HOTBAR_STEP) - this.x;
        }
        return super.getSlotRenderX(slot);
    }

    @Override
    protected int getSlotRenderY(Slot slot) {
        if (!isGameActive() && slot.index >= 36 && slot.index <= 44) {
            int translatedScreenTop = this.y - getYOffset();
            return panelTop() + scaled(HOTBAR_Y) - translatedScreenTop;
        }
        return super.getSlotRenderY(slot);
    }

    @Override
    protected float getSlotRenderScale(Slot slot) {
        if (!isGameActive() && slot.index >= 36 && slot.index <= 44) {
            return PANEL_SCALE;
        }
        return super.getSlotRenderScale(slot);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        refreshShopLayout();
        layoutWaitingMenu();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        renderOverlayMessageOnScreen(context, mouseX, mouseY, delta);
        boolean gameActive = isGameActive();

        // 游戏未开始时：隐藏整个快捷菜单（按钮和选项都不显示）
        if (menuButton != null) {
            menuButton.visible = gameActive;
            menuButton.active = gameActive;
        }
        for (var ms : menuSelections) {
            ms.visible = this.isMenuOpen && gameActive;
            ms.active = this.isMenuOpen && gameActive;
        }
        // 未开始时：等待面板内的便捷菜单接管交互
        updateWaitingMenuVisibility();

        // 右上角参与切换按钮：仅在等待（游戏未开始）时显示，文案随状态刷新
        if (participationButton != null) {
            participationButton.visible = !gameActive;
            participationButton.active = !gameActive;
            participationButton.setMessage(participationButtonLabel());
        }

        if (shopTotalPages > 1) {
            Component pageText = Component.literal((shopCurrentPage + 1) + " / " + shopTotalPages);
            int textX = this.width / 2 - this.font.width(pageText) / 2;
            int textY = shopNavY + (20 - this.font.lineHeight) / 2; // 与 20x20 按钮垂直居中
            context.drawString(this.font, pageText, textX, textY, 0xFFFFFF, false);
        }

        // 面板在背景层绘制，菜单按钮和真实快捷栏槽位在其上层绘制。

        this.drawMouseoverTooltip(context, mouseX, mouseY);
        StoreRenderer.renderHud(this.font, this.player, context, delta);

        // 金币下方的信息行：开启配置后只在本界面显示（HUD 上不再绘制），位置与 HUD 上一致
        if (SREClientConfig.instance().showInfoLinesInInventory && this.minecraft != null) {
            if (fakeGraphics == null || fakeGraphics.getDefaultGuiGraphics() != context) {
                fakeGraphics = new FakeGuiGraphics(context, true);
            }
            CommonClientHudRenderer.renderMessagesBelowMoney(this.minecraft, fakeGraphics, DeltaTracker.ONE, true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 60s 模式：原版 Q → 屏障区批量丢弃；Shift+1~9 → 屏障区转移到对应快捷栏
        if (isGameActive() && minecraft != null && minecraft.player != null) {
            if (this.minecraft.options.keyDrop.matches(keyCode, scanCode) && !hasControlDown()) {
                quickDropFromBarrierArea();
                return true;
            }
            if (hasShiftDown()) {
                for (int i = 0; i < 9; i++) {
                    if (this.minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                        transferFromBarrierToHotbar(i);
                        return true;
                    }
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 找到背包中第一个屏障占位槽的索引（0-35），没有则返回 36。 */
    private int findFirstBarrierSlot() {
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.BARRIER)) {
                return i;
            }
        }
        return 36;
    }

    /** 快速转移：将屏障区中非屏障物品快速移入可用槽位。 */
    private void quickTransferFromBarrierArea() {
        int firstBarrier = findFirstBarrierSlot();
        if (firstBarrier > 35) return;
        for (int i = firstBarrier; i <= 35; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
                this.minecraft.gameMode.handleInventoryMouseClick(
                        this.handler.containerId, i, 0, ClickType.QUICK_MOVE, this.minecraft.player);
            }
        }
    }

    /** Shift+数字键：将屏障区第一个非屏障物品换入指定快捷栏（索引 0-8 → 槽位 36-44）。 */
    private void transferFromBarrierToHotbar(int hotbarIndex) {
        int firstBarrier = findFirstBarrierSlot();
        if (firstBarrier > 35) return;
        int targetSlot = 36 + hotbarIndex;
        for (int i = firstBarrier; i <= 35; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
                this.minecraft.gameMode.handleInventoryMouseClick(
                        this.handler.containerId, i, hotbarIndex, ClickType.SWAP, this.minecraft.player);
                return;
            }
        }
    }

    /** 快速丢弃：将屏障区中非屏障物品丢弃到地面。 */
    private void quickDropFromBarrierArea() {
        int firstBarrier = findFirstBarrierSlot();
        if (firstBarrier > 35) return;
        for (int i = firstBarrier; i <= 35; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
                this.minecraft.gameMode.handleInventoryMouseClick(
                        this.handler.containerId, i, 0, ClickType.THROW, this.minecraft.player);
            }
        }
    }

    private void renderOverlayMessageOnScreen(GuiGraphics context, int mouseX, int mouseY, float delta) {
        var message = minecraft.gui.overlayMessageString;
        int displaytime = minecraft.gui.overlayMessageTime;
        if (message == null || displaytime <= 0)
            return;
        float f = (float) displaytime - delta;
        int i = (int) (f * 255.0F / 20.0F);
        if (i > 255) {
            i = 255;
        }

        if (i > 8) {
            int j;
            if (minecraft.gui.animateOverlayMessageColor) {
               j = Mth.hsvToArgb(f / 50.0F, 0.7F, 0.6F, i);
            } else {
               j = ARGB32.color(i, -1);
            }
            int x = width;
            int y = 10;
            int twidth = font.width(message);
            {
                context.drawStringWithBackdrop(font, message, (x - twidth) / 2, y, twidth, j);
            }
        }
    }

    /** 等待面板翻页箭头：无原版按钮底，仅绘制居中箭头，悬停高亮，与面板背景风格统一。 */
    public class WaitingNavButton extends Button {
        public WaitingNavButton(@NotNull Component label, @NotNull OnPress onPress) {
            super(0, -scaled(12), scaled(12), scaled(12), label, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            if (!this.visible)
                return;
            Font font = LimitedInventoryScreen.this.font;
            boolean usable = this.active && this.isHovered();
            if (usable) {
                context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x40FFFFFF);
            }
            int color = !this.active ? 0xFF707070 : (this.isHovered() ? 0xFFFFFFFF : 0xFFE0E0E0);
            int lineWidth = Math.round(font.width(this.getMessage()) * PANEL_SCALE);
            int lineHeight = Math.round(font.lineHeight * PANEL_SCALE);
            int textX = getX() + (this.width - lineWidth) / 2;
            int textY = getY() + (this.height - lineHeight) / 2;
            context.pose().pushPose();
            context.pose().translate(textX, textY, 0);
            context.pose().scale(PANEL_SCALE, PANEL_SCALE, 1.0F);
            context.drawString(font, this.getMessage(), 0, 0, color, false);
            context.pose().popPose();
        }

        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }

    /** 等待面板中部格子按钮：无默认按钮底，悬停高亮，长标题按四个中文字的宽度折行。 */
    public class WaitingMenuCellButton extends Button {
        public WaitingMenuCellButton(@NotNull Component label, @NotNull OnPress onPress) {
            super(0, -scaled(MENU_BTN_H), scaled(MENU_BTN_W), scaled(MENU_BTN_H), label, onPress,
                    DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            if (!this.visible)
                return;
            Font font = LimitedInventoryScreen.this.font;
            if (this.isHovered()) {
                context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x40FFFFFF);
            }
            int logicalButtonWidth = Math.round((this.width - scaled(4)) / PANEL_SCALE);
            int maxLineWidth = Math.min(logicalButtonWidth, font.width(FOUR_CJK_CHARACTERS));
            List<FormattedCharSequence> lines = font.split(this.getMessage(), maxLineWidth);
            int totalHeight = Math.round(lines.size() * font.lineHeight * PANEL_SCALE);
            int textY = getY() + (this.height - totalHeight) / 2;
            int color = this.isHovered() ? 0xFFFFFFFF : 0xFFE0E0E0;
            for (FormattedCharSequence line : lines) {
                int lineWidth = Math.round(font.width(line) * PANEL_SCALE);
                int textX = getX() + (this.width - lineWidth) / 2;
                context.pose().pushPose();
                context.pose().translate(textX, textY, 0);
                context.pose().scale(PANEL_SCALE, PANEL_SCALE, 1.0F);
                context.drawString(font, line, 0, 0, color, false);
                context.pose().popPose();
                textY += Math.round(font.lineHeight * PANEL_SCALE);
            }
        }

        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }

    public static class StoreItemWidget extends Button {
        public final LimitedInventoryScreen screen;
        public final ShopEntry entry;
        /** 该条目在商店列表中的下标，用于在渲染时取实时（已同步）条目价格，避免显示价与扣费价不同步。 */
        public final int index;

        public StoreItemWidget(LimitedInventoryScreen screen, int x, int y, @NotNull ShopEntry entry, int index) {
            super(x, y, 16, 16, entry.stack().getHoverName(),
                    (a) -> ClientPlayNetworking.send(new StoreBuyPayload(index)), DEFAULT_NARRATION);
            this.screen = screen;
            this.entry = entry;
            this.index = index;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(entry.type().getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);

            context.renderItem(this.entry.stack(), this.getX(), this.getY());
            if (this.isHovered()) {
                this.screen.renderLimitedInventoryTooltip(context, this.entry.stack());
                drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            }
            // \u663E\u793A\u52A8\u6001\u4EF7\u683C\uFF1A\u82E5\u88AB DynamicShopComponent
            // \u6253\u6298\uFF0C\u5219\u7528\u7EFF\u8272\u5C55\u793A\u6298\u540E\u4EF7\u3002
            // Show the dynamic price: if discounted by DynamicShopComponent, render the
            // reduced price in green.
            // 取实时（已同步）的商店条目，使显示的基础价/折后价与服务端实际扣费价一致；
            // 开屏后若基础价被调整（如 /sre config reload 重建商店），快照价会过期，这里改读实时条目。
            ShopEntry liveEntry = this.entry;
            List<ShopEntry> liveEntries = this.screen.getShopEntries();
            if (this.index >= 0 && this.index < liveEntries.size()) {
                liveEntry = liveEntries.get(this.index);
            }
            int basePrice = liveEntry.price();
            // 用服务端同步来的价格覆盖显示用基础价（按下标 + itemId 校验），保证显示价与服务端实际扣费一致。
            basePrice = io.wifi.starrailexpress.shop.client.ShopPriceClientCache.overrideBasePrice(this.index,
                    liveEntry.stack(), basePrice);
            int effectivePrice = basePrice;
            final var clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                effectivePrice = DynamicShopComponent.KEY.get(clientPlayer).effectivePrice(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(liveEntry.stack().getItem()),
                        basePrice);
            }
            MutableComponent price = Component.literal(effectivePrice + "\uE781");
            int displayX = this.getX() - 4 - this.screen.font.width(price) / 2;
            int displayY = this.getY() - 9;
            List<Component> renders = new ArrayList<>();

            if (effectivePrice < basePrice) {
                displayY -= 12;
                renders.add(Component.translatable("gui.starrailexpress.shop.discount",
                        100 - (int) ((double) effectivePrice / (double) basePrice * 100.0))
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                // price.withStyle(net.minecraft.ChatFormatting.GREEN);
            }
            renders.add(price);
            context.renderComponentTooltip(this.screen.font, renders, displayX, displayY);
            ;
        }

        private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
            int color = 0x90FFBF49;
            context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, z);
            context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, z);
            context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, z);
        }

        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }
}
