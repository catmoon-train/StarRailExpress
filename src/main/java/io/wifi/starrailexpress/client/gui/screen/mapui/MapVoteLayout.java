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

package io.wifi.starrailexpress.client.gui.screen.mapui;

import io.wifi.starrailexpress.content.vote.client.VoteFlowFrame;

/** Geometry shared by drawing and hit testing, in Minecraft GUI-scaled pixels. */
public record MapVoteLayout(VoteFlowFrame.Bounds board, int contentX, int contentY, int contentWidth,
        int contentHeight, int infoWidth, int routeY, int routeHeight, int footerY, int stationWidth) {
    public static MapVoteLayout of(int width, int height) {
        var b = VoteFlowFrame.layout(width, height);
        int routeHeight = b.h() < 300 ? 52 : 68;
        int footerY = b.y() + b.h() - 24;
        int routeY = footerY - routeHeight - 8;
        int contentX = b.x() + 12;
        // Destination eyebrow shares the countdown row, leaving more room for details at GUI scale 3/4.
        int contentY = b.y() + 40;
        int contentWidth = b.w() - 24;
        int infoWidth = Math.min(380, Math.round(contentWidth * (b.w() < 520 ? 0.68F : 0.50F)));
        int stationWidth = Math.clamp((contentWidth - 48) / (b.w() < 520 ? 3 : 5), 68, 144);
        return new MapVoteLayout(b, contentX, contentY, contentWidth, routeY - contentY - 10,
                infoWidth, routeY, routeHeight, footerY, stationWidth);
    }

    public int routeLeft() { return contentX + 22; }
    public int routeRight() { return contentX + contentWidth - 22; }
    public int buttonWidth() { return Math.min(140, contentWidth / 2); }
    public int buttonX() { return contentX + contentWidth - buttonWidth(); }
    public boolean compact() { return contentHeight < 140; }
}
