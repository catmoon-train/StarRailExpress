package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonClientGate;
import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 接入 SRE 现有服务端配置包，不把客户端 AutoConfig 值当作服务端授权。 */
@Pseudo
@Mixin(targets = "io.wifi.ConfigCompact.ConfigClassHandler", remap = false)
public class ServerConfigMixin {
    @Inject(method = "recieveConfigPackFromServer", at = @At("TAIL"))
    private static void sre$serverSetting(String id, String content, CallbackInfo ci) {
        if (CobblemonPolicy.CONFIG_ID.equals(id)) {
            CobblemonClientGate.apply(CobblemonPolicy.readServerSetting(content));
        }
    }
}
