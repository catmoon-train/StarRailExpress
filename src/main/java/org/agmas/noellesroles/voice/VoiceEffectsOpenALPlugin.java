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

package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.ClientEmbalmerState;
import org.agmas.noellesroles.init.ModEffects;
import org.lwjgl.openal.AL10;

import java.util.UUID;

/**
 * OpenAL-based voice effects: Heavy Metal Voice (pitch).
 *
 * Replaces the old PCM-level pitch processing with a native OpenAL effect,
 * providing zero-latency, native-performance voice modification.
 *
 * - Heavy Metal Voice: uses AL10.alSourcef(AL_PITCH) to lower voice pitch.
 *
 * <p>注意：回响（VOICE_ECHO）不再在此处处理，已迁移至
 * {@link VoiceExtraEffectsPlugin} 的 PCM 级实现（反馈延迟线），
 * 与其它语音效果走同一链路。</p>
 */
public class VoiceEffectsOpenALPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "noellesroles_voice_effects_openal";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.Post.class, this::onOpenALSound);
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    private void onOpenALSound(OpenALSoundEvent.Post event) {
        UUID speakerId = event.getChannelId();
        if (speakerId == null) return;

        int source = event.getSource();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // This plugin is the single authority for the OpenAL source pitch so the
        // Heavy Metal effect and the Embalmer masquerade pitch never overwrite each
        // other (both used to set AL_PITCH independently and fought over fire order).
        //
        // Embalmer pitch is keyed by the speaker UUID and lives in client state even
        // for players the client cannot resolve as an entity, so it is read first.
        float embalmerPitch = ClientEmbalmerState.pitch(speakerId); // 1.0 when inactive
        float heavyMetalRatio = 1.0F;

        Player player = mc.level.getPlayerByUUID(speakerId);
        if (player != null && player.hasEffect(ModEffects.HEAVY_METAL_VOICE)) {
            heavyMetalRatio = ModEffects.getHeavyMetalPitchRatio(player);
        }

        // ---- Combined pitch (always written so it resets to 1.0 when nothing applies) ----
        float pitch = Mth.clamp(embalmerPitch * heavyMetalRatio, 0.4F, 2.0F);
        try {
            AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        } catch (Throwable ignored) {}
    }

    /**
     * 兼容占位：回响已迁移至 {@link VoiceExtraEffectsPlugin} 的 PCM 级实现，
     * 本插件不再持有 EFX 资源，无需清理。
     */
    public static void cleanupAll() {}
}
