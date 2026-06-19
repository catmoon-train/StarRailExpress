package io.wifi.starrailexpress.client.gui.screen.roster;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.roster.RoleRosterState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 职业轮换查看 / 编辑界面的共同基类：负责暖色面板布局、可滚动的职业列表与基础交互。
 */
abstract class AbstractRoleRosterScreen extends Screen {
    protected static final int ROW_H = 22;

    protected RoleRosterState working;
    protected final List<SRERole> roles = new ArrayList<>();

    protected int panelX, panelY, panelW, panelH;
    protected int listTop, listBottom;
    protected float scroll;

    protected AbstractRoleRosterScreen(Component title, RoleRosterState working) {
        super(title);
        this.working = working == null ? RoleRosterState.createDefault() : working;
    }

    /** 编辑界面可加减数量，查看界面只读。 */
    protected abstract boolean editable();

    /** 子类在此添加底部按钮等控件。 */
    protected abstract void buildControls();

    /** 列表中要展示的职业（查看界面只显示名单内职业）。 */
    protected abstract boolean shouldShow(SRERole role);

    @Override
    protected void init() {
        super.init();
        this.panelW = Math.min(440, this.width - 40);
        this.panelH = this.height - 40;
        this.panelX = (this.width - panelW) / 2;
        this.panelY = 20;
        this.listTop = panelY + 52;
        this.listBottom = panelY + panelH - 40;

        rebuildRoles();
        buildControls();
    }

    protected void rebuildRoles() {
        roles.clear();
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (isRosterEligible(role) && shouldShow(role)) {
                roles.add(role);
            }
        }
        clampScroll();
    }

    protected static boolean isRosterEligible(SRERole role) {
        try {
            return role.canBeRandomed() && !role.isOtherModeRole() && role.getOccupiedRoleCount() <= 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 子类可覆盖此方法来显示启用的职业数量，而非列表中显示的数量。 */
    protected int hintCount() {
        return roles.size();
    }

    protected int contentHeight() {
        return roles.size() * ROW_H;
    }

    protected void clampScroll() {
        int viewport = listBottom - listTop;
        float max = Math.max(0, contentHeight() - viewport);
        scroll = Mth.clamp(scroll, 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) scrollY * 16;
        clampScroll();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {

    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        RoleRosterStyle.renderBackdrop(g, this.width, this.height);

        // 主面板
        RoleRosterStyle.drawPanel(g, panelX, panelY, panelW, panelH, RoleRosterStyle.PANEL_BG, RoleRosterStyle.PANEL_BORDER);

        // 标题 + 状态
        g.drawString(this.font, this.title, panelX + 16, panelY + 14, RoleRosterStyle.TITLE, false);
        Component status = working.enabled
                ? Component.translatable("gui.sre.role_roster.status.on")
                : Component.translatable("gui.sre.role_roster.status.off");
        int statusColor = working.enabled ? RoleRosterStyle.ENABLED_GREEN : RoleRosterStyle.DISABLED_RED;
        int statusW = this.font.width(status);
        g.drawString(this.font, status, panelX + panelW - 16 - statusW, panelY + 14, statusColor, false);
        g.drawString(this.font, Component.translatable("gui.sre.role_roster.hint", hintCount()),
                panelX + 16, panelY + 30, RoleRosterStyle.SUBTITLE, false);

        // 列表（裁剪 + 滚动）
        enableScissor(panelX + 1, listTop, panelX + panelW - 1, listBottom);
        try {
            int y = listTop - (int) scroll;
            for (SRERole role : roles) {
                if (y + ROW_H >= listTop - ROW_H && y <= listBottom + ROW_H) {
                    renderRow(g, role, y, mouseX, mouseY);
                }
                y += ROW_H;
            }
        } finally {
            RenderSystem.disableScissor();
        }

        // 滚动条
        renderScrollbar(g);

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderRow(GuiGraphics g, SRERole role, int y, int mouseX, int mouseY) {
        int rowX = panelX + 10;
        int rowW = panelW - 20;
        boolean hovered = mouseX >= rowX && mouseX <= rowX + rowW
                && mouseY >= y && mouseY <= y + ROW_H - 3
                && mouseY >= listTop && mouseY <= listBottom;

        RoleRosterStyle.drawPanel(g, rowX, y, rowW, ROW_H - 3,
                hovered ? RoleRosterStyle.ROW_BG_HOVER : RoleRosterStyle.ROW_BG, RoleRosterStyle.ROW_BORDER);

        int count = working.countFor(role.identifier().toString());
        int nameColor = count > 0 ? (role.color() == 0 ? RoleRosterStyle.TEXT : role.color()) : 0x807060;
        g.drawString(this.font, RoleUtils.getRoleName(role), rowX + 8, y + 6, nameColor, false);

        if (editable()) {
            // 右侧 [-] N [+]
            int bx = rowX + rowW - 8;
            drawStepper(g, "+", bx - 12, y + 4, mouseX, mouseY);
            String countText = String.valueOf(count);
            int cw = this.font.width(countText);
            g.drawString(this.font, countText, bx - 12 - 10 - cw / 2, y + 6,
                    count > 0 ? RoleRosterStyle.ACCENT_HOVER : RoleRosterStyle.MUTED, false);
            drawStepper(g, "-", bx - 12 - 20 - 12, y + 4, mouseX, mouseY);
        } else {
            String countText = "× " + count;
            int cw = this.font.width(countText);
            g.drawString(this.font, countText, rowX + rowW - 8 - cw, y + 6, RoleRosterStyle.ACCENT, false);
        }
    }

    private void drawStepper(GuiGraphics g, String label, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 11 && mouseY >= y && mouseY <= y + 11
                && mouseY >= listTop && mouseY <= listBottom;
        RoleRosterStyle.drawPanel(g, x, y, 11, 11,
                hovered ? RoleRosterStyle.ROW_BG_HOVER : RoleRosterStyle.ROW_BG, RoleRosterStyle.PANEL_BORDER);
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (11 - tw) / 2 + 1, y + 2,
                hovered ? RoleRosterStyle.TEXT_HOVER : RoleRosterStyle.TEXT, false);
    }

    private void renderScrollbar(GuiGraphics g) {
        int viewport = listBottom - listTop;
        int content = contentHeight();
        if (content <= viewport) {
            return;
        }
        int barX = panelX + panelW - 5;
        g.fill(barX, listTop, barX + 3, listBottom, 0x40FFE8C0);
        int thumbH = Math.max(20, (int) ((float) viewport / content * viewport));
        int maxScroll = content - viewport;
        int thumbY = listTop + (int) ((scroll / maxScroll) * (viewport - thumbH));
        g.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0x88C9A84C);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editable() && mouseY >= listTop && mouseY <= listBottom && button == 0) {
            int y = listTop - (int) scroll;
            for (SRERole role : roles) {
                int rowX = panelX + 10;
                int rowW = panelW - 20;
                int bx = rowX + rowW - 8;
                int plusX = bx - 12;
                int minusX = bx - 12 - 20 - 12;
                if (mouseY >= y + 4 && mouseY <= y + 15) {
                    if (mouseX >= plusX && mouseX <= plusX + 11) {
                        adjust(role, 1);
                        return true;
                    }
                    if (mouseX >= minusX && mouseX <= minusX + 11) {
                        adjust(role, -1);
                        return true;
                    }
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void adjust(SRERole role, int delta) {
        String id = role.identifier().toString();
        int next = Math.max(0, working.countFor(id) + delta);
        if (next <= 0) {
            working.roleCounts.remove(id);
        } else {
            working.roleCounts.put(id, next);
        }
    }

    private void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = this.minecraft.getWindow();
        double scale = w.getGuiScale();
        RenderSystem.enableScissor(
                (int) (x0 * scale),
                (int) (w.getScreenHeight() - y1 * scale),
                (int) ((x1 - x0) * scale),
                (int) ((y1 - y0) * scale));
    }
}
