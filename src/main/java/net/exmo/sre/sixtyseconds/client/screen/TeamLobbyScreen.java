package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamAllocator;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamLobby;
import net.exmo.sre.sixtyseconds.network.OpenTeamLobbyS2CPacket;
import net.exmo.sre.sixtyseconds.network.TeamLobbyActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 赛前组队大厅（{@code /sre:60s team} 打开）：卡片式列出全部预组队伍，可创建/加入/离开。
 * 卡片区可滚轮滚动（scissor 裁剪 + 金色滚动条）。风格遵循 {@code docs/ui_style.md}。
 * 服务端有变动时推送新快照原地刷新（保持滚动位置）。
 */
public class TeamLobbyScreen extends Screen {
    // ── ui_style 色板 ─────────────────────────────────────────
    private static final int BG_TOP = 0xF018120A;
    private static final int BG_BOTTOM = 0xF0061018;
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;

    private static final int CARD_H = 44;
    private static final int CARD_GAP = 4;
    private static final int ROW = CARD_H + CARD_GAP;

    private OpenTeamLobbyS2CPacket data;
    private int scrollOffset;

    // 面板几何（init 计算）
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;
    private int listX;
    private int listW;

    public TeamLobbyScreen(OpenTeamLobbyS2CPacket data) {
        super(Component.translatable("message.noellesroles.sixty_seconds.team_lobby_title"));
        this.data = data;
    }

    /** 服务端推送新快照时原地刷新（保持滚动位置）。 */
    public void refresh(OpenTeamLobbyS2CPacket data) {
        this.data = data;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelW = (int) Mth.clamp(this.width * 0.72F, 340, 520);
        panelH = (int) Mth.clamp(this.height * 0.82F, 240, 400);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listTop = panelY + 66;
        listBottom = panelY + panelH - 40;
        listX = panelX + 12;
        listW = panelW - 24;

        int visible = visibleRows();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, data.partyIds().length - visible)));

        // 每张可见卡片右侧的「加入」按钮；管理员额外有「解散」按钮（上下并排）
        boolean admin = this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.hasPermissions(2);
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            if (index >= data.partyIds().length) {
                break;
            }
            final int partyId = data.partyIds()[index];
            int cardY = listTop + row * ROW;
            Button join = Button.builder(
                    Component.translatable("message.noellesroles.sixty_seconds.team_join_btn"),
                    b -> send(SixtySecondsTeamLobby.ACTION_JOIN, partyId))
                    .bounds(listX + listW - 66,
                            admin ? cardY + 3 : cardY + (CARD_H - 20) / 2, 58, admin ? 18 : 20).build();
            join.active = data.partySizes()[index] < SixtySecondsTeamAllocator.TEAM_SIZE
                    && partyId != data.myPartyId();
            addRenderableWidget(join);
            if (admin) {
                addRenderableWidget(Button.builder(
                        Component.translatable("message.noellesroles.sixty_seconds.team_disband_btn"),
                        b -> send(SixtySecondsTeamLobby.ACTION_DISBAND, partyId))
                        .bounds(listX + listW - 66, cardY + CARD_H - 21, 58, 18).build());
            }
        }

        // 底部操作栏
        int by = panelY + panelH - 30;
        Button create = Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.team_create_btn"),
                b -> send(SixtySecondsTeamLobby.ACTION_CREATE, -1))
                .bounds(panelX + panelW / 2 - 132, by, 84, 20).build();
        // 已在队伍中禁止再创建（修复无限刷小队）
        create.active = data.myPartyId() < 0;
        addRenderableWidget(create);

        Button leave = Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.team_leave_btn"),
                b -> send(SixtySecondsTeamLobby.ACTION_LEAVE, -1))
                .bounds(panelX + panelW / 2 - 42, by, 84, 20).build();
        leave.active = data.myPartyId() >= 0;
        addRenderableWidget(leave);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(panelX + panelW / 2 + 48, by, 84, 20).build());
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW);
    }

    private void send(int action, int partyId) {
        ClientPlayNetworking.send(new TeamLobbyActionC2SPacket(action, partyId));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, data.partyIds().length - visibleRows());
        int next = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, max);
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
        // 主面板
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECO_LINE);

        // 卡片列表画在背景层（在按钮之下，避免遮住每行的「加入」按钮）
        if (data.partyIds().length > 0) {
            int visible = visibleRows();
            g.enableScissor(listX, listTop, listX + listW, listBottom);
            for (int row = 0; row < visible; row++) {
                int index = scrollOffset + row;
                if (index >= data.partyIds().length) {
                    break;
                }
                drawCard(g, index, listTop + row * ROW, mouseX, mouseY);
            }
            g.disableScissor();
            drawScrollbar(g, visible);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 标题 + 说明（画在最上层）
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2,
                panelY + 12, GOLD);
        g.drawCenteredString(this.font,
                Component.translatable("message.noellesroles.sixty_seconds.team_lobby_rules"),
                this.width / 2, panelY + 30, MUTED);
        g.drawCenteredString(this.font,
                Component.translatable("message.noellesroles.sixty_seconds.team_lobby_split_note"),
                this.width / 2, panelY + 44, GOLD);

        if (data.partyIds().length == 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("message.noellesroles.sixty_seconds.team_lobby_empty"),
                    this.width / 2, listTop + 16, MUTED);
        }
    }

    private void drawCard(GuiGraphics g, int index, int y, int mouseX, int mouseY) {
        int id = data.partyIds()[index];
        int size = data.partySizes()[index];
        boolean mine = id == data.myPartyId();
        int x2 = listX + listW;
        boolean hover = mouseX >= listX && mouseX < x2 && mouseY >= y && mouseY < y + CARD_H;

        // 卡片底 + 描边
        int base = mine ? 0x66203A20 : 0x66231A10;
        if (hover) {
            base = blend(base, 0x33FFFFFF, 0.5F);
        }
        g.fill(listX, y, x2, y + CARD_H, base);
        int edge = mine ? GREEN : (hover ? GOLD : 0xFF5A4530);
        g.renderOutline(listX, y, listW, CARD_H, edge);

        // 队伍号 + 人数徽标
        Component title = Component.translatable("message.noellesroles.sixty_seconds.team_row_title", id)
                .withStyle(mine ? ChatFormatting.GREEN : ChatFormatting.WHITE);
        g.drawString(this.font, title, listX + 10, y + 7, mine ? GREEN : TEXT);

        boolean full = size >= SixtySecondsTeamAllocator.TEAM_SIZE;
        Component count = Component.translatable("message.noellesroles.sixty_seconds.team_count",
                size, SixtySecondsTeamAllocator.TEAM_SIZE);
        int countColor = full ? 0xFFE06B65 : (size == 3 ? GOLD : GREEN);
        g.drawString(this.font, count.copy().withStyle(full ? ChatFormatting.RED
                : (size == 3 ? ChatFormatting.GOLD : ChatFormatting.GREEN)), listX + 10, y + 26, countColor);

        // 成员名
        g.drawString(this.font, data.memberLabels()[index], listX + 84, y + 7, 0xFFC8B898);
        if (size == 3) {
            g.drawString(this.font,
                    Component.translatable("message.noellesroles.sixty_seconds.team_split_hint")
                            .withStyle(ChatFormatting.GOLD),
                    listX + 84, y + 26, GOLD);
        }
    }

    private void drawScrollbar(GuiGraphics g, int visible) {
        int total = data.partyIds().length;
        if (total <= visible) {
            return;
        }
        int trackX = listX + listW + 2;
        int trackH = listBottom - listTop;
        int thumbH = Math.max(18, trackH * visible / total);
        int max = total - visible;
        int thumbY = listTop + (trackH - thumbH) * scrollOffset / Math.max(1, max);
        g.fill(trackX, listTop, trackX + 4, listBottom, 0x33000000);
        g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, GOLD);
    }

    private static int blend(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
