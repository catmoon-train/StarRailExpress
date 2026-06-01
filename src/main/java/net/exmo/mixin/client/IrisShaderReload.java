package net.exmo.mixin.client;

import net.exmo.sre.mod_whitelist.client.network.ModWhitelistClientNetworkHandler;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.apiimpl.IrisApiV0ConfigImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IrisApiV0ConfigImpl.class)
public class IrisShaderReload {
    @Inject(method = "setShadersEnabledAndApply", at = @At("TAIL"))
    private static void setShadersEnabledAndApply(boolean enabled, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(ModWhitelistClientNetworkHandler::sendShaderPackWhitelistPayload);
        }
    }
}
