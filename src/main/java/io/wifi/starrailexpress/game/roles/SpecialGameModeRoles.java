package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;

import org.agmas.noellesroles.game.roles.special.super_loose_end.SuperLooseEnd;
import org.agmas.noellesroles.game.roles.special.dirt.Dirt;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import java.awt.Color;

public class SpecialGameModeRoles {

  /**
   * 躲猫猫寻找者
   */
  public static final SRERole SEEKER = registerRole(
      new SeekerRole(SRE.wifiId("hide_and_seek_seeker"), TMMRoles.KILLER.color(), false, true, SRERole.MoodType.NONE, -1, true))
      .setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false).setMax(0).setOtherModeRole(true);
      
  /**
   * 自选职业
   */
  public static final SRERole CUSTOM_PENDING = registerRole(
      new NormalRole(SRE.wifiId("custom_pending"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true))
      .setCanPickUpRevolver(false).setNeutrals(true).setNeutralForKiller(false).setCanBeRandomedByOtherRoles(false).setMax(0).setOtherModeRole(true);

  /**
   * 职业：超级亡命徒
   * <p>
   *     - 击杀获得增益
   * </p>
   */
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
      .setCanAutoAddMoney(true).setMax(0).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

  /**
   * 职业：土块
   * <p>
   *  - 轮盘赌模式特殊职业
   * </p>
   */
  public static SRERole DIRT = TMMRoles.registerRole(new Dirt(
      SRE.xiaoheihandId("dirt_id"),
      new Color(180, 0, 255).getRGB(),
      false,
      false,
      SRERole.MoodType.FAKE,
      Integer.MAX_VALUE,
      true))
      .setCanSeeCoin(true)
      .setCanSeeTime(true)
      .setCanUseInstinct(true)
      .setMax(0)
      .setCanBeRandomedByOtherRoles(false)
      .setNeutrals(true).setOtherModeRole(true);

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
  public static void init(){
    
  }
}
