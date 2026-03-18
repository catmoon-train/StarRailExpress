package io.wifi.starrailexpress.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;

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
