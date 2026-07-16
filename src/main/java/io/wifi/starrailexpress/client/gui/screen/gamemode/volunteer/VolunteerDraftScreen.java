package io.wifi.starrailexpress.client.gui.screen.gamemode.volunteer;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.screen.WithParentScreenPauseScreen;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.content.vote.client.VolunteerCache;
import io.wifi.starrailexpress.game.modes.funny.volunteer.VolunteerDraftState.Phase;
import io.wifi.starrailexpress.network.packet.VolunteerCommitC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.utils.RoleUtils;
import java.util.*;

public class VolunteerDraftScreen extends Screen {

    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int CARD_W = 160;
    private static final int CARD_H = 48;
    private static final int ARROW_BTN_SIZE = 16;
    private static final int VIEWPORT_TOP = 70; // 卡片可视区域顶部 Y
    private static final int VIEWPORT_BOTTOM_PAD = 70; // 提交按钮上方保留空间

    private static final int BG_TOP = 0xF018120A;
    private static final int BG_BOTTOM = 0xF0061018;
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD80B1722;
    private static final int BORDER = 0xFF8B6914;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int BLUE = 0xFF5EB7D8;
    private static final int RED = 0xFFE06B65;

    private List<String> currentOrder;
    private int volunteerCount;
    private Button[][] moveButtons;
    private Button submitButton;

    // 拖拽状态
    private int draggingIndex = -1;
    private int dragOffsetX, dragOffsetY;
    private boolean submitted = false;

    // 滚动状态
    private double scrollY = 0;
    private double maxScrollY = 0;

    // 结果阶段数据
    private Map<UUID, String> finalRoles = Map.of();
    private String myFinalRoleId = "";

    public VolunteerDraftScreen() {
        super(Component.translatable("gui.sre.volunteer.title").withStyle(ChatFormatting.GOLD));
    }

    @Override
    protected void init() {
        super.init();
        volunteerCount = VolunteerCache.getVolunteerCount();
        if (currentOrder == null || currentOrder.size() != volunteerCount) {
            currentOrder = new ArrayList<>(VolunteerCache.getMyCandidates());
            while (currentOrder.size() < volunteerCount) {
                currentOrder.add("random");
            }
        }
        this.clearWidgets();
        if (VolunteerCache.getPhase() == Phase.COMMIT) {
            buildCommitWidgets();
        }
        updateScrollLimits();
    }

    private void buildCommitWidgets() {
        moveButtons = new Button[volunteerCount][3];
        for (int i = 0; i < volunteerCount; i++) {
            final int idx = i;
            moveButtons[i][0] = Button.builder(Component.literal("↑"), btn -> moveUp(idx))
                    .bounds(0, 0, ARROW_BTN_SIZE, ARROW_BTN_SIZE).build();
            moveButtons[i][1] = Button.builder(Component.literal("↓"), btn -> moveDown(idx))
                    .bounds(0, 0, ARROW_BTN_SIZE, ARROW_BTN_SIZE).build();
            moveButtons[i][2] = Button.builder(Component.literal("?"), btn -> toggleRandom(idx))
                    .bounds(0, 0, ARROW_BTN_SIZE, ARROW_BTN_SIZE)
                    .tooltip(Tooltip.create(Component.translatable("gui.sre.volunteer.random_tip")))
                    .build();
            addRenderableWidget(moveButtons[i][0]);
            addRenderableWidget(moveButtons[i][1]);
            addRenderableWidget(moveButtons[i][2]);
        }
        submitButton = Button.builder(Component.translatable("gui.sre.volunteer.submit"), btn -> onSubmit())
                .bounds(width / 2 - 50, height - 40, 100, 20).build();
        addRenderableWidget(submitButton);

        // 如果已经提交过，保持按钮禁用状态
        if (submitted) {
            submitButton.active = false;
            for (Button[] row : moveButtons) {
                for (Button btn : row)
                    btn.active = false;
            }
        }
    }

    private void updateScrollLimits() {
        int contentHeight = volunteerCount * (CARD_H + GAP);
        int viewportHeight = height - VIEWPORT_TOP - VIEWPORT_BOTTOM_PAD;
        maxScrollY = Math.max(0, contentHeight - viewportHeight);
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
    }

    private void updateButtonPositions() {
        if (moveButtons == null)
            return;
        int startX = width / 2 - CARD_W / 2;
        for (int i = 0; i < volunteerCount; i++) {
            int y = VIEWPORT_TOP + i * (CARD_H + GAP) - (int) scrollY;
            moveButtons[i][0].setPosition(startX + CARD_W + 4, y);
            moveButtons[i][0].visible = i > 0;
            moveButtons[i][1].setPosition(startX + CARD_W + 4, y + CARD_H - ARROW_BTN_SIZE);
            moveButtons[i][1].visible = i < volunteerCount - 1;
            moveButtons[i][2].setPosition(startX - 24, y + (CARD_H - ARROW_BTN_SIZE) / 2);
        }
        if (submitButton != null) {
            submitButton.setPosition(width / 2 - 50, height - 40);
        }
    }

    public void updateResult() {
        if (VolunteerCache.getPhase() == Phase.RESULT) {
            finalRoles = VolunteerCache.getFinalRoles();
            myFinalRoleId = VolunteerCache.getMyFinalRole();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (VolunteerCache.getPhase() == Phase.COMMIT) {
            updateButtonPositions();
            updateScrollLimits();
            // 拖拽自动滚动
            if (draggingIndex >= 0) {
                int mouseY = (int) Minecraft.getInstance().mouseHandler.ypos() * height
                        / Minecraft.getInstance().getWindow().getHeight();
                int scrollThreshold = 30;
                if (mouseY < VIEWPORT_TOP + scrollThreshold) {
                    scrollY = Math.max(0, scrollY - 5);
                } else if (mouseY > height - VIEWPORT_BOTTOM_PAD - scrollThreshold) {
                    scrollY = Math.min(maxScrollY, scrollY + 5);
                }
            }
        } else if (VolunteerCache.getPhase() == Phase.RESULT && finalRoles.isEmpty()) {
            updateResult();
        }
    }

    private void onSubmit() {
        if (submitted)
            return;
        List<Integer> prefs = new ArrayList<>();
        List<String> originals = VolunteerCache.getMyCandidates();
        for (String id : currentOrder) {
            if (id.equals("random")) {
                prefs.add(-1);
            } else {
                int idx = originals.indexOf(id);
                prefs.add(idx >= 0 ? idx : -1);
            }
        }
        ClientPlayNetworking.send(new VolunteerCommitC2SPacket(prefs));
        submitted = true;
        submitButton.active = false;
        if (moveButtons != null) {
            for (Button[] row : moveButtons) {
                for (Button btn : row)
                    btn.active = false;
            }
        }
    }

    private void moveUp(int index) {
        if (index > 0 && !submitted)
            Collections.swap(currentOrder, index, index - 1);
    }

    private void moveDown(int index) {
        if (index < volunteerCount - 1 && !submitted)
            Collections.swap(currentOrder, index, index + 1);
    }

    private void toggleRandom(int index) {
        if (submitted)
            return;
        if (currentOrder.get(index).equals("random")) {
            List<String> originals = VolunteerCache.getMyCandidates();
            if (index < originals.size())
                currentOrder.set(index, originals.get(index));
        } else {
            currentOrder.set(index, "random");
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        g.fillGradient(0, 0, width, 34, 0xAA000000, 0x22000000);
        // guiGraphics.fill(0, 0, width, height, java.awt.Color.black.getRGB());

    }

    // ========== 渲染 ==========
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawTitleBar(g);

        Phase phase = VolunteerCache.getPhase();
        if (phase == Phase.COMMIT) {
            drawTimer(g);
            drawCardsInViewport(g, mouseX, mouseY);
            drawScrollbar(g);
            if (draggingIndex >= 0)
                drawDraggedCard(g, mouseX, mouseY);
        } else if (phase == Phase.ADJUST) {
            drawWaiting(g);
        } else if (phase == Phase.RESULT) {
            drawResult(g, mouseX, mouseY);
        }
    }

    private void drawTitleBar(GuiGraphics g) {
        g.drawString(font, title, PAD, 10, GOLD, false);
        Component hint = Component.translatable("gui.sre.volunteer.sort_hint");
        g.drawCenteredString(font, hint, width / 2, 18, MUTED);
    }

    private void drawTimer(GuiGraphics g) {
        int remaining = VolunteerCache.getSmoothRemainingTime();
        int seconds = remaining / 20;
        String timeStr = String.format("%d:%02d", seconds / 60, seconds % 60);
        int color = seconds <= 10 ? RED : seconds <= 30 ? 0xFFFFAA33 : BLUE;
        Component timer = Component.literal(timeStr).withStyle(style -> style.withColor(color).withBold(true));
        g.drawCenteredString(font, timer, width / 2, VIEWPORT_TOP - 32, color);
    }

    private void drawCardsInViewport(GuiGraphics g, int mouseX, int mouseY) {
        int startX = width / 2 - CARD_W / 2;
        int viewportTop = VIEWPORT_TOP;
        int viewportBottom = height - VIEWPORT_BOTTOM_PAD;
        g.enableScissor(0, viewportTop, width, viewportBottom - viewportTop);
        for (int i = 0; i < volunteerCount; i++) {
            int y = viewportTop + i * (CARD_H + GAP) - (int) scrollY;
            if (y + CARD_H < viewportTop || y > viewportBottom)
                continue;
            if (i == draggingIndex) {
                drawCard(g, startX, y, CARD_W, CARD_H, i, false, true);
            } else {
                boolean hover = inside(mouseX, mouseY, startX, y, CARD_W, CARD_H) && !submitted;
                drawCard(g, startX, y, CARD_W, CARD_H, i, hover, false);
            }
        }
        g.disableScissor();
    }

    private void drawCard(GuiGraphics g, int x, int y, int w, int h, int index, boolean hover, boolean ghost) {
        int border, bgTop, bgBottom;
        if (ghost) {
            border = 0x88FFFFFF;
            bgTop = 0x442B2112;
            bgBottom = 0x44112536;
        } else {
            border = hover ? GOLD : 0xFF5A4530;
            bgTop = hover ? 0xFF2B2112 : 0xFF1A1008;
            bgBottom = hover ? 0xFF112536 : 0xFF0B1722;
        }
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBottom);
        g.renderOutline(x, y, w, h, border);
        if (!ghost) {
            g.fill(x + 1, y + 1, x + w - 1, y + 3, hover ? GOLD : 0x33FFE8C0);
        }

        String roleId = currentOrder.get(index);
        if (roleId.equals("random")) {
            int color = ghost ? (GOLD & 0x00FFFFFF) | 0x88000000 : GOLD;
            g.drawCenteredString(font, Component.translatable("gui.sre.volunteer.random"), x + w / 2, y + h / 2 - 4,
                    color);
        } else {
            SRERole role = getRoleByPath(roleId);
            if (role != null) {
                int nameColor = ghost ? ((role.getColor() & 0x00FFFFFF) | 0x88000000) : (role.getColor() | 0xFF000000);
                Component name = RoleUtils.getRoleName(role).withColor(role.getColor());
                g.drawCenteredString(font, name, x + w / 2, y + 8, nameColor);
                Component faction = getFactionText(role);
                int factionColor = ghost ? (faction.getStyle().getColor().getValue() & 0x00FFFFFF) | 0x88000000
                        : faction.getStyle().getColor().getValue();
                g.drawCenteredString(font, faction, x + w / 2, y + 24, factionColor);
            } else {
                int color = ghost ? (TEXT & 0x00FFFFFF) | 0x88000000 : TEXT;
                g.drawCenteredString(font, roleId, x + w / 2, y + h / 2 - 4, color);
            }
        }
        Component pos = Component.literal("#" + (index + 1)).withStyle(ChatFormatting.GRAY);
        g.drawString(font, pos, x + 4, y + 4, ghost ? MUTED : GOLD);
    }

    private void drawDraggedCard(GuiGraphics g, int mouseX, int mouseY) {
        int x = mouseX - dragOffsetX;
        int y = mouseY - dragOffsetY;
        drawCard(g, x, y, CARD_W, CARD_H, draggingIndex, true, false);
    }

    private void drawScrollbar(GuiGraphics g) {
        if (maxScrollY <= 0)
            return;
        int barX = width / 2 + CARD_W / 2 + 10;
        int barY = VIEWPORT_TOP;
        int barH = (height - VIEWPORT_TOP - VIEWPORT_BOTTOM_PAD);
        int barW = 4;
        int thumbH = Math.max(18, (int) (barH * barH / (float) (maxScrollY + barH)));
        int thumbY = barY + (int) ((barH - thumbH) * (scrollY / maxScrollY));
        g.fill(barX, barY, barX + barW, barY + barH, 0x661A1008);
        g.fill(barX, thumbY, barX + barW, thumbY + thumbH, GOLD);
    }

    private void drawWaiting(GuiGraphics g) {
        int remaining = VolunteerCache.getSmoothRemainingTime();
        int sec = remaining / 20;
        Component waitText = Component.translatable("gui.sre.volunteer.calculating", sec);
        g.drawCenteredString(font, waitText, width / 2, height / 2, TEXT);
        String time = String.format("%d:%02d", sec / 60, sec % 60);
        g.drawCenteredString(font, time, width / 2, height / 2 + 16, GOLD);
    }

    private void drawResult(GuiGraphics g, int mouseX, int mouseY) {
        SRERole myRole = getRoleByPath(myFinalRoleId);
        if (myRole == null) {
            g.drawCenteredString(font, Component.translatable("gui.sre.volunteer.waiting"), width / 2, height / 2,
                    TEXT);
            return;
        }

        // 面板占据中央偏右的大部分区域（可根据需要调整）
        int panelX = width / 4;
        int panelY = 40;
        int panelW = width / 2;
        int panelH = height - 80;
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);

        // 职业名称与阵营
        Component name = RoleUtils.getRoleName(myRole).withColor(myRole.getColor());
        g.drawCenteredString(font, name, panelX + panelW / 2, panelY + 15, myRole.getColor() | 0xFF000000);
        Component faction = getFactionText(myRole);
        g.drawCenteredString(font, faction, panelX + panelW / 2, panelY + 30,
                faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT);

        // 介绍文本（带剪裁，防止过长溢出）
        int descY = panelY + 48;
        int descW = panelW - PAD * 2;
        List<FormattedCharSequence> lines = getRoleIntroLines(myRole, descW);
        g.enableScissor(panelX + PAD, descY, panelX + panelW - PAD, panelY + panelH - PAD);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = descY + i * 12;
            if (lineY + 12 > panelY + panelH - PAD)
                break;
            g.drawString(font, lines.get(i), panelX + PAD, lineY, TEXT);
        }
        g.disableScissor();

        // 底部提示
        Component hint = Component.translatable("gui.sre.volunteer.result_hint").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, hint, width / 2, height - 20, MUTED);
    }

    private List<FormattedCharSequence> getRoleIntroLines(SRERole role, int wrapW) {
        return font.split(RoleUtils.getRoleDescription(role), wrapW);
    }

    // ========== 鼠标事件 ==========
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (submitted)
            return super.mouseClicked(mx, my, button);
        if (button == 0 && VolunteerCache.getPhase() == Phase.COMMIT) {
            int startX = width / 2 - CARD_W / 2;
            for (int i = 0; i < volunteerCount; i++) {
                int y = VIEWPORT_TOP + i * (CARD_H + GAP) - (int) scrollY;
                if (inside(mx, my, startX, y, CARD_W, CARD_H)) {
                    draggingIndex = i;
                    dragOffsetX = (int) (mx - startX);
                    dragOffsetY = (int) (my - y);
                    return true;
                }
            }
            // 滚动条拖拽
            if (maxScrollY > 0) {
                int barX = width / 2 + CARD_W / 2 + 10;
                int barY = VIEWPORT_TOP;
                int barH = (height - VIEWPORT_TOP - VIEWPORT_BOTTOM_PAD);
                if (inside(mx, my, barX, barY, 8, barH)) {
                    float ratio = (float) ((my - barY) / barH);
                    scrollY = Math.max(0, Math.min(maxScrollY, ratio * maxScrollY));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingIndex >= 0) {
            int startX = width / 2 - CARD_W / 2;
            int targetIndex = -1;
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < volunteerCount; i++) {
                int y = VIEWPORT_TOP + i * (CARD_H + GAP) - (int) scrollY;
                double cx = startX + CARD_W / 2.0;
                double cy = y + CARD_H / 2.0;
                double dist = Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(my - cy, 2));
                if (dist < minDist && i != draggingIndex) {
                    minDist = dist;
                    targetIndex = i;
                }
            }
            if (targetIndex >= 0) {
                Collections.swap(currentOrder, draggingIndex, targetIndex);
            }
            draggingIndex = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return draggingIndex >= 0 || super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (VolunteerCache.getPhase() == Phase.COMMIT && maxScrollY > 0) {
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - scrollY * 10));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ========== 辅助方法 ==========
    private Component getFactionText(SRERole role) {
        if (role.isInnocent())
            return Component.translatable("display.type.role.innocent").withStyle(s -> s.withColor(0xFF44BB66));
        if (role.canUseKiller())
            return Component.translatable("display.type.role.killer").withStyle(s -> s.withColor(0xFFCC2233));
        if (role.isNeutrals())
            return Component.translatable("display.type.role.neutral_special").withStyle(s -> s.withColor(0xFFCCAA22));
        if (role.isVigilanteTeam())
            return Component.translatable("display.type.role.vigilante").withStyle(s -> s.withColor(0xFF22BBCC));
        return Component.literal("?").withStyle(ChatFormatting.GRAY);
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
        for (String ns : List.of("noellesroles", "starrailexpress", "trainmurdermystery")) {
            id = ResourceLocation.fromNamespaceAndPath(ns, path);
            SRERole role = TMMRoles.ROLES.get(id);
            if (role != null)
                return role;
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(new WithParentScreenPauseScreen(this, false));
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
}