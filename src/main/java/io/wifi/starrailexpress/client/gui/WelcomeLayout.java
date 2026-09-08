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

package io.wifi.starrailexpress.client.gui;

/** Centers the complete announcement block, reserving space for all staged paragraphs. */
public record WelcomeLayout(float top, float scale, float titleY, float ruleY, float premiseY,
        float goalY, float contentHeight) {
    public static WelcomeLayout of(int screenHeight, float titleHeight, float premiseHeight, float goalHeight) {
        float titleY = 25;
        float ruleY = titleY + titleHeight + 12;
        float premiseY = ruleY + 17;
        float goalY = premiseY + premiseHeight + 14;
        float contentHeight = goalY + goalHeight;
        float scale = Math.min(1, Math.max(1, screenHeight - 72) / contentHeight);
        return new WelcomeLayout((screenHeight - contentHeight * scale) / 2,
                scale, titleY, ruleY, premiseY, goalY, contentHeight);
    }
}
