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
 * 只在等待区块时显示列车加载画面；就绪和超时交接沿用原版 tick，不等待演出。
 */
@Environment(EnvType.CLIENT)
public class SREReceivingLevelScreen extends ReceivingLevelScreen {

    private static final long ENTER_MS = 650;
    private final long createdAt;

    private int ellipsis;
    private long lastEllipsisAt;

    public SREReceivingLevelScreen(BooleanSupplier levelReceived, Reason reason) {
        super(levelReceived, reason);
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
        // 原版负责区块就绪与 30 秒兜底，不能用 render 中的动画计时取代它。
        super.tick();
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


        SreUiStyle.renderLoadingBackdrop(g, w, h, delta, enterAlpha);

        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 70;
        float phase = (now % 2600L) / 2600.0F;
        LoadingFx.drawComet(g, cx - half, cx + half, railY, phase, enterAlpha);

        String text = Component.translatable("loading.world.generating").getString() + ".".repeat(ellipsis);
        g.drawString(font, text, cx - font.width(text) / 2, railY - 16,
                LoadingFx.withAlpha(0xC8B898, enterAlpha), false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF000000);
    }
}
