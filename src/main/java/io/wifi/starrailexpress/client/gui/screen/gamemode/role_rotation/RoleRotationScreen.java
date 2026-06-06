package io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.network.RoleRotationSelectC2SPacket;
import io.wifi.starrailexpress.network.RoleRotationSyncS2CPacket;
import io.wifi.starrailexpress.SREConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 职业轮选模式GUI
 * 
 * 布局：
 * - 左侧一列：所有玩家的轮选序号状态（已选择职业的玩家显示职业名，未选择的显示"?"）
 * - 右侧：选职业区域（轮到玩家时显示3个候选职业+随机选项，未轮到时显示"等待中"）
 * - 右上角：当前轮到第几个玩家
 */
public class RoleRotationScreen extends Screen {

    // ==================== 布局常量 ====================
    private static final int PANEL_WIDTH = 700;
    private static final int PANEL_HEIGHT = 450;
    private static final int PANEL_PAD = 20;

    // 左侧玩家列表区域
    private static final int LEFT_PANEL_WIDTH = 280;
    private static final int LEFT_PANEL_X = 0;

    // 右侧选职业区域
    private static final int RIGHT_PANEL_WIDTH = PANEL_WIDTH - LEFT_PANEL_WIDTH;
    private static final int RIGHT_PANEL_X = LEFT_PANEL_WIDTH;

    // 玩家列表
    private static final int PLAYER_LIST_Y = 80;
    private static final int PLAYER_ITEM_HEIGHT = 30;
    private static final int PLAYER_ITEM_SPACING = 4;

    // 职业选择区域
    private static final int ROLE_SELECT_Y = 100;
    private static final int ROLE_CARD_WIDTH = 50;  // 缩小卡片宽度
    private static final int ROLE_CARD_HEIGHT = 100;  // 缩小卡片高度
    private static final int ROLE_CARD_SPACING = 5;  // 减小间距

    // 计时器
    private static final int TIMER_Y = 35;

    // ==================== 颜色 ====================
    private static final int COL_PANEL_BG = 0xE00C1828;
    private static final int COL_PANEL_BORDER = 0xFF152E4E;
    private static final int COL_TITLE = 0xFFD8EFFF;
    private static final int COL_TIMER_NORMAL = 0xFF1ABCCC;
    private static final int COL_TIMER_WARN = 0xFFFFAA33;
    private static final int COL_TIMER_URGENT = 0xFFFF5555;

    private static final int COL_CARD_BG = 0xFF0D2035;
    private static final int COL_CARD_HOVER = 0xFF183855;
    private static final int COL_CARD_SELECTED = 0xFF0C3A46;
    private static final int COL_CARD_BORDER = 0xFF18374F;
    private static final int COL_CARD_BORDER_HOVER = 0xFF287AAA;
    private static final int COL_CARD_BORDER_SELECTED = 0xFF1ABCCC;

    private static final int COL_TEXT_PRIMARY = 0xFFE0F4FF;
    private static final int COL_TEXT_NORMAL = 0xFFB0D0E8;
    private static final int COL_TEXT_MUTED = 0xFF4A7090;

    // ==================== 状态 ====================
    private int tickCounter = 0;
    private int playerListScroll = 0;
    private int maxPlayerListScroll = 0;

    // 当前鼠标悬停的卡片索引 (-1表示无)
    private int hoveredCardIndex = -1;

    private int panelX, panelY;
    private float renderScale = 1.0f; // 缩放比例，适配小屏幕

    public RoleRotationScreen() {
        super(Component.translatable("gui.sre.role_rotation.title").withStyle(ChatFormatting.GOLD));
    }

    @Override
    protected void init() {
        super.init();
        // 根据屏幕大小计算缩放比例，防止面板溢出屏幕
        float scaleX = (float) (width - 16) / PANEL_WIDTH;
        float scaleY = (float) (height - 16) / PANEL_HEIGHT;
        renderScale = Math.min(1.0f, Math.min(scaleX, scaleY));
        
        // 面板位置（未缩放坐标系，可允许负值，通过矩阵变换后正确居中）
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        calculateScroll();
    }

    private void calculateScroll() {
        int totalPlayers = RoleRotationCache.getTotalPlayers();
        int visibleHeight = PANEL_HEIGHT - PLAYER_LIST_Y - 60;
        int totalContent = totalPlayers * (PLAYER_ITEM_HEIGHT + PLAYER_ITEM_SPACING) - PLAYER_ITEM_SPACING;
        maxPlayerListScroll = Math.max(0, totalContent - visibleHeight);
        playerListScroll = Mth.clamp(playerListScroll, 0, maxPlayerListScroll);
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderBackground(g, mouseX, mouseY, partialTick);

        // 根据GUI缩放做缩放变换，防止面板溢出屏幕
        g.pose().pushPose();
        if (renderScale < 1.0f) {
            g.pose().translate(width / 2.0, height / 2.0, 0);
            g.pose().scale(renderScale, renderScale, 1.0f);
            g.pose().translate(-width / 2.0, -height / 2.0, 0);
        }

        // 绘制主面板
        drawPanel(g);

        // 绘制标题
        drawTitle(g);

        // 绘制计时器
        drawTimer(g);

        // 绘制当前轮到第几个玩家
        drawCurrentTurnIndicator(g);

        // 绘制左侧玩家列表（传入缩放后的鼠标坐标）
        int scaledMouseX = scaleMouseX(mouseX);
        int scaledMouseY = scaleMouseY(mouseY);
        drawPlayerList(g, scaledMouseX, scaledMouseY);

        // 绘制右侧职业选择区域
        drawRoleSelection(g, scaledMouseX, scaledMouseY);

        // 绘制提示
        drawHint(g);

        g.pose().popPose();
    }

    private void drawPanel(GuiGraphics g) {
        int x = panelX, y = panelY, w = PANEL_WIDTH, h = PANEL_HEIGHT;

        // 外阴影
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x28000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x40000814);

        // 边框
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_PANEL_BORDER);

        // 背景
        g.fillGradient(x, y, x + w, y + h, COL_PANEL_BG, 0xE0060D18);

        // 顶部高光线
        g.fill(x, y, x + w, y + 1, 0xFF2AAAD4);

        // 中间分隔线
        int dividerX = panelX + LEFT_PANEL_WIDTH;
        g.fill(dividerX, panelY + 10, dividerX + 1, panelY + PANEL_HEIGHT - 10, 0xFF1A3A58);
    }

    private void drawTitle(GuiGraphics g) {
        Component titleText = Component.translatable("gui.sre.role_rotation.title");
        g.drawCenteredString(font, titleText, width / 2, panelY + 12, COL_TITLE);
    }

    private void drawTimer(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        UUID myUuid = mc.player.getUUID();
        int remainingSeconds;
        int finalTimerColor;
        boolean isAdjustPhase = !RoleRotationCache.isSelecting();
        
        // 检查是否处于职业调整阶段（所有人都选完，等待确认倒计时）
        if (isAdjustPhase) {
            // 职业调整阶段，显示确认倒计时
            remainingSeconds = RoleRotationCache.getConfirmCountdown() / 20;
            finalTimerColor = (tickCounter % 20 < 10) ? 0xFFFFAA00 : 0xFFFF8800;
            
            // 显示职业调整阶段提示
            Component adjustPhase = Component.translatable("gui.sre.role_rotation.adjust_phase")
                    .withStyle(style -> style.withColor(0xFFFFAA00).withBold(true));
            int tw = font.width(adjustPhase) + 20;
            int tx = width / 2 - tw / 2;
            int ty = panelY + TIMER_Y - 20;
            g.fill(tx, ty, tx + tw, ty + 16, 0xFF060F1C);
            g.drawCenteredString(font, adjustPhase, width / 2, ty + 4, 0xFFFFAA00);
        } else if (!RoleRotationCache.isMyTurn(myUuid)) {
            // 不是自己的回合，不显示倒计时
            return;
        } else {
            // 轮到当前玩家，显示个人倒计时
            remainingSeconds = RoleRotationCache.getRemainingSeconds();
            int sec = Math.max(0, remainingSeconds);
            
            if (sec <= 10) {
                finalTimerColor = (tickCounter % 20 < 10) ? COL_TIMER_URGENT : COL_TIMER_URGENT - 0x300000;
            } else if (sec <= 30) {
                finalTimerColor = COL_TIMER_WARN;
            } else {
                finalTimerColor = COL_TIMER_NORMAL;
            }
            
            // 绘制个人倒计时
            drawCountdownTimer(g, remainingSeconds, finalTimerColor);
            return;
        }
        
        // 绘制职业调整阶段倒计时
        drawCountdownTimer(g, remainingSeconds, finalTimerColor);
    }
    
    private void drawCountdownTimer(GuiGraphics g, int remainingSeconds, int timerColor) {
        int sec = Math.max(0, remainingSeconds);
        String timeStr = String.format("%d:%02d", sec / 60, sec % 60);
        Component timerComp = Component.literal("⏱ " + timeStr).withStyle(style -> style.withColor(timerColor));

        int tw = font.width(timerComp) + 20;
        int tx = width / 2 - tw / 2;
        int ty = panelY + TIMER_Y;
        int badgeH = 16;

        g.fill(tx, ty, tx + tw, ty + badgeH, 0xFF060F1C);
        g.fill(tx, ty, tx + tw, ty + 1, timerColor & 0x00FFFFFF | 0x99000000);
        g.drawCenteredString(font, timerComp, width / 2, ty + 4, timerColor);
    }

    private void drawCurrentTurnIndicator(GuiGraphics g) {
        int currentIndex = RoleRotationCache.getCurrentIndex();
        int totalPlayers = RoleRotationCache.getTotalPlayers();
        int myIndex = RoleRotationCache.getMyRotationIndex();

        // 当前轮到第几个玩家
        Component indicator = Component.translatable("gui.sre.role_rotation.current_turn",
                currentIndex, totalPlayers)
                .withStyle(ChatFormatting.GOLD);

        int tx = panelX + PANEL_WIDTH - 130;
        int ty = panelY + 12;

        g.drawString(font, indicator, tx, ty, 0xFFFFFF);
        
        // 你是第几号玩家
        if (myIndex > 0) {
            Component yourIndex = Component.translatable("gui.sre.role_rotation.your_index", myIndex)
                    .withStyle(ChatFormatting.AQUA);
            g.drawString(font, yourIndex, tx, ty + 16, 0xFFFFFF);
        }
    }

    private void drawPlayerList(GuiGraphics g, int mouseX, int mouseY) {
        int listX = panelX + 15;
        int listY = panelY + PLAYER_LIST_Y;

        // 列表标题
        Component listTitle = Component.translatable("gui.sre.role_rotation.player_list")
                .withStyle(ChatFormatting.WHITE);
        g.drawString(font, listTitle, listX, listY - 20, COL_TEXT_NORMAL);

        // 滚动裁剪区域
        int clipHeight = PANEL_HEIGHT - PLAYER_LIST_Y - 60;
        g.enableScissor(listX - 5, listY, listX + LEFT_PANEL_WIDTH - 30, listY + clipHeight);

        // 获取排序后的玩家列表（按序号从小到大排序）
        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(RoleRotationCache.getRotationOrder().entrySet());
        sortedEntries.sort(Comparator.comparingInt(Map.Entry::getValue));

        // 绘制玩家条目
        int drawY = listY - playerListScroll;

        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<UUID, Integer> entry = sortedEntries.get(i);
            UUID playerUuid = entry.getKey();
            int playerIndex = entry.getValue();
            String selectedRolePath = RoleRotationCache.getSelectedRoles().get(playerUuid);
            boolean isCurrentTurn = playerIndex == RoleRotationCache.getCurrentIndex();

            int itemY = drawY + i * (PLAYER_ITEM_HEIGHT + PLAYER_ITEM_SPACING);

            // 检查是否在可见区域内
            if (itemY + PLAYER_ITEM_HEIGHT < listY || itemY > listY + clipHeight) {
                continue;
            }

            // 绘制背景
            int bgColor = isCurrentTurn ? 0x401A8A4A : 0x300A1520;
            g.fill(listX, itemY, listX + LEFT_PANEL_WIDTH - 40, itemY + PLAYER_ITEM_HEIGHT, bgColor);

            // 绘制边框
            if (isCurrentTurn) {
                g.fill(listX, itemY, listX + 2, itemY + PLAYER_ITEM_HEIGHT, 0xFF1ABCCC);
            }

            // 序号
            String indexStr = "#" + playerIndex;
            g.drawString(font, indexStr, listX + 5, itemY + 10, isCurrentTurn ? 0xFFFFFF : COL_TEXT_MUTED);

            // 已选职业或等待中（使用翻译后的职业名）
            String roleText;
            int roleColor;
            if (selectedRolePath != null) {
                // 鹅鸭杀轮抽模式：玩家选择随机时不展示职业
                if (SREConfig.instance().hideRandomRoleInRoleRotation
                        && RoleRotationCache.getRandomChoosers().contains(playerUuid)) {
                    roleText = Component.translatable("gui.sre.role_rotation.random").getString();
                    roleColor = 0xFFFFAA33;
                } else {
                    SRERole role = getRoleByPath(selectedRolePath);
                    if (role != null) {
                        roleText = RoleUtils.getRoleName(role).getString();
                    } else {
                        roleText = selectedRolePath;
                    }
                    roleColor = 0xFF1ABCCC;
                }
            } else {
                roleText = "?";
                roleColor = COL_TEXT_MUTED;
            }
            Component roleComp = Component.literal(roleText).withStyle(style -> style.withColor(roleColor));
            g.drawString(font, roleComp, listX + LEFT_PANEL_WIDTH - 80, itemY + 10, roleColor);
        }

        g.disableScissor();

        // 绘制滚动条
        if (maxPlayerListScroll > 0) {
            drawScrollbar(g, listX + LEFT_PANEL_WIDTH - 25, listY, clipHeight);
        }
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int sh) {
        int thumbH = Math.max(20, (int) (sh * 0.2));
        int thumbY = sy + (int) ((sh - thumbH) * ((double) playerListScroll / maxPlayerListScroll));

        g.fill(sx, sy, sx + 5, sy + sh, 0xFF0A1825);
        g.fillGradient(sx, thumbY, sx + 5, thumbY + thumbH, 0xFF3A7AAA, 0xFF2A5A80);
    }

    private void drawRoleSelection(GuiGraphics g, int mouseX, int mouseY) {
        int selectX = panelX + LEFT_PANEL_WIDTH + 20;
        int selectY = panelY + ROLE_SELECT_Y;

        // 标题
        Component title = Component.translatable("gui.sre.role_rotation.select_title")
                .withStyle(ChatFormatting.WHITE);
        g.drawString(font, title, selectX + 80, selectY - 25, COL_TEXT_NORMAL);

        // 检查是否是当前玩家
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        UUID myUuid = mc.player.getUUID();
        boolean isMyTurn = RoleRotationCache.isMyTurn(myUuid);
        boolean hasSelected = RoleRotationCache.getSelectedRoles().containsKey(myUuid);

        if (hasSelected) {
            // 已选择职业，显示已选职业（使用翻译后的名字）
            String rolePath = RoleRotationCache.getSelectedRoles().get(myUuid);
            String roleName;
            if (rolePath != null) {
                SRERole role = getRoleByPath(rolePath);
                roleName = (role != null) ? RoleUtils.getRoleName(role).getString() : rolePath;
            } else {
                roleName = "?";
            }
            Component selectedComp = Component.translatable("gui.sre.role_rotation.you_selected", roleName)
                    .withStyle(ChatFormatting.GREEN);
            g.drawCenteredString(font, selectedComp, panelX + LEFT_PANEL_WIDTH + RIGHT_PANEL_WIDTH / 2, selectY + 60,
                    0xFF1ABCCC);

            // 提示可以按U查看职业介绍
            Component hint = Component.translatable("gui.sre.role_rotation.press_u_hint")
                    .withStyle(ChatFormatting.GRAY);
            g.drawCenteredString(font, hint, panelX + LEFT_PANEL_WIDTH + RIGHT_PANEL_WIDTH / 2, selectY + 85,
                    COL_TEXT_MUTED);
        } else if (isMyTurn) {
            // 轮到当前玩家，显示候选职业
            drawRoleCards(g, selectX, selectY, mouseX, mouseY);
        } else {
            // 未轮到，等待中
            Component waiting = Component.translatable("gui.sre.role_rotation.waiting")
                    .withStyle(ChatFormatting.YELLOW);
            g.drawCenteredString(font, waiting, panelX + LEFT_PANEL_WIDTH + RIGHT_PANEL_WIDTH / 2, selectY + 60,
                    COL_TEXT_MUTED);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {

        return false;
    }

    private void drawRoleCards(GuiGraphics g, int startX, int startY, int mouseX, int mouseY) {
        List<String> candidates = RoleRotationCache.getCurrentCandidates();
        if (candidates.isEmpty()) return;

        // 计算4个卡片 + 3个间距的总宽度
        int totalWidth = 4 * ROLE_CARD_WIDTH + 3 * ROLE_CARD_SPACING;
        int cardStartX = startX + (RIGHT_PANEL_WIDTH - totalWidth) / 2 - 10;

        hoveredCardIndex = -1;

        for (int i = 0; i < 4; i++) {
            int cardX = cardStartX + i * (ROLE_CARD_WIDTH + ROLE_CARD_SPACING);
            int cardY = startY;

            boolean isHovered = mouseX >= cardX && mouseX < cardX + ROLE_CARD_WIDTH &&
                    mouseY >= cardY && mouseY < cardY + ROLE_CARD_HEIGHT;

            if (isHovered) {
                hoveredCardIndex = i;
            }

            // 绘制卡片
            drawRoleCard(g, cardX, cardY, i, candidates, isHovered);
        }
    }

    private void drawRoleCard(GuiGraphics g, int x, int y, int index, List<String> candidates, boolean isHovered) {
        // 背景
        int bgColor = isHovered ? COL_CARD_HOVER : COL_CARD_BG;
        g.fill(x, y, x + ROLE_CARD_WIDTH, y + ROLE_CARD_HEIGHT, bgColor);

        // 边框
        int borderColor = isHovered ? COL_CARD_BORDER_HOVER : COL_CARD_BORDER;
        g.renderOutline(x, y, ROLE_CARD_WIDTH, ROLE_CARD_HEIGHT, borderColor);

        // 顶部装饰
        g.fill(x + 1, y + 1, x + ROLE_CARD_WIDTH - 1, y + 4, 0xFF2AAAD4);

        if (index < 3 && index < candidates.size()) {
            // 前三个：候选职业
            String rolePath = candidates.get(index);
            SRERole role = getRoleByPath(rolePath);

            if (role != null) {
                // 职业颜色背景
                g.fill(x + 10, y + 20, x + ROLE_CARD_WIDTH - 10, y + 80, role.getColor() & 0x00FFFFFF | 0x40000000);

                // 职业名
                Component roleName = RoleUtils.getRoleName(role);
                List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(roleName,
                        ROLE_CARD_WIDTH - 20);
                int nameY = y + 30;
                for (net.minecraft.util.FormattedCharSequence line : wrapped) {
                    g.drawString(font, line, x + 10, nameY, 0xFFFFFF);
                    nameY += font.lineHeight;
                }

                // 职业阵营
                Component faction = getRoleFactionText(role);
                g.drawString(font, faction, x + 10, y + 90, 0xFFB0D0E8);

                // 选择提示
                Component selectHint = Component.translatable("gui.sre.role_rotation.click_to_select")
                        .withStyle(ChatFormatting.GREEN);
                g.drawCenteredString(font, selectHint, x + ROLE_CARD_WIDTH / 2, y + 120, 0xFF22CC6A);
            }
        } else if (index == 3) {
            // 第四个：随机
            g.fill(x + 10, y + 20, x + ROLE_CARD_WIDTH - 10, y + 80, 0x40FFAA33);

            Component randomText = Component.translatable("gui.sre.role_rotation.random")
                    .withStyle(ChatFormatting.GOLD);
            g.drawCenteredString(font, randomText, x + ROLE_CARD_WIDTH / 2, y + 45, 0xFFFFAA33);

            Component randomIcon = Component.literal("?");
            g.drawCenteredString(font, randomIcon, x + ROLE_CARD_WIDTH / 2, y + 65, 0xFFFFAA33);

            Component selectHint = Component.translatable("gui.sre.role_rotation.click_to_random")
                    .withStyle(ChatFormatting.YELLOW);
            g.drawCenteredString(font, selectHint, x + ROLE_CARD_WIDTH / 2, y + 120, 0xFFFFAA33);
        }

        // 悬停效果
        if (isHovered) {
            g.renderOutline(x - 1, y - 1, ROLE_CARD_WIDTH + 2, ROLE_CARD_HEIGHT + 2, 0xFF4AAFDF);
        }
    }

    private Component getRoleFactionText(SRERole role) {
        if (role.isInnocent()) {
            return Component.translatable("display.type.role.innocent").withStyle(style -> style.withColor(0xFF44BB66));
        } else if (role.canUseKiller()) {
            return Component.translatable("display.type.role.killer").withStyle(style -> style.withColor(0xFFCC2233));
        } else if (role.isNeutralForKiller()) {
            return Component.translatable("display.type.role.neutral_for_killer_2").withStyle(style -> style.withColor(0xFFAA44CC));
        } else if (role.isNeutrals()) {
            return Component.translatable("display.type.role.neutral_special").withStyle(style -> style.withColor(0xFFCCAA22));
        } else if (role.isVigilanteTeam()) {
            return Component.translatable("display.type.role.vigilante").withStyle(style -> style.withColor(0xFF22BBCC));
        }
        return Component.literal("Unknown");
    }

    private void drawHint(GuiGraphics g) {
        Component hint = Component.translatable("gui.sre.role_rotation.scroll_hint")
                .withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, hint, width / 2, panelY + PANEL_HEIGHT - 15, COL_TEXT_MUTED);
    }

    /**
     * 将屏幕鼠标坐标转换为缩放前的逻辑坐标
     */
    private int scaleMouseX(double screenX) {
        if (renderScale >= 1.0f) return (int) screenX;
        return (int) (width / 2.0 + (screenX - width / 2.0) / renderScale);
    }
    
    private int scaleMouseY(double screenY) {
        if (renderScale >= 1.0f) return (int) screenY;
        return (int) (height / 2.0 + (screenY - height / 2.0) / renderScale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键
            // 检查是否点击了职业卡片
            if (hoveredCardIndex >= 0) {
                selectRole(hoveredCardIndex);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectRole(int choiceIndex) {
        ClientPlayNetworking.send(new RoleRotationSelectC2SPacket(choiceIndex));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double sx = scaleMouseX(mouseX);
        double sy = scaleMouseY(mouseY);
        if (maxPlayerListScroll > 0) {
            int listX = panelX + 15;
            int listY = panelY + PLAYER_LIST_Y;
            int clipHeight = PANEL_HEIGHT - PLAYER_LIST_Y - 60;

            // 检查鼠标是否在玩家列表区域
            if (sx >= listX && sx < listX + LEFT_PANEL_WIDTH - 40 &&
                    sy >= listY && sy < listY + clipHeight) {
                playerListScroll = Mth.clamp(playerListScroll - (int) scrollY * (PLAYER_ITEM_HEIGHT + PLAYER_ITEM_SPACING),
                        0, maxPlayerListScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 按N键重新打开GUI
        if (keyCode == 78) { // N键
            // 已经在当前界面，不需要处理
            return true;
        }

        // 按U键查看职业介绍
        if (keyCode == 85) { // U键
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                UUID myUuid = mc.player.getUUID();
                String rolePath = RoleRotationCache.getSelectedRoles().get(myUuid);
                if (rolePath != null) {
                    SRERole role = getRoleByPath(rolePath);
                    if (role != null) {
                        // 打开职业介绍界面，parent设置为this以便按ESC时返回轮选页面
                        net.minecraft.client.gui.screens.Screen roleScreen = new org.agmas.noellesroles.client.screen.RoleIntroduceScreen(this, role);
                        mc.setScreen(roleScreen);
                    }
                }
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void updateData() {
        calculateScroll();
    }

    // ==================== 工具方法 ====================

    private String getPlayerName(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            var playerInfo = mc.getConnection().getPlayerInfo(uuid);
            if (playerInfo != null) {
                return playerInfo.getProfile().getName();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    private SRERole getRoleByPath(String path) {
        if (path == null) return null;
        ResourceLocation id = ResourceLocation.parse(path);
        return TMMRoles.ROLES.get(id);
    }
}
