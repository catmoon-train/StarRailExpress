package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.agmas.noellesroles.packet.SkincrawlerSkinS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side skin swap state for Skincrawler - broadcasts visible to all players. */
public class ClientSkincrawlerState {
    private static final Map<UUID, UUID> skins = new ConcurrentHashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SkincrawlerSkinS2CPacket.ID, (payload, ctx) ->
            ctx.client().execute(() -> {
                if (payload.stolenSkinId() != null)
                    skins.put(payload.skincrawlerId(), payload.stolenSkinId());
                else
                    skins.remove(payload.skincrawlerId());
            }));
    }

    public static UUID stolenSkinFor(UUID playerId) {
        return playerId == null ? null : skins.get(playerId);
    }
}
