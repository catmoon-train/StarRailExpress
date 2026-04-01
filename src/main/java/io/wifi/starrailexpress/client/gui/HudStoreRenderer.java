package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class HudStoreRenderer {
    public static MoneyNumberRenderer view = new MoneyNumberRenderer();
    public static float offsetDelta = 0f;
    // 缓存角色状态，避免每帧查询
    private static boolean cachedCanSeeCoin = false;
    private static int lastCachedTick = -1;

    public static void renderHud(Font font, @NotNull LocalPlayer player, @NotNull FakeGuiGraphics context,
            float delta) {
        // 每20tick更新一次角色权限缓存
        int currentTick = player.tickCount;
        if (currentTick - lastCachedTick > 20 || lastCachedTick < 0) {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            SRERole role = gameWorldComponent.getRole(player);
            cachedCanSeeCoin = role != null && role.canSeeCoin();
            lastCachedTick = currentTick;
        }
        if (!cachedCanSeeCoin) {
            return;
        }
        int balance = SREPlayerShopComponent.KEY.get(player).balance;
        if (view.getTarget() != balance) {
            offsetDelta = balance > view.getTarget() ? .6f : -.6f;
            view.setTarget(balance);
        }
        float r = 1f;
        float g = 1f;
        float b = 1f - Math.abs(offsetDelta);
        int colour = Mth.color(r, g, b) | 0xFF000000;
        context.pose().pushPose();
        context.pose().translate(context.guiWidth() - 12, 6, 0);
        view.render(font, context, 0, 0, colour, delta);
        context.pose().popPose();
        offsetDelta = Mth.lerp(delta / 16, offsetDelta, 0f);
    }

    public static void tick() {
        view.update();
    }

    public static class MoneyNumberRenderer {
        private final List<ScrollingDigit> digits = new ArrayList<>();
        private float target;

        public void setTarget(float target) {
            this.target = target;
            int length = String.valueOf(target).length();
            while (this.digits.size() < length)
                this.digits.add(new ScrollingDigit(this.digits.isEmpty()));
            for (int i = 0; i < this.digits.size(); i++) {
                if (i == 0) {
                    this.digits.get(i).setTarget((float) (target / Math.pow(10, i)));
                } else {
                    this.digits.get(i).setTarget((int) (target / Math.pow(10, i)));
                }
            }
        }

        public void update() {
            for (ScrollingDigit digit : this.digits)
                digit.update();
        }

        public void render(Font renderer, @NotNull FakeGuiGraphics context, int x, int y, int colour, float delta) {
            context.pose().pushPose();
            context.pose().translate(x, y, 0);
            context.drawString(renderer, "\uE781", 0, 0, colour);
            int offset = -8;
            for (ScrollingDigit digit : this.digits) {
                context.pose().pushPose();
                context.pose().translate(offset, 0, 0);
                digit.render(renderer, context, colour, delta);
                offset -= 8;
                context.pose().popPose();
            }
            context.pose().popPose();
        }

        public float getTarget() {
            return this.target;
        }
    }

    public static class ScrollingDigit {
        // 预缓存数字字符串，避免每帧创建String对象
        private static final String[] DIGIT_STRINGS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        
        private final boolean force;
        private float target;
        private float value;
        private float lastValue;

        public ScrollingDigit(boolean force) {
            this.force = force;
        }

        public void update() {
            this.lastValue = this.value;
            this.value = Mth.lerp(0.15f, this.value, this.target);
            if (Math.abs(this.value - this.target) < 0.01f)
                this.value = this.target;
        }

        public void render(@NotNull Font renderer, @NotNull FakeGuiGraphics context, int colour, float delta) {
            float value = Mth.lerp(delta, this.lastValue, this.value);
            int digit = Mth.floor(value) % 10;
            int digitNext = (digit + 1) % 10;
            float offset = value % 1;
            colour &= 0xFFFFFF;
            context.pose().pushPose();
            context.pose().translate(0, -offset * (renderer.lineHeight + 2), 0);
            float alpha = (1.0f - offset) * 255.0f;
            if (value < 1 && !this.force)
                alpha *= value;
            int baseColour = colour | (int) alpha << 24;
            int nextColour = colour | (int) (offset * 255.0f) << 24;
            if ((baseColour & -67108864) != 0)
                context.drawString(renderer, DIGIT_STRINGS[digit], 0, 0, baseColour);
            if ((nextColour & -67108864) != 0)
                context.drawString(renderer, DIGIT_STRINGS[digitNext], 0, renderer.lineHeight + 2, nextColour);
            context.pose().popPose();
        }

        public void setTarget(float target) {
            this.target = target;
        }
    }
}