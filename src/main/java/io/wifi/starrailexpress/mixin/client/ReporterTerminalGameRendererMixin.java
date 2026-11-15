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

package io.wifi.starrailexpress.mixin.client;

import io.wifi.starrailexpress.client.ReporterTerminalClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记者终端视角不绘制自己的手持物品与手部模型。
 *
 * <p>正常路径下终端会把相机切到第三人称，原版本就不会画手；这里再兜一层，
 * 防止相机类型被其它限制逻辑强行拉回第一人称时把手/物品画进画面。
 */
@Mixin(GameRenderer.class)
public abstract class ReporterTerminalGameRendererMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void reporterTerminal$hideHand(CallbackInfo ci) {
        if (ReporterTerminalClient.isActive()) {
            ci.cancel();
        }
    }
}
