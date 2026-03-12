package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleIntroduceScreen extends Screen {

    // ─── 布局常量 ───────────────────────────────────────────────────
    private static final int MAX_USABLE_WIDTH  = 700;
    private static final float USABLE_RATIO    = 0.9f;
    private static final float LEFT_RATIO      = 0.30f;

    // ─── 左侧卡片常量 ─────────────────────────────────────────────────
    private static final int CARD_WIDTH        = 86;
    private static final int CARD_HEIGHT       = 110;
    private static final int CARD_SPACING      = 10;
    private static final int H_SCROLL_H        = 6;   // 横向滚动条高度
    private static final int PANEL_PADDING     = 8;

    // ─── 右侧滚动条常量 ───────────────────────────────────────────────
    private static final int V_SCROLL_W        = 6;
    private static final int SCROLL_MIN_THUMB  = 20;

    // ─── 数据 ──────────────────────────────────────────────────────
    private final List<Role>   availableRoles  = new ArrayList<>();
    private final List<Object> filteredItems   = new ArrayList<>();

    // ─── 布局（init时计算） ────────────────────────────────────────────
    private int usableWidth, leftW, rightW;
    private int panelX, panelY, panelH;
    private int leftX, rightX;

    // ─── 左侧卡片横向滚动 ─────────────────────────────────────────────
    /** 横向像素偏移（滚动位置） */
    private int   cardScrollOffset = 0;
    /** 最大可滚动量 */
    private int   maxCardScroll    = 0;
    /** 拖动滚动条时的起始信息 */
    private boolean isDraggingCardScroll = false;
    private double  dragCardStartMouseX  = 0;
    private int     dragCardStartOffset  = 0;
    /** 每个卡片的悬停动画进度 0.0~1.0 */
    private final Map<Object, Float> hoverAnims = new HashMap<>();

    // ─── 右侧详情面板 ─────────────────────────────────────────────────
    private Object selectedRole = null;
    /** 预包行后的文本行列表 */
    private final List<FormattedCharSequence> detailLines = new ArrayList<>();
    private int detailScrollOffset = 0;
    private int maxDetailScroll    = 0;
    private boolean isDraggingDetailScroll = false;
    private double  dragDetailStartMouseY  = 0;
    private int     dragDetailStartOffset  = 0;
    /** "查看详情"按钮 */
    private Button viewDetailButton = null;

    // ─── 搜索 ──────────────────────────────────────────────────────
    private EditBox searchWidget   = null;
    private String  searchContent  = null;

    // ══════════════════════════════════════════════════════════════════
    //  构造
    // ══════════════════════════════════════════════════════════════════

    public RoleIntroduceScreen(Player player) {
        super(Component.translatable("gui.roleintroduce.select_role.title"));
        availableRoles.addAll(Noellesroles.getAllRolesSorted(true));
    }

    // ══════════════════════════════════════════════════════════════════
    //  初始化
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        computeLayout();
        initSearchBox();
        refreshFilter();
        // 默认选中第一个
        if (selectedRole == null && !filteredItems.isEmpty()) {
            selectedRole = filteredItems.get(0);
        }
        rebuildDetailLines();
        initViewDetailButton();
    }

    /** 根据当前屏幕尺寸计算所有布局量 */
    private void computeLayout() {
        usableWidth = Math.min((int)(width  * USABLE_RATIO), MAX_USABLE_WIDTH);
        leftW       = (int)(usableWidth * LEFT_RATIO);
        rightW      = usableWidth - leftW;
        panelX      = (width  - usableWidth) / 2;
        panelY      = 48;                              // 标题 + 搜索框下方
        panelH      = height - panelY - 18;
        leftX       = panelX;
        rightX      = panelX + leftW;
    }

    private void initSearchBox() {
        int searchW = leftW - 2;
        int searchX = leftX + 1;
        int searchY = panelY - 22;

        if (searchWidget == null) {
            searchWidget = new EditBox(font, searchX, searchY, searchW, 18, Component.empty());
            searchWidget.setHint(
                    Component.translatable("screen.noellesroles.search.placeholder")
                              .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                searchContent = text.isEmpty() ? null : text;
                cardScrollOffset = 0;
                refreshFilter();
                // 搜索后自动选首项
                if (!filteredItems.isEmpty()) {
                    selectedRole = filteredItems.get(0);
                } else {
                    selectedRole = null;
                }
                rebuildDetailLines();
                initViewDetailButton();
            });
        } else {
            searchWidget.setPosition(searchX, searchY);
            searchWidget.setWidth(searchW);
            removeWidget(searchWidget);
        }
        addRenderableWidget(searchWidget);

        // 搜索框颜色
        boolean noResult = filteredItems.isEmpty() && searchContent != null && !searchContent.isEmpty();
        searchWidget.setTextColor(noResult ? 0xFFAA2222 : 0xFFFFFFFF);
    }

    private void initViewDetailButton() {
        if (viewDetailButton != null) {
            removeWidget(viewDetailButton);
            viewDetailButton = null;
        }
        if (selectedRole == null) return;

        int btnW = Math.min(rightW - 20, 160);
        int btnH = 20;
        int btnX = rightX + (rightW - btnW) / 2;
        int btnY = panelY + panelH - btnH - PANEL_PADDING;

        viewDetailButton = Button.builder(
                Component.translatable("screen.roleintroduce.view_detail").withStyle(ChatFormatting.GOLD),
                btn -> openDetailScreen()
        ).bounds(btnX, btnY, btnW, btnH).build();
        addRenderableWidget(viewDetailButton);
    }

    // ══════════════════════════════════════════════════════════════════
    //  数据过滤 & 详情构建
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

        // 更新最大横向滚动
        int cardAreaW = leftW - PANEL_PADDING * 2;
        int totalW = filteredItems.size() * (CARD_WIDTH + CARD_SPACING) - CARD_SPACING;
        maxCardScroll = Math.max(0, totalW - cardAreaW);
        cardScrollOffset = Mth.clamp(cardScrollOffset, 0, maxCardScroll);
    }

    /** 将选中角色的详情文字包行，缓存到 detailLines */
    private void rebuildDetailLines() {
        detailLines.clear();

        if (selectedRole == null) return;

        int textW = rightW - PANEL_PADDING * 2 - V_SCROLL_W - 4;

        // ── 类型标签 ──
        Component typeLabel = Component.translatable("screen.roleintroduce.detail.type")
                .withStyle(ChatFormatting.DARK_GRAY);
        Component typeName = RoleUtils.getRoleOrModifierTypeName(selectedRole)
                .copy().withStyle(ChatFormatting.AQUA);
        detailLines.addAll(font.split(
                Component.empty().append(typeLabel).append(" ").append(typeName), textW));

        detailLines.add(FormattedCharSequence.EMPTY);

        // ── 名称 ──
        Component nameLabel = Component.translatable("screen.roleintroduce.detail.name")
                .withStyle(ChatFormatting.DARK_GRAY);
        Component roleName = RoleUtils.getRoleOrModifierNameWithColor(selectedRole);
        detailLines.addAll(font.split(
                Component.empty().append(nameLabel).append(" ").append(roleName), textW));

        detailLines.add(FormattedCharSequence.EMPTY);

        // ── 描述分割线 ──
        detailLines.addAll(font.split(
                Component.translatable("screen.roleintroduce.detail.description")
                         .withStyle(ChatFormatting.YELLOW), textW));

        // 细线用重复横线模拟
        StringBuilder line = new StringBuilder();
        int dashCount = textW / font.width("-");
        for (int i = 0; i < dashCount; i++) line.append("-");
        detailLines.addAll(font.split(
                Component.literal(line.toString()).withStyle(ChatFormatting.DARK_GRAY), textW));

        // ── 描述正文 ──
        Component desc = RoleUtils.getRoleOrModifierDescription(selectedRole)
                .copy().withStyle(ChatFormatting.WHITE);
        detailLines.addAll(font.split(desc, textW));

        // 计算最大滚动量
        int visibleH = detailContentH();
        int totalTextH = detailLines.size() * (font.lineHeight + 2);
        maxDetailScroll = Math.max(0, totalTextH - visibleH);
        detailScrollOffset = 0;
    }

    /** 详情文本区域的可视高度（留出按钮空间） */
    private int detailContentH() {
        // 底部保留按钮 + padding
        return panelH - PANEL_PADDING * 2 - 24;
    }

    // ══════════════════════════════════════════════════════════════════
    //  渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        renderLeftPanel(g, mouseX, mouseY, partialTick);
        renderRightPanel(g, mouseX, mouseY);

        // 顶部标题条
        g.fillGradient(0, 0, width, panelY - 22, 0xCC000000, 0x00000000);
        g.drawCenteredString(font, this.title, width / 2, 8, 0xFFFFFF);

        // 底部提示
        Component hint = Component.translatable("screen.roleintroduce.hint")
                .withStyle(ChatFormatting.DARK_GRAY);
        g.drawCenteredString(font, hint, width / 2, height - 14, 0x666666);

        // 渲染子组件（搜索框、按钮等）
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ─── 左侧面板 ──────────────────────────────────────────────────

    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 面板背景
        drawPanel(g, leftX, panelY, leftW, panelH);

        int cardAreaX  = leftX  + PANEL_PADDING;
        int cardAreaY  = panelY + PANEL_PADDING;
        int cardAreaW  = leftW  - PANEL_PADDING * 2;
        // 留出底部滚动条空间
        int cardAreaH  = panelH - PANEL_PADDING * 2 - H_SCROLL_H - 6;
        // 卡片垂直居中
        int cardY      = cardAreaY + (cardAreaH - CARD_HEIGHT) / 2;

        // ── 用 enableScissor 裁剪卡片区域 ──
        g.enableScissor(cardAreaX, cardAreaY,
                        cardAreaX + cardAreaW, cardAreaY + cardAreaH);

        for (int i = 0; i < filteredItems.size(); i++) {
            Object role  = filteredItems.get(i);
            int cardX = cardAreaX + i * (CARD_WIDTH + CARD_SPACING) - cardScrollOffset;

            // 不在视口就跳过
            if (cardX + CARD_WIDTH < cardAreaX || cardX > cardAreaX + cardAreaW) continue;

            boolean hovered = isInRect(mouseX, mouseY, cardX, cardY, CARD_WIDTH, CARD_HEIGHT);
            boolean selected = role.equals(selectedRole);

            // 更新悬停动画
            float anim = hoverAnims.getOrDefault(role, 0f);
            anim = Mth.lerp(0.25f, anim, hovered ? 1f : 0f);
            hoverAnims.put(role, anim);

            renderCard(g, role, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, anim, selected);
        }

        g.disableScissor();

        // 搜索结果为空时提示
        if (filteredItems.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.search.empty")
                             .withStyle(ChatFormatting.RED),
                    leftX + leftW / 2, panelY + panelH / 2, 0xFFFFFF);
        }

        // 横向滚动条
        int sbY = panelY + panelH - PANEL_PADDING - H_SCROLL_H;
        renderHScrollbar(g, cardAreaX, sbY, cardAreaW, mouseX, mouseY);
    }

    /** 渲染单张卡片 */
    private void renderCard(GuiGraphics g, Object role,
                            int x, int y, int w, int h,
                            float hover, boolean selected) {
        int roleColor = RoleUtils.getRoleOrModifierColor(role);

        // 悬停/选中时小幅放大
        if (hover > 0.01f || selected) {
            float scale = 1f + (selected ? 0.05f : hover * 0.06f);
            int dw = (int)((w * scale) - w);
            int dh = (int)((h * scale) - h);
            x -= dw / 2; y -= dh / 2; w += dw; h += dh;
        }

        // 黑色描边
        g.fill(x, y, x + w, y + h, 0xFF000000);
        // 主背景
        int bgTop = selected ? 0xFF283060 : blendColors(0xFF1E2040, 0xFF2D3580, hover);
        int bgBot = selected ? 0xFF1A204A : blendColors(0xFF161828, 0xFF232A60, hover);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgTop, bgBot);

        // 顶部颜色条
        g.fill(x + 1, y + 1, x + w - 1, y + 5, roleColor | 0xFF000000);

        // 图标区域
        int iconSize = w * 5 / 12;
        int iconX    = x + (w - iconSize) / 2;
        int iconY    = y + 12;
        g.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1,
               (roleColor & 0xFFFFFF) | 0x40000000);
        g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize,
               (roleColor & 0xFFFFFF) | 0x80000000);
        g.renderOutline(iconX, iconY, iconSize, iconSize, (roleColor & 0xFFFFFF) | 0xCC000000);

        // 首字母
        String initial = RoleUtils.getRoleOrModifierName(role).getString();
        if (!initial.isEmpty()) {
            initial = String.valueOf(initial.charAt(0)).toUpperCase();
        }
        g.drawCenteredString(font, Component.literal(initial).withStyle(ChatFormatting.BOLD),
                x + w / 2, iconY + iconSize / 2 - font.lineHeight / 2, 0xFFFFFF);

        // 角色名称（包行，底部居中）
        Component nameComp = RoleUtils.getRoleOrModifierNameWithColor(role);
        List<FormattedCharSequence> nameLines = font.split(nameComp, w - 6);
        int maxLines = 2;
        int nameAreaY = iconY + iconSize + 5;
        int nameAreaH = y + h - 4 - nameAreaY;
        int totalNameH = Math.min(nameLines.size(), maxLines) * font.lineHeight;
        int lineY = nameAreaY + (nameAreaH - totalNameH) / 2;
        for (int li = 0; li < Math.min(nameLines.size(), maxLines); li++) {
            FormattedCharSequence ln = nameLines.get(li);
            g.drawString(font, ln, x + (w - font.width(ln)) / 2, lineY, 0xFFFFFF, true);
            lineY += font.lineHeight;
        }

        // 边框
        int borderColor;
        if (selected) {
            borderColor = 0xFFAABBFF;
        } else {
            borderColor = blendColors(0xFF334466, 0xFF7788CC, hover);
        }
        g.renderOutline(x, y, w, h, borderColor);

        // 选中发光
        if (selected) {
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x888ABAFF);
            g.renderOutline(x - 2, y - 2, w + 4, h + 4, 0x405585EE);
        }
    }

    /** 渲染横向滚动条 */
    private void renderHScrollbar(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        // 轨道
        g.fill(x, y, x + w, y + H_SCROLL_H, 0x33FFFFFF);
        if (maxCardScroll <= 0) return;

        int totalW   = filteredItems.size() * (CARD_WIDTH + CARD_SPACING);
        float ratio  = Math.min(1f, (float) w / totalW);
        int thumbW   = Math.max(SCROLL_MIN_THUMB, (int)(w * ratio));
        int thumbX   = x + (int)((w - thumbW) * ((float) cardScrollOffset / maxCardScroll));

        boolean hovered = isInRect(mouseX, mouseY, thumbX, y, thumbW, H_SCROLL_H)
                          || isDraggingCardScroll;
        g.fill(thumbX, y, thumbX + thumbW, y + H_SCROLL_H,
               hovered ? 0xDDCCDDFF : 0x88AABBFF);
    }

    // ─── 右侧面板 ──────────────────────────────────────────────────

    private void renderRightPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanel(g, rightX, panelY, rightW, panelH);

        if (selectedRole == null) {
            g.drawCenteredString(font,
                    Component.translatable("screen.roleintroduce.select_hint")
                             .withStyle(ChatFormatting.GRAY),
                    rightX + rightW / 2, panelY + panelH / 2, 0x888888);
            return;
        }

        // ── 彩色顶部横幅 ──
        int roleColor = RoleUtils.getRoleOrModifierColor(selectedRole);
        int bannerH   = 28;
        g.fillGradient(rightX + 1, panelY + 1,
                       rightX + rightW - 1, panelY + bannerH,
                       (roleColor & 0xFFFFFF) | 0xAA000000,
                       0x00000000);

        // 横幅上显示类型标签
        Component typeTag = Component.empty()
                .append("[ ")
                .append(RoleUtils.getRoleOrModifierTypeName(selectedRole)
                                 .copy().withStyle(ChatFormatting.BOLD))
                .append(" ]");
        g.drawCenteredString(font, typeTag,
                rightX + rightW / 2, panelY + (bannerH - font.lineHeight) / 2 + 1,
                0xFFFFFF);

        // ── 文本区域 ──
        int textX      = rightX + PANEL_PADDING;
        int textY      = panelY + bannerH + PANEL_PADDING;
        int textW      = rightW - PANEL_PADDING * 2 - V_SCROLL_W - 4;
        int contentH   = detailContentH();
        int scrollBarX = rightX + rightW - PANEL_PADDING - V_SCROLL_W;

        // 裁剪文本
        g.enableScissor(rightX + 1, textY, rightX + rightW - V_SCROLL_W - 4, textY + contentH);

        int lineH = font.lineHeight + 2;
        int lineY = textY - detailScrollOffset;
        for (FormattedCharSequence line : detailLines) {
            if (lineY + lineH >= textY && lineY <= textY + contentH) {
                g.drawString(font, line, textX, lineY, 0xFFFFFF, false);
            }
            lineY += lineH;
        }

        g.disableScissor();

        // ── 纵向滚动条 ──
        renderVScrollbar(g, scrollBarX, textY, contentH, mouseX, mouseY);

        // ── 按钮上方细线分隔 ──
        int sepY = panelY + panelH - 28;
        g.fill(rightX + PANEL_PADDING, sepY, rightX + rightW - PANEL_PADDING, sepY + 1, 0x44FFFFFF);
    }

    /** 渲染纵向滚动条 */
    private void renderVScrollbar(GuiGraphics g, int x, int y, int h, int mouseX, int mouseY) {
        // 轨道
        g.fill(x, y, x + V_SCROLL_W, y + h, 0x33FFFFFF);
        if (maxDetailScroll <= 0) return;

        int totalTextH = detailLines.size() * (font.lineHeight + 2);
        float ratio    = Math.min(1f, (float) h / totalTextH);
        int thumbH     = Math.max(SCROLL_MIN_THUMB, (int)(h * ratio));
        int thumbY     = y + (int)((h - thumbH) * ((float) detailScrollOffset / maxDetailScroll));

        boolean hovered = isInRect(mouseX, mouseY, x, thumbY, V_SCROLL_W, thumbH)
                          || isDraggingDetailScroll;
        g.fill(x, thumbY, x + V_SCROLL_W, thumbY + thumbH,
               hovered ? 0xDDCCDDFF : 0x88AABBFF);
    }

    // ─── 通用面板背景 ──────────────────────────────────────────────

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        // 填充
        g.fillGradient(x, y, x + w, y + h, 0xCC0D1020, 0xCC141828);
        // 边框
        g.renderOutline(x, y, w, h, 0xFF2A3860);
        // 内侧高光（顶部一条亮线）
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    // ─── 背景 ──────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 深蓝渐变底色
        g.fillGradient(0, 0, width, height, 0xFF0E1420, 0xFF0A0F1C);

        // 星点动画
        if (minecraft != null && minecraft.level != null) {
            long t = minecraft.level.getGameTime();
            for (int i = 0; i < 24; i++) {
                float sx = (float)((t * 0.4 + i * 61.8) % width);
                float sy = (float)((Math.sin(t * 0.015 + i * 1.2) * 30 + height * 0.5 + i * 9.7) % height);
                int sz = (int)(1.5f + Math.sin(t * 0.08 + i) * 0.8f);
                int alpha = (int)(80 + 140 * ((Math.sin(t * 0.04 + i) + 1) * 0.5));
                g.fill((int)sx, (int)sy, (int)sx + sz, (int)sy + sz, (alpha << 24) | 0xCCDDFF);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  鼠标事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {

            // ── 点击左侧卡片 ──
            int cardAreaX = leftX + PANEL_PADDING;
            int cardAreaY = panelY + PANEL_PADDING;
            int cardAreaW = leftW - PANEL_PADDING * 2;
            int cardAreaH = panelH - PANEL_PADDING * 2 - H_SCROLL_H - 6;
            int cardY     = cardAreaY + (cardAreaH - CARD_HEIGHT) / 2;

            if (isInRect((int)mouseX, (int)mouseY, cardAreaX, cardAreaY, cardAreaW, cardAreaH)) {
                for (int i = 0; i < filteredItems.size(); i++) {
                    int cardX = cardAreaX + i * (CARD_WIDTH + CARD_SPACING) - cardScrollOffset;
                    if (isInRect((int)mouseX, (int)mouseY, cardX, cardY, CARD_WIDTH, CARD_HEIGHT)) {
                        Object clicked = filteredItems.get(i);
                        if (clicked.equals(selectedRole)) {
                            // 双击同一张牌 → 直接进入详情
                            openDetailScreen();
                        } else {
                            selectedRole = clicked;
                            rebuildDetailLines();
                            initViewDetailButton();
                        }
                        return true;
                    }
                }
            }

            // ── 拖动横向滚动条 ──
            int sbY = panelY + panelH - PANEL_PADDING - H_SCROLL_H;
            if (isInRect((int)mouseX, (int)mouseY, cardAreaX, sbY, cardAreaW, H_SCROLL_H)) {
                isDraggingCardScroll = true;
                dragCardStartMouseX  = mouseX;
                dragCardStartOffset  = cardScrollOffset;
                return true;
            }

            // ── 拖动纵向滚动条 ──
            int vsbX   = rightX + rightW - PANEL_PADDING - V_SCROLL_W;
            int textY  = panelY + 28 + PANEL_PADDING;
            int vsbH   = detailContentH();
            if (isInRect((int)mouseX, (int)mouseY, vsbX, textY, V_SCROLL_W, vsbH)) {
                isDraggingDetailScroll = true;
                dragDetailStartMouseY  = mouseY;
                dragDetailStartOffset  = detailScrollOffset;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (isDraggingCardScroll && maxCardScroll > 0) {
            int cardAreaW = leftW - PANEL_PADDING * 2;
            int totalW    = filteredItems.size() * (CARD_WIDTH + CARD_SPACING);
            double scrollRatio = (double) maxCardScroll / (cardAreaW);
            // thumb 占比
            float thumbRatio = Math.min(1f, (float) cardAreaW / totalW);
            int   thumbW     = Math.max(SCROLL_MIN_THUMB, (int)(cardAreaW * thumbRatio));
            double trackW    = cardAreaW - thumbW;
            double delta     = (mouseX - dragCardStartMouseX) / trackW * maxCardScroll;
            cardScrollOffset = Mth.clamp((int)(dragCardStartOffset + delta), 0, maxCardScroll);
            return true;
        }

        if (isDraggingDetailScroll && maxDetailScroll > 0) {
            int contentH  = detailContentH();
            int totalTextH = detailLines.size() * (font.lineHeight + 2);
            float thumbRatio = Math.min(1f, (float) contentH / totalTextH);
            int   thumbH    = Math.max(SCROLL_MIN_THUMB, (int)(contentH * thumbRatio));
            double trackH   = contentH - thumbH;
            double delta    = (mouseY - dragDetailStartMouseY) / trackH * maxDetailScroll;
            detailScrollOffset = Mth.clamp((int)(dragDetailStartOffset + delta), 0, maxDetailScroll);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingCardScroll   = false;
        isDraggingDetailScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 左侧区域 → 横向滚动
        if (mouseX >= leftX && mouseX <= leftX + leftW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            cardScrollOffset = Mth.clamp(
                    (int)(cardScrollOffset - scrollY * (CARD_WIDTH / 2.0)),
                    0, maxCardScroll);
            return true;
        }
        // 右侧区域 → 纵向滚动
        if (mouseX >= rightX && mouseX <= rightX + rightW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            detailScrollOffset = Mth.clamp(
                    (int)(detailScrollOffset - scrollY * (font.lineHeight + 2) * 3),
                    0, maxDetailScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ══════════════════════════════════════════════════════════════════
    //  键盘事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Left / Right → 切换选中卡片
        if (keyCode == 263 || keyCode == 262) {
            int idx = filteredItems.indexOf(selectedRole);
            idx = keyCode == 263 ? idx - 1 : idx + 1;
            idx = Mth.clamp(idx, 0, filteredItems.size() - 1);
            if (idx >= 0 && idx < filteredItems.size()) {
                selectedRole = filteredItems.get(idx);
                rebuildDetailLines();
                initViewDetailButton();
                // 让选中卡片尽量可见
                int cardAreaW = leftW - PANEL_PADDING * 2;
                int cardX = idx * (CARD_WIDTH + CARD_SPACING);
                if (cardX < cardScrollOffset) {
                    cardScrollOffset = cardX;
                } else if (cardX + CARD_WIDTH > cardScrollOffset + cardAreaW) {
                    cardScrollOffset = cardX + CARD_WIDTH - cardAreaW;
                }
                cardScrollOffset = Mth.clamp(cardScrollOffset, 0, maxCardScroll);
            }
            return true;
        }
        // Enter → 查看详情
        if ((keyCode == 257 || keyCode == 335) && selectedRole != null) {
            openDetailScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════════════════════════
    //  跳转详情页
    // ══════════════════════════════════════════════════════════════════

    private void openDetailScreen() {
        if (minecraft == null || minecraft.player == null || selectedRole == null) return;
        minecraft.setScreen(new RoleIntroduceDetailScreen(selectedRole, this));
    }

    // ══════════════════════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════════════════════

    /** 判断点 (px,py) 是否在矩形 [x, y, x+w, y+h] 内 */
    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** 线性混合两个 ARGB 颜色（忽略 alpha，结果 alpha=0xFF） */
    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int r = (int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int)(((c1 >>  8) & 0xFF) + (((c2 >>  8) & 0xFF) - ((c1 >>  8) & 0xFF)) * t);
        int b = (int)(( c1        & 0xFF) + (( c2        & 0xFF) - ( c1        & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}