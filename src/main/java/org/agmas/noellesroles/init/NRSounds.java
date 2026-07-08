package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import net.minecraft.sounds.SoundEvent;
import org.agmas.noellesroles.Noellesroles;

public class NRSounds {
    public static final SoundEventRegistrar registrar = new SoundEventRegistrar(Noellesroles.MOD_ID);
    public static final SoundEvent GAMBER_DEATH = registrar.create("noellesroles.gamber_died");
    public static final SoundEvent MUSIC_CLOCK = registrar.create("noellesroles.clock");
    public static final SoundEvent GONGXI_FACAI = registrar.create("noellesroles.gongxifacai");
    public static final SoundEvent TO_BE_CONTINUED = registrar.create("noellesroles.to_be_continued");
    public static final SoundEvent HARPY_WELCOME = registrar.create("noellesroles.harpy_welcome");
    public static final SoundEvent WIND = registrar.create("noellesroles.wind");
    public static final SoundEvent JESTER_AMBIENT = registrar.create("noellesroles.jester");
    public static final SoundEvent NYAN_CAT = registrar.create("noellesroles.nyan_cat");
    
    public static final SoundEvent THMUSIC_UN_OWEN = registrar.create("noellesroles.who_kill_un_owen");
    public static final SoundEvent TIME_STOP = registrar.create("noellesroles.time_stop");
    public static final SoundEvent DIO_SPAWN = registrar.create("noellesroles.dio_spawn");
    public static final SoundEvent TIME_START = registrar.create("noellesroles.time_start");
    public static final SoundEvent PARTY_SKILL = registrar.create("noellesroles.party_skill");
//    public static final SoundEvent ITEM_SYRINGE_STAB = registrar.create("item.syringe.stab");
    public static final SoundEvent SHOTGUN_FIRE = registrar.create("noellesroles.shotgun_fire");
    public static final SoundEvent SHORT_CIRCUIT = registrar.create("noellesroles.short_circuit");
    public static final SoundEvent SHOTGUNU_COCK = registrar.create("noellesroles.shotgun_cock");
    
    // 疫使相关音效
    public static final SoundEvent INFECTED_COUGH = registrar.create("noellesroles.cough");
    public static final SoundEvent INFECTED_INFECT = registrar.create("noellesroles.infect");
    public static final SoundEvent BEEP = registrar.create("noellesroles.role_mine_beep");
    public static final SoundEvent SYRINGE_STAB = registrar.create("noellesroles.syringe_stab");
    public static final SoundEvent C4_BEEP = registrar.create("noellesroles.c4_beep");
    public static final SoundEvent MAFIA = registrar.create("noellesroles.mafia");
    public static final SoundEvent BAKA_BAKA = registrar.create("plush.baka");
    public static final SoundEvent WO_SHI_NAI_LONG = registrar.create("plush.nai_long");
    public static final SoundEvent LEVEL = registrar.create("noellesroles.level");
    public static final SoundEvent SNOW_STORM = registrar.create("noellesroles.winter_storm");
    public static final SoundEvent SAND_STORM = registrar.create("noellesroles.dust_storm");
    public static final SoundEvent BROKEN_ALARM = registrar.create("noellesroles.broken_alarm");
    public static final SoundEvent CIRCUS_BACKGROUND = registrar.create("noellesroles.circus_background");
    public static final SoundEvent A_MENG = registrar.create("noellesroles.a_meng");
    // 皮革噶的：疯魔模式神秘追杀音效
    public static final SoundEvent MANHUNT_CHASE = registrar.create("noellesroles.manhunt_chase");
    public static final SoundEvent CIRCUS_INDOOR = registrar.create("noellesroles.dasiy_bell");

    public static void initialize() {
        registrar.registerEntries();
    }
}
