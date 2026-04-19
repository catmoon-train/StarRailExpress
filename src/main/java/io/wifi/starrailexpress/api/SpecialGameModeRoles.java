package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import org.agmas.noellesroles.game.roles.neutral.super_loose_end.SuperLooseEnd;
import org.agmas.noellesroles.roles.dirt.Dirt;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import java.awt.Color;

public class SpecialGameModeRoles {

  public static final SRERole CUSTOM_PENDING = registerRole(
      new NormalRole(SRE.wifiId("custom_pending"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true))
      .setCanPickUpRevolver(false).setNeutrals(true).setNeutralForKiller(false).setCanBeRandomedByOtherRoles(false).setMax(0);

  public static SRERole SUPER_LOOSE_END = TMMRoles.registerRole(new SuperLooseEnd(
      SRE.xiaoheihandId("super_loose_end"),
      new Color(0xFF77AA).getRGB(),
      false,
      false,
      SRERole.MoodType.NONE,
      -1,
      true))
      .setCanSeeCoin(true)
      .setCanUseInstinct(true)
      .setCanAutoAddMoney(true).setMax(0).setCanBeRandomedByOtherRoles(false);

  /**
   * 职业：土块
   * <p>
   * 轮盘赌模式特殊职业
   * </p>
   */
  public static SRERole DIRT = TMMRoles.registerRole(new Dirt(
      SRE.xiaoheihandId("dirt_id"),
      new Color(180, 0, 255).getRGB(),
      false,
      false,
      SRERole.MoodType.FAKE,
      Integer.MAX_VALUE,
      true)).setCanSeeCoin(true).setCanSeeTime(true).setMax(0).setCanBeRandomedByOtherRoles(false);

  public static SRERole registerRole(SRERole role) {
    TMMRoles.ROLES.put(role.identifier(), role);
    if (role.getComponentKey() != null) {
      TMMRoles.COMPONENT_KEYS.add(role.getComponentKey());
    }
    return role;
  }

  public static void addRoleComponents(ComponentKey<? extends RoleComponent> componentKeyToAdd) {
    TMMRoles.COMPONENT_KEYS.add(componentKeyToAdd);
  }
}
