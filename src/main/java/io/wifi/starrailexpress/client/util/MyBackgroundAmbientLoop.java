package io.wifi.starrailexpress.client.util;

import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience.PlayPredicate;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbientLoop;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class MyBackgroundAmbientLoop extends BackgroundAmbientLoop {
    private final LocalPlayer player;
    private int transitionTimer;
    private final BackgroundAmbience.PlayPredicate playPredicate;
    private final int fadeIn;
    private final int fadeOut;
    private final float maxVolume;

    public MyBackgroundAmbientLoop(LocalPlayer player, SoundEvent soundEvent, SoundSource soundCategory, float volume,
            PlayPredicate playPredicate, int fadeIn, int fadeOut) {
        super(player, soundEvent, soundCategory, playPredicate, fadeIn, fadeOut);
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.maxVolume = volume;
        this.relative = true;
        this.playPredicate = playPredicate;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
    }

    @Override
    public void tick() {
        if (!this.player.isRemoved() && this.transitionTimer >= 0) {
            int fadeTime;
            if (this.playPredicate.shouldPlay(this.player)) {
                ++this.transitionTimer;
                fadeTime = this.fadeIn;
            } else {
                --this.transitionTimer;
                fadeTime = this.fadeOut;
            }

            this.transitionTimer = Math.min(this.transitionTimer, fadeTime);
            this.volume = Math.max(0.0F,
                    Math.min(this.maxVolume * ((float) this.transitionTimer / (float) fadeTime), this.maxVolume));
        } else {
            this.stop();
        }
    }
}