package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SkinSelectionList extends ObjectSelectionList<SkinSelectionList.SkinEntry> {
    public static final int ENTRY_HEIGHT = 54; // 每个条目的固定高度
    public static final int ENTRY_PADDING = 4;
    public static final int SCROLLBAR_WIDTH = 8;

    public final SkinManagementScreen parentScreen;
    private final String itemTypeName;
    private final ItemStack itemType;

    private final SREPlayerSkinsComponent skinsComponent;
    private final List<String> availableSkins = new ArrayList<>();
    private final Consumer<String> onSkinSelected;

    // 颜色定义
    private static final int BACKGROUND_COLOR = 0xFF1E1E2E;
    private static final int BORDER_COLOR = 0xFF444455;
    private static final int HOVER_COLOR = 0xFF2A2A3A;
    private static final int SELECTED_COLOR = 0xFF3A553A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY_COLOR = 0xFFAAAAAA;

    // 角色颜色映射
    private static final int[] ROLE_COLORS = {
            0xFFE74C3C, // 红色
            0xFF3498DB, // 蓝色
            0xFF2ECC71, // 绿色
            0xFFF39C12, // 橙色
            0xFF9B59B6, // 紫色
            0xFF1ABC9C, // 青色
            0xFFE67E22, // 深橙色
            0xFF95A5A6, // 灰色
    };

    public SkinSelectionList(SkinManagementScreen parentScreen, Minecraft mc,
            int x, int width, int height, int y, ItemStack itemType,
            SREPlayerSkinsComponent skinsComponent, Consumer<String> onSkinSelected) {
        super(mc, width, height, y, ENTRY_HEIGHT);
        // 设置列表的 X 位置
        this.setX(x);

        this.parentScreen = parentScreen;
        this.itemType = itemType;
        this.itemTypeName = getItemTypeName(itemType);
        this.skinsComponent = skinsComponent;
        this.onSkinSelected = onSkinSelected;

        // 设置正确的滚动条位置

        // 收集可用的皮肤
        collectAvailableSkins();

        // 添加条目
        for (String skinName : availableSkins) {
            this.addEntry(new SkinEntry(skinName));
        }
    }

    private String getItemTypeName(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void collectAvailableSkins() {
        availableSkins.clear();

        // 添加默认皮肤
        availableSkins.add("default");

        // 添加已解锁的皮肤
        var unlockedSkins = skinsComponent.getUnlockedSkinsForItemType(itemTypeName);
        for (var entry : unlockedSkins.entrySet()) {
            if (entry.getValue()) {
                availableSkins.add(entry.getKey());
            }
        }
    }

    @Override
    protected int getScrollbarPosition() {
        // 滚动条在右侧，留出适当边距
        return this.getX() + this.width - SCROLLBAR_WIDTH - 2;
    }

    @Override
    public int getRowWidth() {
        // 条目宽度，留出滚动条空间
        return this.width - SCROLLBAR_WIDTH - 10;
    }

    @Override
    protected void renderHeader(GuiGraphics guiGraphics, int i, int j) {
        // 不渲染Header
    }

    @Override
    protected void renderListBackground(@NotNull GuiGraphics guiGraphics) {
        // 渲染列表背景
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;

        // 半透明黑色背景
        guiGraphics.fill(x0, y0, x1, y1, 0x80000000);

        // 边框
        guiGraphics.fill(x0, y0, x1, y0 + 1, BORDER_COLOR); // 上边框
        guiGraphics.fill(x0, y1 - 1, x1, y1, BORDER_COLOR); // 下边框
        guiGraphics.fill(x0, y0, x0 + 1, y1, BORDER_COLOR); // 左边框
        guiGraphics.fill(x1 - 1, y0, x1, y1, BORDER_COLOR); // 右边框
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 先渲染背景
        renderListBackground(guiGraphics);

        // 然后渲染父类的widget（包括条目）
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void refresh() {
        // 清空现有条目
        this.clearEntries();

        // 重新收集皮肤
        collectAvailableSkins();

        // 重新添加条目
        for (String skinName : availableSkins) {
            this.addEntry(new SkinEntry(skinName));
        }
    }

    public class SkinEntry extends ObjectSelectionList.Entry<SkinEntry> {
        private final String skinName;
        private boolean hovered = false;
        private float hoverAnimation = 0f;
        private final int skinColor;

        private String currentSkin;
        private boolean isCurrent = false;

        public SkinEntry(String skinName) {
            this.skinName = skinName;
            // 根据皮肤名称生成稳定的颜色
            this.skinColor = ROLE_COLORS[Math.abs(skinName.hashCode()) % ROLE_COLORS.length];

            // 获取当前装备的皮肤
            updateCurrentSkin();
        }

        private void updateCurrentSkin() {
            this.currentSkin = skinsComponent.getEquippedSkinForItemType(itemTypeName);
            this.isCurrent = skinName.equals(currentSkin);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x,
                int entryWidth, int entryHeight, int mouseX, int mouseY,
                boolean hovered, float partialTick) {
            this.hovered = hovered;

            // 更新悬停动画
            float targetHover = hovered ? 1f : 0f;
            hoverAnimation = Mth.lerp(0.2f, hoverAnimation, targetHover);

            // 更新当前皮肤状态
            updateCurrentSkin();

            // 渲染条目背景
            renderEntryBackground(guiGraphics, x, y, entryWidth, entryHeight);

            // 渲染皮肤图标
            renderSkinIcon(guiGraphics, x, y, entryHeight);

            // 渲染皮肤信息
            renderSkinInfo(guiGraphics, x, y, entryWidth, entryHeight);

            // 渲染装备状态
            renderEquipStatus(guiGraphics, x, y, entryWidth, entryHeight);
        }

        private void renderEntryBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            // 背景颜色
            int backgroundColor = isCurrent ? SELECTED_COLOR : BACKGROUND_COLOR;

            // 悬停时混合颜色
            if (hoverAnimation > 0) {
                backgroundColor = blendColors(backgroundColor, HOVER_COLOR, hoverAnimation);
            }

            // 渲染背景
            guiGraphics.fill(x + 2, y + 2, x + width - 6, y + height - 2, backgroundColor);

            // 渲染边框
            int borderColor = isCurrent ? 0xFF55AA55 : BORDER_COLOR;
            if (hoverAnimation > 0) {
                borderColor = blendColors(borderColor, 0xFF8888FF, hoverAnimation);
            }

            guiGraphics.fill(x, y, x + width - 4, y + 1, borderColor); // 上边框
            guiGraphics.fill(x, y + height - 1, x + width - 4, y + height, borderColor); // 下边框
            guiGraphics.fill(x, y, x + 1, y + height, borderColor); // 左边框
            guiGraphics.fill(x + width - 5, y, x + width - 4, y + height, borderColor); // 右边框

            // 悬停时的发光效果
            if (hoverAnimation > 0) {
                int glowAlpha = (int) (hoverAnimation * 30) << 24;
                for (int i = 1; i <= 2; i++) {
                    guiGraphics.fill(x - i, y - i, x + width + i, y + height + i, glowAlpha | 0xFFFFFF);
                }
            }
        }

        private void renderSkinIcon(GuiGraphics guiGraphics, int x, int y, int height) {
            int iconSize = height - 16;
            int iconX = x + 8;
            int iconY = y + (height - iconSize) / 2;

            // 图标背景（圆形）
            int iconBgColor = skinColor;
            if (isCurrent) {
                iconBgColor = blendColors(iconBgColor, 0xFF55FF55, 0.3f);
            }

            // 绘制圆形背景
            drawRoundedRect(guiGraphics, iconX, iconY, iconSize, iconSize, 0, iconBgColor);

            int textX = iconX + iconSize / 2 - 8;
            int textY = iconY + iconSize / 2 - 8;
            // itemType.
            ItemStack skinedItem = itemType.copy();
            CompoundTag tag = new CompoundTag();
            tag.putString("train_custom_skin", skinName);
            CustomData customData = CustomData.of(tag);
            skinedItem.set(DataComponents.CUSTOM_DATA, customData);
            guiGraphics.renderFakeItem(skinedItem, textX, textY);

            // 图标边框
            int borderColor = isCurrent ? 0xFF00FF00 : 0x80FFFFFF;
            drawRoundedRectBorder(guiGraphics, iconX, iconY, iconSize, iconSize, 0, borderColor);
        }

        private void renderSkinInfo(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            int infoX = x + 70; // 图标右边
            int infoY = y + 10;
            String skinLowerName = skinName.toLowerCase();
            // 皮肤名称
            var rl = ResourceLocation.tryParse(itemTypeName);
            String itemTypeKey = itemTypeName;
            if (rl != null) {
                itemTypeKey = rl.getPath();
            }
            Component displayName = Component.translatableWithFallback(
                    "screen.sre.skins." + itemTypeKey + "." + skinLowerName + ".name",
                    formatSkinName(skinLowerName));

            int nameColor = isCurrent ? 0xFF55FF55 : TEXT_COLOR;
            guiGraphics.drawString(Minecraft.getInstance().font, displayName, infoX, infoY, nameColor, false);

            // 皮肤描述
            Component description = Component.translatableWithFallback(
                    "screen.sre.skins." + itemTypeKey + "." + skinLowerName + ".desc",
                    formatSkinName(skinLowerName));

            int descColor = TEXT_SECONDARY_COLOR;
            int descY = infoY + 12;
            guiGraphics.drawString(Minecraft.getInstance().font, description, infoX, descY, descColor, false);

            // 皮肤ID（小字）
            Component idText = Component.literal("ID: " + skinName);
            int idColor = 0xFF888888;
            int idY = descY + 12;
            guiGraphics.drawString(Minecraft.getInstance().font, idText, infoX, idY, idColor, false);
        }

        private void renderEquipStatus(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            int buttonWidth = 60;
            int buttonHeight = 20;
            int buttonX = x + width - buttonWidth - 10;
            int buttonY = y + (height - buttonHeight) / 2;

            // 按钮背景
            int buttonColor = isCurrent ? 0x8055AA55 : 0x80404040;
            if (hovered && !isCurrent) {
                buttonColor = 0x806688CC;
            }

            // 绘制圆角按钮
            drawRoundedRect(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight, 0, buttonColor);

            // 按钮边框
            int borderColor = isCurrent ? 0xFF55FF55 : (hovered && !isCurrent ? 0xFF6688CC : 0xFF555555);
            drawRoundedRectBorder(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight, 0, borderColor);

            // 按钮文字
            Component buttonText = isCurrent ? Component.translatable("screen.sre.skins.equipped")
                    : Component.translatable("screen.sre.skins.equip");

            int textColor = isCurrent ? 0xFF00FF00 : 0xFFFFFFFF;
            int textX = buttonX + buttonWidth / 2 - Minecraft.getInstance().font.width(buttonText) / 2;
            int textY = buttonY + (buttonHeight - 8) / 2;
            guiGraphics.drawString(Minecraft.getInstance().font, buttonText, textX, textY, textColor, false);

            // 如果是当前皮肤，添加选中标记
            if (isCurrent) {
                int checkSize = 10;
                int checkX = buttonX - checkSize - 5;
                int checkY = buttonY + (buttonHeight - checkSize) / 2;
                guiGraphics.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, 0xFF00FF00);
                var bingo = Component.literal("✔").withStyle(ChatFormatting.WHITE);
                var font = Minecraft.getInstance().font;
                guiGraphics.drawCenteredString(font, bingo,
                        checkX + checkSize / 2, checkY + checkSize / 2 - font.lineHeight / 2,
                        0xFFFFFF);
            }
        }

        private String formatSkinName(String skinName) {
            // 将下划线或连字符分隔的皮肤名转换为友好格式
            String[] parts = skinName.split("[_\\-]");
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    result.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase())
                            .append(" ");
                }
            }
            return result.toString().trim();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) { // 左键点击
                // 播放点击音效
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));

                // 调用回调函数
                if (onSkinSelected != null) {
                    onSkinSelected.accept(skinName);
                }

                return true;
            }
            return false;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.translatable("screen.sre.skins.narration",
                    skinName.equals("default") ? Component.translatable("screen.sre.skins.default_skin")
                            : Component.literal(skinName));
        }
    }

    // 工具方法
    private static void drawRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius,
            int color) {
        // 简化的圆角矩形实现
        guiGraphics.fill(x + radius, y, x + width - radius, y + height, color);
        guiGraphics.fill(x, y + radius, x + width, y + height - radius, color);

        // 四个角
        for (int i = 0; i < radius; i++) {
            int alpha = (int) ((1.0 - (double) i / radius) * ((color >> 24) & 0xFF)) << 24;
            int cornerColor = alpha | (color & 0xFFFFFF);

            guiGraphics.fill(x + i, y + radius - i, x + radius, y + radius - i, cornerColor);
            guiGraphics.fill(x + width - radius + i, y + radius - i, x + width - i, y + radius - i, cornerColor);
            guiGraphics.fill(x + i, y + height - radius + i, x + radius, y + height - radius + i, cornerColor);
            guiGraphics.fill(x + width - radius + i, y + height - radius + i, x + width - i, y + height - radius + i,
                    cornerColor);
        }
    }

    private static void drawRoundedRectBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius,
            int color) {
        // 绘制边框（简化版）
        guiGraphics.fill(x + radius, y, x + width - radius, y + 1, color); // 上边
        guiGraphics.fill(x + radius, y + height - 1, x + width - radius, y + height, color); // 下边
        guiGraphics.fill(x, y + radius, x + 1, y + height - radius, color); // 左边
        guiGraphics.fill(x + width - 1, y + radius, x + width, y + height - radius, color); // 右边
    }

    private static int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}