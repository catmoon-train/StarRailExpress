package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import net.minecraft.sounds.SoundEvent;
import org.agmas.noellesroles.Noellesroles;

public class NRSounds {
    public static final SoundEventRegistrar registrar = new SoundEventRegistrar(Noellesroles.MOD_ID);
    public static final SoundEvent GAMBER_DEATH = registrar.create("noellesroles.gamber_died");
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
    }
}

