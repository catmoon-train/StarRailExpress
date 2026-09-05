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

package net.exmo.mixin.client;

import io.wifi.starrailexpress.SREClientConfig;
import net.exmo.sre.loading.SreUiStyle;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 进入游戏 / 连接服务器页：只改渲染，连接与取消逻辑保持原版。
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Unique
    private long sre$openedAt = -1L;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (SREClientConfig.instance().disableCustomLoadingScreen) {
            super.renderBackground(g, mouseX, mouseY, delta);
            return;
        }
        if (this.sre$openedAt < 0L) {
            this.sre$openedAt = Util.getMillis();
        }
        SreUiStyle.renderConnectBackground(g, this.width, this.height, delta,
                SreUiStyle.enterT(this.sre$openedAt));
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void sre$drawStatus(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (SREClientConfig.instance().disableCustomLoadingScreen) {
            g.drawCenteredString(font, text, x, y, color);
            return;
        }
        if (this.sre$openedAt < 0L) {
            this.sre$openedAt = Util.getMillis();
        }
        SreUiStyle.drawConnectStatus(g, font, this.width, this.height, text,
                SreUiStyle.enterT(this.sre$openedAt));
    }
}
