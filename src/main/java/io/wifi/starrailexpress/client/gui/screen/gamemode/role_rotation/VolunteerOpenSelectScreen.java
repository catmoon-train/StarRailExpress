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

package io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import io.wifi.starrailexpress.content.vote.client.VolunteerOpenCache;
import io.wifi.starrailexpress.network.packet.RoleRotationConfirmC2SPacket;
import io.wifi.starrailexpress.network.packet.VolunteerOpenSelectC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 志愿海选模式界面。沿用职业轮选模式的版式（左侧玩家序号列表 + 右侧功能区）：
 *
 * <ul>
 * <li>一阶段：右侧是「可搜索的职业列表」（沿用记录员笔记的可搜索职业页），点一个职业即提交志愿；</li>
 * <li>二阶段：右侧上 40% 为海选池（未揭晓的显示 ???），下 60% 为选中职业的名称与介绍；</li>
 * <li>确认阶段：与轮选模式一样按底部按钮确认。</li>
 * </ul>
 */
public class VolunteerOpenSelectScreen extends Screen {

    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int MIN_LEFT_W = 180;
    private static final int MAX_LEFT_W = 280;
    private static final int PLAYER_ROW_H = 28;
    private static final int DETAIL_LINE_H = 11;
    private static final int AUTO_TOGGLE_H = 20;
    private static final int CONFIRM_W = 220;
    private static final int CONFIRM_H = 24;
    /** 志愿阶段：职业列表与职业信息之间那句「蓝框」提示所占的高度（最多两行）。 */
    private static final int VOL_HINT_H = 20;

    private static final int GOLD = SREPanelStyle.GOLD;
    private static final int TEXT = SREPanelStyle.TEXT;
    private static final int MUTED = SREPanelStyle.MUTED;
    private static final int BLUE = SREPanelStyle.BLUE;
    private static final int GREEN = SREPanelStyle.GREEN;
    private static final int RED = SREPanelStyle.RED;
    private static final int BORDER = SREPanelStyle.BORDER;
    private static final int CARD_BORDER = SREPanelStyle.CARD_BORDER;
    /** 卡牌额外揭示的职业（加粗橙色）。 */
    private static final int CARD_REVEAL = 0xFFFFA033;
    /** 好人方中立（与好人一同胜利的中立）阵营颜色，与轮选界面保持一致。 */
    private static final int NEUTRAL_FOR_INNOCENT = 0xFF00AA00;

    // ==================== 布局 ====================
    private int leftX, leftY, leftW, panelH;
    private int rightX, rightY, rightW;

    private int searchX, searchY, searchW, searchH;
    private int volListX, volListY, volListW, volListH;
    private int volHintY; // 列表与详情之间那句提示的顶边
    private int volDetailX, volDetailY, volDetailW, volDetailH;
    private int volCols, volCellW, volCellH;

    private int poolX, poolY, poolW, poolH;
    private int detailX, detailY, detailW, detailH;
    private int gridX, gridY, poolCellW, poolCellH, cellGap;
    private int hoveredPoolIndex = -1;

    private int autoToggleX, autoToggleY, autoToggleW;
    private int confirmX, confirmY;

    // ==================== 状态 ====================
    private int tickCounter;
    private int hoveredPlayerIndex = -1;
    private int hoveredRoleIndex = -1;

    private int volScroll;
    private int maxVolScroll;
    private int playerListScroll;
    private int maxPlayerListScroll;

    private boolean scrolling;
    private double scrollBarClickOffset;

    private int lastAutoScrollRow = -1;
    private int autoScrollTarget;
    private boolean autoScrolling;

    private EditBox searchBox;
    private String searchText = "";
    private final List<SRERole> filteredRoles = new ArrayList<>();
    private int lastPhase = -1;

    public VolunteerOpenSelectScreen() {
        super(Component.translatable("gui.sre.volunteer_open.title").withStyle(ChatFormatting.GOLD));
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        super.init();
        searchBox = null;
        computeLayout();
        recalcPlayerScroll();
        lastPhase = VolunteerOpenCache.getPhase();
        if (lastPhase == VolunteerOpenCache.PHASE_VOLUNTEER) {
            refreshRoleList();
            ensureSearchBox();
        }
    }

    // 注：「界面已就绪」的上报放在 SREClient 的客户端 tick 里做。
    // 那里的判断基于「开局运镜是否还在播」，不受界面是否刚刚被创建影响，
    // 能避免「界面刚创建、运镜同刻才开始」这种一帧的竞争。

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        VolunteerOpenCache.tickTimers();
        int phase = VolunteerOpenCache.getPhase();
        if (phase != lastPhase) {
            lastPhase = phase;
            volScroll = 0;
            if (phase == VolunteerOpenCache.PHASE_VOLUNTEER) {
                refreshRoleList();
                ensureSearchBox();
            } else {
                removeSearchBox();
            }
        }
        updateAutoScroll();

    }

    private void ensureSearchBox() {
        if (searchBox != null) {
            return;
        }
        searchBox = new EditBox(font, searchX, searchY, searchW, searchH, Component.empty());
        searchBox.setHint(SREPanelStyle.hint(
                Component.translatable("screen.noellesroles.search.placeholder")));
        searchBox.setMaxLength(32);
        searchBox.setResponder(text -> {
            searchText = text == null ? "" : text;
            refreshRoleList();
        });
        addRenderableWidget(searchBox);
    }

    private void removeSearchBox() {
        if (searchBox != null) {
            removeWidget(searchBox);
            searchBox = null;
            searchText = "";
        }
    }

    private void refreshRoleList() {
        filteredRoles.clear();
        List<SRERole> all = new ArrayList<>(Noellesroles.getAllRolesSorted(false));
        all.removeIf(r -> r == null || r.identifier().equals(ModRoles.MERCENARY_ID)
                || r instanceof net.exmo.sre.repair.role.RepairRole);
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        for (SRERole role : all) {
            if (query.isEmpty()) {
                filteredRoles.add(role);
                continue;
            }
            String name = RoleUtils.getRoleName(role).getString();
            String id = role.identifier().toString();
            if (name.toLowerCase(Locale.ROOT).contains(query)
                    || id.toLowerCase(Locale.ROOT).contains(query)
                    || PinYinUtils.contains(query, name)) {
                filteredRoles.add(role);
            }
        }
        volScroll = 0;
    }

    // ==================== 布局 ====================

    private void computeLayout() {
        leftX = PAD;
        leftY = PAD + 22;
        panelH = Math.max(120, height - leftY - PAD);
        leftW = Mth.clamp((int) (width * 0.26F), MIN_LEFT_W, MAX_LEFT_W);
        rightX = leftX + leftW + GAP;
        rightY = leftY;
        rightW = Math.max(160, width - rightX - PAD);

        int bodyBottom = rightY + panelH - PAD;

        // 一阶段：搜索框 + 职业列表（上 40%）+ 职业介绍（下 60%），与海选池的分配一致
        searchX = rightX + PAD;
        searchY = rightY + PAD + 14;
        searchW = Math.max(60, rightW - PAD * 2);
        searchH = 18;
        volListX = searchX;
        volListY = searchY + searchH + GAP;
        volListW = searchW;
        int volAvailable = Math.max(60, bodyBottom - volListY - GAP - VOL_HINT_H);
        volListH = Math.max(36, (int) (volAvailable * 0.40F));
        volDetailX = volListX;
        volDetailW = volListW;
        volHintY = volListY + volListH + 4;
        volDetailY = volHintY + VOL_HINT_H;
        volDetailH = Math.max(40, bodyBottom - volDetailY);
        volCols = Mth.clamp(volListW / 108, 2, 8);
        volCellW = Math.max(24, (volListW - 4 * (volCols - 1)) / volCols);
        volCellH = 22;

        // 二阶段：海选池（上 40%）+ 职业介绍（下 60%）
        poolX = rightX + PAD;
        poolY = rightY + PAD + 24;
        poolW = Math.max(60, rightW - PAD * 2);
        int available = Math.max(60, bodyBottom - poolY - GAP);
        poolH = Math.max(40, (int) (available * 0.40F));
        detailX = poolX;
        detailY = poolY + poolH + GAP;
        detailW = poolW;
        detailH = Math.max(40, bodyBottom - detailY);

        // 底部控件
        confirmX = (width - CONFIRM_W) / 2;
        confirmY = height - CONFIRM_H - 16;
        autoToggleX = leftX + PAD;
        autoToggleY = leftY + panelH - AUTO_TOGGLE_H - 10;
        autoToggleW = Math.max(120, leftW - PAD * 2);

        if (searchBox != null) {
            searchBox.setX(searchX);
            searchBox.setY(searchY);
            searchBox.setWidth(searchW);
        }
    }

    private void computePoolGrid() {
        int rows = Math.max(1, VolunteerOpenCache.getPoolRows());
        int cols = Math.max(1, VolunteerOpenCache.getPoolCols());
        cellGap = 3;
        poolCellW = Math.max(14, (poolW - cellGap * (cols - 1)) / cols);
        poolCellH = Mth.clamp((poolH - cellGap * (rows - 1)) / rows, 14, 32);
        int gridW = cols * poolCellW + cellGap * (cols - 1);
        int gridH = rows * poolCellH + cellGap * (rows - 1);
        gridX = poolX + Math.max(0, (poolW - gridW) / 2);
        gridY = poolY + Math.max(0, (poolH - gridH) / 2);
    }

    private int getPlayerListHeight() {
        return Math.max(48, panelH - 78);
    }

    private void recalcPlayerScroll() {
        int listH = getPlayerListHeight();
        int totalContent = VolunteerOpenCache.getTotalPlayers() * PLAYER_ROW_H;
        maxPlayerListScroll = Math.max(0, totalContent - listH);
        playerListScroll = Mth.clamp(playerListScroll, 0, maxPlayerListScroll);
    }

    // ==================== 渲染 ====================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, SREPanelStyle.SCREEN_BG_TOP, SREPanelStyle.SCREEN_BG_BOTTOM);
        g.fillGradient(0, 0, width, 34, 0xAA000000, 0x22000000);
        SREPanelStyle.drawPanel(g, leftX, leftY, leftW, panelH);
        SREPanelStyle.drawPanel(g, rightX, rightY, rightW, panelH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        computeLayout();
        recalcPlayerScroll();
        hoveredPoolIndex = -1;
        hoveredRoleIndex = -1;
        hoveredPlayerIndex = -1;

        drawTitleBar(g);
        drawPlayerList(g, mouseX, mouseY);
        if (VolunteerOpenCache.getPhase() == VolunteerOpenCache.PHASE_VOLUNTEER) {
            drawVolunteerPhase(g, mouseX, mouseY);
        } else {
            drawOpenPhase(g, mouseX, mouseY);
        }
        drawAutoScrollToggle(g, mouseX, mouseY);
        drawConfirmButton(g, mouseX, mouseY);
        drawFooter(g);
        renderPlayerRoleTooltip(g, mouseX, mouseY);
        renderOverlayMessageOnScreen(g, mouseX, mouseY, partialTick);
    }

    private void renderOverlayMessageOnScreen(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (SREClient.areaComponent != null && SREClient.areaComponent.mapDisplayName != null) {
            var mapInfo = Component
                    .translatable("message.tip.map_name", Component.translatable(SREClient.areaComponent.mapDisplayName))
                    .withStyle(ChatFormatting.WHITE);
            g.drawString(font, mapInfo, width - 10 - font.width(mapInfo), 21, 0xffffffff);
        }
        var message = minecraft.gui.overlayMessageString;
        int displaytime = minecraft.gui.overlayMessageTime;
        if (message == null || displaytime <= 0)
            return;
        float f = (float) displaytime - delta;
        int i = (int) (f * 255.0F / 20.0F);
        if (i > 255) {
            i = 255;
        }
        if (i > 8) {
            int j;
            if (minecraft.gui.animateOverlayMessageColor) {
                j = Mth.hsvToArgb(f / 50.0F, 0.7F, 0.6F, i);
            } else {
                j = ARGB32.color(i, -1);
            }
            int twidth = font.width(message);
            g.drawStringWithBackdrop(font, message, (width - twidth) / 2, 5, twidth, j);
        }
    }

    private void drawTitleBar(GuiGraphics g) {
        g.drawString(font, title, PAD, 10, GOLD, false);
        int phase = VolunteerOpenCache.getPhase();
        Component right;
        if (phase == VolunteerOpenCache.PHASE_VOLUNTEER) {
            right = Component.translatable("gui.sre.volunteer_open.phase_volunteer");
        } else if (phase == VolunteerOpenCache.PHASE_OPEN) {
            right = Component.translatable("gui.sre.volunteer_open.phase_open",
                    Math.max(1, VolunteerOpenCache.getCurrentGroup()),
                    Math.max(1, VolunteerOpenCache.getTotalGroups()));
        } else {
            right = Component.translatable("gui.sre.volunteer_open.phase_confirm");
        }
        g.drawString(font, right, width - PAD - font.width(right), 10, TEXT, false);
    }

    // ==================== 左侧玩家列表 ====================

    private List<Map.Entry<UUID, Integer>> getSortedPlayers() {
        List<Map.Entry<UUID, Integer>> players = new ArrayList<>(VolunteerOpenCache.getRotationOrder().entrySet());
        players.sort(Comparator.comparingInt(Map.Entry::getValue));
        return players;
    }

    private int scrollBarX() {
        return leftX + leftW - PAD - 4;
    }

    private int scrollBarY() {
        return leftY + PAD + 28;
    }

    private int scrollBarH() {
        return getPlayerListHeight();
    }

    private int thumbHeight() {
        int h = scrollBarH();
        int content = Math.max(h, VolunteerOpenCache.getTotalPlayers() * PLAYER_ROW_H);
        return Math.max(18, h * h / content);
    }

    private int thumbTopY() {
        int h = scrollBarH();
        int thumb = thumbHeight();
        return scrollBarY() + (int) ((h - thumb) * (playerListScroll / (double) Math.max(1, maxPlayerListScroll)));
    }

    private void drawPlayerList(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftX + PAD;
        int y = leftY + PAD + 28;
        int w = leftW - PAD * 2;
        int h = getPlayerListHeight();

        Component listTitle = Component.translatable("gui.sre.volunteer_open.player_list");
        g.drawString(font, listTitle, x, leftY + PAD, TEXT, false);

        List<Map.Entry<UUID, Integer>> players = getSortedPlayers();
        Set<UUID> groupMembers = VolunteerOpenCache.getCurrentGroupMembers();

        int hoverZoneW = maxPlayerListScroll > 0 ? w - 6 : w;
        g.enableScissor(x, y, x + w, y + h);
        int drawY = y - playerListScroll;
        for (int i = 0; i < players.size(); i++) {
            Map.Entry<UUID, Integer> entry = players.get(i);
            UUID uuid = entry.getKey();
            int rowY = drawY + i * PLAYER_ROW_H;
            if (rowY + PLAYER_ROW_H < y || rowY > y + h) {
                continue;
            }
            boolean rowHover = inside(mouseX, mouseY, x, rowY, hoverZoneW, PLAYER_ROW_H);
            if (rowHover) {
                hoveredPlayerIndex = i;
            }
            boolean inGroup = groupMembers.contains(uuid);
            int bg = inGroup ? 0x552A5A42 : 0x331A1008;
            int bgBottom = 0x33120A04;
            if (rowHover) {
                bg = 0x553A2E12;
                bgBottom = 0x44302010;
            }
            g.fillGradient(x, rowY, x + w, rowY + PLAYER_ROW_H - 3, bg, bgBottom);
            g.renderOutline(x, rowY, w, PLAYER_ROW_H - 3, rowHover ? GOLD : (inGroup ? GREEN : 0x665A4530));
            if (rowHover) {
                g.fill(x, rowY + 1, x + 3, rowY + PLAYER_ROW_H - 3, GOLD);
            }

            String index = "#" + entry.getValue();
            g.drawString(font, index, x + 6, rowY + 8, rowHover ? GOLD : (inGroup ? GREEN : MUTED), false);
            if (VolunteerOpenCache.getConfirmedPlayers().contains(uuid)) {
                g.drawString(font, "✔", x + 32, rowY + 8, GREEN, false);
            }

            Component roleName = pickedRoleText(uuid);
            int roleColor = roleName.getStyle().getColor() != null ? roleName.getStyle().getColor().getValue() : BLUE;
            if (rowHover && getConcreteRoleAtRow(i) != null) {
                roleColor = GOLD;
            }
            g.drawString(font, trim(roleName.getString(), Math.max(30, w - 104)), x + w - 58, rowY + 8, roleColor,
                    false);
        }
        g.disableScissor();

        if (maxPlayerListScroll > 0) {
            int barX = scrollBarX();
            int barY = scrollBarY();
            int barH = scrollBarH();
            int thumbH = thumbHeight();
            int thumbY = thumbTopY();
            g.fill(barX, barY, barX + 3, barY + barH, 0x661A1008);
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, GOLD);
        }
    }

    private Component pickedRoleText(UUID uuid) {
        Map<UUID, String> map = VolunteerOpenCache.getPickedRoles();
        if (!map.containsKey(uuid)) {
            return Component.literal("?").withStyle(ChatFormatting.DARK_GRAY);
        }
        String rolePath = map.get(uuid);
        if (rolePath == null || rolePath.isEmpty()) {
            return Component.translatable("gui.sre.volunteer_open.random").withStyle(ChatFormatting.GOLD);
        }
        SRERole role = getRoleByPath(rolePath);
        if (role == null) {
            return Component.literal(rolePath).withStyle(ChatFormatting.AQUA);
        }
        if (role.isHiddenForRoleRotation()) {
            return Component.translatable("gui.sre.volunteer_open.random").withStyle(ChatFormatting.GOLD);
        }
        return RoleUtils.getRoleName(role).withStyle(style -> style.withColor(factionColor(role)));
    }

    private SRERole getConcreteRoleAtRow(int row) {
        List<Map.Entry<UUID, Integer>> players = getSortedPlayers();
        if (row < 0 || row >= players.size()) {
            return null;
        }
        UUID uuid = players.get(row).getKey();
        String rolePath = VolunteerOpenCache.getPickedRoles().get(uuid);
        if (rolePath == null || rolePath.isEmpty()) {
            return null;
        }
        SRERole role = getRoleByPath(rolePath);
        if (role == null || role.isHiddenForRoleRotation()) {
            return null;
        }
        return role;
    }

    private void renderPlayerRoleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (scrolling || hoveredPlayerIndex < 0) {
            return;
        }
        SRERole role = getConcreteRoleAtRow(hoveredPlayerIndex);
        if (role == null) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(RoleUtils.getRoleName(role).copy()
                .withStyle(style -> style.withBold(true).withColor(role.getColor() | 0xFF000000))
                .getVisualOrderText());
        lines.addAll(font.split(RoleUtils.getRoleSimpleDescription(role), 200));
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    // ==================== 一阶段：志愿职业 ====================

    private void drawVolunteerPhase(GuiGraphics g, int mouseX, int mouseY) {
        Component header = Component.translatable("gui.sre.volunteer_open.volunteer_header");
        g.drawString(font, header, searchX, rightY + PAD, TEXT, false);

        drawTimer(g);

        boolean submitted = !VolunteerOpenCache.getMyVolunteerRoleId().isEmpty();
        String volunteerId = VolunteerOpenCache.getMyVolunteerRoleId();

        // 职业格子
        int rowGap = 4;
        int rows = (filteredRoles.size() + volCols - 1) / volCols;
        int contentH = rows * (volCellH + rowGap);
        maxVolScroll = Math.max(0, contentH - volListH);
        volScroll = Mth.clamp(volScroll, 0, maxVolScroll);

        g.enableScissor(volListX, volListY, volListX + volListW, volListY + volListH);
        int drawY = volListY - volScroll;
        for (int i = 0; i < filteredRoles.size(); i++) {
            int col = i % volCols;
            int row = i / volCols;
            int x = volListX + col * (volCellW + 4);
            int y = drawY + row * (volCellH + rowGap);
            if (y + volCellH < volListY || y > volListY + volListH) {
                continue;
            }
            SRERole role = filteredRoles.get(i);
            boolean isMine = !volunteerId.isEmpty() && volunteerId.equals(role.identifier().toString());
            boolean hover = inside(mouseX, mouseY, x, y, volCellW, volCellH)
                    && mouseY >= volListY && mouseY < volListY + volListH;
            if (hover) {
                hoveredRoleIndex = i;
            }
            int border = isMine ? GOLD : (hover ? GOLD : CARD_BORDER);
            g.fillGradient(x, y, x + volCellW, y + volCellH, hover ? 0xFF2B2112 : 0xFF1A1008,
                    hover ? 0xFF112536 : 0xFF0B1722);
            g.renderOutline(x, y, volCellW, volCellH, border);
            int color = isMine ? GOLD : factionColor(role);
            g.drawCenteredString(font, trim(RoleUtils.getRoleName(role).getString(), volCellW - 8),
                    x + volCellW / 2, y + 7, color);
            if (isMine) {
                g.drawString(font, "✔", x + 2, y + 7, GOLD, false);
            }
        }
        g.disableScissor();

        if (maxVolScroll > 0) {
            int barX = volListX + volListW - 3;
            int thumbH = Math.max(18, volListH * volListH / Math.max(volListH, contentH));
            int thumbY = volListY + (int) ((volListH - thumbH)
                    * (volScroll / (double) Math.max(1, maxVolScroll)));
            g.fill(barX, volListY, barX + 3, volListY + volListH, 0x661A1008);
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, GOLD);
        }

        // 列表与详情之间：蓝框标记的说明（最多两行）
        Component blueHint = Component.translatable("gui.sre.volunteer_open.volunteer_blue_hint");
        List<FormattedCharSequence> blueHintLines = font.split(blueHint, Math.max(40, volListW));
        for (int i = 0; i < blueHintLines.size() && i < 2; i++) {
            g.drawString(font, blueHintLines.get(i), volListX, volHintY + i * 10, BLUE, false);
        }

        // 下方：介绍
        SREPanelStyle.drawPanel(g, volDetailX, volDetailY, volDetailW, volDetailH, 0xAA1A1008, 0xAA0B1722);
        SRERole shown = null;
        if (hoveredRoleIndex >= 0 && hoveredRoleIndex < filteredRoles.size()) {
            shown = filteredRoles.get(hoveredRoleIndex);
        } else if (!volunteerId.isEmpty()) {
            shown = getRoleByPath(volunteerId);
        }
        drawRoleDetail(g, shown, volDetailX, volDetailY, volDetailW, volDetailH);

        if (submitted) {
            Component done = Component.translatable("gui.sre.volunteer_open.volunteer_done")
                    .withStyle(ChatFormatting.GREEN);
            int doneX = searchX + font.width(header) + GAP;
            g.drawString(font, done, doneX, rightY + PAD, GREEN, false);
        }
    }

    // ==================== 二阶段：海选池 ====================

    private void drawOpenPhase(GuiGraphics g, int mouseX, int mouseY) {
        Component header = Component.translatable("gui.sre.volunteer_open.pool_header");
        g.drawString(font, header, poolX, rightY + PAD, TEXT, false);
        drawTimer(g);

        computePoolGrid();
        int rows = VolunteerOpenCache.getPoolRows();
        int cols = VolunteerOpenCache.getPoolCols();
        int size = VolunteerOpenCache.getPoolSize();

        for (int index = 0; index < size; index++) {
            int row = index / cols;
            int col = index % cols;
            if (row >= rows) {
                break;
            }
            int x = gridX + col * (poolCellW + cellGap);
            int y = gridY + row * (poolCellH + cellGap);
            boolean hover = inside(mouseX, mouseY, x, y, poolCellW, poolCellH);
            if (hover) {
                hoveredPoolIndex = index;
            }
            drawPoolCell(g, index, x, y, poolCellW, poolCellH, hover);
        }

        // 底部：介绍
        SREPanelStyle.drawPanel(g, detailX, detailY, detailW, detailH, 0xAA1A1008, 0xAA0B1722);
        int shownIndex = hoveredPoolIndex >= 0 ? hoveredPoolIndex : VolunteerOpenCache.getMyPickIndex();
        drawRoleDetail(g, roleAtPoolIndex(shownIndex), detailX, detailY, detailW, detailH);
    }

    private void drawPoolCell(GuiGraphics g, int index, int x, int y, int w, int h, boolean hover) {
        int border = poolCellBorder(index, hover);
        g.fillGradient(x, y, x + w, y + h, hover ? 0xFF2B2112 : 0xFF1A1008, hover ? 0xFF112536 : 0xFF0B1722);
        g.renderOutline(x, y, w, h, border);

        if (index == VolunteerOpenCache.getMyPickIndex()) {
            g.fill(x + 1, y + 1, x + w - 1, y + 3, GREEN);
        } else if (index == VolunteerOpenCache.getMyVolunteerIndex()) {
            g.fill(x + 1, y + 1, x + w - 1, y + 3, BLUE);
        }

        String roleId = VolunteerOpenCache.getVisibleRoles().get(index);
        int textY = y + (h - 8) / 2;
        if (roleId == null || roleId.isEmpty()) {
            g.drawCenteredString(font, "???", x + w / 2, textY, MUTED);
            return;
        }
        if (VolunteerOpenCache.getHiddenRevealed().contains(index)) {
            Component hidden = Component.translatable("gui.sre.volunteer_open.hidden_role");
            g.drawCenteredString(font, trim(hidden.getString(), w - 6), x + w / 2, textY, MUTED);
            return;
        }
        SRERole role = getRoleByPath(roleId);
        if (role == null) {
            g.drawCenteredString(font, trim(roleId, w - 6), x + w / 2, textY, MUTED);
            return;
        }
        boolean card = VolunteerOpenCache.getCardRevealed().contains(index);
        int color = card ? CARD_REVEAL : factionColor(role);
        Component name = RoleUtils.getRoleName(role).copy()
                .withStyle(style -> style.withColor(color).withBold(card));
        // 不要先转成 String，否则卡牌额外揭示的橙色加粗样式会被丢掉。
        Component displayedName = Component.literal(trim(name.getString(), w - 6)).withStyle(name.getStyle());
        g.drawCenteredString(font, displayedName, x + w / 2, textY, color);
    }

    private int poolCellBorder(int index, boolean hover) {
        if (index == VolunteerOpenCache.getMyPickIndex()) {
            return GREEN;
        }
        if (VolunteerOpenCache.getChosenIndices().contains(index)) {
            return RED;
        }
        if (index == VolunteerOpenCache.getMyVolunteerIndex()) {
            return BLUE;
        }
        return hover ? GOLD : CARD_BORDER;
    }

    private SRERole roleAtPoolIndex(int index) {
        if (index < 0) {
            return null;
        }
        String roleId = VolunteerOpenCache.getVisibleRoles().get(index);
        if (roleId == null || roleId.isEmpty()) {
            return null;
        }
        if (VolunteerOpenCache.getHiddenRevealed().contains(index)) {
            return null;
        }
        return getRoleByPath(roleId);
    }

    // ==================== 通用绘制 ====================

    private void drawTimer(GuiGraphics g) {
        if (VolunteerOpenCache.isTimerHeld()) {
            Component holding = Component.translatable("gui.sre.volunteer_open.waiting_players");
            g.drawString(font, holding, rightX + rightW - PAD - font.width(holding), rightY + PAD, GOLD, false);
            return;
        }
        int seconds = VolunteerOpenCache.getDisplaySeconds();
        int color = seconds <= 10 ? (tickCounter % 20 < 10 ? RED : 0xFFFFA0A0) : seconds <= 30 ? 0xFFFFAA33 : BLUE;
        String time = String.format("%d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
        Component timer = Component.literal(time).withStyle(style -> style.withColor(color).withBold(true));
        int x = rightX + rightW - PAD - font.width(timer);
        g.drawString(font, timer, x, rightY + PAD, color, false);

        // 一阶段的搜索框占着第二行，序号提示只在海选/确认阶段显示
        if (VolunteerOpenCache.getPhase() == VolunteerOpenCache.PHASE_VOLUNTEER) {
            return;
        }
        int myIndex = VolunteerOpenCache.getMyIndex();
        if (myIndex > 0) {
            Component mine = Component.translatable("gui.sre.volunteer_open.your_index", myIndex)
                    .withStyle(ChatFormatting.AQUA);
            g.drawString(font, mine, rightX + rightW - PAD - font.width(mine), rightY + PAD + 12, BLUE, false);
        }
    }

    private void drawRoleDetail(GuiGraphics g, SRERole role, int x, int y, int w, int h) {
        if (role == null) {
            Component waiting = VolunteerOpenCache.canSelect()
                    ? Component.translatable("gui.sre.volunteer_open.click_to_pick")
                    : Component.translatable("gui.sre.volunteer_open.wait_for_others");
            g.drawCenteredString(font, waiting, x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        int tx = x + PAD;
        int ty = y + PAD;
        int roleColor = role.isNeutralForKiller() ? 0xFFAA44CC : (role.getColor() | 0xFF000000);
        Component name = RoleUtils.getRoleName(role).withStyle(style -> style.withColor(roleColor).withBold(true));
        g.drawString(font, name, tx, ty, roleColor, false);
        Component faction = getRoleFactionText(role);
        g.drawString(font, faction, x + w - PAD - font.width(faction), ty,
                faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT, false);

        int textY = ty + 18;
        int wrapW = w - PAD * 2;
        List<FormattedCharSequence> lines = font.split(RoleUtils.getRoleSimpleDescription(role), wrapW);
        int visible = Math.max(1, (y + h - PAD - textY) / DETAIL_LINE_H);
        for (int i = 0; i < visible && i < lines.size(); i++) {
            g.drawString(font, lines.get(i), tx, textY + i * DETAIL_LINE_H, TEXT, false);
        }
    }

    private Component getRoleFactionText(SRERole role) {
        if (role.isVigilanteTeam()) {
            return Component.translatable("display.type.role.vigilante")
                    .withStyle(style -> style.withColor(0xFF22BBCC));
        } else if (role.isInnocent()) {
            return Component.translatable("display.type.role.innocent").withStyle(style -> style.withColor(0xFF44BB66));
        } else if (role.canUseKiller()) {
            return Component.translatable("display.type.role.killer").withStyle(style -> style.withColor(0xFFCC2233));
        } else if (role.isNeutralForKiller()) {
            return Component.translatable("display.type.role.neutral_for_killer_2")
                    .withStyle(style -> style.withColor(0xFFAA44CC));
        } else if (role.isNeutrals()) {
            return Component.translatable("display.type.role.neutral_special")
                    .withStyle(style -> style.withColor(0xFFCCAA22));
        }
        return Component.translatable("gui.sre.volunteer_open.unknown_faction").withStyle(ChatFormatting.GRAY);
    }

    private int factionColor(SRERole role) {
        if (role.isVigilanteTeam())
            return 0xFF22BBCC;
        if (role.canUseKiller())
            return RED;
        if (role.isInnocent())
            return GREEN;
        if (useGoodSideNeutralColor(role))
            return NEUTRAL_FOR_INNOCENT;
        if (role.isNeutralForKiller())
            return 0xFFAA44CC;
        if (role.isNeutrals() || isExcludedGoodSideNeutral(role))
            return GOLD;
        return BLUE;
    }

    private static boolean useGoodSideNeutralColor(SRERole role) {
        return role != null && role.isNeutralForInnocent() && !isExcludedGoodSideNeutral(role);
    }

    private static boolean isExcludedGoodSideNeutral(SRERole role) {
        if (role == null || !role.isNeutralForInnocent()) {
            return false;
        }
        String path = role.identifier().getPath();
        return "amnesiac".equals(path) || "initiate".equals(path);
    }

    private void drawFooter(GuiGraphics g) {
        Component hint = Component.translatable("gui.sre.volunteer_open.scroll_hint").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, hint, width / 2, height - 12, MUTED);
    }

    // ==================== 自动滚动 ====================

    private void drawAutoScrollToggle(GuiGraphics g, int mouseX, int mouseY) {
        boolean enabled = SREClientConfig.instance().roleRotationAutoScroll;
        boolean hover = inside(mouseX, mouseY, autoToggleX, autoToggleY, autoToggleW, AUTO_TOGGLE_H);
        g.fillGradient(autoToggleX, autoToggleY, autoToggleX + autoToggleW, autoToggleY + AUTO_TOGGLE_H,
                hover ? 0x553A2E12 : 0x331A1008, hover ? 0x44112536 : 0x220B1722);
        g.renderOutline(autoToggleX, autoToggleY, autoToggleW, AUTO_TOGGLE_H,
                hover ? GOLD : (enabled ? 0x665A4530 : 0x443B2F1E));
        Component label = Component.translatable(enabled
                ? "gui.sre.volunteer_open.auto_scroll_on"
                : "gui.sre.volunteer_open.auto_scroll_off");
        g.drawCenteredString(font, label, autoToggleX + autoToggleW / 2,
                autoToggleY + AUTO_TOGGLE_H / 2 - 4, enabled ? GREEN : MUTED);
    }

    private void toggleAutoScroll() {
        SREClientConfig config = SREClientConfig.instance();
        config.roleRotationAutoScroll = !config.roleRotationAutoScroll;
        SREClientConfig.HANDLER.save();
        if (!config.roleRotationAutoScroll) {
            autoScrolling = false;
            cancelAutoScroll();
        }
    }

    private void updateAutoScroll() {
        if (!SREClientConfig.instance().roleRotationAutoScroll) {
            autoScrolling = false;
            return;
        }
        int anchor = computeAnchorRow();
        if (anchor != lastAutoScrollRow) {
            lastAutoScrollRow = anchor;
            autoScrolling = anchor >= 0;
            if (anchor >= 0) {
                int listH = scrollBarH();
                int rowTop = anchor * PLAYER_ROW_H;
                int rowBottom = rowTop + PLAYER_ROW_H;
                int margin = PLAYER_ROW_H;
                if (rowTop < playerListScroll + margin) {
                    autoScrollTarget = Math.max(0, rowTop - margin);
                } else if (rowBottom > playerListScroll + listH - margin) {
                    autoScrollTarget = Math.min(maxPlayerListScroll, rowBottom + margin - listH);
                } else {
                    autoScrollTarget = playerListScroll;
                }
            }
        }
        if (!autoScrolling) {
            return;
        }
        int diff = autoScrollTarget - playerListScroll;
        if (diff == 0) {
            autoScrolling = false;
            return;
        }
        int step = Math.min(60, Math.max(1, (int) Math.ceil(Math.abs(diff) * 0.25)));
        playerListScroll = Mth.clamp(playerListScroll + Integer.signum(diff) * step, 0, maxPlayerListScroll);
        if (playerListScroll == autoScrollTarget) {
            autoScrolling = false;
        }
    }

    private int computeAnchorRow() {
        Set<UUID> members = VolunteerOpenCache.getCurrentGroupMembers();
        if (members.isEmpty()) {
            return -1;
        }
        List<Map.Entry<UUID, Integer>> players = getSortedPlayers();
        for (int i = 0; i < players.size(); i++) {
            UUID uuid = players.get(i).getKey();
            if (members.contains(uuid) && !VolunteerOpenCache.getPickedRoles().containsKey(uuid)) {
                return i;
            }
        }
        for (int i = 0; i < players.size(); i++) {
            if (members.contains(players.get(i).getKey())) {
                return i;
            }
        }
        return -1;
    }

    private void cancelAutoScroll() {
        autoScrolling = false;
        lastAutoScrollRow = computeAnchorRow();
    }

    // ==================== 确认按钮 ====================

    private boolean isConfirmStageActive() {
        return VolunteerOpenCache.getPhase() == VolunteerOpenCache.PHASE_CONFIRM
                && VolunteerOpenCache.getConfirmCountdown() > 0
                && VolunteerOpenCache.isConfirmRequired();
    }

    private boolean isConfirmButtonClickable() {
        return isConfirmStageActive() && !VolunteerOpenCache.isLocalPlayerConfirmed();
    }

    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY) {
        if (!isConfirmStageActive()) {
            return;
        }
        boolean confirmed = VolunteerOpenCache.isLocalPlayerConfirmed();
        boolean hover = !confirmed && inside(mouseX, mouseY, confirmX, confirmY, CONFIRM_W, CONFIRM_H);
        g.fillGradient(confirmX, confirmY, confirmX + CONFIRM_W, confirmY + CONFIRM_H,
                hover ? 0xFF3A2E12 : 0xE01A1008, hover ? 0xFF112536 : 0xE00B1722);
        g.renderOutline(confirmX, confirmY, CONFIRM_W, CONFIRM_H,
                hover ? GOLD : (confirmed ? 0x665A4530 : BORDER));
        g.fill(confirmX + 1, confirmY + 1, confirmX + CONFIRM_W - 1, confirmY + 3,
                hover ? GOLD : 0x33FFE8C0);
        Component label = Component.translatable(confirmed
                ? "gui.sre.volunteer_open.confirm_waiting"
                : "gui.sre.volunteer_open.confirm_ready");
        g.drawCenteredString(font, label, confirmX + CONFIRM_W / 2, confirmY + CONFIRM_H / 2 - 4,
                confirmed ? MUTED : TEXT);
    }

    // ==================== 输入 ====================

    private void playClickSound() {
        this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside(mouseX, mouseY, autoToggleX, autoToggleY, autoToggleW, AUTO_TOGGLE_H)) {
                playClickSound();
                toggleAutoScroll();
                return true;
            }
            if (isConfirmButtonClickable() && inside(mouseX, mouseY, confirmX, confirmY, CONFIRM_W, CONFIRM_H)) {
                playClickSound();
                ClientPlayNetworking.send(new RoleRotationConfirmC2SPacket());
                return true;
            }
            if (handleScrollBarClick(mouseX, mouseY)) {
                return true;
            }
            if (hoveredPlayerIndex >= 0) {
                SRERole role = getConcreteRoleAtRow(hoveredPlayerIndex);
                if (role != null) {
                    playClickSound();
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(new RoleIntroduceScreen(this, role));
                    return true;
                }
            }
            if (VolunteerOpenCache.getPhase() == VolunteerOpenCache.PHASE_VOLUNTEER) {
                if (hoveredRoleIndex >= 0 && hoveredRoleIndex < filteredRoles.size()) {
                    String clickedId = filteredRoles.get(hoveredRoleIndex).identifier().toString();
                    // 一阶段内可以随时改选；重复点已选中的那个就不发包了
                    if (!clickedId.equals(VolunteerOpenCache.getMyVolunteerRoleId())) {
                        playClickSound();
                        ClientPlayNetworking.send(new VolunteerOpenSelectC2SPacket(true, -1, clickedId));
                    }
                    return true;
                }
            } else if (hoveredPoolIndex >= 0 && VolunteerOpenCache.canSelect()) {
                playClickSound();
                ClientPlayNetworking.send(new VolunteerOpenSelectC2SPacket(false, hoveredPoolIndex, ""));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleScrollBarClick(double mouseX, double mouseY) {
        if (maxPlayerListScroll <= 0) {
            return false;
        }
        int barX = scrollBarX();
        int barY = scrollBarY();
        int barH = scrollBarH();
        if (mouseX < barX - 2 || mouseX > barX + 5 || mouseY < barY || mouseY > barY + barH) {
            return false;
        }
        int thumb = thumbHeight();
        int thumbY = thumbTopY();
        if (mouseY >= thumbY && mouseY <= thumbY + thumb) {
            scrolling = true;
            scrollBarClickOffset = mouseY - thumbY;
        } else {
            int trackLen = barH - thumb;
            if (trackLen > 0) {
                double ratio = Mth.clamp((mouseY - barY - thumb / 2.0) / trackLen, 0.0, 1.0);
                playerListScroll = (int) Math.round(ratio * maxPlayerListScroll);
            }
        }
        cancelAutoScroll();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (VolunteerOpenCache.getPhase() == VolunteerOpenCache.PHASE_VOLUNTEER
                && inside(mouseX, mouseY, volListX, volListY, volListW, volListH) && maxVolScroll > 0) {
            volScroll = Mth.clamp(volScroll - (int) Math.signum(scrollY) * (volCellH + 4), 0, maxVolScroll);
            return true;
        }
        if (inside(mouseX, mouseY, leftX, leftY, leftW, panelH) && maxPlayerListScroll > 0) {
            cancelAutoScroll();
            playerListScroll = Mth.clamp(playerListScroll - (int) Math.signum(scrollY) * PLAYER_ROW_H, 0,
                    maxPlayerListScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (scrolling) {
            int barY = scrollBarY();
            int barH = scrollBarH();
            int thumb = thumbHeight();
            int trackLen = barH - thumb;
            if (trackLen > 0) {
                double pos = Mth.clamp(mouseY - barY - scrollBarClickOffset, 0.0, trackLen);
                playerListScroll = (int) Math.round(pos / trackLen * maxPlayerListScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrolling) {
            scrolling = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            // 让搜索框先吃掉按键（包括 ESC 取消焦点）
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (keyCode == 256) {
            minecraft.setScreen(new WithParentScreenPauseScreen(this, true));
            return true;
        }
        if (NoellesrolesClient.roleIntroClientBind.matches(keyCode, scanCode)) {
            if (showMyRoleIntro()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean showMyRoleIntro() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        SRERole role = null;
        int myPick = VolunteerOpenCache.getMyPickIndex();
        if (myPick >= 0) {
            role = roleAtPoolIndex(myPick);
        }
        if (role == null) {
            role = getRoleByPath(VolunteerOpenCache.getMyVolunteerRoleId());
        }
        if (role != null) {
            mc.setScreen(new RoleIntroduceScreen(this, role));
            return true;
        }
        if (SREClient.areaComponent != null && SREClient.areaComponent.areasSettings != null) {
            mc.setScreen(new RoleIntroduceScreen(this, SREClient.areaComponent.areasSettings));
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 工具 ====================

    private SRERole getRoleByPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(path);
        if (id != null) {
            SRERole role = TMMRoles.ROLES.get(id);
            if (role != null) {
                return role;
            }
        }
        for (var role : TMMRoles.ROLES.entrySet()) {
            if (role.getKey().getPath().equals(path)) {
                return role.getValue();
            }
        }
        return null;
    }

    private String trim(String value, int maxWidth) {
        return font.plainSubstrByWidth(value, Math.max(4, maxWidth));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
