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

import com.mojang.authlib.GameProfile;
import io.sre.client.utils.VTModePlayerSkin;
import io.wifi.starrailexpress.SREClientConfig;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(value = SkinManager.class, priority = 1)
public class PlayerSkinManagerMixin {
    @Inject(method = "getOrLoad", at = @At("HEAD"), cancellable = true)
    private void getOrLoad$sre(GameProfile gameProfile, CallbackInfoReturnable<CompletableFuture<PlayerSkin>> cir) {
        if (SREClientConfig.instance().enableRandomSkinForStreaming) {
            // 获取自定义皮肤数据
            VTModePlayerSkin.LocalPlayerSkin localSkin = VTModePlayerSkin.getPlayerSkin(gameProfile.getId());
            if (localSkin == null) {
                // 如果没有找到自定义皮肤，可以选择回退到原版逻辑（不取消）
                return;
            }

            // 创建原版 PlayerSkin 对象（1.21.1 中的构造方式）
            PlayerSkin playerSkin = localSkin.toPlayerSkin();

            if (playerSkin != null) {
                // SRE.LOGGER.info("Returning skin for {}: texture = {}", gameProfile.getId(), playerSkin.texture());

                // 返回已完成的 CompletableFuture
                cir.setReturnValue(CompletableFuture.completedFuture(playerSkin));
                cir.cancel(); // 阻止原方法执行
            }
        }
    }
}
