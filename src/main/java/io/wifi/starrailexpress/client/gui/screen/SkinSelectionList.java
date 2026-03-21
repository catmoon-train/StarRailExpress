package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.item.SkinableItem;
import io.wifi.starrailexpress.util.SkinManager;
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

public class SkinSelectionList extends ObjectSelectionList<SkinSelectionList.SkinEntry> {
    public static final int ENTRY_HEIGHT = 54;
    public static final int ENTRY_PADDING = 4;
    public static final int SCROLLBAR_WIDTH = 8;

    public final SkinManagementScreen parentScreen;
    private final String itemTypeName;
    private final ItemStack itemType;

    private final SREPlayerSkinsComponent skinsComponent;
    private final List<String> availableSkins = new ArrayList<>();
    private final Consumer<String> onSkinSelected;

    private static final int BACKGROUND_COLOR = 0xFF1E1E2E;
    private static final int BORDER_COLOR = 0xFF444455;
    private static final int HOVER_COLOR = 0xFF2A2A3A;
    private static final int SELECTED_COLOR = 0xFF3A553A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY_COLOR = 0xFFAAAAAA;

    /** Suffix appended when a string is truncated. */
    private static final String ELLIPSIS = "...";

    public SkinSelectionList(SkinManagementScreen parentScreen, Minecraft mc,
            int x, int width, int height, int y, ItemStack itemType,
            SREPlayerSkinsComponent skinsComponent, Consumer<String> onSkinSelected) {
        super(mc, width, height, y, ENTRY_HEIGHT);
        this.setX(x);

        this.parentScreen = parentScreen;
        this.itemType = itemType;
        this.itemTypeName = getItemTypeName(itemType);
        this.skinsComponent = skinsComponent;
        this.onSkinSelected = onSkinSelected;

        collectAvailableSkins();

        for (String skinName : availableSkins) {
            this.addEntry(new SkinEntry(skinName));
        }
        children().sort((o1, o2) -> {
            if (o1.isUnlocked != o2.isUnlocked) {
                return o1.isUnlocked ? -1 : 1;
            }
            var qColors = SkinManager.QualityColor.values();
            for (SkinManager.QualityColor qColor : qColors) {
                if (o1.skinColor == qColor.getColor()) {
                    return o2.skinColor == qColor.getColor() ? 0 : -1;
                } else if (o2.skinColor == qColor.getColor()) {
                    return 1;
                }
            }
            return 0;
        });
        this.children().addFirst(new SkinEntry("default"));
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
            var allSkins = SkinManager.getSkins(it.getItemSkinType());
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

    /**
     * Truncates {@code text} so that it fits within {@code maxWidth} pixels,
     * appending "..." when a truncation occurs.
     * Works on the plain string of the component, then wraps back in a literal.
     */
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

        // Binary-search the longest prefix that still fits
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
    protected void renderListBackground(@NotNull GuiGraphics guiGraphics) {
        int x0 = this.getX(), y0 = this.getY();
        int x1 = x0 + this.width, y1 = y0 + this.height;

        guiGraphics.fill(x0, y0, x1, y1, 0x80000000);
        guiGraphics.fill(x0, y0, x1, y0 + 1, BORDER_COLOR);
        guiGraphics.fill(x0, y1 - 1, x1, y1, BORDER_COLOR);
        guiGraphics.fill(x0, y0, x0 + 1, y1, BORDER_COLOR);
        guiGraphics.fill(x1 - 1, y0, x1, y1, BORDER_COLOR);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderListBackground(guiGraphics);
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void refresh() {
        this.clearEntries();
        collectAvailableSkins();
        for (String skinName : availableSkins) {
            this.addEntry(new SkinEntry(skinName));
        }
    }

    // ─── SkinEntry
    // ────────────────────────────────────────────────────────────────

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
                var skin = SkinManager.Skin.fromString(it.getItemSkinType(), skinName);
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
            // renderDescriptionTooltip(guiGraphics, x, y, entryWidth, entryHeight);
        }

        public void renderDescriptionTooltip(GuiGraphics guiGraphics, int x, int y, int entryWidth, int entryHeight) {
            if (!hovered)
                return;
            String skinLowerName = skinName.toLowerCase();
            var rl = ResourceLocation.tryParse(itemTypeName);
            String itemTypeKey = (rl != null) ? rl.getPath() : itemTypeName;

            ArrayList<FormattedCharSequence> tooltipList = new ArrayList<>();
            MutableComponent description = !isUnlocked
                    ? Component.translatable("screen.sre.skins.locked")
                    : Component.translatableWithFallback(
                            "screen.sre.skins." + itemTypeKey + "." + skinLowerName + ".desc",
                            formatSkinName(skinName.toLowerCase()));
            tooltipList.addAll(minecraft.font.split(Component
                    .translatable("screen.sre.skins.skin.tooltip.line1",
                            Component.translatableWithFallback(
                                    "screen.sre.skins." + itemTypeKey + "." + skinLowerName + ".name",
                                    formatSkinName(skinName.toLowerCase())).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD), entryWidth));
            tooltipList.addAll(
                    minecraft.font.split(Component.translatable("screen.sre.skins.skin.tooltip.line2", description.withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.GOLD), entryWidth));
            tooltipList.addAll(minecraft.font.split(Component
                    .translatable("screen.sre.skins.skin.tooltip.line3",
                            Component.literal(skinName).withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GOLD), entryWidth));
            guiGraphics.renderTooltip(minecraft.font, tooltipList,
                    x - 10, y + entryHeight + 16);
        }

        private void renderEntryBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            int backgroundColor = !isUnlocked ? 0xFF151520
                    : (isCurrent ? SELECTED_COLOR : BACKGROUND_COLOR);
            if (hoverAnimation > 0)
                backgroundColor = blendColors(backgroundColor, HOVER_COLOR, hoverAnimation);

            guiGraphics.fill(x + 2, y + 2, x + width - 6, y + height - 2, backgroundColor);

            int borderColor = !isUnlocked ? 0xFF333344
                    : (isCurrent ? 0xFF55AA55 : BORDER_COLOR);
            if (hoverAnimation > 0)
                borderColor = blendColors(borderColor, 0xFF8888FF, hoverAnimation);

            guiGraphics.fill(x, y, x + width - 4, y + 1, borderColor);
            guiGraphics.fill(x, y + height - 1, x + width - 4, y + height, borderColor);
            guiGraphics.fill(x, y, x + 1, y + height, borderColor);
            guiGraphics.fill(x + width - 5, y, x + width - 4, y + height, borderColor);

            if (hoverAnimation > 0 && isUnlocked) {
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

            int iconBgColor = skinColor;
            if (isCurrent)
                iconBgColor = blendColors(iconBgColor, 0xFF55FF55, 0.3f);

            drawRoundedRect(guiGraphics, iconX, iconY, iconSize, iconSize, 0, iconBgColor);

            int textX = iconX + iconSize / 2 - 8;
            int textY = iconY + iconSize / 2 - 8;
            ItemStack skinedItem = itemType.copy();
            skinedItem.set(SREDataComponentTypes.SKIN, this.skinName);
            guiGraphics.renderFakeItem(skinedItem, textX, textY);

            int borderColor = isCurrent ? 0xFF00FF00 : 0x80FFFFFF;
            drawRoundedRectBorder(guiGraphics, iconX, iconY, iconSize, iconSize, 0, borderColor);
        }

        private void renderSkinInfo(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            // ── Layout constants ──────────────────────────────────────────────
            // Text starts after the icon column (≈70 px) and must stop before
            // the equip-button column (60 px wide + 10 px gap + some breathing
            // room). We derive maxTextWidth from these two anchors so it scales
            // with entryWidth automatically.
            final int ICON_COLUMN_END = 70; // x-offset where text begins
            final int BUTTON_WIDTH = 60;
            final int BUTTON_GAP = 10;
            // Extra breathing room between text and button (lock icon may sit here)
            final int LOCK_ICON_ZONE = 20;

            int infoX = x + ICON_COLUMN_END;
            int maxTextW = width - ICON_COLUMN_END - BUTTON_WIDTH - BUTTON_GAP - LOCK_ICON_ZONE - 6;
            // Guard against very narrow entries
            if (maxTextW < 20)
                maxTextW = 20;

            int infoY = y + 10;

            // ── Build raw components ──────────────────────────────────────────
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

            // ── Truncate with ellipsis ────────────────────────────────────────
            Component nameClipped = ellipsis(displayName, maxTextW);
            Component descClipped = ellipsis(description, maxTextW);
            Component idClipped = ellipsis(idText, maxTextW);

            // ── Render ───────────────────────────────────────────────────────
            int nameColor = !isUnlocked ? 0xFF888888 : (isCurrent ? 0xFF55FF55 : TEXT_COLOR);
            guiGraphics.drawString(Minecraft.getInstance().font, nameClipped, infoX, infoY, nameColor, false);

            int descColor = !isUnlocked ? 0xFF666666 : TEXT_SECONDARY_COLOR;
            guiGraphics.drawString(Minecraft.getInstance().font, descClipped, infoX, infoY + 12, descColor, false);

            guiGraphics.drawString(Minecraft.getInstance().font, idClipped, infoX, infoY + 24, 0xFF888888, false);
        }

        private void renderEquipStatus(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            int buttonWidth = 60;
            int buttonHeight = 20;
            int buttonX = x + width - buttonWidth - 10;
            int buttonY = y + (height - buttonHeight) / 2;

            if (!isUnlocked) {
                int buttonColor = 0x80333333;
                drawRoundedRect(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight, 0, buttonColor);
                drawRoundedRectBorder(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight, 0, 0xFF555555);

                Component buttonText = Component.translatable("screen.sre.skins.locked_short");
                int textColor = 0xFF888888;
                int textX = buttonX + buttonWidth / 2 - Minecraft.getInstance().font.width(buttonText) / 2;
                int textY = buttonY + (buttonHeight - 8) / 2;
                guiGraphics.drawString(Minecraft.getInstance().font, buttonText, textX, textY, textColor, false);

                int lockSize = 10;
                int lockX = buttonX - lockSize - 5;
                int lockY = buttonY + (buttonHeight - lockSize) / 2;
                guiGraphics.fill(lockX, lockY, lockX + lockSize, lockY + lockSize, 0xFF555555);
                var lockIcon = Component.literal("🔒").withStyle(ChatFormatting.GRAY);
                var font = Minecraft.getInstance().font;
                guiGraphics.drawCenteredString(font, lockIcon,
                        lockX + lockSize / 2, lockY + lockSize / 2 - font.lineHeight / 2, 0xAAAAAA);
                return;
            }

            int buttonColor = isCurrent ? 0x8055AA55 : 0x80404040;
            if (hovered && !isCurrent)
                buttonColor = 0x806688CC;
            drawRoundedRect(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight, 0, buttonColor);

            int borderColor = isCurrent ? 0xFF55FF55 : (hovered && !isCurrent ? 0xFF6688CC : 0xFF555555);
            drawRoundedRectBorder(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight, 0, borderColor);

            Component buttonText = isCurrent
                    ? Component.translatable("screen.sre.skins.equipped")
                    : Component.translatable("screen.sre.skins.equip");
            int textColor = isCurrent ? 0xFF00FF00 : 0xFFFFFFFF;
            int textX = buttonX + buttonWidth / 2 - Minecraft.getInstance().font.width(buttonText) / 2;
            int textY = buttonY + (buttonHeight - 8) / 2;
            guiGraphics.drawString(Minecraft.getInstance().font, buttonText, textX, textY, textColor, false);

            if (isCurrent) {
                int checkSize = 10;
                int checkX = buttonX - checkSize - 5;
                int checkY = buttonY + (buttonHeight - checkSize) / 2;
                guiGraphics.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, 0xFF00FF00);
                var bingo = Component.literal("✔").withStyle(ChatFormatting.WHITE);
                var font = Minecraft.getInstance().font;
                guiGraphics.drawCenteredString(font, bingo,
                        checkX + checkSize / 2, checkY + checkSize / 2 - font.lineHeight / 2, 0xFFFFFF);
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

    // ─── Drawing utilities
    // ────────────────────────────────────────────────────────

    private static void drawRoundedRect(GuiGraphics guiGraphics,
            int x, int y, int width, int height, int radius, int color) {
        guiGraphics.fill(x + radius, y, x + width - radius, y + height, color);
        guiGraphics.fill(x, y + radius, x + width, y + height - radius, color);
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

    private static void drawRoundedRectBorder(GuiGraphics guiGraphics,
            int x, int y, int width, int height, int radius, int color) {
        guiGraphics.fill(x + radius, y, x + width - radius, y + 1, color);
        guiGraphics.fill(x + radius, y + height - 1, x + width - radius, y + height, color);
        guiGraphics.fill(x, y + radius, x + 1, y + height - radius, color);
        guiGraphics.fill(x + width - 1, y + radius, x + width, y + height - radius, color);
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