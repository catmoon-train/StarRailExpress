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

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

public class TimeRenderer {
    public static TimeNumberRenderer view = new TimeNumberRenderer();
    public static float offsetDelta = 0f;
    // 缓存角色状态，避免每帧查询
    private static boolean cachedCanSeeTime = false;

    public static void renderHud(Font renderer, @NotNull LocalPlayer player, @NotNull FakeGuiGraphics context,
            float delta) {
        // 每20tick更新一次角色权限缓存
        SREGameWorldComponent gameWorldComponent = SREClient.gameComponent;
        if (gameWorldComponent == null)
            return;
        SRERole role = gameWorldComponent.getRole(player);
        cachedCanSeeTime = gameWorldComponent.isRunning() &&
                (role != null && role.canSeeTime() || GameUtils.isPlayerSpectatingOrCreative(player)
                        || SREClient.cachedCanSeeTime);
        if (cachedCanSeeTime) {
            final var timeCCA = SREGameTimeComponent.KEY.get(player.level());
            int time = timeCCA.getTime();
            boolean isFrozen = timeCCA.isTimeFrozen();
            if (Math.abs(view.getTarget() - time) > 10)
                offsetDelta = time > view.getTarget() ? .6f : -.6f;
            if (time < GameConstants.getInTicks(1, 0)) {
                offsetDelta = -0.9f;
            } else {
                offsetDelta = Mth.lerp(delta / 16, offsetDelta, 0f);
            }
            view.setTarget(time);
            view.setFrozen(isFrozen);
            float r = 1f;
            float g = 1f;
            float b = 1f;
            int colour = Mth.color(r, g, b) | 0xFF000000;
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, 6, 0);
            view.render(renderer, context, 0, 0, colour, delta);
            context.pose().popPose();
        }
    }

    public static void tick() {
        view.update();
    }

    public static class TimeNumberRenderer {
        private final Tuple<ScrollingDigit, ScrollingDigit> minutes = new Tuple<>(new ScrollingDigit(7200, false),
                new ScrollingDigit(720, false));
        private final Tuple<ScrollingDigit, ScrollingDigit> seconds = new Tuple<>(new ScrollingDigit(120, true),
                new ScrollingDigit(12, false));
        private float target;
        private boolean isFrozen = false;

        public void setTarget(float target) {
            this.target = target;
            float seconds = target / 20;
            float mins = seconds / 60;
            this.seconds.getA().setTarget(seconds / 10);
            this.seconds.getB().setTarget(seconds);
            this.minutes.getA().setTarget(mins / 10);
            this.minutes.getB().setTarget(mins);
        }

        public void setFrozen(boolean flag) {
            this.isFrozen = flag;
        }

        public void update() {
            this.minutes.getA().update();
            this.minutes.getB().update();
            this.seconds.getA().update();
            this.seconds.getB().update();
        }

        public void render(Font renderer, @NotNull FakeGuiGraphics context, int x, int y, int colour, float delta) {
            context.pose().pushPose();
            context.pose().translate(x, y, 0);
            if (isFrozen) {
                final int gap = 12;
                // 0 + (32 + gap + textWidth) / 2
                Component text = Component.translatable("hud.time.frozen").withStyle(ChatFormatting.AQUA);
                int textWidth = renderer.width(text);
                int textOffsetX = (32 + gap - textWidth) / 2;
                // int textHalfWidth = (32 + gap + textWidth) / 2;
                context.pose().translate(textOffsetX, 0, 0);
                context.drawString(renderer, text, 0, 0, colour);
                context.pose().translate(-textOffsetX - (gap + textWidth) / 2, 0, 0);
            }
            context.pose().translate(16, 0, 0);
            this.seconds.getB().render(renderer, context, colour, delta, isFrozen);
            context.pose().translate(-8, 0, 0);
            this.seconds.getA().render(renderer, context, colour, delta, isFrozen);
            context.pose().translate(-8, 0, 0);
            context.drawString(renderer, ":", 2, 0, colour, isFrozen);
            context.pose().translate(-8, 0, 0);
            this.minutes.getB().render(renderer, context, colour, delta, isFrozen);
            context.pose().translate(-8, 0, 0);
            this.minutes.getA().render(renderer, context, colour, delta, isFrozen);
            context.pose().popPose();
        }

        public float getTarget() {
            return this.target;
        }
    }

    public static class ScrollingDigit {
        // 预缓存数字字符串和幂次结果，避免每帧创建对象和重复计算
        private static final String[] DIGIT_STRINGS = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };

        private final int power;
        private final boolean cap6;
        private float target;
        private float value;
        private float lastValue;

        public ScrollingDigit(int power, boolean cap6) {
            this.power = power;
            this.cap6 = cap6;
        }

        public void update() {
            this.lastValue = this.value;
            this.value = Mth.lerp(0.15f, this.value, this.target);
            if (Math.abs(this.value - this.target) < 0.01f)
                this.value = this.target;
        }

        public void render(@NotNull Font renderer, @NotNull FakeGuiGraphics context, int colour, float delta,
                boolean isFrozen) {
            float value = Mth.lerp(delta, this.lastValue, this.value);
            int mod = this.cap6 ? 6 : 10;
            int digit = Mth.floor(value) % mod;
            int digitNext = (digit + 1) % mod;
            // 使用快速幂近似代替Math.pow
            float base = value % 1;
            float offset = fastPow(base, this.power);
            if (isFrozen) {
                this.lastValue = this.target;
                this.value = this.target;
                value = this.value;
                offset = 0;
            }
            colour &= 0xFFFFFF;
            context.pose().pushPose();
            context.pose().translate(0, -offset * (renderer.lineHeight + 2), 0);
            float alpha = (1.0f - offset) * 255.0f;
            int baseColour = colour | (int) alpha << 24;
            int nextColour = colour | (int) (offset * 255.0f) << 24;
            if ((baseColour & -67108864) != 0)
                context.drawString(renderer, DIGIT_STRINGS[digit], 0, 0, baseColour);
            if ((nextColour & -67108864) != 0)
                context.drawString(renderer, DIGIT_STRINGS[digitNext], 0, renderer.lineHeight + 2, nextColour);
            context.pose().popPose();
        }

        // 快速整数幂计算，比Math.pow更高效
        private static float fastPow(float base, int exp) {
            float result = 1.0f;
            while (exp > 0) {
                if ((exp & 1) == 1)
                    result *= base;
                base *= base;
                exp >>= 1;
            }
            return result;
        }

        public void setTarget(float target) {
            this.target = target;
        }
    }
}