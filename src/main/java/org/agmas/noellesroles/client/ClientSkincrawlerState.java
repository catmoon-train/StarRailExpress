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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.agmas.noellesroles.packet.SkincrawlerSkinS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side skin swap state for Skincrawler - broadcasts visible to all
 * players.
 */
public class ClientSkincrawlerState {
    private static final Map<UUID, UUID> skins = new ConcurrentHashMap<>();

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            java.util.UUID targetId = ClientEmbalmerState.replacement(player.getUUID());
            if (targetId == null)
                targetId = ClientSkincrawlerState.stolenSkinFor(player.getUUID());
            if (targetId == null || targetId.equals(player.getUUID()))
                return PlayerSkinResult.SKIP;
            // 优先使用 ClientSkinCache 获取完整皮肤数据（含有双层），回退到玩家列表
            PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(targetId);
            Minecraft client = Minecraft.getInstance();

            if (info == null) {
                info = client.getConnection().getPlayerInfo(targetId);
            }
            if (info != null && info.getSkin() != null) {
                return PlayerSkinResult.playerSkin(info.getSkin());
            }
            return PlayerSkinResult.SKIP;
        });
        ClientPlayNetworking.registerGlobalReceiver(SkincrawlerSkinS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (payload.skincrawlerId() == null) {
                        skins.clear();
                    } else if (payload.stolenSkinId() != null)
                        skins.put(payload.skincrawlerId(), payload.stolenSkinId());
                    else
                        skins.remove(payload.skincrawlerId());
                }));
    }

    public static UUID stolenSkinFor(UUID playerId) {
        return playerId == null ? null : skins.get(playerId);
    }

    /** 清除所有窃皮者皮肤映射，在游戏重置时调用以确保客户端干净 */
    public static void clearAll() {
        skins.clear();
    }

}
