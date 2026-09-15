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

package org.agmas.noellesroles.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.speaker.SpeakerTracks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端循环播放管理。曲目结束后若仍处于开启状态会立刻重开，效果对齐疯魔背景乐。
 */
public final class SpeakerClientSounds {
    private static final Map<UUID, String> STATES = new HashMap<>();
    private static final Map<UUID, SpeakerSoundInstance> PLAYING = new HashMap<>();

    private SpeakerClientSounds() {
    }

    public static void apply(UUID playerId, String trackId, boolean playing) {
        if (!playing || trackId == null || trackId.isEmpty() || SpeakerTracks.byId(trackId) == null) {
            STATES.remove(playerId);
            stop(playerId);
            return;
        }
        String current = STATES.get(playerId);
        SpeakerSoundInstance existing = PLAYING.get(playerId);
        if (trackId.equals(current) && existing != null && Minecraft.getInstance().getSoundManager().isActive(existing)) {
            return;
        }
        STATES.put(playerId, trackId);
        start(playerId, trackId);
    }

    public static boolean isPlaying(UUID playerId) {
        return STATES.containsKey(playerId);
    }

    public static String currentTrack(UUID playerId) {
        return STATES.getOrDefault(playerId, "");
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.getSoundManager() == null) {
            clear();
            return;
        }
        for (Map.Entry<UUID, String> entry : Map.copyOf(STATES).entrySet()) {
            SpeakerSoundInstance sound = PLAYING.get(entry.getKey());
            Player owner = client.level.getPlayerByUUID(entry.getKey());
            if (owner == null) {
                continue;
            }
            if (sound != null) {
                sound.refreshPosition(owner, 1.0F);
            }
            if (sound == null || !client.getSoundManager().isActive(sound)) {
                start(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void updateChannelPositions(Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || instanceToChannel == null || instanceToChannel.isEmpty()) {
            return;
        }
        float partial = client.getTimer().getGameTimeDeltaPartialTick(false);
        for (Map.Entry<UUID, SpeakerSoundInstance> entry : PLAYING.entrySet()) {
            SpeakerSoundInstance sound = entry.getValue();
            Player owner = client.level.getPlayerByUUID(entry.getKey());
            if (owner == null) {
                continue;
            }
            sound.refreshPosition(owner, partial);
            ChannelAccess.ChannelHandle handle = instanceToChannel.get(sound);
            if (handle == null) {
                continue;
            }
            Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            handle.execute(channel -> channel.setSelfPosition(pos));
        }
    }

    public static void clear() {
        for (UUID id : Map.copyOf(PLAYING).keySet()) {
            stop(id);
        }
        STATES.clear();
    }

    private static void start(UUID playerId, String trackId) {
        stop(playerId);
        SpeakerTracks.Track track = SpeakerTracks.byId(trackId);
        Minecraft client = Minecraft.getInstance();
        if (track == null || client.level == null) {
            return;
        }
        Player owner = client.level.getPlayerByUUID(playerId);
        Vec3 pos = owner == null ? Vec3.ZERO : SpeakerSoundInstance.shoulderPos(owner, 1.0F);
        SpeakerSoundInstance sound = new SpeakerSoundInstance(track.sound(), playerId, pos);
        PLAYING.put(playerId, sound);
        client.getSoundManager().play(sound);
    }

    private static void stop(UUID playerId) {
        SpeakerSoundInstance sound = PLAYING.remove(playerId);
        if (sound != null) {
            sound.release();
            Minecraft client = Minecraft.getInstance();
            if (client.getSoundManager() != null) {
                client.getSoundManager().stop(sound);
            }
        }
    }
}
