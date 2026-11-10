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

import io.wifi.starrailexpress.client.gui.SecurityMonitorFeedRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版世界渲染之前先抓一帧「监控台自身视角」到离屏目标。
 *
 * <p>注入点选在 {@code GameRenderer.render} 里对 {@code renderLevel} 的调用处，
 * 此处 viewport 已按窗口设置好、主渲染目标已绑定；抓完帧立即还原，
 * 接着原版会用正常的摄像头视角渲染主画面。
 */
@Mixin(GameRenderer.class)
public class SecurityMonitorFeedMixin {

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void sre$captureSecurityMonitorFeed(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            SecurityMonitorFeedRenderer.capture(deltaTracker);
        }
    }
}
