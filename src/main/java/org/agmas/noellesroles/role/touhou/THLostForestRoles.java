package org.agmas.noellesroles.role.touhou;

import org.agmas.noellesroles.game.roles.innocence.magician.MagicianPlayerComponent;
import org.agmas.noellesroles.role.touhou.roles.*;

import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.resources.ResourceLocation;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

public class THLostForestRoles {
  public static final String NAMESPACE = "th_lost_forest";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  // 蓬莱山辉夜
  public static final ResourceLocation KAGUYA_ID = id("houraisan_kaguya");
  public static final SRERole KAGUYA = TMMRoles
      .registerRole(new THKaguyaRole(KAGUYA_ID, new Color(111, 105, 101).getRGB(),
          false, false, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true), "lost_forest")
      .setCanSetSpawnInfoInConfig(false)
      .setDefaultMax(0)
      .setDefaultEnableChance(0)
      .setComponentKey(MagicianPlayerComponent.KEY)
      .addRelatedModifier(SEModifiers.LOVERS)
      .setNeutrals(true)
      .setNeutralForKiller(true)
      .setToggledOnInstinctType(InstinctType.KILLER_INSTINCT)
      .setCanBeRandomedByOtherRoles(false)
      .setCanUseInstinctAndNightVision(true)
      .setHiddenForRoleRotation(true);

  // 藤原妹红
  public static final ResourceLocation MOKOU_ID = id("huziwara_no_mokou");
  public static final SRERole MOKOU = TMMRoles
      .registerRole(new THMokouRole(MOKOU_ID, new Color(159, 148, 162).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), true), "lost_forest")
      .setCanSetSpawnInfoInConfig(true)
      .setDefaultMax(1)
      .setDefaultEnableNeededPlayerCount(18)
      .setDefaultEnableChance(1000)
      .addOccupationRole(KAGUYA)
      .setNeutrals(true)
      .addRelatedModifier(SEModifiers.LOVERS)
      .setCanBeRandomedByOtherRoles(false)
      .setHiddenForRoleRotation(true);

  public static void init() {
  }
}
