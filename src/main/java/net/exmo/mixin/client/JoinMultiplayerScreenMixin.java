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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 天际列车风格多人加入页：只改渲染，列表/按钮/加入逻辑保持原版。
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    @Unique
    private long sre$openedAt = -1L;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (SREClientConfig.instance().disableCustomTitleScreen) {
            super.renderBackground(g, mouseX, mouseY, delta);
            return;
        }
        if (this.sre$openedAt < 0L) {
            this.sre$openedAt = Util.getMillis();
        }
        SreUiStyle.renderJoinBackground(g, this.width, this.height, delta,
                SreUiStyle.enterT(this.sre$openedAt));
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void sre$drawTitle(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (SREClientConfig.instance().disableCustomTitleScreen) {
            g.drawCenteredString(font, text, x, y, color);
            return;
        }
        if (this.sre$openedAt < 0L) {
            this.sre$openedAt = Util.getMillis();
        }
        SreUiStyle.drawJoinHeader(g, font, this.width, SreUiStyle.enterT(this.sre$openedAt));
    }
}
