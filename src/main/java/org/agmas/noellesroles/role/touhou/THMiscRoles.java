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

package org.agmas.noellesroles.role.touhou;

import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.roles.*;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;
import org.agmas.noellesroles.role_data.killer.HoujuuNueRoleData;
import org.agmas.noellesroles.role_data.neutral.THYuyukoRoleData;

import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.resources.ResourceLocation;

public class THMiscRoles {
    public static final String NAMESPACE = "th_misc";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // 二岩猯藏：可购买瞄准目标的商店物品futatuiwa_mamizou。冷却60s
    // 可以考虑和阴谋配合
    public static final ResourceLocation MAMIZOU_ID = id("futatuiwa_mamizou");
    public static SRERole MAMIZOU = TMMRoles
            .registerRole(new THMamizouRole(MAMIZOU_ID, new Color(113, 81, 71).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setDefaultMax(1)
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");
    // Kirisame Marisa
    public static final ResourceLocation IBUKI_SUIKA_ID = id("ibuki_suika");
    public static SRERole IBUKI_SUIKA = TMMRoles
            .registerRole(new THSuikaRole(IBUKI_SUIKA_ID, new Color(149, 76, 24).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setDefaultMax(1)
            .setDefaultEnableNeededPlayerCount(18)
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");

    public static final ResourceLocation HAKUREI_REIMU_ID = id("hakurei_reimu");
    public static SRERole HAKUREI_REIMU = TMMRoles
            .registerRole(new THReimuRole(HAKUREI_REIMU_ID, new Color(153, 82, 89).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setCanPickUpRevolver(false)
            .setFallDamageImmune(true) // 不会因高度限制摔死
            .setDefaultEnableNeededPlayerCount(18).setDefaultEnableChance(1000)
            .setAddedVersion("4.4");

    // 灵乌路空
    public static final ResourceLocation REIUJI_UTSUHO_ID = id("reiuji_utsuho");
    public static SRERole REIUJI_UTSUHO = TMMRoles
            .registerRole(new THUtsuhoRole(REIUJI_UTSUHO_ID, new Color(87, 86, 71).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setDefaultEnableNeededPlayerCount(24)
            .setDefaultEnableChance(1000)
            .setAddedVersion("4.4");
    // 四季映姬·夜摩仙那度 Shikieiki（有点像判官）
    // 四季映姬曾经是地藏，后来全国各地的地藏联名上书请求分担阎魔大人的工作，她也成为了阎魔。
    public static final ResourceLocation SHIKIEIKI_ID = id("shikieiki");
    public static SRERole SHIKIEIKI = TMMRoles
            .registerRole(new THShikieikiRole(SHIKIEIKI_ID, new Color(87, 79, 117).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true), "th_hell")
            .setCanPickUpRevolver(false).setVigilanteTeam(true).setSpecialVigilante(true)
            .setDefaultEnableNeededPlayerCount(24).setDefaultEnableChance(3000)
            .setAddedVersion("4.4");
    // 小野冢小町 Onozuka Komachi
    public static final ResourceLocation KOMACHI_ID = id("onozuka_komachi");
    public static SRERole KOMACHI = TMMRoles
            .registerRole(new THKomachiRole(KOMACHI_ID, new Color(199, 144, 161).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true), "th_hell")
            .setCanEarnKillerCoinAwardsFromKills(false)
            .setAddedVersion("4.4"); // 杀人无法获得基础金币奖励
    // 天子Hinanawi Tenshi
    public static final ResourceLocation TENSHI_ID = id("hinanawi_tenshi");
    public static SRERole TENSHI = TMMRoles
            .registerRole(new THTenshiRole(TENSHI_ID, new Color(89, 177, 250).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime() * 2, false))
            .setAddedVersion("4.4")
            .setCanBePoisoned(false);
    

    // 鬼人正邪 Kijin Seija
    public static SRERole KIJIN_SEIJA = TMMRoles.registerRole(new TouhouRole(id("kijin_seija"),
            new Color(49, 38, 40).getRGB(), false, true, MoodType.FAKE, Integer.MAX_VALUE, true))
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(6000)
            .setAddedVersion("4.4");

    // 封兽鵺 Houjuu Nue
    public static SRERole HOUJUU_NUE = TMMRoles.registerRole(new THHoujuuNueRole(id("houjuu_nue"),
            new Color(87, 78, 99).getRGB(), false, true, MoodType.FAKE, Integer.MAX_VALUE, true))
            .setRoleData(HoujuuNueRoleData::new)
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(4000)
            .setHiddenForRoleRotation(true)
            .setAddedVersion("4.4");

    // 魂魄妖梦 Konpaku Youmu
    public static SRERole KONPAKU_YOUMU = TMMRoles.registerRole(new THKonpakuYoumuRole(id("konpaku_youmu"),
            new Color(170, 152, 151).getRGB(), true, false, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000)
            .setVigilanteTeam(true)
            .setSpecialPolice(true)
            .setCanPickUpRevolver(true)
            .setBeSeenInstinctType(InstinctType.DEFAULT,
                    InstinctType.customWithFunction((self, target, selfRole, targetRole) -> {
                        if (target == null || self == null) {
                            return InstinctType.NONE;
                        }
                        if (target.isInvisible()) {
                            return InstinctType.NONE;
                        }
                        return InstinctType.DEFAULT;
                    }))
            .setAddedVersion("4.4");

    // 哆来咪 Doremy
    public static SRERole DOREMY = TMMRoles.registerRole(new THDoremyRole(id("doremy_sweet"),
            new Color(169, 80, 101).getRGB(), false, true, MoodType.FAKE, Integer.MAX_VALUE, true))
            .setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(500)
            .setCanBeRandomedByOtherRoles(false)
            .setRoleData(DoremyRoleData::new)
            .addTwoWayOpposingRole(ModRoles.DELAYER)
            .setAddedVersion("4.4");

    // 八云紫 Yakumo Yukari
    public static SRERole YAKUMO_YUKARI = TMMRoles.registerRole(new THYukariRole(id("yakumo_yukari"),
            new Color(109, 64, 128).getRGB(), false, true, MoodType.FAKE, Integer.MAX_VALUE, true))
            .setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(1000)
            .setAddedVersion("4.4");

    // 米斯蒂娅·萝蕾拉 Mystia Lorelei
    public static SRERole MYSTIA = TMMRoles.registerRole(new TouhouRole(id("mystia_lorelei"),
            new Color(223, 177, 166).getRGB(), true, false, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");

    // 火焰猫燐 kaenbyou_rin
    public static SRERole KAENBYOU_RIN = TMMRoles.registerRole(new THKaenbyouRinRole(id("kaenbyou_rin"),
            new Color(169, 80, 101).getRGB(), true, false, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(2000)
            .setHiddenForRoleRotation(true)
            .setCanBeRandomedByOtherRoles(true)
            .setAddedVersion("4.4");

    // 秦心 hata_no_kokoro
    public static SRERole HATA_NO_KOKORO = TMMRoles.registerRole(new THHatanokokoroRole(id("hata_no_kokoro"),
            new Color(245,226,241).getRGB(), RoleType.KILLER, MoodType.FAKE,Integer.MAX_VALUE, true))
            .setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(3000)
            .setAddedVersion("4.4");

    // 西行寺幽幽子 saigyouji_yuyuko
    public static SRERole YUYUKO = TMMRoles.registerRole(new THYuyukoRole(id("saigyouji_yuyuko"),
            new Color(202,148,155).getRGB(), RoleType.NEUTRALS, MoodType.FAKE,Integer.MAX_VALUE, true))
            .setCanUseInstinctAndNightVision(true)
            .setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(1000)
            .setHiddenForRoleRotation(true)
            .setRoleData(THYuyukoRoleData::new)
            .setAddedVersion("4.4");

    public static void init() {
    }
}
