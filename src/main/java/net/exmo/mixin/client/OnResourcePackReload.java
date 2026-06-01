package net.exmo.mixin.client;

import net.exmo.sre.mod_whitelist.client.network.ModWhitelistClientNetworkHandler;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModResourcePackUtil.class)
public class OnResourcePackReload {
    @Inject(
            method = "refreshAutoEnabledPacks",
            at = @At("TAIL")
    )
    private static void refreshAutoEnabledPacks(List<Pack> enabledProfiles, Map<String, Pack> allProfiles, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(ModWhitelistClientNetworkHandler::sendResourcePackWhitelistPayload);
        }
    }
}
