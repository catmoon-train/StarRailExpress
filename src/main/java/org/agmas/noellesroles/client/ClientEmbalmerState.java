package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side state for Embalmer masquerade: skin swaps and voice pitches. */
public class ClientEmbalmerState {
    private static final Map<UUID, UUID> swaps = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> pitches = new ConcurrentHashMap<>();
    private static long expiresAt;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EmbalmerSkinSwapS2CPacket.ID, (payload, ctx) ->
            ctx.client().execute(() -> {
                if (payload.durationTicks() <= 0 || payload.swaps().isEmpty()) {
                    clear();
                    return;
                }
                swaps.clear(); swaps.putAll(payload.swaps());
                pitches.clear(); pitches.putAll(payload.pitches());
                var client = Minecraft.getInstance();
                expiresAt = client.level != null ? client.level.getGameTime() + payload.durationTicks() : 0;
            }));
    }

    public static UUID replacement(UUID id) {
        if (!isActive() || id == null) return null;
        return swaps.get(id);
    }

    public static float pitch(UUID id) {
        if (!isActive() || id == null) return 1.0F;
        return pitches.getOrDefault(id, 1.0F);
    }

    public static boolean isActive() {
        var client = Minecraft.getInstance();
        if (client == null || client.level == null || swaps.isEmpty()) { clear(); return false; }
        if (client.level.getGameTime() >= expiresAt) { clear(); return false; }
        return true;
    }

    private static void clear() { swaps.clear(); pitches.clear(); expiresAt = 0; }
}
