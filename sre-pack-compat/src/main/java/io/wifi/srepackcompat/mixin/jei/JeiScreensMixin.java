package io.wifi.srepackcompat.mixin.jei;

import io.wifi.srepackcompat.jei.JeiLobbyGate;
import io.wifi.srepackcompat.jei.JeiLobbyPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class JeiScreensMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sre$onlyLobby(Screen screen, CallbackInfo ci) {
        if (screen != null && !JeiLobbyGate.enabled() && JeiLobbyPolicy.isPresentation(screen.getClass())) ci.cancel();
    }
}
