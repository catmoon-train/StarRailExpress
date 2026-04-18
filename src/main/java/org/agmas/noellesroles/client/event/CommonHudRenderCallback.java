package org.agmas.noellesroles.client.event;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;

import java.util.ArrayList;
import java.util.function.BiConsumer;

public interface CommonHudRenderCallback {
    public static class CommonRenderEvent {
        public ArrayList<BiConsumer<FakeGuiGraphics, DeltaTracker>> role_events = new ArrayList<>();

        public ArrayList<BiConsumer<FakeGuiGraphics, DeltaTracker>> getConsumer() {
            return role_events;
        }

        public void register(BiConsumer<FakeGuiGraphics, DeltaTracker> consumer) {
            role_events.add(consumer);
        }
    }

    public final static CommonRenderEvent EVENT = new CommonRenderEvent();
}