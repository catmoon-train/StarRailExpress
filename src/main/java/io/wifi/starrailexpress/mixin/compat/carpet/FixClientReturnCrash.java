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

package io.wifi.starrailexpress.mixin.compat.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.logging.HUDController;
import carpet.logging.LoggerRegistry;
import carpet.network.ServerNetworkHandler;
import carpet.script.CarpetScriptServer;
import carpet.script.external.Vanilla;
import carpet.script.utils.ParticleParser;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

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
