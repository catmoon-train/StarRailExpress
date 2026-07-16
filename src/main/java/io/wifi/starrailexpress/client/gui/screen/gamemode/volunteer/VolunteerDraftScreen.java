package io.wifi.starrailexpress.client.gui.screen.gamemode.volunteer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.utils.RoleUtils;

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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class VolunteerDraftScreen extends Screen {

    private static final int PAD = 12;
    private static final int GAP = 8;
    private static final int CARD_W = 160;
    private static final int CARD_H = 48;
    private static final int ARROW_BTN_SIZE = 14;
    private static final int RANDOM_BTN_SIZE = 14;
    private static final int SCROLL_BAR_WIDTH = 4;

    private static final int VIEWPORT_TOP = 64;
    private static final int VIEWPORT_BOTTOM_RESERVE = 50;

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

    private List<String> currentOrder;
    private int volunteerCount;
    private Button submitButton;

    private int draggingIndex = -1;
    private int dragOffsetX, dragOffsetY;
    private boolean submitted = false;

    private double scrollY = 0;
    private double maxScrollY = 0;

    private boolean scrolling = false;
    private double scrollBarClickOffset = 0;

    private String myFinalRoleId = "";

    public VolunteerDraftScreen() {
        super(Component.translatable("gui.sre.volunteer.title").withStyle(ChatFormatting.GOLD));
        // super(Component.translatable("gui.sre.volunteer.subtitle.1").withStyle(ChatFormatting.GOLD));
        // super(Component.translatable("gui.sre.volunteer.subtitle.2").withStyle(ChatFormatting.GOLD));
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
        submitButton = Button.builder(Component.translatable("gui.sre.volunteer.submit"), btn -> onSubmit())
                .bounds(width / 2 - 50, height - 36, 100, 20)
                .build();
        addRenderableWidget(submitButton);
        if (submitted)
            submitButton.active = false;
    }

    private void updateScrollLimits() {
        int contentHeight = volunteerCount * (CARD_H + GAP);
        int viewportHeight = height - VIEWPORT_TOP - VIEWPORT_BOTTOM_RESERVE;
        maxScrollY = Math.max(0, contentHeight - viewportHeight);
        scrollY = Mth.clamp(scrollY, 0, maxScrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (VolunteerCache.getPhase() == Phase.COMMIT) {
            updateScrollLimits();
            if (submitButton != null) {
                submitButton.setPosition(width / 2 - 50, height - 36);
            }
        } else if (VolunteerCache.getPhase() == Phase.RESULT && myFinalRoleId.isEmpty()) {
            updateResult();
        }
    }

    public void updateResult() {
        if (VolunteerCache.getPhase() == Phase.RESULT) {
            myFinalRoleId = VolunteerCache.getMyFinalRole();
        }
    }

    private void onSubmit() {
        if (submitted)
            return;
        List<Integer> prefs = new ArrayList<>();
        List<String> originals = VolunteerCache.getMyCandidates();
        for (String id : currentOrder) {
            if (id.equals("random"))
                prefs.add(-1);
            else {
                int idx = originals.indexOf(id);
                prefs.add(idx >= 0 ? idx : -1);
            }
        }
        ClientPlayNetworking.send(new VolunteerCommitC2SPacket(prefs));
        submitted = true;
        if (submitButton != null)
            submitButton.active = false;
    }

    private void moveUp(int index) {
        if (!submitted && index > 0 && index < volunteerCount) {
            Collections.swap(currentOrder, index, index - 1);
        }
    }

    private void moveDown(int index) {
        if (!submitted && index < volunteerCount - 1) {
            Collections.swap(currentOrder, index, index + 1);
        }
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
            drawResult(g);
        }

    }

    private void drawTitleBar(GuiGraphics g) {
        g.drawString(font, title, PAD, 10, GOLD, false);
        g.drawString(font, Component.translatable("gui.sre.volunteer.subtitle.1"), PAD, 20,
                ChatFormatting.GRAY.getColor() | 0xff000000, false);
        g.drawString(font, Component.translatable("gui.sre.volunteer.subtitle.2"), PAD, 30,
                ChatFormatting.GRAY.getColor() | 0xff000000, false);
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
        g.drawCenteredString(font, timer, width / 2, VIEWPORT_TOP - 30, color);
    }

    private void drawCardsInViewport(GuiGraphics g, int mouseX, int mouseY) {
        int cardStartX = width / 2 - CARD_W / 2;
        int viewportTop = VIEWPORT_TOP;
        int viewportBottom = height - VIEWPORT_BOTTOM_RESERVE;
        g.enableScissor(0, viewportTop, width, viewportBottom);
        for (int i = 0; i < volunteerCount; i++) {
            int y = viewportTop + i * (CARD_H + GAP) - (int) scrollY;
            if (y + CARD_H < viewportTop || y > viewportBottom)
                continue;
            if (i == draggingIndex) {
                drawCard(g, cardStartX, y, CARD_W, CARD_H, i, false, true);
            } else {
                boolean hoverCard = inside(mouseX, mouseY, cardStartX, y, CARD_W, CARD_H) && !submitted;
                drawCard(g, cardStartX, y, CARD_W, CARD_H, i, hoverCard, false);
            }

            int btnX = cardStartX + CARD_W + 4;
            int btnY = y;
            if (i > 0 && !submitted) {
                boolean hoverUp = inside(mouseX, mouseY, btnX, btnY, ARROW_BTN_SIZE, ARROW_BTN_SIZE);
                drawArrowButton(g, btnX, btnY, true, hoverUp);
            }
            if (i < volunteerCount - 1 && !submitted) {
                boolean hoverDown = inside(mouseX, mouseY, btnX, btnY + CARD_H - ARROW_BTN_SIZE,
                        ARROW_BTN_SIZE, ARROW_BTN_SIZE);
                drawArrowButton(g, btnX, btnY + CARD_H - ARROW_BTN_SIZE, false, hoverDown);
            }
            int randomX = cardStartX - RANDOM_BTN_SIZE - 4;
            boolean hoverRandom = inside(mouseX, mouseY, randomX, btnY + (CARD_H - RANDOM_BTN_SIZE) / 2,
                    RANDOM_BTN_SIZE, RANDOM_BTN_SIZE) && !submitted;
            drawRandomButton(g, randomX, btnY + (CARD_H - RANDOM_BTN_SIZE) / 2, hoverRandom);
        }
        g.disableScissor();
    }

    private void drawCard(GuiGraphics g, int x, int y, int w, int h, int index, boolean hover, boolean ghost) {
        int border = ghost ? 0x88FFFFFF : (hover ? GOLD : 0xFF5A4530);
        int bgTop = ghost ? 0x442B2112 : (hover ? 0xFF2B2112 : 0xFF1A1008);
        int bgBottom = ghost ? 0x44112536 : (hover ? 0xFF112536 : 0xFF0B1722);
        g.fillGradient(x, y, x + w, y + h, bgTop, bgBottom);
        g.renderOutline(x, y, w, h, border);
        if (!ghost) {
            g.fill(x + 1, y + 1, x + w - 1, y + 3, hover ? GOLD : 0x33FFE8C0);
        }

        String roleId = currentOrder.get(index);
        if ("random".equals(roleId)) {
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
                int fColor = ghost ? (faction.getStyle().getColor().getValue() & 0x00FFFFFF) | 0x88000000
                        : faction.getStyle().getColor().getValue();
                g.drawCenteredString(font, faction, x + w / 2, y + 24, fColor);
            } else {
                int color = ghost ? (TEXT & 0x00FFFFFF) | 0x88000000 : TEXT;
                g.drawCenteredString(font, roleId, x + w / 2, y + h / 2 - 4, color);
            }
        }
        Component pos = Component.literal("#" + (index + 1)).withStyle(ChatFormatting.GRAY);
        g.drawString(font, pos, x + 4, y + 4, ghost ? MUTED : GOLD);
    }

    private void drawArrowButton(GuiGraphics g, int x, int y, boolean up, boolean hover) {
        int color = hover ? GOLD : MUTED;
        int size = ARROW_BTN_SIZE;
        g.fill(x, y, x + size, y + size, hover ? 0x552A5A42 : 0x331A1008);
        g.renderOutline(x, y, size, size, color);
        String arrow = up ? "▲" : "▼";
        g.drawCenteredString(font, arrow, x + size / 2, y + size / 2 - 4, color);
    }

    private void drawRandomButton(GuiGraphics g, int x, int y, boolean hover) {
        int color = hover ? GOLD : MUTED;
        int size = RANDOM_BTN_SIZE;
        g.fill(x, y, x + size, y + size, hover ? 0x552A5A42 : 0x331A1008);
        g.renderOutline(x, y, size, size, color);
        g.drawCenteredString(font, "?", x + size / 2, y + size / 2 - 4, color);
    }

    private void drawDraggedCard(GuiGraphics g, int mouseX, int mouseY) {
        int x = mouseX - dragOffsetX;
        int y = mouseY - dragOffsetY;
        drawCard(g, x, y, CARD_W, CARD_H, draggingIndex, true, false);
    }

    private void drawScrollbar(GuiGraphics g) {
        if (maxScrollY <= 0)
            return;
        int barX = width / 2 + CARD_W / 2 + ARROW_BTN_SIZE + 8;
        int barY = VIEWPORT_TOP;
        int barH = height - VIEWPORT_TOP - VIEWPORT_BOTTOM_RESERVE;
        int thumbH = Math.max(20, (int) (barH * barH / (double) (maxScrollY + barH)));
        int thumbY = barY + (int) ((barH - thumbH) * (scrollY / maxScrollY));
        g.fill(barX, barY, barX + SCROLL_BAR_WIDTH, barY + barH, 0x661A1008);
        g.fill(barX, thumbY, barX + SCROLL_BAR_WIDTH, thumbY + thumbH, GOLD);
    }

    private void drawWaiting(GuiGraphics g) {
        int remaining = VolunteerCache.getSmoothRemainingTime();
        int sec = remaining / 20;
        Component waitText = Component.translatable("gui.sre.volunteer.calculating", sec);
        g.drawCenteredString(font, waitText, width / 2, height / 2, TEXT);
        String time = String.format("%d:%02d", sec / 60, sec % 60);
        g.drawCenteredString(font, time, width / 2, height / 2 + 16, GOLD);
    }

    private void drawResult(GuiGraphics g) {
        SRERole myRole = getRoleByPath(myFinalRoleId);
        if (myRole == null) {
            g.drawCenteredString(font, Component.translatable("gui.sre.volunteer.waiting"), width / 2, height / 2,
                    TEXT);
            return;
        }
        int panelX = width / 4;
        int panelY = 40;
        int panelW = width / 2;
        int panelH = height - 80;
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);

        Component name = RoleUtils.getRoleName(myRole).withColor(myRole.getColor());
        g.drawCenteredString(font, name, panelX + panelW / 2, panelY + 15, myRole.getColor() | 0xFF000000);
        Component faction = getFactionText(myRole);
        g.drawCenteredString(font, faction, panelX + panelW / 2, panelY + 30,
                faction.getStyle().getColor() != null ? faction.getStyle().getColor().getValue() : TEXT);

        int descY = panelY + 48;
        int descW = panelW - PAD * 2;
        List<FormattedCharSequence> lines = font.split(RoleUtils.getRoleDescription(myRole), descW);
        g.enableScissor(panelX + PAD, descY, panelX + panelW - PAD, panelY + panelH - PAD);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = descY + i * 12;
            if (lineY + 12 > panelY + panelH - PAD)
                break;
            g.drawString(font, lines.get(i), panelX + PAD, lineY, TEXT);
        }
        g.disableScissor();

        Component hint = Component.translatable("gui.sre.volunteer.result_hint").withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, hint, width / 2, height - 20, MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || submitted)
            return super.mouseClicked(mx, my, button);
        if (VolunteerCache.getPhase() != Phase.COMMIT)
            return super.mouseClicked(mx, my, button);

        if (maxScrollY > 0) {
            int barX = width / 2 + CARD_W / 2 + ARROW_BTN_SIZE + 8;
            int barY = VIEWPORT_TOP;
            int barH = height - VIEWPORT_TOP - VIEWPORT_BOTTOM_RESERVE;
            if (mx >= barX && mx <= barX + SCROLL_BAR_WIDTH && my >= barY && my <= barY + barH) {
                int thumbH = Math.max(20, (int) (barH * barH / (double) (maxScrollY + barH)));
                int thumbY = barY + (int) ((barH - thumbH) * (scrollY / maxScrollY));
                if (my >= thumbY && my <= thumbY + thumbH) {
                    scrolling = true;
                    scrollBarClickOffset = my - thumbY;
                } else {
                    float ratio = (float) ((my - barY) / barH);
                    scrollY = Mth.clamp(ratio * maxScrollY, 0, maxScrollY);
                }
                return true;
            }
        }

        int cardStartX = width / 2 - CARD_W / 2;
        for (int i = 0; i < volunteerCount; i++) {
            int y = VIEWPORT_TOP + i * (CARD_H + GAP) - (int) scrollY;
            if (y + CARD_H < VIEWPORT_TOP || y > height - VIEWPORT_BOTTOM_RESERVE)
                continue;

            if (i > 0) {
                int btnX = cardStartX + CARD_W + 4;
                if (inside(mx, my, btnX, y, ARROW_BTN_SIZE, ARROW_BTN_SIZE)) {
                    moveUp(i);
                    return true;
                }
            }
            if (i < volunteerCount - 1) {
                int btnX = cardStartX + CARD_W + 4;
                if (inside(mx, my, btnX, y + CARD_H - ARROW_BTN_SIZE, ARROW_BTN_SIZE, ARROW_BTN_SIZE)) {
                    moveDown(i);
                    return true;
                }
            }
            int randomX = cardStartX - RANDOM_BTN_SIZE - 4;
            if (inside(mx, my, randomX, y + (CARD_H - RANDOM_BTN_SIZE) / 2, RANDOM_BTN_SIZE, RANDOM_BTN_SIZE)) {
                toggleRandom(i);
                return true;
            }
        }

        for (int i = 0; i < volunteerCount; i++) {
            int y = VIEWPORT_TOP + i * (CARD_H + GAP) - (int) scrollY;
            if (inside(mx, my, cardStartX, y, CARD_W, CARD_H)) {
                draggingIndex = i;
                dragOffsetX = (int) (mx - cardStartX);
                dragOffsetY = (int) (my - y);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (scrolling) {
            scrolling = false;
            return true;
        }
        if (draggingIndex >= 0) {
            int cardStartX = width / 2 - CARD_W / 2;
            int targetIndex = -1;
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < volunteerCount; i++) {
                int y = VIEWPORT_TOP + i * (CARD_H + GAP) - (int) scrollY;
                double cx = cardStartX + CARD_W / 2.0;
                double cy = y + CARD_H / 2.0;
                double dist = Math.sqrt(Math.pow(mx - cx, 2) + Math.pow(my - cy, 2));
                if (dist < minDist && i != draggingIndex) {
                    minDist = dist;
                    targetIndex = i;
                }
            }
            if (targetIndex >= 0)
                Collections.swap(currentOrder, draggingIndex, targetIndex);
            draggingIndex = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrolling) {
            int barY = VIEWPORT_TOP;
            int barH = height - VIEWPORT_TOP - VIEWPORT_BOTTOM_RESERVE;
            int thumbH = Math.max(20, (int) (barH * barH / (double) (maxScrollY + barH)));
            float trackLength = barH - thumbH;
            float pos = Mth.clamp((float) (my - barY - scrollBarClickOffset), 0, trackLength);
            scrollY = (pos / trackLength) * maxScrollY;
            return true;
        }
        if (draggingIndex >= 0 && maxScrollY > 0) {
            int scrollThreshold = 30;
            if (my < VIEWPORT_TOP + scrollThreshold)
                scrollY = Math.max(0, scrollY - 5);
            else if (my > height - VIEWPORT_BOTTOM_RESERVE - scrollThreshold)
                scrollY = Math.min(maxScrollY, scrollY + 5);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (VolunteerCache.getPhase() == Phase.COMMIT && maxScrollY > 0) {
            this.scrollY = Mth.clamp(this.scrollY - scrollY * 10, 0, maxScrollY);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

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