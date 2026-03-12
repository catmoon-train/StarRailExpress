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
import java.util.function.Predicate;

public class RoleIntroduceScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 【可自定义分类】在 CATEGORIES 末尾追加即可，无需改动其他代码。
    // ══════════════════════════════════════════════════════════════════

    public static class RoleCategory {
        public final String labelKey;
        public final int activeColor;
        public final Predicate<Object> filter;

        public RoleCategory(String labelKey, int activeColor, Predicate<Object> filter) {
            this.labelKey = labelKey;
            this.activeColor = activeColor;
            this.filter = filter;
        }
    }

    public static final List<RoleCategory> CATEGORIES = new ArrayList<>();

    static {
        CATEGORIES.add(new RoleCategory(
                "screen.roleintroduce.category.all", 0xFF5577CC, item -> true));

        CATEGORIES.add(new RoleCategory(
                "display.type.role.innocent", 0xFF44BB66,
                item -> item instanceof Role r
                        && (PlayerRoleWeightManager.getRoleType(r) == 0
                                || PlayerRoleWeightManager.getRoleType(r) == 1)));

        CATEGORIES.add(new RoleCategory(
                "display.type.role.vigilante", 0xFF22BBCC,
                item -> item instanceof Role r
                        && PlayerRoleWeightManager.getRoleType(r) == 5));

        CATEGORIES.add(new RoleCategory(
                "display.type.role.neutral", 0xFFCCAA22,
                item -> item instanceof Role r
                        && PlayerRoleWeightManager.getRoleType(r) == 2));

        CATEGORIES.add(new RoleCategory(
                "display.type.role.neutral_for_killer", 0xFFAA44CC,
                item -> item instanceof Role r
                        && PlayerRoleWeightManager.getRoleType(r) == 3));

        CATEGORIES.add(new RoleCategory(
                "display.type.role.killer", 0xFFCC2233,
                item -> item instanceof Role r
                        && PlayerRoleWeightManager.getRoleType(r) == 4));

        CATEGORIES.add(new RoleCategory(
                "screen.roleintroduce.category.modifier", 0xFF8877BB,
                item -> item instanceof Modifier));
    }

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
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int BANNER_H = 26;

    /** 搜索框与分类标签共享的顶部行高度 */
    private static final int TOP_BAR_H = 18;
    /** 搜索框与分类标签之间的水平间距 */
    private static final int TOP_BAR_GAP = 6;
    /** 分类标签之间的间距 */
    private static final int TAB_GAP = 2;

    // ══════════════════════════════════════════════════════════════════
    // 图标纹理
    // ══════════════════════════════════════════════════════════════════

    private static final ResourceLocation ICON_DEFAULT = SRE.watheId("textures/gui/sprites/hud/mood_happy.png");

    private static final Map<String, ResourceLocation> TYPE_ICON_MAP = new HashMap<>();

    static {
        TYPE_ICON_MAP.put("role_1", SRE.watheId("textures/gui/sprites/hud/mood_happy.png"));
        TYPE_ICON_MAP.put("role_2", Noellesroles.id("textures/gui/sprites/hud/mood_neu.png"));
        TYPE_ICON_MAP.put("role_3", Noellesroles.id("textures/gui/sprites/hud/mood_jester.png"));
        TYPE_ICON_MAP.put("role_4", SRE.watheId("textures/gui/sprites/hud/mood_killer.png"));
        TYPE_ICON_MAP.put("role_5", Noellesroles.id("textures/gui/sprites/hud/mood_vig.png"));
        TYPE_ICON_MAP.put("modifier", SRE.watheId("textures/gui/sprites/hud/mood_happy.png"));
    }

    private static ResourceLocation getTypeIcon(Object role) {
        if (role instanceof Role rrole)
            return TYPE_ICON_MAP.getOrDefault(
                    "role_" + PlayerRoleWeightManager.getRoleType(rrole), ICON_DEFAULT);
        return TYPE_ICON_MAP.getOrDefault("modifier", ICON_DEFAULT);
    }

    // ══════════════════════════════════════════════════════════════════
    // 状态
    // ══════════════════════════════════════════════════════════════════

    private final List<Role> availableRoles = new ArrayList<>();
    private final List<Object> filteredItems = new ArrayList<>();

    private int usableWidth, leftW, rightW;
    private int panelX, panelY, panelH;
    private int leftX, rightX;

    /** 顶部行（搜索框 + 分类标签）的 Y 坐标 */
    private int topBarY;

    // 分类标签布局缓存（renderCategoryBar 写入，mouseClicked 读取）
    private int selectedCategoryIndex = 0;
    private final int[] tabX = new int[64];
    private final int[] tabW = new int[64];

    // 左侧列表滚动
    private int listScrollOffset = 0;
    private int maxListScroll = 0;
    private boolean isDraggingListScroll = false;
    private double dragListStartY = 0;
    private int dragListStartOffset = 0;

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
    public RoleIntroduceScreen() {
        super(Component.translatable("gui.roleintroduce.select_role.title"));
        availableRoles.addAll(Noellesroles.getAllRolesSorted(true));
    }

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
        if (selectedRole == null && !filteredItems.isEmpty())
            selectedRole = filteredItems.get(0);
        rebuildDetailLines();
        initCloseButton();
    }

    private void computeLayout() {
        usableWidth = Math.min((int) (width * USABLE_RATIO), MAX_USABLE_WIDTH);
        leftW = (int) (usableWidth * LEFT_RATIO);
        rightW = usableWidth - leftW;
        panelX = (width - usableWidth) / 2;
        panelY = 48;
        panelH = height - panelY - 42;
        leftX = panelX;
        rightX = panelX + leftW;
        // 顶部行紧贴 panelY 上方
        topBarY = panelY - TOP_BAR_H - 4;
    }

    private void initSearchBox() {
        // 搜索框仅占左侧面板宽度
        int sx = leftX;
        int sw = leftW - 2;

        if (searchWidget == null) {
            searchWidget = new EditBox(font, sx, topBarY, sw, TOP_BAR_H, Component.empty());
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
            searchWidget.setPosition(sx, topBarY);
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
        int btnW = 100, btnH = 18;
        closeButton = Button.builder(
                Component.translatable("gui.back").withStyle(ChatFormatting.WHITE),
                btn -> onClose())
                .bounds((width - btnW) / 2, panelY + panelH + 8, btnW, btnH)
                .build();
        addRenderableWidget(closeButton);
    }

    // ══════════════════════════════════════════════════════════════════
    // 数据
    // ══════════════════════════════════════════════════════════════════

    private void refreshFilter() {
        filteredItems.clear();
        RoleCategory cat = currentCategory();

        for (Role role : availableRoles) {
            if (!cat.filter.test(role))
                continue;
            String name = RoleUtils.getRoleName(role).getString();
            if (searchContent == null
                    || name.toLowerCase().contains(searchContent.toLowerCase())
                    || role.identifier().toString().contains(searchContent.toLowerCase()))
                filteredItems.add(role);
        }
        for (Modifier mod : HMLModifiers.MODIFIERS) {
            if (!cat.filter.test(mod))
                continue;
            String name = mod.getName().getString();
            if (searchContent == null
                    || name.toLowerCase().contains(searchContent.toLowerCase())
                    || mod.identifier().toString().contains(searchContent.toLowerCase()))
                filteredItems.add(mod);
        }
        int totalH = filteredItems.size() * (CARD_H + CARD_SPACING) - CARD_SPACING;
        maxListScroll = Math.max(0, totalH - listAreaH());
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
    }

    private RoleCategory currentCategory() {
        return (selectedCategoryIndex >= 0 && selectedCategoryIndex < CATEGORIES.size())
                ? CATEGORIES.get(selectedCategoryIndex)
                : CATEGORIES.get(0);
    }

    /** 左侧列表可用高度（面板全高，不再扣除分类栏） */
    private int listAreaH() {
        return panelH - PANEL_PAD * 2;
    }

    private void rebuildDetailLines() {
        detailLines.clear();
        if (selectedRole == null)
            return;

        int textW = rightW - PANEL_PAD * 2 - SCROLL_W - 4;

        if (selectedRole instanceof Role role) {
            detailLines.addAll(font.split(
                    Component.translatable(
                            "announcement.star.goals." + role.identifier().getPath()),
                    textW));
            detailLines.add(FormattedCharSequence.EMPTY);
        }

        detailLines.addAll(font.split(Component.empty()
                .append(Component.translatable("screen.roleintroduce.detail.name",
                        RoleUtils.getRoleOrModifierNameWithColor(selectedRole)))
                .withStyle(ChatFormatting.DARK_GRAY), textW));
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

        int totalTextH = detailLines.size() * (font.lineHeight + 2);
        maxDetailScroll = Math.max(0, totalTextH - detailContentH());
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
        // 渲染背景
        renderBackground(g, mouseX, mouseY, partialTick);
        // render要放在renderBackground后，其余前，否则会被遮挡。
        super.render(g, mouseX, mouseY, partialTick);
        // 其余代码
        renderLeftPanel(g, mouseX, mouseY);
        renderRightPanel(g, mouseX, mouseY);

        // ── 分类标签：紧接搜索框右侧，与搜索框同高同 Y ────────────
        int catBarX = rightX + TOP_BAR_GAP;
        int catBarW = rightW - TOP_BAR_GAP;
        renderCategoryBar(g, mouseX, mouseY, catBarX, topBarY, catBarW, TOP_BAR_H);

        // 顶部遮罩 + 标题
        g.fillGradient(0, 0, width, topBarY - 4, 0xBB000000, 0x00000000);
        g.drawCenteredString(font, this.title, width / 2, 8, 0xEEEEFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 左侧面板（纯列表，不含分类栏）
    // ══════════════════════════════════════════════════════════════════

    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, leftX, panelY, leftW, panelH);

        int areaX = leftX + PANEL_PAD;
        int areaY = panelY + PANEL_PAD;
        int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
        int areaH = listAreaH();

        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        for (int i = 0; i < filteredItems.size(); i++) {
            Object role = filteredItems.get(i);
            int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;
            if (cardY + CARD_H < areaY || cardY > areaY + areaH)
                continue;

            boolean hovered = isInRect(mouseX, mouseY, areaX, cardY, areaW, CARD_H);
            boolean selected = role.equals(selectedRole);

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
                    leftX + leftW / 2, areaY + areaH / 2, 0xFF5555);
        }

        int sbX = leftX + leftW - PANEL_PAD - SCROLL_W;
        int totalH = Math.max(1, filteredItems.size() * (CARD_H + CARD_SPACING));
        renderVScrollbar(g, sbX, areaY, areaH,
                listScrollOffset, maxListScroll, totalH,
                mouseX, mouseY, isDraggingListScroll);
    }

    // ══════════════════════════════════════════════════════════════════
    // 分类标签栏（搜索框右侧，与搜索框等高）
    // ══════════════════════════════════════════════════════════════════

    /**
     * @param barX 起始 X（= rightX + TOP_BAR_GAP）
     * @param barY 起始 Y（= topBarY，与搜索框顶对齐）
     * @param barW 可用宽度
     * @param barH 高度（= TOP_BAR_H，与搜索框等高）
     */
    private void renderCategoryBar(GuiGraphics g, int mouseX, int mouseY,
            int barX, int barY, int barW, int barH) {
        int n = CATEGORIES.size();

        // 计算自然宽度
        int[] naturalW = new int[n];
        int totalNatural = TAB_GAP * (n - 1);
        for (int i = 0; i < n; i++) {
            String label = Component.translatable(CATEGORIES.get(i).labelKey).getString();
            naturalW[i] = font.width(label) + 10; // 左右各 5px
            totalNatural += naturalW[i];
        }
        float scale = totalNatural > barW ? (float) barW / totalNatural : 1f;

        int curX = barX;
        for (int i = 0; i < n; i++) {
            int tw = (int) (naturalW[i] * scale);
            tabX[i] = curX;
            tabW[i] = tw;

            boolean active = (i == selectedCategoryIndex);
            boolean hovered = !active && isInRect(mouseX, mouseY, curX, barY, tw, barH);
            int baseColor = CATEGORIES.get(i).activeColor;

            if (active) {
                // 激活：彩色渐变背景 + 底部指示条
                g.fillGradient(curX, barY, curX + tw, barY + barH,
                        blendColors(0xFF0D1020, baseColor, 0.50f),
                        blendColors(0xFF0A0C18, baseColor, 0.28f));
                g.fill(curX, barY + barH - 2, curX + tw, barY + barH, baseColor);
                g.fill(curX, barY, curX + 1, barY + barH,
                        (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(curX + tw - 1, barY, curX + tw, barY + barH,
                        (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(curX + 1, barY, curX + tw - 1, barY + 1,
                        (baseColor & 0x00FFFFFF) | 0x55000000);
            } else if (hovered) {
                g.fillGradient(curX, barY, curX + tw, barY + barH,
                        blendColors(0xFF0D1020, baseColor, 0.22f),
                        blendColors(0xFF0A0C18, baseColor, 0.10f));
                g.fill(curX, barY + barH - 1, curX + tw, barY + barH,
                        (baseColor & 0x00FFFFFF) | 0x66000000);
                g.renderOutline(curX, barY, tw, barH,
                        (baseColor & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(curX, barY, curX + tw, barY + barH, 0x33111828);
                g.renderOutline(curX, barY, tw, barH, 0x33334466);
            }

            String label = Component.translatable(CATEGORIES.get(i).labelKey).getString();
            String truncated = font.plainSubstrByWidth(label, tw - 4);
            int textColor = active ? (baseColor | 0xFF000000)
                    : hovered ? 0xFFCCDDFF
                            : 0xFF7788AA;
            g.drawCenteredString(font, truncated,
                    curX + tw / 2,
                    barY + (barH - font.lineHeight) / 2,
                    textColor);

            curX += tw + TAB_GAP;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 单张卡片
    // ══════════════════════════════════════════════════════════════════

    private void renderListCard(GuiGraphics g, Object role,
            int x, int y, int w, int h,
            float hover, boolean selected) {
        int rawColor = RoleUtils.getRoleOrModifierColor(role);
        int roleColor = rawColor | 0xFF000000;

        int outerBorder = selected ? 0xFF6688EE
                : (hover > 0.05f ? blendColors(0xFF2A3060, 0xFF5566BB, hover) : 0xFF2A3060);
        g.fill(x, y, x + w, y + h, outerBorder);

        int bgL, bgR;
        if (selected) {
            bgL = 0xFF223380;
            bgR = 0xFF162060;
        } else if (hover > 0.05f) {
            bgL = blendColors(0xFF141828, 0xFF1E2E68, hover);
            bgR = blendColors(0xFF0E1020, 0xFF162050, hover);
        } else {
            bgL = 0xFF141828;
            bgR = 0xFF0E1020;
        }
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);

        g.fill(x + 1, y + 1, x + w - 1, y + 2,
                selected ? 0x44AABBFF : (hover > 0.05f ? 0x25FFFFFF : 0x10FFFFFF));

        int barW = 3;
        g.fill(x + 1, y + 1, x + 1 + barW, y + h - 1, roleColor);
        g.fillGradient(x + 1 + barW, y + 1, x + 1 + barW + 4, y + h - 1,
                (rawColor & 0x00FFFFFF) | 0x40000000, 0x00000000);

        int iconX = x + 1 + barW + 5;
        int iconY = y + (h - ICON_SIZE) / 2;
        g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE,
                blendColors(0xFF0A0C18, roleColor, 0.25f));
        boolean iconOk = false;
        try {
            g.blit(getTypeIcon(role), iconX, iconY, 0f, 0f,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            iconOk = true;
        } catch (Exception ignored) {
        }
        if (!iconOk) {
            g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE,
                    blendColors(0xFF111320, roleColor, 0.55f));
            String initial = RoleUtils.getRoleOrModifierName(role).getString();
            if (!initial.isEmpty())
                g.drawCenteredString(font,
                        Component.literal(String.valueOf(initial.charAt(0)).toUpperCase())
                                .withStyle(ChatFormatting.BOLD),
                        iconX + ICON_SIZE / 2,
                        iconY + (ICON_SIZE - font.lineHeight) / 2, 0xFFFFFF);
        }
        g.renderOutline(iconX, iconY, ICON_SIZE, ICON_SIZE,
                blendColors(roleColor, 0xFFFFFFFF, 0.3f));

        int textX = iconX + ICON_SIZE + 5;
        int textMaxW = w - (textX - x) - 6;

        g.drawString(font,
                font.plainSubstrByWidth(
                        RoleUtils.getRoleOrModifierTypeName(role).getString(), textMaxW),
                textX, y + 5,
                selected ? 0xFF88CCEE : blendColors(0xFF5577AA, 0xFF88BBDD, hover), false);

        int nameY = y + 5 + font.lineHeight + 1;
        List<FormattedCharSequence> nameLines = font.split(
                RoleUtils.getRoleOrModifierNameWithColor(role), textMaxW);
        if (!nameLines.isEmpty())
            g.drawString(font, nameLines.get(0), textX, nameY,
                    selected ? 0xFFFFDD88 : (hover > 0.3f ? 0xFFEEEEFF : 0xFFCCCCDD), selected);

        Component subText = getCardSubText(role);
        if (subText != null) {
            int subColor = 0xFFFFFFFF;
            var tc = subText.getStyle().getColor();
            if (tc != null)
                subColor = new java.awt.Color(tc.getValue()).getRGB();
            g.drawString(font,
                    font.plainSubstrByWidth(subText.getString(), textMaxW),
                    textX, nameY + font.lineHeight + 1, subColor, false);
        }

        if (selected) {
            int indX = x + w - 4;
            g.fill(indX, y + 3, indX + 3, y + h - 3,
                    blendColors(roleColor, 0xFFFFFFFF, 0.7f));
            g.renderOutline(x - 1, y - 1, w + 2, h + 2,
                    (rawColor & 0x00FFFFFF) | 0x55000000);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 右侧面板
    // ══════════════════════════════════════════════════════════════════

    private Component getCardSubText(Object role) {
        if (role instanceof Role r) {
            return switch (PlayerRoleWeightManager.getRoleType(r)) {
                case 0, 1 -> Component.translatable("display.type.role.innocent")
                        .withStyle(ChatFormatting.GREEN);
                case 2 -> Component.translatable("display.type.role.neutral")
                        .withStyle(ChatFormatting.YELLOW);
                case 3 -> Component.translatable("display.type.role.neutral_for_killer")
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                case 4 -> Component.translatable("display.type.role.killer")
                        .withStyle(ChatFormatting.RED);
                case 5 -> Component.translatable("display.type.role.vigilante")
                        .withStyle(ChatFormatting.AQUA);
                default -> Component.literal("UNKNOWN");
            };
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

        g.fillGradient(rightX + 1, panelY + 1, rightX + rightW / 2, panelY + BANNER_H,
                (rawColor & 0x00FFFFFF) | 0xCC000000, (rawColor & 0x00FFFFFF) | 0x44000000);
        g.fillGradient(rightX + rightW / 2, panelY + 1, rightX + rightW - 1, panelY + BANNER_H,
                (rawColor & 0x00FFFFFF) | 0x44000000, 0x00000000);

        int bIconSize = BANNER_H - 6;
        int bIconX = rightX + PANEL_PAD;
        int bIconY = panelY + 3;
        g.fill(bIconX, bIconY, bIconX + bIconSize, bIconY + bIconSize,
                blendColors(0xFF0A0C18, rawColor | 0xFF000000, 0.3f));
        try {
            g.blit(getTypeIcon(selectedRole), bIconX, bIconY, 0f, 0f,
                    bIconSize, bIconSize, bIconSize, bIconSize);
        } catch (Exception ignored) {
            String s = RoleUtils.getRoleOrModifierName(selectedRole).getString();
            if (!s.isEmpty())
                g.drawCenteredString(font,
                        Component.literal(String.valueOf(s.charAt(0)).toUpperCase())
                                .withStyle(ChatFormatting.BOLD),
                        bIconX + bIconSize / 2,
                        bIconY + (bIconSize - font.lineHeight) / 2, 0xFFFFFF);
        }
        g.renderOutline(bIconX, bIconY, bIconSize, bIconSize,
                (rawColor & 0x00FFFFFF) | 0xAA000000);

        g.drawString(font,
                Component.translatable("gui.roleintroduce.right.warp",
                        RoleUtils.getRoleOrModifierTypeName(selectedRole)
                                .withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA),
                        RoleUtils.getRoleOrModifierName(selectedRole)),
                bIconX + bIconSize + 5, panelY + (BANNER_H - font.lineHeight) / 2,
                0xFFFFFF, true);

        int textX0 = rightX + PANEL_PAD;
        int textY0 = panelY + BANNER_H + PANEL_PAD;
        int contentH = detailContentH();

        g.enableScissor(rightX + 1, textY0,
                rightX + rightW - SCROLL_W - 2, textY0 + contentH);

        int lineH = font.lineHeight + 2;
        int lineY = textY0 - detailScrollOffset;
        for (FormattedCharSequence line : detailLines) {
            if (lineY + lineH > textY0 && lineY < textY0 + contentH)
                g.drawString(font, line, textX0, lineY,
                        java.awt.Color.WHITE.getRGB(), false);
            lineY += lineH;
        }
        g.disableScissor();

        renderVScrollbar(g,
                rightX + rightW - PANEL_PAD - SCROLL_W, textY0, contentH,
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

    private void renderVScrollbar(GuiGraphics g, int x, int y, int h,
            int scrollOffset, int maxScroll, int totalContentH,
            int mouseX, int mouseY, boolean dragging) {
        g.fill(x, y, x + SCROLL_W, y + h, 0xFF111828);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x55334466);
        if (maxScroll <= 0)
            return;

        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hl = dragging || isInRect(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);

        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH,
                hl ? 0xFF8899CC : 0xFF556699);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1,
                hl ? 0xFFAABBEE : 0xFF7788BB);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // ── 分类标签点击（搜索框右侧）────────────────────────
            for (int i = 0; i < CATEGORIES.size(); i++) {
                if (tabW[i] > 0 && isInRect((int) mx, (int) my,
                        tabX[i], topBarY, tabW[i], TOP_BAR_H)) {
                    if (selectedCategoryIndex != i) {
                        selectedCategoryIndex = i;
                        listScrollOffset = 0;
                        refreshFilter();
                        if (selectedRole != null && !filteredItems.contains(selectedRole)) {
                            selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
                            rebuildDetailLines();
                        }
                    }
                    return true;
                }
            }

            // ── 卡片点击 ──────────────────────────────────────────
            int areaX = leftX + PANEL_PAD;
            int areaY = panelY + PANEL_PAD;
            int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
            int areaH = listAreaH();

            if (isInRect((int) mx, (int) my, areaX, areaY, areaW, areaH)) {
                for (int i = 0; i < filteredItems.size(); i++) {
                    int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;
                    if (isInRect((int) mx, (int) my, areaX, cardY, areaW, CARD_H)) {
                        selectedRole = filteredItems.get(i);
                        rebuildDetailLines();
                        return true;
                    }
                }
                return true;
            }

            // ── 左侧滚动条 ────────────────────────────────────────
            int lsbX = leftX + leftW - PANEL_PAD - SCROLL_W;
            if (isInRect((int) mx, (int) my, lsbX, areaY, SCROLL_W, areaH)
                    && maxListScroll > 0) {
                isDraggingListScroll = true;
                dragListStartY = my;
                dragListStartOffset = listScrollOffset;
                return true;
            }

            // ── 右侧滚动条 ────────────────────────────────────────
            int rsbX = rightX + rightW - PANEL_PAD - SCROLL_W;
            int textY0 = panelY + BANNER_H + PANEL_PAD;
            if (isInRect((int) mx, (int) my, rsbX, textY0, SCROLL_W, detailContentH())
                    && maxDetailScroll > 0) {
                isDraggingDetailScroll = true;
                dragDetailStartY = my;
                dragDetailStartOffset = detailScrollOffset;
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
            int thumbH = Math.max(SCROLL_MIN_THUMB,
                    (int) (areaH * Math.min(1f, (float) areaH / totalH)));
            double trackH = areaH - thumbH;
            if (trackH > 0)
                listScrollOffset = Mth.clamp(
                        (int) (dragListStartOffset + (my - dragListStartY) / trackH * maxListScroll),
                        0, maxListScroll);
            return true;
        }
        if (isDraggingDetailScroll && maxDetailScroll > 0) {
            int contentH = detailContentH();
            int totalTextH = Math.max(1, detailLines.size() * (font.lineHeight + 2));
            int thumbH = Math.max(SCROLL_MIN_THUMB,
                    (int) (contentH * Math.min(1f, (float) contentH / totalTextH)));
            double trackH = contentH - thumbH;
            if (trackH > 0)
                detailScrollOffset = Mth.clamp(
                        (int) (dragDetailStartOffset + (my - dragDetailStartY) / trackH * maxDetailScroll),
                        0, maxDetailScroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingListScroll = isDraggingDetailScroll = false;
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
        // ↑ ↓ 移动选中卡片
        if (keyCode == 265 || keyCode == 264) {
            int idx = filteredItems.indexOf(selectedRole) + (keyCode == 265 ? -1 : 1);
            idx = Mth.clamp(idx, 0, filteredItems.size() - 1);
            if (idx >= 0 && idx < filteredItems.size()) {
                selectedRole = filteredItems.get(idx);
                rebuildDetailLines();
                int cardY = idx * (CARD_H + CARD_SPACING);
                if (cardY < listScrollOffset)
                    listScrollOffset = cardY;
                else if (cardY + CARD_H > listScrollOffset + listAreaH())
                    listScrollOffset = cardY + CARD_H - listAreaH();
                listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
            }
            return true;
        }
        // ← → 切换分类
        if (keyCode == 263 || keyCode == 262) {
            int newIdx = Mth.clamp(
                    selectedCategoryIndex + (keyCode == 263 ? -1 : 1),
                    0, CATEGORIES.size() - 1);
            if (newIdx != selectedCategoryIndex) {
                selectedCategoryIndex = newIdx;
                listScrollOffset = 0;
                refreshFilter();
                if (selectedRole != null && !filteredItems.contains(selectedRole)) {
                    selectedRole = filteredItems.isEmpty() ? null : filteredItems.get(0);
                    rebuildDetailLines();
                }
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