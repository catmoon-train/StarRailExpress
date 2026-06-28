package io.wifi.starrailexpress.mixin.compat.carpet;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.logging.HUDController;
import carpet.logging.LoggerRegistry;
import carpet.network.ServerNetworkHandler;
import carpet.script.CarpetScriptServer;
import carpet.script.external.Vanilla;
import carpet.script.utils.ParticleParser;
import net.minecraft.server.MinecraftServer;

@Mixin(value = CarpetServer.class)
public class FixClientReturnCrash {
    @Shadow
    public static MinecraftServer minecraft_server;
    @Shadow
    public static CarpetScriptServer scriptServer;
    @Shadow
    @Final
    public static List<CarpetExtension> extensions;

    @Overwrite
    public static void onServerClosed(MinecraftServer server) {
        if (minecraft_server != null) {
            if (scriptServer != null) {
                scriptServer.onClose();
            }

            if (!Vanilla.MinecraftServer_getScriptServer(minecraft_server).stopAll) {
                Vanilla.MinecraftServer_getScriptServer(minecraft_server).onClose();
            }

            scriptServer = null;
            ServerNetworkHandler.close();
            LoggerRegistry.stopLoggers();
            HUDController.resetScarpetHUDs();
            ParticleParser.resetCache();
            extensions.forEach((e) -> e.onServerClosed(minecraft_server));
            minecraft_server = null;
        }

    }
}
