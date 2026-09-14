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

package io.wifi.starrailexpress.client.disguise;

import io.wifi.starrailexpress.event.client.OnGameFinishedClient;
import io.wifi.starrailexpress.event.client.OnGameStartedClient;
import io.wifi.starrailexpress.network.EntityDisguiseSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 客户端接入：接收伪装同步、刷新尺寸、开局 / 结束时清缓存。
 */
@Environment(EnvType.CLIENT)
public final class EntityDisguiseClient {

    private EntityDisguiseClient() {
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(EntityDisguiseSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> apply(payload)));
        EntityDisguiseHud.register();
        OnGameStartedClient.EVENT.register(EntityDisguiseClient::resetClient);
        OnGameFinishedClient.EVENT.register(EntityDisguiseClient::resetClient);
    }

    /**
     * 应用同步并刷新受影响玩家的尺寸。
     * <p>
     * 眼高来自 {@code Entity#getDefaultDimensions}，而 {@code Entity} 会把结果缓存进
     * {@code eyeHeight} 字段，所以必须显式 {@code refreshDimensions()} 才会生效——本地玩家影响相机，
     * 其他人影响名牌位置。
     */
    private static void apply(EntityDisguiseSyncPayload payload) {
        ClientEntityDisguiseCache.applySync(payload);
        EntityDisguiseRenderer.prune();
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        for (UUID uuid : payload.entries().keySet()) {
            Player player = level.getPlayerByUUID(uuid);
            if (player != null) {
                player.refreshDimensions();
            }
        }
    }

    private static void resetClient() {
        ClientEntityDisguiseCache.clear();
        EntityDisguiseRenderer.clear();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.refreshDimensions();
        }
    }
}
