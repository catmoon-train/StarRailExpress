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
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = DefaultPlayerSkin.class, priority = 2000)
public class DefaultPlayerSkinMixin {
    /**
     * 拦截 getDefaultTexture() 方法
     */
    @Inject(method = "getDefaultTexture", at = @At("HEAD"), cancellable = true)
    private static void onGetDefaultTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        var c = VTModePlayerSkin.getAPlayerSkin();
        if (c == null)
            return;
        var res = ResourceLocation.tryParse(c.path);
        if (res != null)
            cir.setReturnValue(res);
    }

    /**
     * 拦截 get(UUID) 方法
     */
    @Inject(method = "get(Ljava/util/UUID;)Lnet/minecraft/client/resources/PlayerSkin;", at = @At("HEAD"), cancellable = true)
    private static void onGetByUuid(UUID uuid, CallbackInfoReturnable<PlayerSkin> cir) {
        var customSkin = VTModePlayerSkin.getPlayerSkin(uuid);
        if (customSkin != null) {
            cir.setReturnValue(customSkin.toPlayerSkin(true));
        }
    }

    /**
     * 拦截 get(GameProfile) 方法（可选，因为内部已调用 get(UUID)，
     * 但为了更完整的覆盖和防止未来实现变动，建议也加上）
     */
    @Inject(method = "get(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/client/resources/PlayerSkin;", at = @At("RETURN"), cancellable = true)
    private static void onGetByProfile(GameProfile profile, CallbackInfoReturnable<PlayerSkin> cir) {
        var customSkin = VTModePlayerSkin.getPlayerSkin(profile.getId());
        if (customSkin != null) {
            cir.setReturnValue(customSkin.toPlayerSkin(true));
        }
    }
}
