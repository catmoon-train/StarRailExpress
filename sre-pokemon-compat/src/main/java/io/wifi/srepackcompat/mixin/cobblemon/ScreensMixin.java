package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ScreensMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sre$blockPokemonScreen(Screen screen, CallbackInfo ci) {
        if (screen == null || CobblemonPolicy.enabled() || !CobblemonPolicy.isPresentation(screen.getClass())) return;
        Minecraft client = (Minecraft) (Object) this;
        // 服务端打开的容器必须发送正常关闭包，避免菜单状态不同步。
        if (screen instanceof AbstractContainerScreen<?> && client.player != null) client.player.closeContainer();
        ci.cancel();
    }
}
