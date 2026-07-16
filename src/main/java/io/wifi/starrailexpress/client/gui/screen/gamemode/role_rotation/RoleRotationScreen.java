package io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.network.packet.RoleRotationSelectC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class RoleRotationScreen extends Screen {
    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int MIN_LEFT_W = 180;
    private static final int MAX_LEFT_W = 280;
    private static final int PLAYER_ROW_H = 28;
    private static final int CARD_H = 104;
    private static final int DETAIL_LINE_H = 11;

    private static final int BG_TOP = 0xF018120A;
    private static final int BG_BOTTOM = 0xF0061018;
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD80B1722;
    private static final int BORDER = 0xFF8B6914;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int BLUE = 0xFF5EB7D8;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;

    private int leftX, leftY, leftW, panelH;
    private int rightX, rightY, rightW, cardW, cardY;
    private int detailX, detailY, detailW, detailH;

    private int tickCounter;
    private int playerListScroll;
    private int maxPlayerListScroll;
    private int hoveredCardIndex = -1;
    private int hoveredDetailIndex = -1;

    public RoleRotationScreen() {
        super(Component.translatable("gui.sre.role_rotation.title").withStyle(ChatFormatting.GOLD));
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        calculateScroll();
    }

    private void computeLayout() {
        leftX = PAD;
        leftY = PAD + 22;
        panelH = Math.max(120, height - leftY - PAD);
        leftW = Mth.clamp((int) (width * 0.26F), MIN_LEFT_W, MAX_LEFT_W);
        rightX = leftX + leftW + GAP;
        rightY = leftY;
        rightW = Math.max(160, width - rightX - PAD);
        cardY = rightY + 42;
        cardW = Math.max(74, (rightW - GAP * 3 - PAD * 2) / 4);
        detailX = rightX + PAD;
        detailY = cardY + CARD_H + GAP;
        detailW = rightW - PAD * 2;
        detailH = Math.max(70, rightY + panelH - detailY - PAD);
    }

    private void calculateScroll() {
        int listH = getPlayerListHeight();
        int totalContent = RoleRotationCache.getTotalPlayers() * PLAYER_ROW_H;
        maxPlayerListScroll = Math.max(0, totalContent - listH);
        playerListScroll = Mth.clamp(playerListScroll, 0, maxPlayerListScroll);
    }

    private int getPlayerListHeight() {
        return Math.max(48, panelH - 78);
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        RoleRotationCache.tickTimers(); // 客户端本地倒计时 tick
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        computeLayout();
        calculateScroll();
        hoveredCardIndex = -1;
        hoveredDetailIndex = -1;

        drawTitleBar(g);
        drawPanel(g, leftX, leftY, leftW, panelH);
        drawPanel(g, rightX, rightY, rightW, panelH);
        drawPlayerList(g, mouseX, mouseY);
        drawTurnInfo(g);
        drawRoleArea(g, mouseX, mouseY);
        drawFooter(g);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        g.fillGradient(0, 0, width, 34, 0xAA000000, 0x22000000);
    }

    private void drawTitleBar(GuiGraphics g) {
        g.drawString(font, title, PAD, 10, GOLD, false);
        // 显示当前轮次，而不是“当前玩家序号”
        Component turn = Component.translatable("gui.sre.role_rotation.current_round",
                RoleRotationCache.getCurrentRoundIndex());
        g.drawString(font, turn, width - PAD - font.width(turn), 10, TEXT, false);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFE8C0);
    }

    private void drawPlayerList(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftX + PAD;
        int y = leftY + PAD + 28;
        int w = leftW - PAD * 2;
        int h = getPlayerListHeight();

        Component listTitle = Component.translatable("gui.sre.role_rotation.player_list");
        g.drawString(font, listTitle, x, leftY + PAD, TEXT, false);

        List<Map.Entry<UUID, Integer>> players = new ArrayList<>(RoleRotationCache.getRotationOrder().entrySet());
        players.sort(Comparator.comparingInt(Map.Entry::getValue));

        // 获取本轮参与的玩家 UUID 集合
        Set<UUID> roundPlayers = RoleRotationCache.getRoundCandidates().keySet();

        g.enableScissor(x, y, x + w, y + h);
        int drawY = y - playerListScroll;
        for (int i = 0; i < players.size(); i++) {
            Map.Entry<UUID, Integer> entry = players.get(i);
            UUID uuid = entry.getKey();
            int rowY = drawY + i * PLAYER_ROW_H;
            if (rowY + PLAYER_ROW_H < y || rowY > y + h) {
                continue;
            }
            // 高亮：该玩家正在本轮选择中
            boolean inThisRound = roundPlayers.contains(uuid);
            int bg = inThisRound ? 0x552A5A42 : 0x331A1008;
            g.fillGradient(x, rowY, x + w, rowY + PLAYER_ROW_H - 3, bg, 0x33120A04);
            g.renderOutline(x, rowY, w, PLAYER_ROW_H - 3, inThisRound ? GREEN : 0x665A4530);

            String index = "#" + entry.getValue();
            g.drawString(font, index, x + 6, rowY + 8, inThisRound ? GREEN : MUTED, false);

            Component roleName = selectedRoleText(uuid);
            g.drawString(font, trim(roleName.getString(), Math.max(30, w - 104)), x + w - 58, rowY + 8,
                    roleName.getStyle().getColor() != null ? roleName.getStyle().getColor().getValue() : BLUE, false);
        }
        g.disableScissor();

        if (maxPlayerListScroll > 0) {
            int barX = x + w - 4;
            int thumbH = Math.max(18, h * h / Math.max(h, players.size() * PLAYER_ROW_H));
            int thumbY = y + (int) ((h - thumbH) * (playerListScroll / (double) maxPlayerListScroll));
            g.fill(barX, y, barX + 3, y + h, 0x661A1008);
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, GOLD);
        }
    }

    private Component selectedRoleText(UUID uuid) {
        String rolePath = RoleRotationCache.getSelectedRoles().get(uuid);
        if (rolePath == null) {
            return Component.literal("?").withStyle(ChatFormatting.DARK_GRAY);
        }
        if (RoleRotationCache.getRandomChoosers().contains(uuid)) {
            return Component.translatable("gui.sre.role_rotation.random").withStyle(ChatFormatting.GOLD);
        }
        SRERole role = getRoleByPath(rolePath);
        if (role == null) {
            return Component.literal(rolePath).withStyle(ChatFormatting.AQUA);
        }
        if (role.isFlag("inner.role_rotation.hidden")) {
            return Component.translatable("gui.sre.role_rotation.random").withStyle(ChatFormatting.GOLD);
        }
        int factionColor = getFactionColor(role);
        return RoleUtils.getRoleName(role).withStyle(style -> style.withColor(factionColor));
    }

    private int getFactionColor(SRERole role) {
        if (role.canUseKiller())
            return RED;
        if (role.isInnocent())
            return GREEN;
        if (role.isNeutrals() || role.isNeutralForKiller() || role.isVigilanteTeam())
            return GOLD;
        return BLUE;
    }

    private void drawTurnInfo(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        int x = rightX + PAD;
        int y = rightY + PAD;

        // 个人剩余时间或确认倒计时
        int seconds = RoleRotationCache.getDisplaySeconds();
        int color = seconds <= 10 ? (tickCounter % 20 < 10 ? RED : 0xFFFFA0A0) : seconds <= 30 ? 0xFFFFAA33 : BLUE;
        String time = String.format("%d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
        Component timer = Component.literal(time).withStyle(style -> style.withColor(color).withBold(true));
        g.drawString(font, timer, x, y, color, false);

        if (!RoleRotationCache.isSelecting()) {
            Component phase = Component.translatable("gui.sre.role_rotation.adjust_phase");
            g.drawString(font, phase, x + 54, y, TEXT, false);
        }

        // 显示玩家自己的序号（靠右）
        int myIndex = RoleRotationCache.getMyRotationIndex();
        if (myIndex > 0) {
            Component mine = Component.translatable("gui.sre.role_rotation.your_index", myIndex)
                    .withStyle(ChatFormatting.AQUA);
            int textW = font.width(mine);
            g.drawString(font, mine, rightX + rightW - PAD - textW, y, BLUE, false);
        }
    }

    private void drawRoleArea(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        UUID myUuid = mc.player.getUUID();
        boolean hasSelected = RoleRotationCache.getSelectedRoles().containsKey(myUuid);
        boolean isMyTurn = RoleRotationCache.isMyTurn();
        boolean canChoose = isMyTurn && !hasSelected && RoleRotationCache.isSelecting();

        // 自己不在本轮且未选择 → 等待画面
        if (!isMyTurn && !hasSelected) {
            drawWaitingText(g);
            return;
        }

        // 获取自己的候选职业列表
        List<String> myCandidates = RoleRotationCache.getMyCandidates();

        // 已选完职业 → 只显示详情
        if (hasSelected) {
            String selectedPath = RoleRotationCache.getSelectedRoles().get(myUuid);
            hoveredDetailIndex = detailIndexForSelected(selectedPath, myCandidates);
            drawDetail(g, selectedPath, myCandidates);
            return;
        }

        // 绘制 3 个候选卡片 + 随机卡片
        for (int i = 0; i < 4; i++) {
            int x = detailX + i * (cardW + GAP);
            boolean hover = canChoose && inside(mouseX, mouseY, x, cardY, cardW, CARD_H);
            if (hover) {
                hoveredCardIndex = i;
                hoveredDetailIndex = i;
            }
            drawRoleCard(g, x, cardY, cardW, CARD_H, i, myCandidates, hover, canChoose);
        }

        // 悬停预览详情
        String selectedPath = null;
        drawDetail(g, selectedPath, myCandidates);
    }

    private void drawRoleCard(GuiGraphics g, int x, int y, int w, int h, int index, List<String> candidates,
            boolean hover, boolean enabled) {
        int border = hover ? GOLD : 0xFF5A4530;
        int top = hover ? 0xFF2B2112 : 0xFF1A1008;
        int bottom = hover ? 0xFF112536 : 0xFF0B1722;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        g.renderOutline(x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, hover ? GOLD : 0x33FFE8C0);

        if (index == 3) {
            drawRandomCard(g, x, y, w, h, enabled);
            return;
        }
        if (index >= candidates.size()) {
            drawEmptyCard(g, x, y, w, h);
            return;
        }
        SRERole role = getRoleByPath(candidates.get(index));
        if (role == null) {
            drawCenteredWrapped(g, Component.literal(candidates.get(index)), x + 8, y + 32, w - 16, 0xFFFFFFFF);
            return;
        }
        int roleColor = role.getColor() | 0xFF000000;
        g.fill(x + 8, y + 14, x + w - 8, y + 34, (roleColor & 0x00FFFFFF) | 0x66000000);
        g.drawCenteredString(font, trim(RoleUtils.getRoleName(role).getString(), w - 16), x + w / 2, y + 20,
                0xFFFFFFFF);
        Component faction = getRoleFactionText(role);
        g.drawCenteredString(font, trim(faction.getString(), w - 16), x + w / 2, y + 48,
                faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT);
        Component hint = Component.translatable("gui.sre.role_rotation.click_to_select");
        g.drawCenteredString(font, trim(hint.getString(), w - 16), x + w / 2, y + h - 18,
                enabled ? GREEN : MUTED);
    }

    private void drawRandomCard(GuiGraphics g, int x, int y, int w, int h, boolean enabled) {
        g.fill(x + 8, y + 14, x + w - 8, y + 34, 0x55D4AF37);
        g.drawCenteredString(font, Component.translatable("gui.sre.role_rotation.random"), x + w / 2, y + 20, GOLD);
        g.drawCenteredString(font, "?", x + w / 2, y + 49, GOLD);
        Component hint = Component.translatable("gui.sre.role_rotation.click_to_random");
        g.drawCenteredString(font, trim(hint.getString(), w - 16), x + w / 2, y + h - 18,
                enabled ? GOLD : MUTED);
    }

    private void drawEmptyCard(GuiGraphics g, int x, int y, int w, int h) {
        g.drawCenteredString(font, "-", x + w / 2, y + h / 2 - 4, MUTED);
    }

    private void drawDetail(GuiGraphics g, String selectedPath, List<String> candidates) {
        g.fillGradient(detailX, detailY, detailX + detailW, detailY + detailH, 0xAA1A1008, 0xAA0B1722);
        g.renderOutline(detailX, detailY, detailW, detailH, 0xFF5A4530);

        SRERole role = null;
        if (hoveredDetailIndex >= 0 && hoveredDetailIndex < 3 && hoveredDetailIndex < candidates.size()) {
            role = getRoleByPath(candidates.get(hoveredDetailIndex));
        } else if (selectedPath != null) {
            role = getRoleByPath(selectedPath);
        }

        if (role == null) {
            Component text = hoveredDetailIndex == 3
                    ? Component.translatable("gui.sre.role_rotation.random")
                    : Component.translatable("gui.sre.role_rotation.waiting");
            g.drawCenteredString(font, text, detailX + detailW / 2, detailY + detailH / 2 - 4,
                    hoveredDetailIndex == 3 ? GOLD : MUTED);
            return;
        }

        int x = detailX + PAD;
        int y = detailY + PAD;
        int roleColor = role.getColor();
        Component name = RoleUtils.getRoleName(role).withStyle(style -> style.withColor(roleColor).withBold(true));
        g.drawString(font, name, x, y, roleColor | 0xFF000000, false);
        Component faction = getRoleFactionText(role);
        g.drawString(font, faction, detailX + detailW - PAD - font.width(faction), y,
                faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT, false);

        int textY = y + 18;
        int wrapW = detailW - PAD * 2;
        List<FormattedCharSequence> lines = getRoleIntroLines(role, wrapW);
        int visible = Math.max(1, (detailY + detailH - PAD - textY) / DETAIL_LINE_H);
        for (int i = 0; i < visible && i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, textY + i * DETAIL_LINE_H, TEXT, false);
        }
    }

    private List<FormattedCharSequence> getRoleIntroLines(SRERole role, int wrapW) {
        return font.split(RoleUtils.getRoleDescription(role), wrapW);
    }

    private int detailIndexForSelected(String selectedPath, List<String> candidates) {
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            if (selectedPath.equals(candidates.get(i)))
                return i;
        }
        return RoleRotationCache.getRandomChoosers().contains(Minecraft.getInstance().player.getUUID()) ? 3 : -1;
    }

    private void drawFooter(GuiGraphics g) {
        Component hint = Component.translatable("gui.sre.role_rotation.scroll_hint").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, hint, width / 2, height - 12, MUTED);
    }

    private void drawCenteredWrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(text, w);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.drawCenteredString(font, lines.get(i), x + w / 2, y + i * font.lineHeight, color);
        }
    }

    private void drawWaitingText(GuiGraphics g) {
        Component waiting = Component.translatable("gui.sre.role_rotation.wait_for_others");
        int textW = font.width(waiting);
        int availW = rightW - PAD * 2;
        float scale = Math.min(1.0f, (float) availW / Math.max(1, textW));
        int centerX = rightX + rightW / 2;
        int centerY = rightY + panelH / 2;
        g.pose().pushPose();
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawCenteredString(font, waiting, 0, -font.lineHeight / 2, TEXT);
        g.pose().popPose();
    }

    private Component getRoleFactionText(SRERole role) {
        if (role.isInnocent()) {
            return Component.translatable("display.type.role.innocent").withStyle(style -> style.withColor(0xFF44BB66));
        } else if (role.canUseKiller()) {
            return Component.translatable("display.type.role.killer").withStyle(style -> style.withColor(0xFFCC2233));
        } else if (role.isNeutralForKiller()) {
            return Component.translatable("display.type.role.neutral_for_killer_2")
                    .withStyle(style -> style.withColor(0xFFAA44CC));
        } else if (role.isNeutrals()) {
            return Component.translatable("display.type.role.neutral_special")
                    .withStyle(style -> style.withColor(0xFFCCAA22));
        } else if (role.isVigilanteTeam()) {
            return Component.translatable("display.type.role.vigilante")
                    .withStyle(style -> style.withColor(0xFF22BBCC));
        }
        return Component.literal("Unknown").withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCardIndex >= 0 && canChooseNow()) {
            ClientPlayNetworking.send(new RoleRotationSelectC2SPacket(hoveredCardIndex));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean canChooseNow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && RoleRotationCache.isSelecting()
                && RoleRotationCache.isMyTurn()
                && !RoleRotationCache.getSelectedRoles().containsKey(mc.player.getUUID());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftX, leftY, leftW, panelH) && maxPlayerListScroll > 0) {
            playerListScroll = Mth.clamp(playerListScroll - (int) Math.signum(scrollY) * PLAYER_ROW_H, 0,
                    maxPlayerListScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(new WithParentScreenPauseScreen(this, true));
            return true;
        }
        if (NoellesrolesClient.roleIntroClientBind.matches(keyCode, scanCode)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String rolePath = RoleRotationCache.getSelectedRoles().get(mc.player.getUUID());
                SRERole role = getRoleByPath(rolePath);
                if (role != null) {
                    mc.setScreen(new org.agmas.noellesroles.client.screen.RoleIntroduceScreen(this, role));
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void updateData() {
        calculateScroll();
    }

    private SRERole getRoleByPath(String path) {
        if (path == null || path.isBlank())
            return null;
        ResourceLocation id = ResourceLocation.tryParse(path);
        if (id != null) {
            SRERole role = TMMRoles.ROLES.get(id);
            if (role != null)
                return role;
        }
        if (path.contains(":"))
            return null;
        for (String namespace : List.of("noellesroles", "starrailexpress", "trainmurdermystery")) {
            SRERole role = TMMRoles.ROLES.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
            if (role != null)
                return role;
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