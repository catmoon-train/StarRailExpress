package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;

public class NRSounds {
    public static final SoundEventRegistrar registrar = new SoundEventRegistrar(Noellesroles.MOD_ID);
    public static final SoundEvent GAMBER_DEATH = registrar.create("noellesroles.gamber_died");
    public static final SoundEvent MUSIC_CLOCK = registrar.create("noellesroles.clock");
    public static final SoundEvent GONGXI_FACAI = registrar.create("noellesroles.gongxifacai");
    public static final SoundEvent TO_BE_CONTINUED = registrar.create("noellesroles.to_be_continued");
    public static final SoundEvent HARPY_WELCOME = registrar.create("noellesroles.harpy_welcome");
    public static final SoundEvent WIND = registrar.create("noellesroles.wind");
    public static final SoundEvent JESTER_AMBIENT = registrar.create("noellesroles.jester");
    public static final SoundEvent TIME_STOP = registrar.create("noellesroles.time_stop");
    public static final SoundEvent DIO_SPAWN = registrar.create("noellesroles.dio_spawn");
    public static final SoundEvent TIME_START = registrar.create("noellesroles.time_start");

    public static void initialize() {
        registrar.registerEntries();
        registerAmbience();
    }

    public static void registerAmbience() {

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
                new MyBackgroundAmbience(NRSounds.MUSIC_CLOCK, SoundSource.MASTER,
                        player -> {
                            var client = Minecraft.getInstance();
                            if (client == null || client.player == null)
                                return false;
                            if (client.player.hasEffect(ModEffects.OTHERWORLD_AURA))
                                return true;
                            return false;
                        },
                        0.5f, 10, 10));
    }
}
