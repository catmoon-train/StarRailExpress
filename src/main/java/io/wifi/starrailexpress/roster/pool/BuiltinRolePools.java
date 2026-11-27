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

package io.wifi.starrailexpress.roster.pool;

import java.util.List;

/**
 * 内置职业池预设。
 *
 * <p>
 * 池子只记录「职业 path」，展示名与阵营全部在运行时从
 * {@link io.wifi.starrailexpress.api.TMMRoles#getRoleByPath(String)} 与
 * {@link io.wifi.starrailexpress.api.RoleTeam} 解析，不在这里手写阵营，
 * 避免翻译改动 / 阵营调整后池子数据失真。
 *
 * <p>
 * 前四个池子（常驻池 / 爆破狂欢 / 东方之夜 / 黑暗之时）与需求给的职业池表一致，
 * 东方池与全职业池是额外补充的。修饰符不在池内单独列举：{@link RolePool#modifierPaths()}
 * 为空表示「全部修饰符都启用」。
 *
 * <p>
 * 新增池子只需要在这里加一个 {@link RolePool#of} 常量并登记进 {@link #ALL}。
 */
public final class BuiltinRolePools {
    private BuiltinRolePools() {}

    /** 常驻池 */
    public static final RolePool RESIDENT = RolePool.of("resident", "常驻池",
            List.of("telegrapher", "attendant", "conductor", "noisemaker", "fighter", "avenger", "singer",
                    "engineer", "broadcaster", "better_vigilante", "awesome_binglus", "bartender", "star", "agent",
                    "veteran", "voodoo", "ghost", "psychologist", "courier", "coroner", "doctor", "athlete",
                    "recaller", "glitch_robot", "monitor", "photographer", "chef", "oldman", "magician",
                    "fortuneteller", "clockmaker", "writer", "accountant", "rescuer", "firefighter", "alchemist",
                    "locksmith", "hatate", "disc_master", "best_vigilante", "patroller", "elf", "jojo", "swast",
                    "martial_arts_instructor", "baseball_player", "morphling", "manipulator", "poisoner",
                    "executioner", "swapper", "stalker", "trapper", "the_insane_damned_paranoid_killer",
                    "conspirator", "phantom", "avaricious", "necromancer", "bomber", "cleaner", "dio", "amnesiac",
                    "initiate", "admirer", "prankster", "illusionist", "vulture", "jester", "wind_yaose",
                    "commander", "gambler", "arsonist", "wayfarer", "thief", "goose", "leader", "recorder",
                    "nianshou"
));

    /** 爆破狂欢 */
    public static final RolePool BLAST_CARNIVAL = RolePool.of("blast_carnival", "爆破狂欢",
            List.of("builder", "fitter", "barbarian", "nurse", "lao_da", "rift_walker", "mushroom_scholar",
                    "salted_fish", "kaenbyou_rin", "mystia_lorelei", "hoto_kokoa", "kafu_chino", "the_fool",
                    "net_cop", "guard", "leon", "ghost_eye", "heng_xing_ti", "zhang_angel", "spellbreaker",
                    "creeper", "lost_killer", "boom_maniac", "gangsters", "bandit", "niaoshoushou",
                    "cat_necromancer", "silent_killer", "reiuji_utsuho", "blood_feudist", "kijin_seija",
                    "lengxiao", "embalmer", "voice_changer", "silver_wing", "lender", "doomed_sinner",
                    "rabbit_wansui", "amon", "anatman", "asatya", "candlebearer"
));

    /** 东方之夜 */
    public static final RolePool TOUHOU_NIGHT = RolePool.of("touhou_night", "东方之夜",
            List.of("great_detective", "diviner", "painter", "ibaraki_kasen", "daiyousei", "baka", "pachuri",
                    "jade_general", "huziwara_no_mokou", "kirisame_marisa", "kotiya_sanae", "return_traveler",
                    "maid_sakuya", "hoan_meirin", "konpaku_youmu", "shikieiki", "fat_fish", "echo_listener",
                    "imitator", "warlock", "silencer", "exampler", "remilia", "hakurei_reimu", "ibuki_suika",
                    "futatuiwa_mamizou", "doremy_sweet", "ayayaya", "yakumo_yukari", "hata_no_kokoro",
                    "onozuka_komachi", "koakuma", "witch_maiden", "houraisan_kaguya", "morichika_rinnosuke",
                    "kawashiro_nitori", "furandoru", "saigyouji_yuyuko", "godfather", "raven", "reasoner"
));

    /** 黑暗之时 */
    public static final RolePool DARK_HOURS = RolePool.of("dark_hours", "黑暗之时",
            List.of("meatball", "cake_maker", "leather_pig", "mortician", "medium", "insurance",
                    "disabled_patient", "waiter", "watchman", "tomato_head", "tailor", "kasodani_kyouko",
                    "hinanawi_tenshi", "magic_apprentice", "dictator", "swordsman", "phantom_spirit", "watcher",
                    "ninja", "delayer", "dream", "skincrawler", "wizard", "wraith_assassin", "programmer",
                    "party_killer", "nostalgist", "tartaglia", "youlu", "nature_spirit", "undead_lord", "infected",
                    "mortician_bodymaker", "musician_phantom", "mercenary", "cuckoo", "bee_queen",
                    "licensed_villain", "lin_family", "daywalker"
));

    /** 东方池 */
    public static final RolePool TOUHOU = RolePool.of("touhou", "东方池",
            List.of("great_detective", "diviner", "painter", "ibaraki_kasen", "daiyousei", "baka", "pachuri",
                    "jade_general", "huziwara_no_mokou", "kirisame_marisa", "kotiya_sanae", "return_traveler",
                    "maid_sakuya", "hoan_meirin", "konpaku_youmu", "shikieiki", "fat_fish", "echo_listener",
                    "imitator", "warlock", "silencer", "exampler", "remilia", "hakurei_reimu", "ibuki_suika",
                    "futatuiwa_mamizou", "doremy_sweet", "ayayaya", "yakumo_yukari", "hata_no_kokoro",
                    "onozuka_komachi", "koakuma", "witch_maiden", "houraisan_kaguya", "morichika_rinnosuke",
                    "kawashiro_nitori", "furandoru", "saigyouji_yuyuko", "godfather", "raven", "reasoner",
                    "mystia_lorelei", "kaenbyou_rin", "reiuji_utsuho", "hinanawi_tenshi", "kasodani_kyouko",
                    "kijin_seija"
));

    /** 全职业池：不列举职业，应用时展开为全部已注册职业。 */
    public static final RolePool ALL_ROLES = RolePool.of("all_roles", "全职业池",
            List.of());

    /** 全部内置池子，顺序即投票界面的展示顺序。 */
    public static final List<RolePool> ALL = List.of(
            RESIDENT, BLAST_CARNIVAL, TOUHOU_NIGHT, DARK_HOURS, TOUHOU, ALL_ROLES);
}
