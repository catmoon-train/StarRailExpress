package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleIntroduceScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 布局常量
    // ══════════════════════════════════════════════════════════════════

    private static final int MAX_USABLE_WIDTH = 700;
    private static final float USABLE_RATIO = 0.9f;
    private static final float LEFT_RATIO = 0.30f;

    private static final int PANEL_PAD = 6;
    private static final int CARD_H = 42;
    private static final int CARD_SPACING = 4;
    private static final int ICON_SIZE = 26;
    /** 滚动条宽度，加粗到 7px 更容易看到 */
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int BANNER_H = 26;

    // ══════════════════════════════════════════════════════════════════
    // 图标纹理
    // 注意：Minecraft 只支持 PNG 格式，JPG 会加载失败。
    // 请将图标文件命名为 .png 并放置于：
    // src/main/resources/assets/noellesroles/textures/gui/role_icons/a.png
    // 若纹理加载失败，会自动降级为首字母色块。
    // ══════════════════════════════════════════════════════════════════

    // 目前用外部模组的纹理作为默认图标（如果该文件是 PNG 则可正常显示）
    private static final ResourceLocation ICON_DEFAULT = SRE.watheId("textures/gui/sprites/hud/mood_happy.png");

    private static final Map<String, ResourceLocation> TYPE_ICON_MAP = new HashMap<>();

    // 扩展示例：
    static {
        TYPE_ICON_MAP.put("role_1", SRE.watheId("textures/gui/sprites/hud/mood_happy.png"));
        TYPE_ICON_MAP.put("role_2", Noellesroles.id("textures/gui/sprites/hud/mood_neu.png"));
        TYPE_ICON_MAP.put("role_3", Noellesroles.id("textures/gui/sprites/hud/mood_jester.png"));
        TYPE_ICON_MAP.put("role_4", SRE.watheId("textures/gui/sprites/hud/mood_killer.png"));
        TYPE_ICON_MAP.put("role_5", Noellesroles.id("textures/gui/sprites/hud/mood_vig.png"));
        TYPE_ICON_MAP.put("modifier", SRE.watheId("textures/gui/sprites/hud/mood_happy.png"));
    }

    private static ResourceLocation getTypeIcon(Object role) {
        if (role instanceof Role rrole) {
            return TYPE_ICON_MAP.getOrDefault("role_" + PlayerRoleWeightManager.getRoleType(rrole), ICON_DEFAULT);
        } else {
            return TYPE_ICON_MAP.getOrDefault("modifier", ICON_DEFAULT);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 状态
    // ══════════════════════════════════════════════════════════════════

    private final List<Role> availableRoles = new ArrayList<>();
    private final List<Object> filteredItems = new ArrayList<>();

    private int usableWidth, leftW, rightW;
    private int panelX, panelY, panelH;
    private int leftX, rightX;

    // 左侧列表滚动
    private int listScrollOffset = 0;
    private int maxListScroll = 0;
    private boolean isDraggingListScroll = false;
    private double dragListStartY = 0;
    private int dragListStartOffset = 0;

    // 卡片悬停动画（0~1）
    private final Map<Object, Float> hoverAnims = new HashMap<>();

    // 右侧详情滚动
    private Object selectedRole = null;
    private final List<FormattedCharSequence> detailLines = new ArrayList<>();
    private int detailScrollOffset = 0;
    private int maxDetailScroll = 0;
    private boolean isDraggingDetailScroll = false;
    private double dragDetailStartY = 0;
    private int dragDetailStartOffset = 0;

    // Widgets
    private EditBox searchWidget = null;
    private String searchContent = null;
    private Button closeButton = null;

    // ══════════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════════

    public RoleIntroduceScreen(Player player) {
        super(Component.translatable("gui.roleintroduce.select_role.title"));
        availableRoles.addAll(Noellesroles.getAllRolesSorted(true));
    }

    // ══════════════════════════════════════════════════════════════════
    // 初始化
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        computeLayout();
        initSearchBox();
        refreshFilter();
        if (selectedRole == null && !filteredItems.isEmpty()) {
            selectedRole = filteredItems.get(0);
        }
        rebuildDetailLines();
        initCloseButton();
    }

    private void computeLayout() {
        usableWidth = Math.min((int) (width * USABLE_RATIO), MAX_USABLE_WIDTH);
        leftW = (int) (usableWidth * LEFT_RATIO);
        rightW = usableWidth - leftW;
        panelX = (width - usableWidth) / 2;
        panelY = 48;
        panelH = height - panelY - 42; // 改前是 -22，加大底部留白即可
        leftX = panelX;
        rightX = panelX + leftW;
    }

    private void initSearchBox() {
        int sw = leftW - 2;
        int sx = leftX + 1;
        int sy = panelY - 22;

        if (searchWidget == null) {
            searchWidget = new EditBox(font, sx, sy, sw, 18, Component.empty());
            searchWidget.setHint(
                    Component.translatable("screen.noellesroles.search.placeholder")
                            .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                searchContent = text.isEmpty() ? null : text;
                listScrollOffset = 0;
                refreshFilter();
                selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
                rebuildDetailLines();
            });
        } else {
            searchWidget.setPosition(sx, sy);
            searchWidget.setWidth(sw);
            removeWidget(searchWidget);
        }
        addRenderableWidget(searchWidget);

        boolean noResult = filteredItems.isEmpty()
                && searchContent != null && !searchContent.isEmpty();
        searchWidget.setTextColor(noResult ? 0xFFAA2222 : 0xFFFFFFFF);
    }

    private void initCloseButton() {
        if (closeButton != null) {
            removeWidget(closeButton);
            closeButton = null;
        }
        int btnW = 100;
        int btnH = 18;
        int btnX = (width - btnW) / 2;
        int btnY = panelY + panelH + 8;

        closeButton = Button.builder(
                Component.translatable("gui.back").withStyle(ChatFormatting.GRAY),
                btn -> onClose()).bounds(btnX, btnY, btnW, btnH).build();
        addRenderableWidget(closeButton);
    }

    // ══════════════════════════════════════════════════════════════════
    // 数据
    // ══════════════════════════════════════════════════════════════════

    private void refreshFilter() {
        filteredItems.clear();
        for (Role role : availableRoles) {
            String name = RoleUtils.getRoleName(role).getString();
            if (searchContent == null
                    || name.toLowerCase().contains(searchContent.toLowerCase())
                    || role.identifier().toString().contains(searchContent.toLowerCase())) {
                filteredItems.add(role);
            }
        }
        for (Modifier mod : HMLModifiers.MODIFIERS) {
            String name = mod.getName().getString();
            if (searchContent == null
                    || name.toLowerCase().contains(searchContent.toLowerCase())
                    || mod.identifier().toString().contains(searchContent.toLowerCase())) {
                filteredItems.add(mod);
            }
        }
        int areaH = listAreaH();
        int totalH = filteredItems.size() * (CARD_H + CARD_SPACING) - CARD_SPACING;
        maxListScroll = Math.max(0, totalH - areaH);
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
    }

    private int listAreaH() {
        // 右侧留滚动条轨道宽度的空间
        return panelH - PANEL_PAD * 2;
    }

    private void rebuildDetailLines() {
        detailLines.clear();
        if (selectedRole == null)
            return;

        int textW = rightW - PANEL_PAD * 2 - SCROLL_W - 4;

        if (selectedRole instanceof Role role) {
            detailLines.addAll(font.split(
                    Component.translatable("announcement.star.goals." + role.identifier().getPath()), textW));
            detailLines.add(FormattedCharSequence.EMPTY);
        }

        detailLines.addAll(font.split(Component.empty()
                .append(Component.translatable("screen.roleintroduce.detail.name")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(" ")
                .append(RoleUtils.getRoleOrModifierNameWithColor(selectedRole)),
                textW));
        detailLines.add(FormattedCharSequence.EMPTY);

        detailLines.addAll(font.split(
                Component.translatable("screen.roleintroduce.detail.description")
                        .withStyle(ChatFormatting.YELLOW),
                textW));

        int dashCount = textW / Math.max(1, font.width("─"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dashCount; i++)
            sb.append("─");
        detailLines.addAll(font.split(
                Component.literal(sb.toString()).withStyle(ChatFormatting.DARK_GRAY), textW));

        detailLines.addAll(font.split(
                RoleUtils.getRoleOrModifierDescription(selectedRole)
                        .copy().withStyle(ChatFormatting.WHITE),
                textW));

        int visH = detailContentH();
        int totalTextH = detailLines.size() * (font.lineHeight + 2);
        maxDetailScroll = Math.max(0, totalTextH - visH);
        detailScrollOffset = 0;
    }

    private int detailContentH() {
        return panelH - BANNER_H - PANEL_PAD * 2 - 4;
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderLeftPanel(g, mouseX, mouseY);
        renderRightPanel(g, mouseX, mouseY);

        g.fillGradient(0, 0, width, panelY - 22, 0xBB000000, 0x00000000);
        g.drawCenteredString(font, this.title, width / 2, 8, 0xEEEEFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ══════════════════════════════════════════════════════════════════
    // 左侧面板
    // ══════════════════════════════════════════════════════════════════

    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, leftX, panelY, leftW, panelH);

        int areaX = leftX + PANEL_PAD;
        int areaY = panelY + PANEL_PAD;
        // 留出右侧滚动条区域
        int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
        int areaH = listAreaH();

        // ── 裁剪 ──────────────────────────────────────────────
        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        for (int i = 0; i < filteredItems.size(); i++) {
            Object role = filteredItems.get(i);
            int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;

            if (cardY + CARD_H < areaY || cardY > areaY + areaH)
                continue;

            boolean hovered = isInRect(mouseX, mouseY, areaX, cardY, areaW, CARD_H);
            boolean selected = role.equals(selectedRole);

            // 平滑悬停动画
            float anim = hoverAnims.getOrDefault(role, 0f);
            anim = Mth.lerp(0.3f, anim, (hovered && !selected) ? 1f : 0f);
            hoverAnims.put(role, anim);

            renderListCard(g, role, areaX, cardY, areaW, CARD_H, anim, selected);
        }

        g.disableScissor();

        if (filteredItems.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.search.empty")
                            .withStyle(ChatFormatting.RED),
                    leftX + leftW / 2, panelY + panelH / 2, 0xFF5555);
        }

        // ── 滚动条（在裁剪区域外，始终可见） ────────────────────
        int sbX = leftX + leftW - PANEL_PAD - SCROLL_W;
        int totalH = Math.max(1, filteredItems.size() * (CARD_H + CARD_SPACING));
        renderVScrollbar(g, sbX, areaY, areaH,
                listScrollOffset, maxListScroll, totalH,
                mouseX, mouseY, isDraggingListScroll);
    }

    // ── 单张横条卡片 ───────────────────────────────────────────────────

    private void renderListCard(GuiGraphics g, Object role,
            int x, int y, int w, int h,
            float hover, boolean selected) {
        int rawColor = RoleUtils.getRoleOrModifierColor(role);
        // 强制 alpha=FF，防止职业色含透明
        int roleColor = rawColor | 0xFF000000;

        // ── 外层描边（所有卡片都有，颜色随状态变化） ──────────────
        int outerBorder;
        if (selected) {
            outerBorder = 0xFF6688EE; // 亮蓝
        } else if (hover > 0.05f) {
            outerBorder = blendColors(0xFF2A3060, 0xFF5566BB, hover); // 悬停渐亮
        } else {
            outerBorder = 0xFF2A3060; // 普通可见边框
        }
        g.fill(x, y, x + w, y + h, outerBorder);

        // ── 内层背景 ──────────────────────────────────────────────
        int bgL, bgR;
        if (selected) {
            // 选中：明显的蓝紫渐变
            bgL = 0xFF223380;
            bgR = 0xFF162060;
        } else if (hover > 0.05f) {
            // 悬停：从暗蓝渐变到中蓝
            bgL = blendColors(0xFF141828, 0xFF1E2E68, hover);
            bgR = blendColors(0xFF0E1020, 0xFF162050, hover);
        } else {
            // 普通：深蓝黑，但不要太黑（让边框有对比）
            bgL = 0xFF141828;
            bgR = 0xFF0E1020;
        }
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);

        // 顶部高光细线
        g.fill(x + 1, y + 1, x + w - 1, y + 2,
                selected ? 0x44AABBFF : (hover > 0.05f ? 0x25FFFFFF : 0x10FFFFFF));

        // ── 左侧职业色竖条 ─────────────────────────────────────────
        int barW = 3;
        g.fill(x + 1, y + 1, x + 1 + barW, y + h - 1, roleColor);
        // 竖条右侧淡出
        g.fillGradient(x + 1 + barW, y + 1, x + 1 + barW + 4, y + h - 1,
                (rawColor & 0x00FFFFFF) | 0x40000000, 0x00000000);

        // ── 图标 ──────────────────────────────────────────────────
        int iconPadV = (h - ICON_SIZE) / 2;
        int iconX = x + 1 + barW + 5;
        int iconY = y + iconPadV;

        // 图标背景（不透明的暗色底，确保图标可见）
        int iconBgColor = blendColors(0xFF0A0C18, roleColor, 0.25f);
        g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, iconBgColor);

        // 尝试渲染纹理（仅 PNG 有效）
        boolean iconOk = false;
        try {
            ResourceLocation icon = getTypeIcon(role);
            // 1.21.1 Mojang mappings: blit(ResourceLocation, int,int, float,float, int,int,
            // int,int)
            g.blit(icon, iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            iconOk = true;
        } catch (Exception ignored) {
        }

        // 降级：首字母（在不透明背景上绘制，保证可见）
        if (!iconOk) {
            // 用职业色做背景圆块
            int tintBg = blendColors(0xFF111320, roleColor, 0.55f);
            g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, tintBg);

            String initial = RoleUtils.getRoleOrModifierName(role).getString();
            if (!initial.isEmpty()) {
                // 大号首字母，白色带阴影
                String ch = String.valueOf(initial.charAt(0)).toUpperCase();
                g.drawCenteredString(font,
                        Component.literal(ch).withStyle(ChatFormatting.BOLD),
                        iconX + ICON_SIZE / 2,
                        iconY + (ICON_SIZE - font.lineHeight) / 2,
                        0xFFFFFF);
            }
        }

        // 图标外框（用职业色，较亮）
        int iconBorder = blendColors(roleColor, 0xFFFFFFFF, 0.3f);
        g.renderOutline(iconX, iconY, ICON_SIZE, ICON_SIZE, iconBorder);

        // ── 文字 ──────────────────────────────────────────────────
        int textX = iconX + ICON_SIZE + 5;
        int textMaxW = w - (textX - x) - 6;

        // 类型名（小字，青灰色，选中时稍亮）
        int typeColor = selected ? 0xFF88CCEE : blendColors(0xFF5577AA, 0xFF88BBDD, hover);
        String typeStr = font.plainSubstrByWidth(
                RoleUtils.getRoleOrModifierTypeName(role).getString(), textMaxW);
        g.drawString(font, typeStr, textX, y + 5, typeColor, false);

        // 职业名称（选中时金色，否则白色）
        int nameY = y + 5 + font.lineHeight + 1;
        int nameColor = selected ? 0xFFFFDD88 : (hover > 0.3f ? 0xFFEEEEFF : 0xFFCCCCDD);
        Component nameComp = RoleUtils.getRoleOrModifierNameWithColor(role);
        List<FormattedCharSequence> nameLines = font.split(nameComp, textMaxW);
        if (!nameLines.isEmpty()) {
            g.drawString(font, nameLines.get(0), textX, nameY, nameColor, selected);
        }
        // ── 新增：第三行副文本 ─────────────────────────────────────
        int subY = nameY + font.lineHeight + 1;
        Component subText = getCardSubText(role); // 见下方
        if (subText != null) {
            String subStr = font.plainSubstrByWidth(subText.getString(), textMaxW);
            int subColor = java.awt.Color.WHITE.getRGB();
            var style = subText.getStyle();
            if (style != null) {
                var textcolor = style.getColor();
                if (textcolor != null) {
                    subColor = new java.awt.Color(textcolor.getValue()).getRGB();
                }
            }

            g.drawString(font, subStr, textX, subY, subColor, false);
        }
        // ── 选中时：右侧指示条 + 外发光 ──────────────────────────
        if (selected) {
            // 右侧亮条
            int indX = x + w - 4;
            g.fill(indX, y + 3, indX + 3, y + h - 3,
                    blendColors(roleColor, 0xFFFFFFFF, 0.7f));

            // 外发光圈（比外层描边再大一圈）
            int glowColor = (rawColor & 0x00FFFFFF) | 0x55000000;
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, glowColor);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 右侧面板
    // ══════════════════════════════════════════════════════════════════

    private Component getCardSubText(Object role) {
        if (role instanceof Role r) {
            int type = PlayerRoleWeightManager.getRoleType(r);
            switch (type) {
                case 0, 1:
                    return Component.translatable("display.type.role.innocent").withStyle(ChatFormatting.GREEN);
                case 2:
                    return Component.translatable("display.type.role.neutral").withStyle(ChatFormatting.YELLOW);
                case 3:
                    return Component.translatable("display.type.role.neutral_for_killer")
                            .withStyle(ChatFormatting.LIGHT_PURPLE);
                case 4:
                    return Component.translatable("display.type.role.killer").withStyle(ChatFormatting.RED);
                case 5:
                    return Component.translatable("display.type.role.vigilante").withStyle(ChatFormatting.AQUA);
                default:
                    return Component.literal("UNKNOWN");
            }
        }
        return Component.literal("");
    }

    private void renderRightPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, rightX, panelY, rightW, panelH);

        if (selectedRole == null) {
            g.drawCenteredString(font,
                    Component.translatable("screen.roleintroduce.select_hint")
                            .withStyle(ChatFormatting.GRAY),
                    rightX + rightW / 2, panelY + panelH / 2, 0x888899);
            return;
        }

        int rawColor = RoleUtils.getRoleOrModifierColor(selectedRole);

        // ── 顶部横幅 ──────────────────────────────────────────────
        g.fillGradient(rightX + 1, panelY + 1,
                rightX + rightW / 2, panelY + BANNER_H,
                (rawColor & 0x00FFFFFF) | 0xCC000000,
                (rawColor & 0x00FFFFFF) | 0x44000000);
        g.fillGradient(rightX + rightW / 2, panelY + 1,
                rightX + rightW - 1, panelY + BANNER_H,
                (rawColor & 0x00FFFFFF) | 0x44000000,
                0x00000000);

        // 横幅图标
        int bIconSize = BANNER_H - 6;
        int bIconX = rightX + PANEL_PAD;
        int bIconY = panelY + 3;
        int bIconBg = blendColors(0xFF0A0C18, rawColor | 0xFF000000, 0.3f);
        g.fill(bIconX, bIconY, bIconX + bIconSize, bIconY + bIconSize, bIconBg);
        try {
            g.blit(getTypeIcon(selectedRole),
                    bIconX, bIconY, 0f, 0f, bIconSize, bIconSize, bIconSize, bIconSize);
        } catch (Exception ignored) {
            // 降级首字母
            String initial = RoleUtils.getRoleOrModifierName(selectedRole).getString();
            if (!initial.isEmpty()) {
                g.drawCenteredString(font,
                        Component.literal(String.valueOf(initial.charAt(0)).toUpperCase())
                                .withStyle(ChatFormatting.BOLD),
                        bIconX + bIconSize / 2,
                        bIconY + (bIconSize - font.lineHeight) / 2,
                        0xFFFFFF);
            }
        }
        g.renderOutline(bIconX, bIconY, bIconSize, bIconSize,
                (rawColor & 0x00FFFFFF) | 0xAA000000);

        // 横幅类型标签
        Component typeTag = Component.empty()
                .append("【 ")
                .append(RoleUtils.getRoleOrModifierTypeName(selectedRole)
                        .copy().withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA))
                .append(" 】");
        g.drawString(font, typeTag,
                bIconX + bIconSize + 5,
                panelY + (BANNER_H - font.lineHeight) / 2,
                0xFFFFFF, true);

        // ── 文字内容 ──────────────────────────────────────────────
        int textX0 = rightX + PANEL_PAD;
        int textY0 = panelY + BANNER_H + PANEL_PAD;
        int contentH = detailContentH();

        g.enableScissor(rightX + 1, textY0,
                rightX + rightW - SCROLL_W - 2, textY0 + contentH);

        int lineH = font.lineHeight + 2;
        int lineY = textY0 - detailScrollOffset;
        for (FormattedCharSequence line : detailLines) {
            if (lineY + lineH > textY0 && lineY < textY0 + contentH) {
                g.drawString(font, line, textX0, lineY, java.awt.Color.WHITE.getRGB(), false);
            }
            lineY += lineH;
        }

        g.disableScissor();

        // 右侧滚动条
        int sbX = rightX + rightW - PANEL_PAD - SCROLL_W;
        renderVScrollbar(g, sbX, textY0, contentH,
                detailScrollOffset, maxDetailScroll,
                Math.max(1, detailLines.size() * lineH),
                mouseX, mouseY, isDraggingDetailScroll);
    }

    // ══════════════════════════════════════════════════════════════════
    // 通用渲染工具
    // ══════════════════════════════════════════════════════════════════

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD80C1020, 0xD8101828);
        g.renderOutline(x, y, w, h, 0xFF1E3060);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    /**
     * 纵向滚动条。
     * 轨道始终可见（不透明度约 35%），thumb 在有内容可滚时显示。
     */
    private void renderVScrollbar(GuiGraphics g,
            int x, int y, int h,
            int scrollOffset, int maxScroll, int totalContentH,
            int mouseX, int mouseY, boolean dragging) {
        // ── 轨道：始终可见 ─────────────────────────────────────────
        // 外层深色轮廓
        g.fill(x, y, x + SCROLL_W, y + h, 0xFF111828);
        // 内层半透明轨道槽
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x55334466);

        if (maxScroll <= 0)
            return;

        // ── Thumb ─────────────────────────────────────────────────
        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOffset / maxScroll));

        boolean hl = dragging || isInRect(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);

        // thumb 外框
        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH,
                hl ? 0xFF8899CC : 0xFF556699);
        // thumb 内部（亮一点）
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1,
                hl ? 0xFFAABBEE : 0xFF7788BB);
        // thumb 顶部高光
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (true)
            return;
        g.fillGradient(0, 0, width, height, 0xFF07090F, 0xFF090C18);
        if (minecraft != null && minecraft.level != null) {
            long t = minecraft.level.getGameTime();
            for (int i = 0; i < 30; i++) {
                int sx = (int) ((t * 0.32 + i * 57.6) % width);
                int sy = (int) (((Math.sin(t * 0.012 + i * 1.15) * 30) + height * 0.5 + i * 8.1) % height);
                int sz = (int) (0.8 + Math.abs(Math.sin(t * 0.065 + i)) * 1.2);
                int alpha = (int) (50 + 120 * ((Math.sin(t * 0.035 + i) + 1) * 0.5));
                g.fill(sx, sy, sx + sz, sy + sz, (alpha << 24) | 0xAABBFF);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int areaX = leftX + PANEL_PAD;
            int areaY = panelY + PANEL_PAD;
            int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
            int areaH = listAreaH();

            // ── 卡片点击 ──────────────────────────────────────────
            if (isInRect((int) mx, (int) my, areaX, areaY, areaW, areaH)) {
                for (int i = 0; i < filteredItems.size(); i++) {
                    int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;
                    if (isInRect((int) mx, (int) my, areaX, cardY, areaW, CARD_H)) {
                        selectedRole = filteredItems.get(i);
                        rebuildDetailLines();
                        return true;
                    }
                }
                // 点击了列表区但没中任何卡片，仍然消费事件防止搜索框失焦异常
                return true;
            }

            // ── 左侧滚动条 ────────────────────────────────────────
            int lsbX = leftX + leftW - PANEL_PAD - SCROLL_W;
            if (isInRect((int) mx, (int) my, lsbX, areaY, SCROLL_W, areaH)) {
                if (maxListScroll > 0) {
                    isDraggingListScroll = true;
                    dragListStartY = my;
                    dragListStartOffset = listScrollOffset;
                }
                return true;
            }

            // ── 右侧滚动条 ────────────────────────────────────────
            int rsbX = rightX + rightW - PANEL_PAD - SCROLL_W;
            int textY0 = panelY + BANNER_H + PANEL_PAD;
            int dH = detailContentH();
            if (isInRect((int) mx, (int) my, rsbX, textY0, SCROLL_W, dH)) {
                if (maxDetailScroll > 0) {
                    isDraggingDetailScroll = true;
                    dragDetailStartY = my;
                    dragDetailStartOffset = detailScrollOffset;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingListScroll && maxListScroll > 0) {
            int areaH = listAreaH();
            int totalH = Math.max(1, filteredItems.size() * (CARD_H + CARD_SPACING));
            float ratio = Math.min(1f, (float) areaH / totalH);
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (areaH * ratio));
            double trackH = areaH - thumbH;
            if (trackH > 0) {
                listScrollOffset = Mth.clamp(
                        (int) (dragListStartOffset + (my - dragListStartY) / trackH * maxListScroll),
                        0, maxListScroll);
            }
            return true;
        }
        if (isDraggingDetailScroll && maxDetailScroll > 0) {
            int contentH = detailContentH();
            int totalTextH = Math.max(1, detailLines.size() * (font.lineHeight + 2));
            float ratio = Math.min(1f, (float) contentH / totalTextH);
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (contentH * ratio));
            double trackH = contentH - thumbH;
            if (trackH > 0) {
                detailScrollOffset = Mth.clamp(
                        (int) (dragDetailStartOffset + (my - dragDetailStartY) / trackH * maxDetailScroll),
                        0, maxDetailScroll);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingListScroll = false;
        isDraggingDetailScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= leftX && mx < leftX + leftW && my >= panelY && my < panelY + panelH) {
            listScrollOffset = Mth.clamp(
                    (int) (listScrollOffset - scrollY * (CARD_H + CARD_SPACING)),
                    0, maxListScroll);
            return true;
        }
        if (mx >= rightX && mx < rightX + rightW && my >= panelY && my < panelY + panelH) {
            detailScrollOffset = Mth.clamp(
                    (int) (detailScrollOffset - scrollY * (font.lineHeight + 2) * 3),
                    0, maxDetailScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ══════════════════════════════════════════════════════════════════
    // 键盘事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 265 || keyCode == 264) { // ↑ ↓
            int idx = filteredItems.indexOf(selectedRole);
            idx += (keyCode == 265 ? -1 : 1);
            idx = Mth.clamp(idx, 0, filteredItems.size() - 1);
            if (idx >= 0 && idx < filteredItems.size()) {
                selectedRole = filteredItems.get(idx);
                rebuildDetailLines();
                int cardY = idx * (CARD_H + CARD_SPACING);
                if (cardY < listScrollOffset) {
                    listScrollOffset = cardY;
                } else if (cardY + CARD_H > listScrollOffset + listAreaH()) {
                    listScrollOffset = cardY + CARD_H - listAreaH();
                }
                listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════

    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f)
            return c1;
        if (t >= 1f)
            return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}