package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.StoreRenderer;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.network.original.StoreBuyPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class LimitedInventoryScreen extends LimitedHandledScreen<InventoryMenu> {

    public static final ResourceLocation BACKGROUND_TEXTURE = SRE
            .watheId("textures/gui/container/limited_inventory.png");

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

    public void toggleViewMenu(boolean flag) {
        this.isMenuOpen = flag;
        if (menuButton != null) {
            menuButton.setMessage(
                    Component.translatable("screen.limited_inventory.button.menu." + (!isMenuOpen ? "show" : "hide")));
        }
        for (var ms : menuSelections) {
            ms.visible = this.isMenuOpen;
            ms.active = this.isMenuOpen;
        }
    }

    public static List<ShopEntry> getRoleShopEntries(SRERole role) {
        if (role == null)
            return List.of();
        final var shopEntries = ShopContent.getShopEntries(role.getIdentifier());
        if (!shopEntries.isEmpty())
            return shopEntries;
        if (role.canUseKiller())
            return ShopContent.defaultKnifeEntries;
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

        refreshShopLayout();
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
        context.blit(BACKGROUND_TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        context.pose().pushPose();
        context.pose().translate(context.guiWidth() / 2f, context.guiHeight(), 0);
        context.pose().popPose();
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        refreshShopLayout();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (shopTotalPages > 1) {
            Component pageText = Component.literal((shopCurrentPage + 1) + " / " + shopTotalPages);
            int textX = this.width / 2 - this.font.width(pageText) / 2;
            int textY = shopNavY + (20 - this.font.lineHeight) / 2; // 与 20x20 按钮垂直居中
            context.drawString(this.font, pageText, textX, textY, 0xFFFFFF, false);
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);
        StoreRenderer.renderHud(this.font, this.player, context, delta);
    }

    public static class StoreItemWidget extends Button {
        public final LimitedInventoryScreen screen;
        public final ShopEntry entry;

        public StoreItemWidget(LimitedInventoryScreen screen, int x, int y, @NotNull ShopEntry entry, int index) {
            super(x, y, 16, 16, entry.stack().getHoverName(),
                    (a) -> ClientPlayNetworking.send(new StoreBuyPayload(index)), DEFAULT_NARRATION);
            this.screen = screen;
            this.entry = entry;
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
            MutableComponent price = Component.literal(this.entry.price() + "\uE781");
            context.renderTooltip(this.screen.font, price, this.getX() - 4 - this.screen.font.width(price) / 2,
                    this.getY() - 9);
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