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
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 皮肤选择列表（复古列车风格）。
 * <p>
 * 遵循 docs/ui_style.md 配色：深棕背景 + 金色/米色文字 + 棕褐色描边。
 */
public class SkinSelectionList extends ObjectSelectionList<SkinSelectionList.SkinEntry> {
    public static final int ENTRY_HEIGHT = 54;
    public static final int ENTRY_PADDING = 4;
    public static final int SCROLLBAR_WIDTH = 7;

    public final SkinManagementScreen parentScreen;
    private final String itemTypeName;
    private final ItemStack itemType;

    private final SREPlayerSkinsComponent skinsComponent;
    private final List<String> availableSkins = new ArrayList<>();
    private final Consumer<String> onSkinSelected;

    // 复古列车主题色
    private static final int BACKGROUND_COLOR = 0x801A1008;
    private static final int BORDER_COLOR = 0x558B6914;
    private static final int ENTRY_BG = 0x991A1008;
    private static final int ENTRY_BG_HOVER = 0x992A1A08;
    private static final int ENTRY_BG_SELECTED = 0xB0202010;
    private static final int ENTRY_BORDER = 0x408B6914;
    private static final int ENTRY_BORDER_HOVER = 0xFFD4AF37;
    private static final int TEXT_COLOR = 0xFFC8B898;
    private static final int TEXT_SELECTED = 0xFFFFF4DC;
    private static final int TEXT_SECONDARY_COLOR = 0xFF9E8B6E;
    private static final int TEXT_LOCKED = 0xFF5A5340;

    private boolean draggingScrollbar = false;
    private final String searchFilter;

    /** Suffix appended when a string is truncated. */
    private static final String ELLIPSIS = "...";

    public SkinSelectionList(SkinManagementScreen parentScreen, Minecraft mc,
            int x, int width, int height, int y, ItemStack itemType,
            SREPlayerSkinsComponent skinsComponent, Consumer<String> onSkinSelected,
            String searchFilter) {
        super(mc, width, height, y, ENTRY_HEIGHT);
        this.setX(x);

        this.parentScreen = parentScreen;
        this.itemType = itemType;
        this.itemTypeName = getItemTypeName(itemType);
        this.skinsComponent = skinsComponent;
        this.onSkinSelected = onSkinSelected;
        this.searchFilter = searchFilter != null ? searchFilter.toLowerCase() : "";

        collectAvailableSkins();

        for (String skinName : availableSkins) {
            this.addEntry(new SkinEntry(skinName));
        }
        children().sort((o1, o2) -> {
            if (o1.isUnlocked != o2.isUnlocked) {
                return o1.isUnlocked ? -1 : 1;
            }
            var qColors = ItemSkinManager.QualityColor.values();
            for (ItemSkinManager.QualityColor qColor : qColors) {
                if (o1.skinColor == qColor.getColor()) {
                    return o2.skinColor == qColor.getColor() ? 0 : -1;
                } else if (o2.skinColor == qColor.getColor()) {
                    return 1;
                }
            }
            return 0;
        });
        this.children().addFirst(new SkinEntry("default"));

        // 搜索过滤
        if (!this.searchFilter.isEmpty()) {
            this.children().removeIf(e -> {
                if ("default".equals(e.skinName)) return false;
                return !e.skinName.toLowerCase().contains(this.searchFilter);
            });
        }
    }

    private String getItemTypeName(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void collectAvailableSkins() {
        availableSkins.clear();
        var unlockedSkins = skinsComponent.getUnlockedSkinsForItemType(itemTypeName);
        for (var entry : unlockedSkins.entrySet()) {
            availableSkins.add(entry.getKey());
        }
        if (itemType.getItem() instanceof SkinableItem it) {
            var allSkins = ItemSkinManager.getSkins(it.getItemSkinType());
            if (allSkins != null) {
                for (String skinName : allSkins.keySet()) {
                    if (!availableSkins.contains(skinName)) {
                        availableSkins.add(skinName);
                    }
                }
            }
        }
    }

    // ─── Ellipsis helper ─────────────────────────────────────────────────────────

    private static Component ellipsis(Component text, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth)
            return text;

        String raw = text.getString();
        String suffix = ELLIPSIS;
        int suffixW = font.width(suffix);
        int budget = maxWidth - suffixW;
        if (budget <= 0)
            return Component.literal(suffix);

        int lo = 0, hi = raw.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(raw.substring(0, mid)) <= budget)
                lo = mid;
            else
                hi = mid - 1;
        }
        return Component.literal(raw.substring(0, lo) + suffix);
    }

    // ─── ObjectSelectionList overrides ───────────────────────────────────────────

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - SCROLLBAR_WIDTH - 2;
    }

    @Override
    public int getRowWidth() {
        return this.width - SCROLLBAR_WIDTH - 10;
    }

    @Override
    protected void renderHeader(GuiGraphics guiGraphics, int i, int j) {
    }

    @Override
    protected void renderListBackground(@NotNull GuiGraphics g) {
        int x0 = this.getX(), y0 = this.getY();
        int x1 = x0 + this.width, y1 = y0 + this.height;
        g.fillGradient(x0, y0, x1, y1, BACKGROUND_COLOR, BACKGROUND_COLOR);
        g.fill(x0, y0, x1, y0 + 1, BORDER_COLOR);
        g.fill(x0, y1 - 1, x1, y1, BORDER_COLOR);
        g.fill(x0, y0, x0 + 1, y1, BORDER_COLOR);
        g.fill(x1 - 1, y0, x1, y1, BORDER_COLOR);
    }

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

    public void refresh() {
        this.clearEntries();
        collectAvailableSkins();
        for (String skinName : availableSkins) {
            this.addEntry(new SkinEntry(skinName));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SkinEntry
    // ═══════════════════════════════════════════════════════════════════════════════

    public class SkinEntry extends ObjectSelectionList.Entry<SkinEntry> {
        public final String skinName;
        public boolean hovered = false;
        public float hoverAnimation = 0f;
        public final int skinColor;
        public final boolean isUnlocked;

        private String currentSkin;
        public boolean isCurrent = false;

        public SkinEntry(String skinName) {
            this.skinName = skinName;
            int sskinColor = java.awt.Color.WHITE.getRGB();
            if (itemType.getItem() instanceof SkinableItem it) {
                var skin = ItemSkinManager.Skin.fromString(it.getItemSkinType(), skinName);
                if (skin != null)
                    sskinColor = skin.getColor();
            }
            this.skinColor = sskinColor;
            this.isUnlocked = skinName.equals("default")
                    || skinsComponent.isSkinUnlockedForItemType(itemTypeName, skinName);
            updateCurrentSkin();
        }

        private void updateCurrentSkin() {
            this.currentSkin = skinsComponent.getEquippedSkin(itemTypeName);
            this.isCurrent = skinName.equals(currentSkin);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x,
                int entryWidth, int entryHeight,
                int mouseX, int mouseY, boolean hovered, float partialTick) {
            this.hovered = hovered;
            hoverAnimation = Mth.lerp(0.2f, hoverAnimation, hovered ? 1f : 0f);
            updateCurrentSkin();

            renderEntryBackground(guiGraphics, x, y, entryWidth, entryHeight);
            renderSkinIcon(guiGraphics, x, y, entryHeight);
            renderSkinInfo(guiGraphics, x, y, entryWidth, entryHeight);
            renderEquipStatus(guiGraphics, x, y, entryWidth, entryHeight);
        }

        private void renderEntryBackground(GuiGraphics g, int x, int y, int width, int height) {
            // 复古主题背景色
            int backgroundColor = !isUnlocked ? 0x990A0804
                    : (isCurrent ? ENTRY_BG_SELECTED : ENTRY_BG);
            if (hoverAnimation > 0 && isUnlocked) {
                backgroundColor = blendColors(backgroundColor, ENTRY_BG_HOVER, hoverAnimation);
            }

            g.fill(x + 2, y + 2, x + width - 6, y + height - 2, backgroundColor);

            int borderColor = !isUnlocked ? 0x40302010
                    : (isCurrent ? 0xFFD4AF37 : ENTRY_BORDER);
            if (hoverAnimation > 0 && isUnlocked) {
                borderColor = blendColors(borderColor, ENTRY_BORDER_HOVER, hoverAnimation);
            }

            g.fill(x + 2, y + 2, x + width - 6, y + 3, borderColor);
            g.fill(x + 2, y + height - 3, x + width - 6, y + height - 2, borderColor);
            g.fill(x + 2, y + 2, x + 3, y + height - 2, borderColor);
            g.fill(x + width - 7, y + 2, x + width - 6, y + height - 2, borderColor);

            // 悬停微弱辉光
            if (hoverAnimation > 0 && isUnlocked) {
                int glowAlpha = (int) (hoverAnimation * 25) << 24;
                for (int i = 1; i <= 2; i++) {
                    g.fill(x - i + 2, y - i + 2, x + width + i - 6, y + height + i - 2,
                            glowAlpha | 0xD4AF37);
                }
            }
        }

        private void renderSkinIcon(GuiGraphics g, int x, int y, int height) {
            int iconSize = height - 16;
            int iconX = x + 8;
            int iconY = y + (height - iconSize) / 2;

            int iconBgColor = skinColor;
            if (isCurrent)
                iconBgColor = blendColors(iconBgColor, 0xFFD4AF37, 0.25f);

            drawRoundedRect(g, iconX, iconY, iconSize, iconSize, 0, iconBgColor);

            int textX = iconX + iconSize / 2 - 8;
            int textY = iconY + iconSize / 2 - 8;
            ItemStack skinedItem = itemType.copy();
            skinedItem.set(SREDataComponentTypes.SKIN, this.skinName);
            g.renderFakeItem(skinedItem, textX, textY);

            int borderColor = isCurrent ? 0xFFD4AF37 : 0x80FFFFFF;
            drawRoundedRectBorder(g, iconX, iconY, iconSize, iconSize, 0, borderColor);
        }

        private void renderSkinInfo(GuiGraphics g, int x, int y, int width, int height) {
            final int ICON_COLUMN_END = 70;
            final int BUTTON_WIDTH = 60;
            final int BUTTON_GAP = 10;
            final int LOCK_ICON_ZONE = 20;

            int infoX = x + ICON_COLUMN_END;
            int maxTextW = width - ICON_COLUMN_END - BUTTON_WIDTH - BUTTON_GAP - LOCK_ICON_ZONE - 6;
            if (maxTextW < 20) maxTextW = 20;
            int infoY = y + 10;

            String skinLowerName = skinName.toLowerCase();
            var rl = ResourceLocation.tryParse(itemTypeName);
            String itemTypeKey = (rl != null) ? rl.getPath() : itemTypeName;

            Component displayName = Component.translatableWithFallback(
                    "screen.sre.skins." + itemTypeKey + "." + skinLowerName + ".name",
                    formatSkinName(skinLowerName));

            Component description = !isUnlocked
                    ? Component.translatable("screen.sre.skins.locked")
                    : Component.translatableWithFallback(
                            "screen.sre.skins." + itemTypeKey + "." + skinLowerName + ".desc",
                            formatSkinName(skinLowerName));

            Component idText = Component.literal("ID: " + skinName);

            Component nameClipped = ellipsis(displayName, maxTextW);
            Component descClipped = ellipsis(description, maxTextW);
            Component idClipped = ellipsis(idText, maxTextW);

            int nameColor = !isUnlocked ? TEXT_LOCKED
                    : (isCurrent ? TEXT_SELECTED : TEXT_COLOR);
            g.drawString(Minecraft.getInstance().font, nameClipped, infoX, infoY, nameColor, false);

            int descColor = !isUnlocked ? TEXT_LOCKED : TEXT_SECONDARY_COLOR;
            g.drawString(Minecraft.getInstance().font, descClipped, infoX, infoY + 12, descColor, false);

            g.drawString(Minecraft.getInstance().font, idClipped, infoX, infoY + 24, 0xFF6A6048, false);
        }

        private void renderEquipStatus(GuiGraphics g, int x, int y, int width, int height) {
            int buttonWidth = 60;
            int buttonHeight = 20;
            int buttonX = x + width - buttonWidth - 10;
            int buttonY = y + (height - buttonHeight) / 2;

            if (!isUnlocked) {
                int buttonColor = 0x802A1A08;
                drawRoundedRect(g, buttonX, buttonY, buttonWidth, buttonHeight, 0, buttonColor);
                drawRoundedRectBorder(g, buttonX, buttonY, buttonWidth, buttonHeight, 0, 0xFF5A4530);

                Component buttonText = Component.translatable("screen.sre.skins.locked_short");
                int textColor = 0xFF8B7B5A;
                int textX = buttonX + buttonWidth / 2 - Minecraft.getInstance().font.width(buttonText) / 2;
                int textY = buttonY + (buttonHeight - 8) / 2;
                g.drawString(Minecraft.getInstance().font, buttonText, textX, textY, textColor, false);

                int lockSize = 10;
                int lockX = buttonX - lockSize - 5;
                int lockY = buttonY + (buttonHeight - lockSize) / 2;
                g.fill(lockX, lockY, lockX + lockSize, lockY + lockSize, 0xFF5A4530);
                var lockIcon = Component.literal("\uD83D\uDD12").withStyle(ChatFormatting.GRAY);
                var font = Minecraft.getInstance().font;
                g.drawCenteredString(font, lockIcon,
                        lockX + lockSize / 2, lockY + lockSize / 2 - font.lineHeight / 2, 0xAAAAAA);
                return;
            }

            int buttonColor = isCurrent ? 0x802A1A08 : 0x801A1008;
            if (hovered && !isCurrent) buttonColor = 0x80302010;
            drawRoundedRect(g, buttonX, buttonY, buttonWidth, buttonHeight, 0, buttonColor);

            int borderColor = isCurrent ? 0xFFD4AF37
                    : (hovered && !isCurrent ? 0xFFC9A84C : 0xFF5A4530);
            drawRoundedRectBorder(g, buttonX, buttonY, buttonWidth, buttonHeight, 0, borderColor);

            Component buttonText = isCurrent
                    ? Component.translatable("screen.sre.skins.equipped")
                    : Component.translatable("screen.sre.skins.equip");
            int textColor = isCurrent ? 0xFFD4AF37 : 0xFFFFF4DC;
            int textX = buttonX + buttonWidth / 2 - Minecraft.getInstance().font.width(buttonText) / 2;
            int textY = buttonY + (buttonHeight - 8) / 2;
            g.drawString(Minecraft.getInstance().font, buttonText, textX, textY, textColor, false);

            if (isCurrent) {
                int checkSize = 10;
                int checkX = buttonX - checkSize - 5;
                int checkY = buttonY + (buttonHeight - checkSize) / 2;
                g.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, 0xFFD4AF37);
                var bingo = Component.literal("\u2714").withStyle(ChatFormatting.WHITE);
                var font = Minecraft.getInstance().font;
                g.drawCenteredString(font, bingo,
                        checkX + checkSize / 2, checkY + checkSize / 2 - font.lineHeight / 2, 0xFFFFF4DC);
            }
        }

        private String formatSkinName(String skinName) {
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
            if (button == 0) {
                if (!isUnlocked) {
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f));
                    return true;
                }
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                if (onSkinSelected != null)
                    onSkinSelected.accept(skinName);
                return true;
            }
            return false;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.translatable("screen.sre.skins.narration",
                    skinName.equals("default")
                            ? Component.translatable("screen.sre.skins.default_skin")
                            : Component.literal(skinName));
        }
    }

    // ─── Drawing utilities ──────────────────────────────────────────────────────

    private static void drawRoundedRect(GuiGraphics g,
            int x, int y, int width, int height, int radius, int color) {
        g.fill(x + radius, y, x + width - radius, y + height, color);
        g.fill(x, y + radius, x + width, y + height - radius, color);
        for (int i = 0; i < radius; i++) {
            int alpha = (int) ((1.0 - (double) i / radius) * ((color >> 24) & 0xFF)) << 24;
            int cornerColor = alpha | (color & 0xFFFFFF);
            g.fill(x + i, y + radius - i, x + radius, y + radius - i, cornerColor);
            g.fill(x + width - radius + i, y + radius - i, x + width - i, y + radius - i, cornerColor);
            g.fill(x + i, y + height - radius + i, x + radius, y + height - radius + i, cornerColor);
            g.fill(x + width - radius + i, y + height - radius + i, x + width - i, y + height - radius + i,
                    cornerColor);
        }
    }

    private static void drawRoundedRectBorder(GuiGraphics g,
            int x, int y, int width, int height, int radius, int color) {
        g.fill(x + radius, y, x + width - radius, y + 1, color);
        g.fill(x + radius, y + height - 1, x + width - radius, y + height, color);
        g.fill(x, y + radius, x + 1, y + height - radius, color);
        g.fill(x + width - 1, y + radius, x + width, y + height - radius, color);
    }

    private static int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF,
                g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF,
                g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * ratio) << 24)
                | ((int) (r1 + (r2 - r1) * ratio) << 16)
                | ((int) (g1 + (g2 - g1) * ratio) << 8)
                | (int) (b1 + (b2 - b1) * ratio);
    }
}
