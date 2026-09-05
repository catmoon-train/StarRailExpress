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

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.loading.SreUiStyle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.MediumAnswerC2SPacket;
import org.agmas.noellesroles.role_data.innocence.MediumRoleData;

@Environment(EnvType.CLIENT)
public class MediumAnswerScreen extends Screen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 168;
    private static final int CARD_W = 186;
    private static final int CARD_H = 36;
    private static final int GAP = 8;

    private final String mediumName;
    private final long sessionEndTick;
    private final float[] hoverAnim = new float[4];
    private int panelX;
    private int panelY;

    public MediumAnswerScreen(String mediumName, long sessionEndTick) {
        super(Component.translatable("screen.noellesroles.medium.title"));
        this.mediumName = mediumName;
        this.sessionEndTick = sessionEndTick;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = this.height - PANEL_H - 28;
        SreUiStyle.drawPanel(g, this.panelX, this.panelY, PANEL_W, PANEL_H, 1.0F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        long remainingTicks = this.sessionEndTick - SREClient.getTicksFromGameStart();
        if (remainingTicks <= 0) {
            this.minecraft.setScreen(null);
            return;
        }
        int remainingSeconds = (int) ((remainingTicks + 19) / 20);
        int timerColor = remainingSeconds <= 5 ? 0xFFE06B65 : 0xFF5EB7D8;

        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.medium.title").withStyle(ChatFormatting.BOLD),
                this.width / 2, this.panelY + 8, SreUiStyle.GOLD);
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.medium.hint", this.mediumName),
                this.width / 2, this.panelY + 22, SreUiStyle.BODY);
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.medium.remaining", remainingSeconds),
                this.width / 2, this.panelY + 34, timerColor);

        for (int i = 0; i < 4; i++) {
            int[] box = cardBox(i);
            boolean hovered = isInRect(mouseX, mouseY, box[0], box[1], CARD_W, CARD_H);
            this.hoverAnim[i] += ((hovered ? 1.0F : 0.0F) - this.hoverAnim[i]) * 0.22F;
            int bg = SreUiStyle.blend(0xFF1A1008, 0xFFC9A84C, 0.12F + 0.20F * this.hoverAnim[i]);
            int border = this.hoverAnim[i] > 0.5F ? SreUiStyle.GOLD : 0xFF5A4530;
            g.fill(box[0], box[1], box[0] + CARD_W, box[1] + CARD_H, bg);
            g.renderOutline(box[0], box[1], CARD_W, CARD_H, border);
            g.drawCenteredString(this.font, answerLabel(i),
                    box[0] + CARD_W / 2, box[1] + (CARD_H - 8) / 2, SreUiStyle.TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < 4; i++) {
                int[] box = cardBox(i);
                if (isInRect((int) mouseX, (int) mouseY, box[0], box[1], CARD_W, CARD_H)) {
                    ClientPlayNetworking.send(new MediumAnswerC2SPacket(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int[] cardBox(int index) {
        int col = index % 2;
        int row = index / 2;
        int gridW = CARD_W * 2 + GAP;
        int startX = this.panelX + (PANEL_W - gridW) / 2;
        int startY = this.panelY + 52;
        return new int[] {
                startX + col * (CARD_W + GAP),
                startY + row * (CARD_H + GAP)
        };
    }

    private static Component answerLabel(int id) {
        MediumRoleData.SeanceAnswer answer = MediumRoleData.SeanceAnswer.fromId(id);
        return answer == null ? Component.empty() : answer.translatable();
    }

    private static boolean isInRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
