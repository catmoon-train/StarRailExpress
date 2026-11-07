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

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.gui.anim.GuiAnim;
import io.wifi.starrailexpress.client.util.SkinVisibility;
import io.wifi.starrailexpress.network.UpdatePetPublicPayload;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 饰品设置界面。
 * <p>
 * 集中管理三类「只影响自己观感」的皮肤开关，原先散落在皮肤页标题栏：
 * <ul>
 *   <li><b>展示随从给其它玩家</b>（服务端权威，勾选后立即发包）；</li>
 *   <li><b>仅显示自己的饰品</b>（本地：帽子 / 背饰 / 手饰 / 随从只看自己的）；</li>
 *   <li><b>隐藏指定皮肤</b>（本地：按类别隐藏其他玩家身上的皮肤，自己的不受影响）。</li>
 * </ul>
 * 除随从公开项外，其余开关全部写入 {@link SREClientConfig} 的本地配置文件，
 * 进服时自动读取并应用，无需每次手动设置。
 * <p>
 * 配色与布局沿用 {@link SkinManagementScreen}（docs/ui_style.md 的复古列车风格）。
 */
public class AccessorySettingsScreen extends Screen {

    // ─── 主题色（与主皮肤页保持一致） ──────────────────────────────────────
    private static final int ACCENT = 0xFFD4AF37;
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int SCREEN_BG_TOP = 0xF018120A;
    private static final int SCREEN_BG_BOTTOM = 0xF0061018;
    private static final int PANEL_BORDER = 0xFF8B6914;
    private static final int TEXT_MAIN = 0xFFFFF4DC;
    private static final int TEXT_TITLE = 0xFFF5E8C8;
    private static final int TEXT_DIM = 0xFF9E8B6E;

    /** 「隐藏指定皮肤」列表里可以出现的类别（顺序即显示顺序）。 */
    private static final String[] HIDEABLE_TYPES = {
            ItemSkinManager.SkinTypes.KNIFE,
            ItemSkinManager.SkinTypes.REVOLVER,
            ItemSkinManager.SkinTypes.BAT,
            ItemSkinManager.SkinTypes.GRENADE,
            ItemSkinManager.SkinTypes.SHORT_SHOTGUN,
            ItemSkinManager.SkinTypes.DERRINGER,
            ItemSkinManager.SkinTypes.HAT,
            "backpack",
            "hand_accessory",
            ItemSkinManager.SkinTypes.PET,
            ItemSkinManager.SkinTypes.HANDCUFFS,
            ItemSkinManager.SkinTypes.LOCK,
            ItemSkinManager.SkinTypes.KEY,
            ItemSkinManager.SkinTypes.BOW,
            ItemSkinManager.SkinTypes.CROSSBOW,
            ItemSkinManager.SkinTypes.LETTER,
            ItemSkinManager.SkinTypes.LOCKPICK,
            ItemSkinManager.SkinTypes.CROWBAR,
            ItemSkinManager.SkinTypes.BODY_BAG,
            ItemSkinManager.SkinTypes.NOTE,
            ItemSkinManager.SkinTypes.FIRECRACKER,
    };

    private final Screen parent;
    private final List<SkinTypeToggle> hideToggles = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int scroll;
    private long openTimeMs = -1L;

    public AccessorySettingsScreen(Screen parent) {
        super(Component.translatable("screen.sre.skins.accessory_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openTimeMs < 0) {
            openTimeMs = System.currentTimeMillis();
        }
        SREClientConfig config = SREClientConfig.instance();

        this.panelWidth = Math.min(width - 24, 460);
        this.panelHeight = Math.min(height - 24, 320);
        this.panelX = (width - panelWidth) / 2;
        this.panelY = (height - panelHeight) / 2;

        int innerX = panelX + 10;
        int innerWidth = panelWidth - 20;
        int rowY = panelY + 26;

        // ── 展示随从给其它玩家（服务端权威）───────────────────────────────
        SREPlayerSkinsComponent component = Minecraft.getInstance().player == null ? null
                : SREPlayerSkinsComponent.KEY.getNullable(Minecraft.getInstance().player);
        boolean petPublic = component != null && component.isPetPublic();
        PetPublicToggle publicToggle = new PetPublicToggle(innerX, rowY, innerWidth, 18, petPublic);
        addRenderableWidget(publicToggle);
        rowY += 24;

        // ── 仅显示自己的饰品（本地）─────────────────────────────────────
        SettingToggle ownOnly = new SettingToggle(innerX, rowY, innerWidth, 18,
                Component.translatable("screen.sre.skins.accessory_settings.own_only"),
                !config.hideAllHats && config.showOwnHatOnly && !config.hideAllPets && config.showOwnPetOnly,
                v -> {
                    config.hideAllHats = false;
                    config.showOwnHatOnly = v;
                    config.hideAllPets = false;
                    config.showOwnPetOnly = v;
                    config.save();
                });
        ownOnly.setTooltip(Tooltip.create(
                Component.translatable("screen.sre.skins.accessory_settings.own_only.tip")));
        addRenderableWidget(ownOnly);
        rowY += 28;

        // ── 隐藏指定皮肤（本地，按类别）──────────────────────────────────
        this.listX = innerX;
        this.listY = rowY + 12;
        this.listWidth = innerWidth;
        this.listHeight = panelY + panelHeight - 40 - listY;

        List<String> visibleTypes = new ArrayList<>();
        for (String type : HIDEABLE_TYPES) {
            if (SkinVisibility.isHideableType(type)) {
                visibleTypes.add(type);
            }
        }
        int rowHeight = 16;
        int maxRows = Math.max(1, listHeight / rowHeight);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, visibleTypes.size() - maxRows)));

        hideToggles.clear();
        for (int i = 0; i < visibleTypes.size(); i++) {
            int visibleIndex = i - scroll;
            if (visibleIndex < 0 || visibleIndex >= maxRows) {
                continue;
            }
            String type = visibleTypes.get(i);
            SkinTypeToggle toggle = new SkinTypeToggle(listX, listY + visibleIndex * rowHeight,
                    listWidth, rowHeight - 1, type, config.isSkinTypeHidden(type),
                    v -> config.setSkinTypeHidden(type, v));
            hideToggles.add(toggle);
            addRenderableWidget(toggle);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .pos(panelX + panelWidth - 10 - 70, panelY + panelHeight - 12 - 16)
                .size(70, 16)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight) {
            int before = scroll;
            scroll += scrollY < 0 ? 1 : -1;
            if (scroll != before) {
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super，绘制自定义全屏背景
        graphics.fillGradient(0, 0, width, height, SCREEN_BG_TOP, SCREEN_BG_BOTTOM);

        float openProgress = GuiAnim.easeOutCubic((System.currentTimeMillis() - openTimeMs) / 300f);
        float slide = (1f - openProgress) * -12f;
        graphics.pose().pushPose();
        graphics.pose().translate(0, slide, 0);

        int alpha = GuiAnim.alphaOf(openProgress);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                GuiAnim.withAlpha(BG_TOP, alpha), GuiAnim.withAlpha(BG_BOTTOM, alpha));
        int border = GuiAnim.withAlpha(PANEL_BORDER, alpha);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, border);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, border);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, border);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, border);

        graphics.drawCenteredString(font, title, width / 2, panelY + 8,
                GuiAnim.withAlpha(TEXT_TITLE, alpha));
        int underlineW = (int) (64 * openProgress);
        graphics.fill(width / 2 - underlineW / 2, panelY + 20,
                width / 2 + underlineW / 2, panelY + 21, GuiAnim.withAlpha(ACCENT, alpha));

        graphics.pose().popPose();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 分区标题：隐藏指定皮肤
        graphics.drawString(font,
                Component.translatable("screen.sre.skins.accessory_settings.hidden_skins").getString(),
                listX, listY - 10, TEXT_MAIN, false);
        // 滚动提示
        graphics.drawString(font,
                Component.translatable("screen.sre.skins.accessory_settings.scroll_hint")
                        .withStyle(ChatFormatting.DARK_GRAY).getString(),
                listX, listY + listHeight + 1, TEXT_DIM, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── 「展示随从给其它玩家」：服务端权威 ──────────────────────────────

    /**
     * 随从公开开关。勾选后立刻发 {@link UpdatePetPublicPayload}，
     * 由服务端写入组件并在下一轮扫描时广播给其他客户端。
     */
    private static final class PetPublicToggle extends AbstractWidget {
        private boolean toggled;

        PetPublicToggle(int x, int y, int width, int height, boolean initial) {
            super(x, y, width, height,
                    Component.translatable("screen.sre.skins.show_pet_to_others"));
            this.toggled = initial;
            setTooltip(Tooltip.create(
                    Component.translatable("screen.sre.skins.accessory_settings.pet_public.tip")));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderToggleRow(g, getX(), getY(), width, height, getMessage(), toggled,
                    isHoveredOrFocused(), TEXT_MAIN);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOver(mouseX, mouseY)) {
                toggled = !toggled;
                click();
                ClientPlayNetworking.send(new UpdatePetPublicPayload(toggled));
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    // ─── 「仅显示自己的饰品」：本地开关 ────────────────────────────────────

    private static final class SettingToggle extends AbstractWidget {
        private boolean toggled;
        private final java.util.function.Consumer<Boolean> onToggle;

        SettingToggle(int x, int y, int width, int height, Component label, boolean initial,
                java.util.function.Consumer<Boolean> onToggle) {
            super(x, y, width, height, label);
            this.toggled = initial;
            this.onToggle = onToggle;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderToggleRow(g, getX(), getY(), width, height, getMessage(), toggled,
                    isHoveredOrFocused(), TEXT_MAIN);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOver(mouseX, mouseY)) {
                toggled = !toggled;
                click();
                if (onToggle != null) {
                    onToggle.accept(toggled);
                }
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    // ─── 「隐藏指定皮肤」单行开关 ──────────────────────────────────────────

    private static final class SkinTypeToggle extends AbstractWidget {
        private final String skinType;
        private boolean toggled;
        private final java.util.function.Consumer<Boolean> onToggle;

        SkinTypeToggle(int x, int y, int width, int height, String skinType, boolean initial,
                java.util.function.Consumer<Boolean> onToggle) {
            super(x, y, width, height, Component.translatable(typeLabelKey(skinType)));
            this.skinType = skinType;
            this.toggled = initial;
            this.onToggle = onToggle;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderToggleRow(g, getX(), getY(), width, height, getMessage(), toggled,
                    isHoveredOrFocused(), toggled ? 0xFFFF9C6B : TEXT_MAIN);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOver(mouseX, mouseY)) {
                toggled = !toggled;
                click();
                if (onToggle != null) {
                    onToggle.accept(toggled);
                }
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }

        String skinType() {
            return skinType;
        }
    }

    /** 皮肤类型 → 翻译键；未知类型回退到 {@code screen.sre.skins.type.<type>}。 */
    private static String typeLabelKey(String skinType) {
        return "screen.sre.skins.type." + skinType;
    }

    // ─── 共用绘制 ────────────────────────────────────────────────────────────

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK, 0.7f));
    }

    /**
     * 一行开关的统一样式：小方框 + 勾号 + 标签。
     * <p>
     * 勾选态用金色强调，未勾选态用土褐色；hover 时整行提亮。
     */
    private static void renderToggleRow(GuiGraphics g, int x, int y, int width, int height,
            Component label, boolean toggled, boolean hovered, int labelColor) {
        int bg = toggled ? 0x992A2010 : (hovered ? 0x992A1A08 : 0x991A1008);
        g.fill(x, y, x + width, y + height, bg);
        int border = toggled ? 0xFFD4AF37 : (hovered ? 0xFFC9A84C : 0x558B6914);
        g.fill(x, y, x + width, y + 1, border);
        g.fill(x, y + height - 1, x + width, y + height, border);
        g.fill(x, y, x + 1, y + height, border);
        g.fill(x + width - 1, y, x + width, y + height, border);

        int boxSize = Math.min(10, height - 4);
        int boxX = x + 4;
        int boxY = y + (height - boxSize) / 2;
        g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, toggled ? 0xFFD4AF37 : 0x553A3020);
        g.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0x558B6914);
        g.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0x558B6914);
        g.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0x558B6914);
        g.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0x558B6914);
        if (toggled) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, "\u2713", boxX + 1, boxY - 1, 0xFF1A1008, false);
        }

        var font = Minecraft.getInstance().font;
        String clipped = font.plainSubstrByWidth(label.getString(), width - boxSize - 14);
        g.drawString(font, clipped, boxX + boxSize + 6, y + (height - 8) / 2, labelColor, false);
    }

    /** 供外部（皮肤页）复用：把类别名翻成界面可读的名字。 */
    public static Component displayName(String skinType) {
        return Component.translatable(typeLabelKey(skinType));
    }

    /** 可隐藏类别的完整列表（含当前没有皮肤的类别，由调用方自行过滤）。 */
    public static Map<String, Component> hideableTypeNames() {
        Map<String, Component> map = new LinkedHashMap<>();
        for (String type : HIDEABLE_TYPES) {
            if (SkinVisibility.isHideableType(type)) {
                map.put(type, displayName(type));
            }
        }
        return map;
    }
}
