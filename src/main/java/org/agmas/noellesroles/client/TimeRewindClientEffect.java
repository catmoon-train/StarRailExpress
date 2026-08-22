/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.packet.TimeRewindVisualS2CPacket;

/** Client clock shared by the rewind shader and its packet receiver. */
public final class TimeRewindClientEffect {
    private static int totalTicks;
    private static int ticksLeft;
    private static boolean initialized;

    private TimeRewindClientEffect() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(TimeRewindVisualS2CPacket.ID,
                (payload, context) -> context.client().execute(
                        () -> start(payload.durationTicks())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!client.isPaused() && ticksLeft > 0) {
                ticksLeft--;
            }
        });
    }

    public static void start(int durationTicks) {
        if (durationTicks <= 0) {
            totalTicks = 0;
            ticksLeft = 0;
            return;
        }
        totalTicks = Math.max(1, durationTicks);
        ticksLeft = totalTicks;
    }

    public static boolean isActive() {
        return ticksLeft > 0;
    }

    public static float progress(float partialTick) {
        if (totalTicks <= 0) {
            return 1.0f;
        }
        return Mth.clamp(1.0f - (ticksLeft - partialTick) / totalTicks, 0.0f, 1.0f);
    }

    public static float strength(float partialTick) {
        if (!isActive()) {
            return 0.0f;
        }
        float progress = progress(partialTick);
        float fadeIn = (float) Mth.smoothstep(Math.min(1.0f, progress / 0.16f));
        float fadeOut = 1.0f - (float) Mth.smoothstep(
                Math.max(0.0f, (progress - 0.78f) / 0.22f));
        float pulse = 0.88f + 0.12f * Mth.sin(progress * Mth.TWO_PI * 5.0f);
        return Mth.clamp(fadeIn * fadeOut * pulse, 0.0f, 1.0f);
    }
}
