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

import io.wifi.starrailexpress.network.packet.MobRiotStateS2CPacket;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class MobRiotHudRenderer {
    private static boolean active;
    private static boolean day;
    private static int remainingSeconds;
    private static int tokens;
    private static int tokenGoal;
    private static boolean unlocked;

    private MobRiotHudRenderer() {
    }

    public static void update(MobRiotStateS2CPacket packet) {
        active = packet.active();
        day = packet.day();
        remainingSeconds = packet.remainingSeconds();
        tokens = packet.tokens();
        tokenGoal = packet.tokenGoal();
        unlocked = packet.unlocked();
    }

    public static void reset() {
        active = false;
    }

    public static void render(Font font, FakeGuiGraphics context) {
        if (!active) {
            return;
        }
        Component phase = Component.translatable(day ? "hud.sre.mob_riot.day" : "hud.sre.mob_riot.night",
                remainingSeconds);
        Component tokensLine = Component.translatable("hud.sre.mob_riot.tokens", tokens, tokenGoal);
        int x = context.guiWidth() / 2;
        int y = 26;
        context.drawCenteredString(font, phase, x, y, day ? 0xFFE8B86D : 0xFFC08CFF);
        context.drawCenteredString(font, tokensLine, x, y + 10, 0xFFFFE6A3);
        if (unlocked) {
            context.drawCenteredString(font, Component.translatable("hud.sre.mob_riot.unlocked"), x, y + 20, 0xFF7CFF9A);
        }
    }
}
