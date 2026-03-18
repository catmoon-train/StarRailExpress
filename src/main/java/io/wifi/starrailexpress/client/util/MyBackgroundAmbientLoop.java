package io.wifi.starrailexpress.client.util;

import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience.PlayPredicate;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbientLoop;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class MyBackgroundAmbientLoop extends BackgroundAmbientLoop {

    public MyBackgroundAmbientLoop(LocalPlayer player, SoundEvent soundEvent, SoundSource soundCategory, float volume,
            PlayPredicate playPredicate, int fadeIn, int fadeOut) {
        super(player, soundEvent, soundCategory, playPredicate, fadeIn, fadeOut);
        this.volume = volume;
    }
}