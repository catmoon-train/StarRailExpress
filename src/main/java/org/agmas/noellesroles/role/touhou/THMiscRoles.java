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

import org.agmas.noellesroles.role.touhou.roles.*;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.resources.ResourceLocation;

public class THMiscRoles {
  public static final String NAMESPACE = "th_misc";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  // Kirisame Marisa
  public static final ResourceLocation IBUKI_SUIKA_ID = id("ibuki_suika");
  public static SRERole IBUKI_SUIKA = TMMRoles
      .registerRole(new THSuikaRole(IBUKI_SUIKA_ID, new Color(149, 76, 24).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          TMMRoles.CIVILIAN_MAX_SPRINT_TICKS * 100, true))
      .setDefaultMax(1)
      .setDefaultEnableNeededPlayerCount(18)
      .setDefaultEnableChance(5000);

  public static final ResourceLocation HAKUREI_REIMU_ID = id("hakurei_reimu");
  public static SRERole HAKUREI_REIMU = TMMRoles
      .registerRole(new THReimuRole(HAKUREI_REIMU_ID, new Color(153, 82, 89).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true))
      .setCanPickUpRevolver(false)
      .setFallDamageImmune(true) // 不会因高度限制摔死
      .setDefaultEnableNeededPlayerCount(18).setDefaultEnableChance(1000);

  // 灵乌路空
  public static final ResourceLocation REIUJI_UTSUHO_ID = id("reiuji_utsuho");
  public static SRERole REIUJI_UTSUHO = TMMRoles
      .registerRole(new THUtsuhoRole(REIUJI_UTSUHO_ID, new Color(87, 86, 71).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true))
      .setDefaultEnableNeededPlayerCount(24)
      .setDefaultEnableChance(1000);
  // 四季映姬·夜摩仙那度 Shikieiki（有点像判官）
  // 四季映姬曾经是地藏，后来全国各地的地藏联名上书请求分担阎魔大人的工作，她也成为了阎魔。
  public static final ResourceLocation SHIKIEIKI_ID = id("shikieiki");
  public static SRERole SHIKIEIKI = TMMRoles
      .registerRole(new THShikieikiRole(SHIKIEIKI_ID, new Color(87, 79, 117).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true), "th_hell")
      .setCanPickUpRevolver(false).setVigilanteTeam(true).setSpecialVigilante(true)
      .setDefaultEnableNeededPlayerCount(24).setDefaultEnableChance(3000);
  // 小野冢小町 Onozuka Komachi
  public static final ResourceLocation KOMACHI_ID = id("onozuka_komachi");
  public static SRERole KOMACHI = TMMRoles
      .registerRole(new THKomachiRole(KOMACHI_ID, new Color(199, 144, 161).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true), "th_hell")
      .setCanEarnKillerCoinAwardsFromKills(false); // 杀人无法获得基础金币奖励
  // 天子Hinanawi Tenshi
  public static final ResourceLocation TENSHI_ID = id("hinanawi_tenshi");
  public static SRERole TENSHI = TMMRoles
      .registerRole(new THTenshiRole(TENSHI_ID, new Color(89, 177, 250).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 2, false));
  public static final ResourceLocation RINNOSUKE_ID = id("morichika_rinnosuke");
  // 森近霖之助 Morichika Rinnosuke
  public static SRERole RINNOSUKE = TMMRoles.registerRole(new THRinnosukeRole(
      RINNOSUKE_ID, // 角色 ID
      new Color(252, 250, 249).getRGB(),
      false, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      SRERole.MoodType.REAL, // 真实心情
      Integer.MAX_VALUE, // 标准冲刺时间
      true))
      .setNeutrals(true)
      .setDefaultEnableNeededPlayerCount(12)
      .setDefaultEnableChance(100)
      .setCanUseInstinctAndNightVision(false)
      .setCanPickUpRevolver(false)
      .addTwoWayOpposingRole(MountainRoles.NITORI)
      .setServerGameTickEvent((player, cca) -> {
        if (player.level().getGameTime() % (20 * 60) == 0) {
          SREPlayerShopComponent.KEY.get(player).addToBalance(50);
        }
      });

  public static void init() {
  }
}
