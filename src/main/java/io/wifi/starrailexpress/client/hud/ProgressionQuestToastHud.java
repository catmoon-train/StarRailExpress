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

package io.wifi.starrailexpress.client.hud;

import io.wifi.starrailexpress.network.ProgressionQuestToastPayload;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.ArrayList;
import java.util.List;

public final class ProgressionQuestToastHud {
    private static final int MAX_TOASTS = 4;
    private static final int LIFE_TICKS = 70;
    private static final List<Toast> TOASTS = new ArrayList<>();

    private ProgressionQuestToastHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    public static void push(ProgressionQuestToastPayload payload) {
        if (payload == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        TOASTS.add(new Toast(
                payload.title() == null || payload.title().isBlank()
                        ? Component.translatable("sre.pass.toast.completed")
                        : Component.literal(payload.title()),
                payload.experience(),
                payload.coins(),
                payload.loot(),
                tick));
        while (TOASTS.size() > MAX_TOASTS) {
            TOASTS.remove(0);
        }
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || TOASTS.isEmpty()) {
            return;
        }
        long tick = client.level == null ? 0L : client.level.getGameTime();
        TOASTS.removeIf(toast -> tick - toast.createdTick > LIFE_TICKS);
        int width = graphics.guiWidth();
        int index = 0;
        for (Toast toast : List.copyOf(TOASTS)) {
            long age = tick - toast.createdTick;
            float alpha = age < 8 ? age / 8.0F : age > LIFE_TICKS - 12 ? (LIFE_TICKS - age) / 12.0F : 1.0F;
            alpha = Mth.clamp(alpha, 0.0F, 1.0F);
            int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
            if (a <= 0) {
                index++;
                continue;
            }
            Component reward = rewardLine(toast);
            int toastWidth = Math.min(220, 28 + Math.max(client.font.width(toast.title), client.font.width(reward)));
            int x = width - toastWidth - 8;
            int y = 8 + index * 38;
            graphics.fill(x, y, x + toastWidth, y + 34, (Mth.clamp((int) (alpha * 200.0F), 0, 255) << 24) | 0x1A0C05);
            graphics.fill(x, y, x + toastWidth, y + 2, (a << 24) | 0xD8A442);
            graphics.drawString(client.font, Component.translatable("sre.pass.toast.completed"), x + 8, y + 5,
                    (a << 24) | 0xF4C460, false);
            graphics.drawString(client.font, toast.title, x + 8, y + 15, (a << 24) | 0xEEE4CF, false);
            graphics.drawString(client.font, reward, x + 8, y + 24, (a << 24) | 0xC4B59A, false);
            index++;
        }
    }

    private static Component rewardLine(Toast toast) {
        List<String> parts = new ArrayList<>();
        if (toast.experience > 0) {
            parts.add("+" + toast.experience + " XP");
        }
        if (toast.coins > 0) {
            parts.add("+" + toast.coins);
        }
        if (toast.loot > 0) {
            parts.add("+" + toast.loot + " loot");
        }
        if (parts.isEmpty()) {
            return Component.translatable("sre.pass.toast.reward_none");
        }
        return Component.literal(String.join(" · ", parts));
    }

    private record Toast(Component title, int experience, int coins, int loot, long createdTick) {
    }
}
