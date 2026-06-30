package io.wifi.starrailexpress.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import com.mojang.authlib.GameProfile;

import io.sre.client.utils.VTModePlayerSkin;
import io.wifi.starrailexpress.SREClientConfig;
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
