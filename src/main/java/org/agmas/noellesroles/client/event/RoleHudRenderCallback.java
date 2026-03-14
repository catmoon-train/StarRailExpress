package org.agmas.noellesroles.client.event;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class RoleHudRenderCallback {
    public static class CustomRenderEvent<T> {
        public HashMap<T, ArrayList<BiConsumer<GuiGraphics, DeltaTracker>>> role_events = new HashMap<>();

        public ArrayList<BiConsumer<GuiGraphics, DeltaTracker>> getConsumer(T identifier) {
            return role_events.get(identifier);
        }

        public void register(T identifier, BiConsumer<GuiGraphics, DeltaTracker> consumer) {
            role_events.computeIfAbsent(identifier, (a) -> {
                return new ArrayList<BiConsumer<GuiGraphics, DeltaTracker>>();
            });
            role_events.get(identifier).add(consumer);
        }

        public boolean removeConsumer(ResourceLocation identifier) {
            if (role_events.containsKey(identifier)) {
                role_events.remove(identifier);
                return true;
            }
            return false;
        }
    }

    public final static CustomRenderEvent<ResourceLocation> EVENT = new CustomRenderEvent<>();
}