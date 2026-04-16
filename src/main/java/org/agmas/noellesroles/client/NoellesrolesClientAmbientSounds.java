package org.agmas.noellesroles.client;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public class NoellesrolesClientAmbientSounds {

  public static void register() {
    AmbienceUtil.registerBackgroundAmbience(
        new BackgroundAmbience(NRSounds.JESTER_AMBIENT,
            player -> {
              if (SREClient.gameComponent == null)
                return false;
              if (SREClient.gameComponent.isPsychoActive()) {
                var level = Minecraft.getInstance().level;
                if (level == null)
                  return false;
                return (level.players().stream().anyMatch((p) -> {
                  if (SREClient.gameComponent.isRole(p, ModRoles.JESTER)) {
                    if (SREPlayerPsychoComponent.KEY.get(p).getPsychoTicks() > 0) {
                      return true;
                    }
                  }
                  return false;
                }));
              }
              return false;
            },
            1));
            AmbienceUtil.registerBackgroundAmbience(
        new BackgroundAmbience(NRSounds.NYAN_CAT,
            player -> {
              if (SREClient.gameComponent == null)
                return false;
              if (SREClient.gameComponent.isPsychoActive()) {
                var level = Minecraft.getInstance().level;
                if (level == null)
                  return false;
                return (level.players().stream().anyMatch((p) -> {
                  if (SREClient.gameComponent.isRole(p, ModRoles.CAT_KILLER)) {
                    if (SREPlayerPsychoComponent.KEY.get(p).getPsychoTicks() > 0) {
                      return true;
                    }
                  }
                  return false;
                }));
              }
              return false;
            },
            1));
    AmbienceUtil.registerBackgroundAmbience(
        new MyBackgroundAmbience(NRSounds.MUSIC_CLOCK, SoundSource.MASTER,
            player -> {
              var client = Minecraft.getInstance();
              if (client == null || client.player == null)
                return false;
              if (client.player.hasEffect(ModEffects.OTHERWORLD_AURA))
                return true;
              return false;
            },
            0.8f, 10, 10));
  }
}
