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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多人列表渲染：去掉原版泥土底，改用车票选中高亮。仅作用于 {@link ServerSelectionList}。
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {

    @Unique
    private boolean sre$joinList() {
        return (Object) this instanceof ServerSelectionList
                && !SREClientConfig.instance().disableCustomTitleScreen;
    }

    @Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true)
    private void sre$hideDirtBackground(GuiGraphics g, CallbackInfo ci) {
        if (!sre$joinList()) {
            return;
        }
        ci.cancel();
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        int x0 = list.getX();
        int y0 = list.getY();
        int x1 = x0 + list.getWidth();
        int y1 = y0 + list.getHeight();
        g.fill(x0, y0, x1, y1, 0x14000000);
    }

    @Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
    private void sre$hideSeparators(GuiGraphics g, CallbackInfo ci) {
        if (sre$joinList()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void sre$goldSelection(GuiGraphics g, int y, int w, int h, int outer, int inner, CallbackInfo ci) {
        if (!sre$joinList()) {
            return;
        }
        ci.cancel();
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        int x0 = list.getX() + (list.getWidth() - w) / 2;
        int x1 = list.getX() + (list.getWidth() + w) / 2;
        g.fill(x0, y - 2, x1, y + h + 2, SreUiStyle.GOLD);
        g.fillGradient(x0 + 1, y - 1, x1 - 1, y + h + 1,
                SreUiStyle.blend(0xFF1A1008, 0xFFC9A84C, 0.32F),
                SreUiStyle.blend(0xFF120A04, 0xFFC9A84C, 0.18F));
    }
}
