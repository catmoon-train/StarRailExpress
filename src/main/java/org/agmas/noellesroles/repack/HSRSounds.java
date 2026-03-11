package org.agmas.noellesroles.repack;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import net.minecraft.sounds.SoundEvent;
import org.agmas.noellesroles.Noellesroles;

public interface HSRSounds {
    SoundEventRegistrar registrar = new SoundEventRegistrar(Noellesroles.MOD_ID);
    SoundEvent ITEM_SYRINGE_STAB = registrar.create("item.syringe.stab");

    static void init() {
        registrar.registerEntries();
    }
}
