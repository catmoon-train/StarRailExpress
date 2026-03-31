package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

public class TimeRenderer {
    public static TimeNumberRenderer view = new TimeNumberRenderer();
    public static float offsetDelta = 0f;

    public static void renderHud(Font renderer, @NotNull LocalPlayer player, @NotNull GuiGraphics context, float delta) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        SRERole role = gameWorldComponent.getRole(player);
        if (gameWorldComponent.isRunning() && (role != null && role.canSeeTime() || GameUtils.isPlayerSpectatingOrCreative(player))) {
            int time = SREGameTimeComponent.KEY.get(player.level()).getTime();
            if (Math.abs(view.getTarget() - time) > 10) offsetDelta = time > view.getTarget() ? .6f : -.6f;
            if (time < GameConstants.getInTicks(1, 0)) {
                offsetDelta = -0.9f;
            } else {
                offsetDelta = Mth.lerp(delta / 16, offsetDelta, 0f);
            }
            view.setTarget(time);
            float r = offsetDelta > 0 ? 1f - offsetDelta : 1f;
            float g = offsetDelta < 0 ? 1f + offsetDelta : 1f;
            float b = 1f - Math.abs(offsetDelta);
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
        private final Tuple<ScrollingDigit, ScrollingDigit> minutes = new Tuple<>(new ScrollingDigit(7200, false), new ScrollingDigit(720, false));
        private final Tuple<ScrollingDigit, ScrollingDigit> seconds = new Tuple<>(new ScrollingDigit(120, true), new ScrollingDigit(12, false));
        private float target;

        public void setTarget(float target) {
            this.target = target;
            float seconds = target / 20;
            float mins = seconds / 60;
            this.seconds.getA().setTarget(seconds / 10);
            this.seconds.getB().setTarget(seconds);
            this.minutes.getA().setTarget(mins / 10);
            this.minutes.getB().setTarget(mins);
        }

        public void update() {
            this.minutes.getA().update();
            this.minutes.getB().update();
            this.seconds.getA().update();
            this.seconds.getB().update();
        }

        public void render(Font renderer, @NotNull GuiGraphics context, int x, int y, int colour, float delta) {
            context.pose().pushPose();
            context.pose().translate(x, y, 0);
            context.pose().translate(16, 0, 0);
            this.seconds.getB().render(renderer, context, colour, delta);
            context.pose().translate(-8, 0, 0);
            this.seconds.getA().render(renderer, context, colour, delta);
            context.pose().translate(-8, 0, 0);
            context.drawString(renderer, ":", 2, 0, colour);
            context.pose().translate(-8, 0, 0);
            this.minutes.getB().render(renderer, context, colour, delta);
            context.pose().translate(-8, 0, 0);
            this.minutes.getA().render(renderer, context, colour, delta);
            context.pose().popPose();
        }

        public float getTarget() {
            return this.target;
        }
    }

    public static class ScrollingDigit {
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
            if (Math.abs(this.value - this.target) < 0.01f) this.value = this.target;
        }

        public void render(@NotNull Font renderer, @NotNull GuiGraphics context, int colour, float delta) {
            float value = Mth.lerp(delta, this.lastValue, this.value);
            int digit = Mth.floor(value) % (this.cap6 ? 6 : 10);
            int digitNext = Mth.floor(value + 1) % (this.cap6 ? 6 : 10);
            double offset = Math.pow(value % 1, this.power);
            colour &= 0xFFFFFF;
            context.pose().pushPose();
            context.pose().translate(0, -offset * (renderer.lineHeight + 2), 0);
            double alpha = (1.0f - Math.abs(offset)) * 255.0f;
            int baseColour = colour | (int) alpha << 24;
            int nextColour = colour | (int) (Math.abs(offset) * 255.0f) << 24;
            if ((baseColour & -67108864) != 0)
                context.drawString(renderer, String.valueOf(digit), 0, 0, baseColour);
            if ((nextColour & -67108864) != 0)
                context.drawString(renderer, String.valueOf(digitNext), 0, renderer.lineHeight + 2, nextColour);
            context.pose().popPose();
        }

        public void setTarget(float target) {
            this.target = target;
        }
    }
}