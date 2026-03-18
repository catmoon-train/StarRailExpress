package org.agmas.noellesroles.client.screen;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ViewLotteryPoolScreen extends AbstractPixelScreen{
    public static class ItemCardGroup {
        public ItemCardGroup() {
            itemCards = new ArrayList<>();
        }
        public ItemCardGroup(List<List<ItemCard>> itemCards, int height) {
            this.itemCards = itemCards;
            this.height = height;
            minY += height;
        }
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            itemCards.forEach(itemCardGroup -> {
                itemCardGroup.forEach(itemCard -> itemCard.render(guiGraphics, mouseX, mouseY, delta));
            });
        }
        public void setItemCards(List<List<ItemCard>> itemCards) {
            this.itemCards = itemCards;
        }
        public void setHeight(int height) {
            this.height = height;
            minY -= height;
        }
        public void addY(double y) {
            if (y < 0 && this.y <= minY)
                return;
            if (y > 0 && this.y >= maxY)
                return;
            this.y += y;
            itemCards.forEach(itemCardGroup -> {
                itemCardGroup.forEach(itemCard -> itemCard.setY(itemCard.getY() + (int)y));
            });
        }
        public double x = 0, y = 0;
        public double height = 0;
        public double minY = -100, maxY = 100;
        public List<List<ItemCard>> itemCards = null;
    }
    public static class ItemCard extends AbstractWidget {
        public ItemCard(int i, int j, int pixelSize, int quality, ResourceLocation itemResLocation, Button.OnPress onPress) {
            super(i, j,
                    ITEM_PIXELS_SIZE * pixelSize,
                    (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE) * pixelSize, Component.empty());
            item = new TextureWidget(
                    i, j,
                    ITEM_PIXELS_SIZE * pixelSize, ITEM_PIXELS_SIZE * pixelSize,
                    ITEM_PIXELS_SIZE, ITEM_PIXELS_SIZE,
                    itemResLocation
            );
            qualityBar = new TextureWidget(
                    i, j + ITEM_PIXELS_SIZE * pixelSize,
                    ITEM_PIXELS_SIZE * pixelSize, QUALITY_BAR_PIXELS_SIZE * pixelSize,
                    ITEM_PIXELS_SIZE, QUALITY_BAR_PIXELS_SIZE,
                    LotteryManager.getInstance().getQualityBgResourceLocation(quality)
            );
            button = Button.builder(
                    Component.empty(),
                    onPress)
                    .pos(i, j)
                    .size(ITEM_PIXELS_SIZE * pixelSize, (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE) * pixelSize)
                    .build();
            button.setAlpha(0f);
            this.pixelSize = pixelSize;
        }
        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            // render BG
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BG_COLOR.getRGB());
            item.render(guiGraphics, i, j, f);
            qualityBar.render(guiGraphics, i, j, f);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }
        @Override
        public void onClick(double d, double e) {
            button.onClick(d, e);
        }
        @Override
        public void setX(int x) {
            super.setX(x);
            item.setX(x);
            qualityBar.setX(x);
        }
        @Override
        public void setY(int y) {
            super.setY(y);
            item.setY(y);
            qualityBar.setY(y + ITEM_PIXELS_SIZE * pixelSize);
        }
        public static final Color BG_COLOR = new Color(0x1FCCCCCC, true);
        private TextureWidget item = null;
        private TextureWidget qualityBar = null;
        private Button button = null;
        private int pixelSize;
    }
    protected ViewLotteryPoolScreen(int poolID) {
        this(poolID, null);
    }
    protected ViewLotteryPoolScreen(int poolID, Screen parent) {
        super(Component.empty());
        this.POOL_ID = poolID;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        while ((VIEW_POOL_SCREEN_COL_NUM * (ROW_INTERVAL_PIXELS + ITEM_PIXELS_SIZE) - ROW_INTERVAL_PIXELS) * (pixelSize + 1) < width)
            ++pixelSize;
        int totalPixels = (VIEW_POOL_SCREEN_COL_NUM * (ROW_INTERVAL_PIXELS + ITEM_PIXELS_SIZE) - ROW_INTERVAL_PIXELS) * pixelSize;
        LotteryManager.LotteryPool lotteryPool = LotteryManager.getInstance().getLotteryPool(POOL_ID);
        List<List<ItemCard>> itemCardsGroup = new ArrayList<>();
        // 添加物品展示卡
        int curX = 0, curY = COL_INTERVAL_PIXELS * pixelSize;
        for (int i = 0;
             i < lotteryPool.getQualityListGroupConfigs().size();
             ++i,
             curY += (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE + COL_INTERVAL_PIXELS) * pixelSize) {
            List<String> itemNames = lotteryPool.getQualityListGroupConfigs().get(i).second;
            curX = (width - totalPixels) / 2;
            itemCardsGroup.add(new ArrayList<>());
            for (int j = itemNames.size() - 1, curCount = 0;
                 j >= 0;
                 --j, ++curCount,
                curX += (ITEM_PIXELS_SIZE + ROW_INTERVAL_PIXELS) * pixelSize)
            {
                if (curCount > 4) {
                    curX = (width - totalPixels) / 2;
                    curY += (ITEM_PIXELS_SIZE + QUALITY_BAR_PIXELS_SIZE + COL_INTERVAL_PIXELS) * pixelSize;
                }
                String itemName = itemNames.get(j);
                
                ItemCard itemCard = new ItemCard(
                        curX, curY,
                        pixelSize,
                        i,
                        itemNames.get(j).equals("coin") ?
                                StarRailExpressID.watheId("textures/font/coin.png") :
                                ResourceLocation.fromNamespaceAndPath("starrailexpress",
                                        "textures/item/" +
                                                itemNames.get(j)
                                                + ".png"),
                        button -> {
                            // TODO : itemNames.get(j)-使用该物品名获取具体物品然后进行展示
                            ItemStack itemStack = null;
                            // 设置itemStack
                            if (itemName.startsWith("knife/")) {
                                itemStack = TMMItems.KNIFE.getDefaultInstance();
                            }
                            else if (itemName.startsWith("gun/")) {
                                itemStack = TMMItems.REVOLVER.getDefaultInstance();
                            }
                            else if (itemName.startsWith("bat/")) {
                                itemStack = TMMItems.BAT.getDefaultInstance();
                            }
                            else if (itemName.startsWith("grenade/")) {
                                itemStack = TMMItems.GRENADE.getDefaultInstance();
                            }
                            Minecraft minecraft = Minecraft.getInstance();
                            if (itemStack != null) {
                                String trueName = itemName.substring(itemName.indexOf('/') + 1);
                                itemStack.set(SREDataComponentTypes.SKIN, trueName);
                                minecraft.setScreen(new DisplayItemScreen(itemStack,this));
                            }
                        });
                addRenderableWidget(itemCard);
                itemCardsGroup.getLast().add(itemCard);
            }
        }
        // 设置物品卡片组
        itemCards = new ItemCardGroup();
        itemCards.setItemCards(itemCardsGroup);
        itemCards.setHeight(curY);
        viewPoolBg = new TextureWidget(
                0,0,
                width, height,
                VIEW_POOL_BG_WIDTH, VIEW_POOL_BG_HEIGHT,
                VIEW_POOL_BG_RESOURCE_LOCATION
        );
        // 背景最后添加防止覆盖按钮优先级
        addRenderableWidget(viewPoolBg);
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        viewPoolBg.render(guiGraphics, mouseX, mouseY, delta);
        itemCards.render(guiGraphics, mouseX, mouseY, delta);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmound, double verticalAmound) {
        // TODO : 更加灵动的滚动效果
        itemCards.addY(verticalAmound * 10);
        return true;
    }
    public static final ResourceLocation VIEW_POOL_BG_RESOURCE_LOCATION = ResourceLocation.fromNamespaceAndPath(
            "noellesroles","textures/gui/loot/view_pool_bg.png"
    );
    @Override
    public void onClose() {
        if (parent != null && minecraft != null)
            minecraft.setScreen(parent);
        else
            super.onClose();
    }
    public static final int VIEW_POOL_BG_WIDTH = 480;
    public static final int VIEW_POOL_BG_HEIGHT = 270;
    private static final int VIEW_POOL_SCREEN_COL_NUM = 5;
    private static final int ROW_INTERVAL_PIXELS = 5;
    private static final int COL_INTERVAL_PIXELS = 2;
    private static final int QUALITY_BAR_PIXELS_SIZE = 1;
    private static final int ITEM_PIXELS_SIZE = 16;
    private final Screen parent;
    private final int POOL_ID;
    private ItemCardGroup itemCards;
    private TextureWidget viewPoolBg;
}
