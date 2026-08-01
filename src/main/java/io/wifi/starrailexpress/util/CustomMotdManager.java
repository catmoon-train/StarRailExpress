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

package io.wifi.starrailexpress.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class CustomMotdManager {
    private static final int UPDATE_INTERVAL = 20;// 1s (20tick)更新一次；
    private static Component customMotd = null;
    private static Component cachedMotd;
    private static int ticker = 0;
    private static Path configPath;
    private static MinecraftServer motdServer;

    public static void init() {
        registerEvents();
    }

    public static Component getMotd() {
        if (motdServer == null)
            return Component.literal("[MOTD] Server not available").withStyle(ChatFormatting.RED);
        if (customMotd == null) {
            return Component.literal(motdServer.getMotd());
        }
        if (cachedMotd == null) {
            cachedMotd = updateCustomMotdCache(motdServer, customMotd);
        }
        return cachedMotd;
    }

    private static void serverStart(MinecraftServer server) {
        configPath = (server.getWorldPath(LevelResource.ROOT).resolve(Paths.get("custom_motd.json")));
        motdServer = server;
        customMotd = null;
        cachedMotd = null;
        loadMotd();
    }

    private static void serverStop(MinecraftServer server) {
        configPath = null;
        motdServer = null;
        cachedMotd = null;
    }

    private static void serverTick(MinecraftServer server) {
        ticker++;
        if (cachedMotd == null || ticker >= UPDATE_INTERVAL) {
            if (customMotd != null) {
                try {
                    cachedMotd = updateCustomMotdCache(server, customMotd);
                } catch (Exception e) {
                    customMotd = null;
                    SRE.LOGGER.error("[MOTD] Error while resolve custom motd. ", e);
                }
            }
            ticker = 0;
        }
    }

    private static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(CustomMotdManager::serverStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(CustomMotdManager::serverStop);
        ServerTickEvents.END_SERVER_TICK.register(CustomMotdManager::serverTick);
    }

    private static Component updateCustomMotdCache(MinecraftServer server, Component motd) {
        if (motd == null)
            return Component.empty();
        HashMap<String, String> replaceMap = new HashMap<>();
        replaceMap.put("%player_count%", server.getPlayerCount() + "");
        replaceMap.put("%max_player%", server.getMaxPlayers() + "");
        replaceMap.put("%sre_packet_version%", SRE.modPacketVersion + "");
        replaceMap.put("%sre_version%", SRE.MOD_VERSION);
        var motd2 = motd;
        try {

            motd2 = ComponentUtils.updateForEntity(
                    server.createCommandSourceStack(),
                    motd2,
                    null, 0);
        } catch (CommandSyntaxException e) {
            SRE.LOGGER.error("[MOTD] Error while processing custom motd. ", e);
            customMotd = null;
            return Component.literal("[MOTD] Error.").withStyle(ChatFormatting.RED);
        }
        return ComponentReplacer.replacePlaceholders(motd2, replaceMap);
    }

    public static void setCustomMotd(Component motd) {
        customMotd = motd;
        saveMotd();
    }

    public static void loadMotd() {
        if (configPath == null || motdServer == null)
            return;
        if (!Files.exists(configPath)) {
            return;
        }
        try {
            String savedStr = Files.readString(configPath);
            if (savedStr.isBlank())
                return;
            customMotd = Component.Serializer.fromJson(savedStr, motdServer.registryAccess());
            SRE.LOGGER.info("[MOTD] Loaded custom motd.");
        } catch (IOException e) {
            SRE.LOGGER.error("[MOTD] Error while loading custom motd", e);
            return;
        }
    }

    public static void saveMotd() {
        if (configPath == null || motdServer == null) {
            SRE.LOGGER.info("[MOTD] Unable to save custom motd: server was down!");
            return;
        }
        if (customMotd != null) {

            try {
                String str = Component.Serializer.toJson(customMotd, motdServer.registryAccess());
                if (!str.isEmpty())
                    Files.writeString(configPath, str);
            } catch (IOException e) {
                SRE.LOGGER.error("[MOTD] Error while saving custom motd.", e);
                return;
            }
        } else {
            try {
                Files.deleteIfExists(configPath);
            } catch (IOException e) {
                SRE.LOGGER.error("[MOTD] Error while saving custom motd.", e);
                return;
            }
        }

    }

    public static Component getCustomMotdPattern() {
        if (customMotd == null)
            return Component.translatable("message.serverutils.none").withStyle(ChatFormatting.GRAY);
        return customMotd;
    }
}
