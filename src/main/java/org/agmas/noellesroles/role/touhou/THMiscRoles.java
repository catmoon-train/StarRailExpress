package org.agmas.noellesroles.role.touhou;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.touhou.roles.*;

public class THMiscRoles {
  public static final String NAMESPACE = "th_misc";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  // Kirisame Marisa
  public static final ResourceLocation KIRISAME_MARISA_ID = id("kirisame_marisa");
  public static SRERole KIRISAME_MARISA = TMMRoles
      .registerRole(new THReimuRole(KIRISAME_MARISA_ID, new java.awt.Color(172, 154, 104).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true))
      .setCanSetSpawnInfoInConfig(false).setDefaultMax(0);
  public static final ResourceLocation HAKUREI_REIMU_ID = id("hakurei_reimu");
  public static SRERole HAKUREI_REIMU = TMMRoles
      .registerRole(new THReimuRole(HAKUREI_REIMU_ID, new java.awt.Color(153, 82, 89).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true))
      .setCanPickUpRevolver(false)
      .setDefaultEnableNeededPlayerCount(18).setDefaultEnableChance(1000).addOccupationRole(KIRISAME_MARISA);
  // 四季映姬·夜摩仙那度 Shikieiki（有点像判官）
  // 四季映姬曾经是地藏，后来全国各地的地藏联名上书请求分担阎魔大人的工作，她也成为了阎魔。
  public static final ResourceLocation SHIKIEIKI_ID = id("shikieiki");
  public static SRERole SHIKIEIKI = TMMRoles
      .registerRole(new THShikieikiRole(SHIKIEIKI_ID, new java.awt.Color(87, 79, 117).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true), "th_hell")
      .setCanPickUpRevolver(false).setVigilanteTeam(true).setSpecialVigilante(true)
      .setDefaultEnableNeededPlayerCount(24).setDefaultEnableChance(3000);
  // 小野冢小町 Onozuka Komachi
  public static final ResourceLocation KOMACHI_ID = id("onozuka_komachi");
  public static SRERole KOMACHI = TMMRoles
      .registerRole(new THKomachiRole(KOMACHI_ID, new java.awt.Color(199, 144, 161).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true), "th_hell");
  // 天子Hinanawi Tenshi
  public static final ResourceLocation TENSHI_ID = id("hinanawi_tenshi");
  public static SRERole TENSHI = TMMRoles
      .registerRole(new THTenshiRole(TENSHI_ID, new java.awt.Color(89, 177, 250).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 2, false));
  public static final ResourceLocation RINNOSUKE_ID = id("morichika_rinnosuke");
  // 森近霖之助 Morichika Rinnosuke
  public static SRERole RINNOSUKE = TMMRoles.registerRole(new THRinnosukeRole(
      RINNOSUKE_ID, // 角色 ID
      new java.awt.Color(252, 250, 249).getRGB(),
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
