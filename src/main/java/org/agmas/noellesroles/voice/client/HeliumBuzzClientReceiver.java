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

package org.agmas.noellesroles.voice.client;

import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import org.agmas.noellesroles.voice.HeliumPitchShifter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side receiver for processing voice chat audio with helium effect.
 * Applies WSOLA pitch shifting for helium voice only.
 * (Heavy metal pitch and voice echo are now handled by VoiceEffectsOpenALPlugin via native OpenAL.)
 */
@Environment(EnvType.CLIENT)
public class HeliumBuzzClientReceiver {

    private static final float RAMP_OUT_TICKS = 10.0F;
    private static final Map<UUID, HeliumPitchShifter> SHIFTERS = new ConcurrentHashMap<>();

    /**
     * Base pitch ratio for helium effect (1.75 = 75% higher pitch)
     */
    private static final float BASE_HELIUM_RATIO = 1.75F;

    /**
     * Register the client-side audio processing event.
     */
    public static void register(EventRegistration r) {
        r.registerEvent(ClientReceiveSoundEvent.EntitySound.class, HeliumBuzzClientReceiver::onReceiveEntity);
    }

    private static void onReceiveEntity(ClientReceiveSoundEvent.EntitySound event) {
        if (event.isCancelled()) {
            return;
        }

        short[] pcm = event.getRawAudio();
        if (pcm == null || pcm.length == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        UUID speaker = event.getEntityId();
        if (speaker == null) {
            return;
        }

        Player player = mc.level.getPlayerByUUID(speaker);
        if (player == null) {
            SHIFTERS.remove(speaker);
            return;
        }

        HeliumBuzzPlayerComponent comp = ModComponents.HELIUM_BUZZ.get(player);
        boolean hasHelium = comp != null && comp.isActive();
        if (!hasHelium) {
            SHIFTERS.remove(speaker);
            return;
        }

        float ratio = pitchRatioFor(comp);
        HeliumPitchShifter shifter = SHIFTERS.computeIfAbsent(speaker, k -> new HeliumPitchShifter());
        event.setRawAudio(shifter.process(pcm, ratio));
    }

    /**
     * Calculate the pitch ratio based on the remaining effect time.
     * Applies a fade-out effect when the effect is about to end.
     */
    private static float pitchRatioFor(HeliumBuzzPlayerComponent comp) {
        int remaining = comp.getTicksRemaining();
        if (remaining >= (int) RAMP_OUT_TICKS) {
            return BASE_HELIUM_RATIO;
        }
        float ramp = Math.max(0.0F, remaining / RAMP_OUT_TICKS);
        return 1.0F + (BASE_HELIUM_RATIO - 1.0F) * ramp;
    }
}
