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
import io.wifi.starrailexpress.client.gui.anim.GuiAnim;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 帽子皮肤网格面板（复古列车风格）。
 * <p>
 * 以卡片网格形式展示皮肤系统中类型为 {@code "hat"} 的所有皮肤。
 * 支持平滑滚动、悬停放大、选中高亮与未解锁置灰。
 * 配色遵循 docs/ui_style.md：深棕背景 + 金色描边 + 米色文字。
 */
@Environment(EnvType.CLIENT)
public class HatSkinGridPanel extends AbstractWidget {
    private static final int CARD_WIDTH = 64;
    private static final int CARD_HEIGHT = 74;
    private static final int CARD_GAP = 6;
    private static final int PADDING = 6;

    /** 帽子物品的命名空间（附属模组将帽子模组物品注册为皮肤） */
    private static final String HAT_ITEM_NAMESPACE = "hats";

    // 复古列车主题色
    private static final int PANEL_BG = 0x801A1008;
    private static final int PANEL_BORDER = 0x558B6914;
    private static final int CARD_BG = 0xB01A1008;
    private static final int CARD_BG_HOVER = 0xB02A1A08;
    private static final int CARD_BG_EQUIPPED = 0xB02A2010;
    private static final int CARD_BG_LOCKED = 0xB0140E08;
    private static final int CARD_BORDER_LOCKED = 0xFF3A3020;
    private static final int TEXT_NAME = 0xFFE8C88A;
    private static final int TEXT_EQUIPPED = 0xFFD4AF37;
    private static final int TEXT_EQUIP = 0xFFC9A84C;
    private static final int TEXT_LOCKED = 0xFF5A5340;
    private static final int TEXT_EMPTY = 0xFF9E8B6E;
    private static final int SCROLL_TRACK = 0x331A1008;
    private static final int SCROLL_THUMB = 0xAA8B6914;
    private static final int SCROLL_THUMB_HL = 0xAAD4AF37;

    private final SREPlayerSkinsComponent skinsComponent;
    private final Consumer<String> onHatSelected;
    private final List<HatCard> cards = new ArrayList<>();

    private String equippedHat = "default";
    private float scroll = 0f;
    private float targetScroll = 0f;
    private float contentHeight = 0f;
    /** 由父界面每帧注入的帧间隔（秒），保证全界面动画使用同一时间步 */
    private float frameDelta = 0.016f;
    private boolean draggingScrollbar = false;

    public void setFrameDelta(float frameDelta) {
        this.frameDelta = frameDelta;
    }

    public HatSkinGridPanel(int x, int y, int width, int height,
            SREPlayerSkinsComponent skinsComponent, Consumer<String> onHatSelected, String searchFilter) {
        super(x, y, width, height, Component.empty());
        this.skinsComponent = skinsComponent;
        this.onHatSelected = onHatSelected;
        rebuild(searchFilter);
    }

    /** 重新收集帽子皮肤数据（刷新或装备变化后调用） */
    public final void rebuild() {
        rebuild("");
    }

    public final void rebuild(String filter) {
        cards.clear();
        this.equippedHat = skinsComponent.getEquippedSkin("hat");

        cards.add(new HatCard("default", null, 0xFF9AA0AA, true));

        Map<String, ItemSkinManager.Skin> hatSkins = ItemSkinManager.getSkins("hat");
        List<Map.Entry<String, ItemSkinManager.Skin>> sorted = new ArrayList<>(hatSkins.entrySet());
        sorted.sort(Comparator
                .comparing((Map.Entry<String, ItemSkinManager.Skin> e) -> !isUnlocked(e.getKey()))
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, ItemSkinManager.Skin> entry : sorted) {
            String name = entry.getKey();
            if ("default".equals(name)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.fromNamespaceAndPath(HAT_ITEM_NAMESPACE, name))
                    .orElse(Items.LEATHER_HELMET);
            int color = entry.getValue() != null ? entry.getValue().getColor() : 0xFFFFFFFF;
            cards.add(new HatCard(name, new ItemStack(item), color, isUnlocked(name)));
        }

        // 搜索过滤
        if (filter != null && !filter.isEmpty()) {
            String f = filter.toLowerCase(java.util.Locale.ROOT);
            cards.removeIf(c -> !"default".equals(c.skinName) && !c.displayName().getString().toLowerCase(java.util.Locale.ROOT).contains(f));
        }

        int columns = columns();
        contentHeight = PADDING * 2f + (float) Math.ceil(cards.size() / (double) columns) * (CARD_HEIGHT + CARD_GAP);
        targetScroll = Mth.clamp(targetScroll, 0, maxScroll());
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private boolean isUnlocked(String skinName) {
        return "default".equals(skinName) || skinsComponent.isSkinUnlockedForItemType("hat", skinName);
    }

    private int columns() {
        return Math.max(1, (this.width - PADDING * 2 + CARD_GAP) / (CARD_WIDTH + CARD_GAP));
    }

    private float maxScroll() {
        return Math.max(0f, contentHeight - this.height);
    }

    public String getEquippedHat() {
        return equippedHat;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        targetScroll = Mth.clamp(targetScroll - (float) scrollY * 28f, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        // 检查是否点击了滚动条
        if (maxScroll() > 0) {
            int barWidth = 4;
            int trackX = getX() + width - barWidth - 3;
            int barHeight = Math.max(18, (int) (height * (height / contentHeight)));
            int barY = getY() + (int) ((height - barHeight) * (scroll / maxScroll()));
            if (mouseX >= trackX && mouseX <= trackX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
                draggingScrollbar = true;
                return true;
            }
        }
        HatCard card = cardAt(mouseX, mouseY);
        if (card == null) {
            return false;
        }
        if (!card.unlocked) {
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
            return true;
        }
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        this.equippedHat = card.skinName;
        if (onHatSelected != null) {
            onHatSelected.accept(card.skinName);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && maxScroll() > 0) {
            float scrollPerPx = maxScroll() / (height - Math.max(18, (int) (height * (height / contentHeight))));
            targetScroll = Mth.clamp(targetScroll + (float) dragY * scrollPerPx, 0, maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private HatCard cardAt(double mouseX, double mouseY) {
        int columns = columns();
        int localX = (int) mouseX - getX() - PADDING;
        int localY = (int) (mouseY - getY() - PADDING + scroll);
        if (localX < 0 || localY < 0) {
            return null;
        }
        int col = localX / (CARD_WIDTH + CARD_GAP);
        int row = localY / (CARD_HEIGHT + CARD_GAP);
        if (col >= columns) {
            return null;
        }
        int index = row * columns + col;
        int inCardX = localX % (CARD_WIDTH + CARD_GAP);
        int inCardY = localY % (CARD_HEIGHT + CARD_GAP);
        if (inCardX >= CARD_WIDTH || inCardY >= CARD_HEIGHT) {
            return null;
        }
        return index >= 0 && index < cards.size() ? cards.get(index) : null;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = this.frameDelta;
        scroll = GuiAnim.approach(scroll, targetScroll, 14f, dt);

        // 面板背景（复古棕色）
        graphics.fillGradient(getX(), getY(), getX() + width, getY() + height, PANEL_BG, PANEL_BG);
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, PANEL_BORDER);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, PANEL_BORDER);

        if (cards.size() <= 1) {
            Component hint = Component.translatable("screen.sre.skins.hat_empty");
            graphics.drawCenteredString(Minecraft.getInstance().font, hint,
                    getX() + width / 2, getY() + height / 2 - 4, TEXT_EMPTY);
        }

        graphics.enableScissor(getX(), getY() + 1, getX() + width, getY() + height - 1);
        int columns = columns();
        HatCard hovered = this.isMouseOver(mouseX, mouseY) ? cardAt(mouseX, mouseY) : null;
        for (int i = 0; i < cards.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int cardX = getX() + PADDING + col * (CARD_WIDTH + CARD_GAP);
            int cardY = getY() + PADDING + row * (CARD_HEIGHT + CARD_GAP) - (int) scroll;
            if (cardY + CARD_HEIGHT < getY() || cardY > getY() + height) {
                continue;
            }
            renderCard(graphics, cards.get(i), cardX, cardY, cards.get(i) == hovered, dt);
        }
        graphics.disableScissor();

        // 复古金色滚动条
        if (maxScroll() > 0) {
            int barWidth = 4;
            int trackX = getX() + width - barWidth - 3;
            int barHeight = Math.max(18, (int) (height * (height / contentHeight)));
            int barY = getY() + (int) ((height - barHeight) * (scroll / maxScroll()));
            graphics.fill(trackX, getY(), trackX + barWidth, getY() + height, SCROLL_TRACK);
            boolean hoverScroll = mouseX >= trackX && mouseX <= trackX + barWidth
                    && mouseY >= barY && mouseY <= barY + barHeight;
            int thumbColor = hoverScroll ? SCROLL_THUMB_HL : SCROLL_THUMB;
            graphics.fill(trackX, barY, trackX + barWidth, barY + barHeight, thumbColor);
        }
    }

    private void renderCard(GuiGraphics graphics, HatCard card, int x, int y, boolean hovered, float dt) {
        card.hover = GuiAnim.toggle(card.hover, hovered && card.unlocked, 16f, dt);
        boolean equipped = card.skinName.equals(equippedHat);
        card.equipPulse = GuiAnim.toggle(card.equipPulse, equipped, 12f, dt);

        // 悬停轻微放大
        float scale = 1f + card.hover * 0.06f;
        int cx = x + CARD_WIDTH / 2;
        int cy = y + CARD_HEIGHT / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-cx, -cy, 0);

        int baseColor = !card.unlocked ? CARD_BG_LOCKED : CARD_BG;
        baseColor = GuiAnim.blend(baseColor, CARD_BG_HOVER, card.hover);
        baseColor = GuiAnim.blend(baseColor, CARD_BG_EQUIPPED, card.equipPulse * 0.5f);
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, baseColor);

        // 品质色描边
        int borderColor = card.unlocked ? card.qualityColor : CARD_BORDER_LOCKED;
        int borderAlpha = 0x50 + (int) (card.hover * 0x60) + (int) (card.equipPulse * 0x40);
        borderColor = GuiAnim.withAlpha(borderColor, Math.min(255, borderAlpha));
        graphics.fill(x, y, x + CARD_WIDTH, y + 1, borderColor);
        graphics.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor);
        graphics.fill(x, y, x + 1, y + CARD_HEIGHT, borderColor);
        graphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor);

        // 图标
        var font = Minecraft.getInstance().font;
        if (card.icon != null && !card.icon.isEmpty()) {
            int iconSize = 32;
            int iconX = cx - iconSize / 2;
            int iconY = y + 8;
            graphics.pose().pushPose();
            graphics.pose().translate(iconX, iconY, 0);
            graphics.pose().scale(2f, 2f, 1f);
            if (!card.unlocked) {
                graphics.setColor(0.35f, 0.32f, 0.28f, 1f);
            }
            graphics.renderFakeItem(card.icon, 0, 0);
            graphics.setColor(1f, 1f, 1f, 1f);
            graphics.pose().popPose();
        } else {
            Component none = Component.literal("\u2205");
            graphics.drawCenteredString(font, none, cx, y + 16,
                    card.unlocked ? TEXT_EMPTY : TEXT_LOCKED);
        }

        // 名称
        Component name = card.displayName();
        int nameColor = !card.unlocked ? TEXT_LOCKED : (equipped ? TEXT_EQUIPPED : TEXT_NAME);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(name.getString(), CARD_WIDTH - 8),
                cx, y + CARD_HEIGHT - 26, nameColor);

        // 状态行
        Component status;
        int statusColor;
        if (!card.unlocked) {
            status = Component.translatable("screen.sre.skins.locked_short");
            statusColor = TEXT_LOCKED;
        } else if (equipped) {
            status = Component.translatable("screen.sre.skins.equipped_short");
            statusColor = TEXT_EQUIPPED;
        } else {
            status = Component.translatable("screen.sre.skins.equip_short");
            statusColor = TEXT_EQUIP;
        }
        graphics.drawCenteredString(font, status, cx, y + CARD_HEIGHT - 14, statusColor);

        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    /** 单顶帽子卡片的数据 */
    private final class HatCard {
        final String skinName;
        final ItemStack icon;
        final int qualityColor;
        final boolean unlocked;
        float hover = 0f;
        float equipPulse = 0f;

        HatCard(String skinName, ItemStack icon, int qualityColor, boolean unlocked) {
            this.skinName = skinName;
            this.icon = icon;
            this.qualityColor = qualityColor;
            this.unlocked = unlocked;
        }

        Component displayName() {
            if ("default".equals(skinName)) {
                return Component.translatable("screen.sre.skins.no_hat");
            }
            return Component.translatableWithFallback(
                    "screen.sre.skins.hat." + skinName.toLowerCase(java.util.Locale.ROOT) + ".name",
                    formatName(skinName));
        }

        private String formatName(String raw) {
            StringBuilder result = new StringBuilder();
            for (String part : raw.split("[_\\-]")) {
                if (!part.isEmpty()) {
                    result.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1).toLowerCase()).append(' ');
                }
            }
            return result.toString().trim();
        }
    }
}
