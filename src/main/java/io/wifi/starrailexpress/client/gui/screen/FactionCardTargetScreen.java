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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 自定义卡的目标阵营选择屏：从背包界面点自定义卡进入。
 *
 * <p>
 * 这里只负责「选哪个阵营、把命令发出去」：选中后走
 * {@code /sre:pass activate custom <阵营>}，服务端的扣卡 / 强制 / 退卡规则统一在
 * {@code io.wifi.starrailexpress.backpack.FactionCardForcing}。
 * 关闭（ESC）回到来源界面，不消耗卡。
 */
public class FactionCardTargetScreen extends Screen {

    /** 可选目标：与 {@link FactionCardType#isSelectableTarget()} 一致，顺序即展示顺序。 */
    private static final FactionCardType[] TARGETS = {
            FactionCardType.KILLER, FactionCardType.CIVILIAN,
            FactionCardType.NEUTRAL, FactionCardType.NEUTRAL_FOR_KILLER };

    private static final int PAD = 8;
    private static final int HEADER_H = 44;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 34;
    private static final int ROW_GAP = 6;

    private final LocalPlayer player;
    private final Screen parent;
    private final int ownedCustomCards;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowX;
    private int rowW;

    public FactionCardTargetScreen(Screen parent) {
        super(Component.translatable("sre.backpack.custom.title"));
        this.player = Minecraft.getInstance().player;
        this.parent = parent;
        this.ownedCustomCards = ClientPlayerDataCache.backpack(player.getUUID())
                .cards.getOrDefault(FactionCardType.CUSTOM, 0);
    }

    private void computeLayout() {
        int rows = TARGETS.length;
        panelW = Mth.clamp((int) (width * 0.5F), 260, 360);
        panelH = PAD * 2 + HEADER_H + rows * ROW_H + (rows - 1) * ROW_GAP + FOOTER_H;
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        rowX = panelX + PAD;
        rowW = panelW - PAD * 2;
    }

    private int rowY(int index) {
        return panelY + PAD + HEADER_H + index * (ROW_H + ROW_GAP);
    }

    private static int accentOf(FactionCardType type) {
        return switch (type) {
            case KILLER -> RoleTeam.KILLER.color();
            case CIVILIAN -> RoleTeam.CIVILIAN.color();
            case NEUTRAL -> RoleTeam.NEUTRAL.color();
            case NEUTRAL_FOR_KILLER -> RoleTeam.NEUTRAL_KILLER.color();
            default -> SRETheme.GOLD;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        super.render(g, mouseX, mouseY, partialTick);

        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, SRETheme.SURFACE_TOP,
                SRETheme.SURFACE_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, SRETheme.BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, SRETheme.DECOR_LINE);

        g.drawCenteredString(font, title, panelX + panelW / 2, panelY + PAD + 2, SRETheme.TITLE);
        g.drawCenteredString(font, Component.translatable("sre.backpack.custom.subtitle", ownedCustomCards),
                panelX + panelW / 2, panelY + PAD + 18, SRETheme.MUTED);

        for (int i = 0; i < TARGETS.length; i++) {
            renderTargetRow(g, i, mouseX, mouseY);
        }

        int footerY = panelY + panelH - FOOTER_H;
        g.drawString(font, Component.translatable("sre.backpack.custom.hint"), rowX, footerY + 4,
                SRETheme.MUTED, false);
        Component close = Component.translatable("sre.backpack.close_hint");
        g.drawString(font, close, rowX + rowW - font.width(close), footerY + 4, SRETheme.MUTED, false);
    }

    private void renderTargetRow(GuiGraphics g, int index, int mouseX, int mouseY) {
        FactionCardType type = TARGETS[index];
        int accent = accentOf(type);
        int x = rowX;
        int y = rowY(index);
        boolean hovered = inside(mouseX, mouseY, x, y, rowW, ROW_H);

        if (hovered) {
            g.fillGradient(x, y, x + rowW, y + ROW_H,
                    SRETheme.blendColors(SRETheme.SURFACE_TOP, accent, 0.42F),
                    SRETheme.blendColors(SRETheme.SURFACE_BOTTOM, accent, 0.24F));
        } else {
            g.fillGradient(x, y, x + rowW, y + ROW_H,
                    SRETheme.blendColors(SRETheme.SURFACE_TOP, accent, 0.18F),
                    SRETheme.blendColors(SRETheme.SURFACE_BOTTOM, accent, 0.08F));
        }
        g.renderOutline(x, y, rowW, ROW_H, hovered ? SRETheme.GOLD : SRETheme.CARD_BORDER);
        g.fill(x + 1, y + 1, x + 4, y + ROW_H - 1, accent);

        g.drawString(font, Component.translatable(type.displayName), x + 12, y + 5, SRETheme.TEXT, false);
        g.drawString(font, Component.translatable("sre.backpack.desc." + type.questKey), x + 12, y + 18,
                hovered ? SRETheme.BODY : SRETheme.MUTED, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, SRETheme.SCREEN_TOP, SRETheme.SCREEN_BOTTOM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < TARGETS.length; i++) {
                if (inside(mouseX, mouseY, rowX, rowY(i), rowW, ROW_H)) {
                    sendCommand("sre:pass activate " + FactionCardType.CUSTOM.questKey + " "
                            + TARGETS[i].questKey);
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }
}
