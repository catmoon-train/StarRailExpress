package org.agmas.noellesroles.client.event;

import net.minecraft.client.DeltaTracker;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface CommonHudRenderCallback {

    Event<CommonHudRenderCallback> EVENT = createArrayBacked(CommonHudRenderCallback.class,
            listeners -> (client, guiGraphics, deltaTracker) -> {
                for (CommonHudRenderCallback listener : listeners) {
                    listener.onRenderer(client, guiGraphics, deltaTracker);
                }
            });

    void onRenderer(Minecraft client, FakeGuiGraphics guiGraphics, DeltaTracker deltaTracker);
}