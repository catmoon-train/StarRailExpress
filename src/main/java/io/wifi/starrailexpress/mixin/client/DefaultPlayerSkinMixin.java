package io.wifi.starrailexpress.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import com.mojang.authlib.GameProfile;

import io.sre.client.utils.VTModePlayerSkin;
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
        var res = ResourceLocation.tryParse(VTModePlayerSkin.getAPlayerSkin().path);
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
            cir.setReturnValue(customSkin.toPlayerSkin(false));
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
            cir.setReturnValue(customSkin.toPlayerSkin(false));
        }
    }
}
