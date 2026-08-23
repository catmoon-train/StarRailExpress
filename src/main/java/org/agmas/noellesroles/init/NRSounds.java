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
    // public static final SoundEvent ITEM_SYRINGE_STAB =
    // registrar.create("item.syringe.stab");
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
    public static final SoundEvent FLOWER_OUTDOOR = registrar.create("noellesroles.flower_outdoor");
    public static final SoundEvent MUSIC_INDOOR = registrar.create("noellesroles.music_indoor");
    public static final SoundEvent ODO = registrar.create("noellesroles.odo");

    // 未使用
    public static final SoundEvent MUSIC_SAKURA_MOYU = registrar.create("music.sakura_moyu");
    // public static final SoundEvent MUSIC_DR_NIGULA =
    // registrar.create("music.dr_nigula");
    // public static final SoundEvent MUSIC_ENTRUST_THIS_WORLD_TO_IDOLS = registrar
    // .create("music.entrust_this_world_to_idols");
    // public static final SoundEvent MUSIC_GO_TOWARDS_NIGHT =
    // registrar.create("music.go_towards_night");
    // public static final SoundEvent MUSIC_IDOL = registrar.create("music.idol");
    // public static final SoundEvent MUSIC_MAIDEN_S_CAPRICCIO =
    // registrar.create("music.maiden_s_capriccio");
    // public static final SoundEvent MUSIC_SECRET_BASE =
    // registrar.create("music.secret_base");
    public static final SoundEvent MUSIC_UNWELCOME_SCHOOL = registrar.create("music.unwelcome_school");
    // public static final SoundEvent MUSIC_YUZU_FUN_FUN_RE_BOOT =
    // registrar.create("music.yuzu_fun_fun_re_boot");
    public static final SoundEvent MUSIC_ZENRIANBANKA = registrar.create("music.zenrianbanka");

    // ==================== 常量定义 ====================
    public static final SoundEvent MUSIC_DISC_LAVA_CHICKEN_CUT = registrar.create("music_disc.lava_chicken_cut");
    public static final SoundEvent MUSIC_DISC_CREATOR_CUT = registrar.create("music_disc.creator_cut");
    public static final SoundEvent MUSIC_DISC_BROKEN_MOON = registrar.create("music_disc.broken_moon");
    public static final SoundEvent MUSIC_DISC_PIGSTEP_CUT = registrar.create("music_disc.pigstep_cut");
    public static final SoundEvent MUSIC_DISC_LUPINUS = registrar.create("music_disc.lupinus");

    public static void initialize() {
        registrar.registerEntries();
    }
}
