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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class MyBackgroundAmbience extends BackgroundAmbience {

   private final PlayPredicate predicate;
   private final SoundFactory factory;
   private SoundInstance soundInstance;

   public MyBackgroundAmbience(SoundEvent soundEvent, SoundSource soundCategory, PlayPredicate predicate, float volume,
         int fadeIn, int fadeOut) {
      super(soundEvent, predicate, fadeIn);
      this.factory = (player) -> new MyBackgroundAmbientLoop(player, soundEvent, soundCategory, volume, predicate,
            fadeIn,
            fadeOut);
      this.predicate = predicate;
   }

   public boolean tryStarting(LocalPlayer player, SoundManager soundManager) {
      if (this.soundInstance != null
            && (!this.predicate.shouldPlay(player) || soundManager.isActive(this.soundInstance))) {
         return false;
      } else {
         this.soundInstance = this.factory.create(player);
         soundManager.play(this.soundInstance);
         return true;
      }
   }
}
