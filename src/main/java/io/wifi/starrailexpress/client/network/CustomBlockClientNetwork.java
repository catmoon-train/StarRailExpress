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

package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customblock.CustomBlockConfig;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import io.wifi.starrailexpress.synccontent.ContentChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 自定义方块客户端网络处理。
 *
 * <p>
 * 只负责「内容拿到之后怎么办」：写一份 config 目录副本（单机 / 编辑器本地读取用）并重载本地索引。
 * 传输（握手 / 压缩 / 磁盘缓存）统一由 {@link ContentSyncClient} 负责，这里不再有自己的分块协议。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockClientNetwork {

    public static void register() {
        ContentSyncClient.registerChannel(ContentChannel.CUSTOM_BLOCK, CustomBlockClientNetwork::applyJson);
    }

    /** 应用服务端下发的配置内容。 */
    private static void applyJson(String json) {
        writeToLocalConfig(json);
        CustomBlockLoader.reloadClient();
        io.wifi.starrailexpress.client.render.block.CustomBlockAppearance.clearCache();
    }

    private static void writeToLocalConfig(String json) {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve(CustomBlockConfig.FILE_NAME),
                    json == null ? "" : json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomBlock-Client] Failed to write local config copy", e);
        }
    }

    /** 断开连接时清理本地索引与副本文件（磁盘缓存保留在 ContentSyncClient 里）。 */
    public static void clearCache() {
        CustomBlockLoader.removeClientCache();
        io.wifi.starrailexpress.client.render.block.CustomBlockAppearance.clearCache();
        try {
            Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve(CustomBlockConfig.FILE_NAME));
        } catch (Exception ignored) {
        }
    }
}
