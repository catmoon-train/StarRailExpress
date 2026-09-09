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

package net.exmo.sre.loading;

/**
 * 开局免责声明屏的响应式布局（docs/ui_style.md §4）。
 */
public record GameDisclaimerLayout(
        int panelX, int panelY, int panelW, int panelH,
        int headerH, int footerH,
        int contentX, int contentY, int contentW, int contentH,
        int sbX, int sbTop, int sbBot, int sbW,
        int buttonX, int buttonY, int buttonW, int buttonH) {

    public static final int PAD = 14;
    public static final int HEADER_H = 40;
    public static final int FOOTER_H = 54;
    public static final int SB_W = 5;
    public static final int BUTTON_W = 168;
    public static final int BUTTON_H = 22;
    public static final int SECTION_INNER_PAD = 8;
    public static final int SECTION_GAP = 8;

    public static GameDisclaimerLayout of(int width, int height) {
        int panelW = Math.min(700, Math.max(280, (int) (width * 0.90F)));
        panelW = Math.min(panelW, Math.max(1, width - 16));
        int panelH = Math.min(Math.max(230, (int) (height * 0.82F)), Math.max(1, height - 20));
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        int contentX = panelX + PAD;
        int contentY = panelY + HEADER_H;
        int contentW = Math.max(40, panelW - PAD * 2 - SB_W - 8);
        int contentH = Math.max(40, panelH - HEADER_H - FOOTER_H);

        int sbX = panelX + panelW - PAD - SB_W;
        int sbTop = contentY + 4;
        int sbBot = contentY + contentH - 4;

        int buttonW = Math.min(BUTTON_W, Math.max(80, panelW - PAD * 2));
        int buttonH = BUTTON_H;
        int buttonX = panelX + (panelW - buttonW) / 2;
        int buttonY = panelY + panelH - 8 - buttonH;

        return new GameDisclaimerLayout(
                panelX, panelY, panelW, panelH,
                HEADER_H, FOOTER_H,
                contentX, contentY, contentW, contentH,
                sbX, sbTop, sbBot, SB_W,
                buttonX, buttonY, buttonW, buttonH);
    }

    public boolean inPanel(double x, double y) {
        return inRect(x, y, panelX, panelY, panelW, panelH);
    }

    public boolean inContent(double x, double y) {
        return inRect(x, y, contentX, contentY, contentW + sbW + 8, contentH);
    }

    public boolean inScrollbar(double x, double y) {
        return inRect(x, y, sbX, sbTop, sbW, Math.max(0, sbBot - sbTop));
    }

    public boolean inButton(double x, double y) {
        return inRect(x, y, buttonX, buttonY, buttonW, buttonH);
    }

    public static boolean inRect(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
}
