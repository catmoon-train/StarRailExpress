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
    private boolean tryStop = false;

    public void tryStop() {
        this.tryStop = true;
    }

    @Override
    public boolean equals(Object instance) {
        if (instance == null)
            return false;
        if (!(instance instanceof MyBackgroundAmbientLoop t)) {
            return false;
        }
        // player
        // looping
        // delay
        // volume
        // maxVolume
        // relative
        // playPredicate
        // fadeIn
        // fadeOut
        if (t.looping == this.looping && t.relative == this.relative
                && this.delay == t.delay && t.fadeIn == this.fadeIn && t.fadeOut == this.fadeOut
                && t.location == this.location && t.maxVolume == this.maxVolume && t.pitch == this.pitch
                && t.player == this.player && t.source == this.source)
            return true;
        return false;
    }

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
            if (shouldPlay()) {
                ++this.transitionTimer;
                fadeTime = this.fadeIn;
            } else {
                --this.transitionTimer;
                fadeTime = this.fadeOut;
            }

            this.transitionTimer = Math.min(this.transitionTimer, fadeTime);
            this.volume = Math.max(0.0F,
                    Math.min(this.maxVolume * ((float) this.transitionTimer / (float) fadeTime), this.maxVolume));

            // 淡出完成，主动停止
            if (this.transitionTimer <= 0 && this.volume <= 0) {
                this.stop();
            }
        } else {
            this.stop();
        }
    }

    public boolean shouldPlay() {
        if (this.tryStop) {
            return false;
        }
        return this.playPredicate.shouldPlay(this.player);
    }
}