package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.packet.FakeSteveHuntS2CPacket;

/** Client-only presentation state for the Fake Steve endgame hunt. */
public final class ClientFakeSteveHuntState {
    private static volatile boolean active;

    private ClientFakeSteveHuntState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FakeSteveHuntS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> active = payload.active()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> active = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics.guiWidth(),
                graphics.guiHeight(), graphics));
    }

    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (active && (client.level == null || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())) {
            active = false;
        }
        return active;
    }

    public static void clear() {
        active = false;
    }

    /** A restrained red-black vignette: the same all-client scene role as Loose End's endgame ambience. */
    private static void render(int width, int height, io.wifi.utils.client.betterrender.FakeGuiGraphics graphics) {
        if (!isActive()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        long ticks = client.level == null ? 0L : client.level.getGameTime();
        float pulse = (Mth.sin(ticks * 0.12F) + 1.0F) * 0.5F;
        int edge = 0x2A000000 | ((int) (pulse * 24.0F) << 16);
        int band = Math.max(14, Math.min(width, height) / 12);
        graphics.fill(0, 0, width, height, 0x16000000);
        graphics.fill(0, 0, width, band, edge);
        graphics.fill(0, height - band, width, height, edge);
        graphics.fill(0, 0, band, height, edge);
        graphics.fill(width - band, 0, width, height, edge);
    }
}
