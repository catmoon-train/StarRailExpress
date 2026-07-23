package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.special.dirt.DirtRole;
import org.agmas.noellesroles.game.roles.special.super_loose_end.SuperLooseEnd;

import java.awt.*;

public class SpecialGameModeRoles {

  /**
   * 躲猫猫寻找者
   */
  public static final SRERole SEEKER = registerRole(
      new SeekerRole(SRE.wifiId("hide_and_seek_seeker"), TMMRoles.KILLER.color(), false, true,
          SRERole.MoodType.NONE,
          -1, true))
      .setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(0).setOtherModeRole(true);

  /**
   * 自选职业
   */
  public static final SRERole CUSTOM_PENDING = registerRole(
      new NormalRole(SRE.wifiId("custom_pending"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true),
      "inner.other_gamemode")
      .setCanPickUpRevolver(false).setNeutrals(true).setNeutralForKiller(false)
      .setCanBeRandomedByOtherRoles(false)
      .setDefaultMax(0).setOtherModeRole(true);

  /**
   * 职业：超级亡命徒
   * <p>
   * - 击杀获得增益
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
      .setComponentKey(ModComponents.SUPER_LOOSE_END)
      .setCanSeeCoin(true)
      .setCanUseInstinctAndNightVision(true)
      .setCanAutoAddMoney(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true)
      .setToggledOnInstinctType(InstinctType.OBSERVER_ROLE_COLOR);

  /**
   * 职业：土块
   * <p>
   * - 轮盘赌模式特殊职业
   * </p>
   */
  public static SRERole DIRT = TMMRoles.registerRole(new DirtRole(
      SRE.xiaoheihandId("dirt_id"),
      new Color(180, 0, 255).getRGB(),
      false,
      false,
      SRERole.MoodType.FAKE,
      Integer.MAX_VALUE,
      true), "inner.other_gamemode")
      .setCanSeeCoin(true)
      .setCanSeeTime(true)
      .setCanUseInstinctAndNightVision(true)
      .setDefaultMax(0)
      .setCanBeRandomedByOtherRoles(false)
      .setNeutrals(true).setOtherModeRole(true);

  public static SRERole registerRole(SRERole role) {
    return TMMRoles.registerRole(role);
  }

  public static SRERole registerRole(SRERole role, String... flags) {
    return TMMRoles.registerRole(role, flags);
  }

  public static void init() {

  }
}
