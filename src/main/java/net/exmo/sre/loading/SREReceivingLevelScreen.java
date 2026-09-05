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

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * 复古列车风格 —— 进入世界（接收区块）加载界面。
 * <p>
 * 时间线与资源加载界面 {@link StarRailLoadingOverlay} 一致：
 * 黑屏淡入 → 不确定加载（彗星往复）→ 收到世界后保留并把星轨走满 → 缓动淡出。
 */
@Environment(EnvType.CLIENT)
public class SREReceivingLevelScreen extends ReceivingLevelScreen {

    private static final long ENTER_MS = 650;
    private static final long MIN_SHOW_MS = 900;
    private static final long END_HOLD_MS = 1300;
    private static final long EXIT_MS = 650;

    private final BooleanSupplier levelReceived;
    private final long createdAt;

    private long receivedAt = -1L;
    private int ellipsis;
    private long lastEllipsisAt;

    public SREReceivingLevelScreen(BooleanSupplier levelReceived, Reason reason) {
        super(levelReceived, reason);
        this.levelReceived = levelReceived;
        this.createdAt = Util.getMillis();
    }

    @Override
    protected void init() {
        SreUiStyle.registerLoadingBackground();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (now - lastEllipsisAt > 400L) {
            ellipsis = (ellipsis + 1) % 4;
            lastEllipsisAt = now;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        long now = Util.getMillis();

        float enterAlpha = LoadingFx.smoothstep((now - createdAt) / (float) ENTER_MS);

        if (receivedAt < 0L && levelReceived.getAsBoolean() && now - createdAt >= MIN_SHOW_MS) {
            receivedAt = now;
        }

        float exitAlpha = 1.0F;
        float arriveT = 0.0F;
        boolean arriving = receivedAt >= 0L;
        if (arriving) {
            long since = now - receivedAt;
            arriveT = LoadingFx.smoothstep(since / (float) END_HOLD_MS);
            if (since >= END_HOLD_MS) {
                long ex = since - END_HOLD_MS;
                exitAlpha = 1.0F - LoadingFx.smoothstep(ex / (float) EXIT_MS);
                if (ex >= EXIT_MS) {
                    onClose();
                    return;
                }
            }
        }
        float alpha = enterAlpha * exitAlpha;

        SreUiStyle.renderLoadingBackdrop(g, w, h, delta, alpha);

        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 70;
        if (arriving) {
            LoadingFx.drawRail(g, cx - half, cx + half, railY, arriveT, alpha);
        } else {
            float phase = (now % 2600L) / 2600.0F;
            LoadingFx.drawComet(g, cx - half, cx + half, railY, phase, alpha);
        }

        String base = Component.translatable("loading.world.generating").getString();
        String text = arriving
                ? Component.translatable("loading.ready").getString()
                : base + ".".repeat(ellipsis);
        float pulse = arriving ? 0.70F + 0.30F * (float) Math.sin(now / 180.0) : 1.0F;
        int color = LoadingFx.withAlpha(arriving ? 0xD4AF37 : 0xC8B898, alpha * pulse);
        g.drawString(font, text, cx - font.width(text) / 2, railY - 16, color, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF000000);
    }
}
