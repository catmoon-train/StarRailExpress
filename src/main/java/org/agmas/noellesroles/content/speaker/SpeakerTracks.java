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

package org.agmas.noellesroles.content.speaker;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.NRSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * 音响可选曲目：原版唱片 + 本模组循环/氛围音乐。
 */
public final class SpeakerTracks {
    public record Track(String id, Component name, SoundEvent sound) {
    }

    private static List<Track> CACHE;

    private SpeakerTracks() {
    }

    public static List<Track> all() {
        if (CACHE == null) {
            CACHE = build();
        }
        return CACHE;
    }

    public static Track byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (Track track : all()) {
            if (track.id().equals(id)) {
                return track;
            }
        }
        return null;
    }

    public static int indexOf(String id) {
        List<Track> tracks = all();
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private static List<Track> build() {
        List<Track> tracks = new ArrayList<>();
        disc(tracks, Items.MUSIC_DISC_13, SoundEvents.MUSIC_DISC_13.value());
        disc(tracks, Items.MUSIC_DISC_CAT, SoundEvents.MUSIC_DISC_CAT.value());
        disc(tracks, Items.MUSIC_DISC_BLOCKS, SoundEvents.MUSIC_DISC_BLOCKS.value());
        disc(tracks, Items.MUSIC_DISC_CHIRP, SoundEvents.MUSIC_DISC_CHIRP.value());
        disc(tracks, Items.MUSIC_DISC_FAR, SoundEvents.MUSIC_DISC_FAR.value());
        disc(tracks, Items.MUSIC_DISC_MALL, SoundEvents.MUSIC_DISC_MALL.value());
        disc(tracks, Items.MUSIC_DISC_MELLOHI, SoundEvents.MUSIC_DISC_MELLOHI.value());
        disc(tracks, Items.MUSIC_DISC_STAL, SoundEvents.MUSIC_DISC_STAL.value());
        disc(tracks, Items.MUSIC_DISC_STRAD, SoundEvents.MUSIC_DISC_STRAD.value());
        disc(tracks, Items.MUSIC_DISC_WARD, SoundEvents.MUSIC_DISC_WARD.value());
        disc(tracks, Items.MUSIC_DISC_11, SoundEvents.MUSIC_DISC_11.value());
        disc(tracks, Items.MUSIC_DISC_WAIT, SoundEvents.MUSIC_DISC_WAIT.value());
        disc(tracks, Items.MUSIC_DISC_PIGSTEP, SoundEvents.MUSIC_DISC_PIGSTEP.value());
        disc(tracks, Items.MUSIC_DISC_OTHERSIDE, SoundEvents.MUSIC_DISC_OTHERSIDE.value());
        disc(tracks, Items.MUSIC_DISC_5, SoundEvents.MUSIC_DISC_5.value());
        disc(tracks, Items.MUSIC_DISC_RELIC, SoundEvents.MUSIC_DISC_RELIC.value());
        disc(tracks, Items.MUSIC_DISC_CREATOR, SoundEvents.MUSIC_DISC_CREATOR.value());
        disc(tracks, Items.MUSIC_DISC_CREATOR_MUSIC_BOX, SoundEvents.MUSIC_DISC_CREATOR_MUSIC_BOX.value());
        disc(tracks, Items.MUSIC_DISC_PRECIPICE, SoundEvents.MUSIC_DISC_PRECIPICE.value());

        mod(tracks, TMMSounds.AMBIENT_PSYCHO_DRONE, "psycho_drone");
        mod(tracks, NRSounds.MANHUNT_CHASE, "manhunt_chase");
        mod(tracks, NRSounds.CIRCUS_BACKGROUND, "circus");
        mod(tracks, NRSounds.NYAN_CAT, "nyan_cat");
        mod(tracks, NRSounds.A_MENG, "a_meng");
        mod(tracks, NRSounds.JESTER_AMBIENT, "jester");
        mod(tracks, NRSounds.MUSIC_CLOCK, "clock");
        mod(tracks, NRSounds.MUSIC_UNWELCOME_SCHOOL, "unwelcome_school");
        mod(tracks, NRSounds.MUSIC_SAKURA_MOYU, "sakura_moyu");
        mod(tracks, NRSounds.MUSIC_ZENRIANBANKA, "zenrianbanka");
        mod(tracks, NRSounds.MUSIC_DISC_PIGSTEP_CUT, "pigstep_cut");
        mod(tracks, NRSounds.MUSIC_DISC_LAVA_CHICKEN_CUT, "lava_chicken");
        mod(tracks, NRSounds.MUSIC_DISC_CREATOR_CUT, "creator_cut");
        mod(tracks, NRSounds.MUSIC_DISC_BROKEN_MOON, "broken_moon");
        mod(tracks, NRSounds.MUSIC_DISC_LUPINUS, "lupinus");
        mod(tracks, NRSounds.ROLES_LAODA_SEE_YOU_AGAIN, "see_you_again");
        mod(tracks, NRSounds.ROLES_FURANDORU_FINAL, "furandoru");
        mod(tracks, NRSounds.ROLES_REMILIA, "remilia");
        mod(tracks, NRSounds.CIRCUS_INDOOR, "circus_indoor");
        mod(tracks, NRSounds.MUSIC_INDOOR, "music_indoor");
        mod(tracks, NRSounds.FLOWER_OUTDOOR, "flower_outdoor");
        mod(tracks, TMMSounds.AMBIENT_TRAIN_INSIDE, "train_inside");
        return List.copyOf(tracks);
    }

    private static void disc(List<Track> tracks, Item item, SoundEvent sound) {
        tracks.add(new Track(idOf(sound), item.getDescription(), sound));
    }

    private static void mod(List<Track> tracks, SoundEvent sound, String key) {
        tracks.add(new Track(idOf(sound),
                Component.translatable("gui.noellesroles.speaker.track." + key), sound));
    }

    private static String idOf(SoundEvent sound) {
        ResourceLocation key = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if (key != null) {
            return key.toString();
        }
        return sound.getLocation().toString();
    }
}
