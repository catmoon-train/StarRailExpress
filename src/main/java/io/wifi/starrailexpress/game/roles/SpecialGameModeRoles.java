package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.game.modes.funny.SREChameleonGameMode;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.special.dirt.DirtRole;
import org.agmas.noellesroles.game.roles.special.super_loose_end.SuperLooseEnd;

import java.awt.*;

public class SpecialGameModeRoles {

  /**
   * 躲猫猫寻找者
   */
  public static final SRERole SEEKER = registerRole(
      new SeekerRole(SRE.wifiId("hide_and_seek_seeker"), TMMRoles.KILLER.color(), false, true, SRERole.MoodType.NONE,
          -1, true))
      .setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(0).setOtherModeRole(true);

  /**
   * 自选职业
   */
  public static final SRERole CUSTOM_PENDING = registerRole(
      new NormalRole(SRE.wifiId("custom_pending"), 0x5CFF4A, false, false, SRERole.MoodType.NONE, -1, true), "inner.other_gamemode")
      .setCanPickUpRevolver(false).setNeutrals(true).setNeutralForKiller(false).setCanBeRandomedByOtherRoles(false)
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
      .setCanAutoAddMoney(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false).setOtherModeRole(true);

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
      true),"inner.other_gamemode")
      .setCanSeeCoin(true)
      .setCanSeeTime(true)
      .setCanUseInstinctAndNightVision(true)
      .setDefaultMax(0)
      .setCanBeRandomedByOtherRoles(false)
      .setNeutrals(true).setOtherModeRole(true);

  /**
   * 变色龙：变色龙模式的平民阵营
   * <p>
   * - 穿全套变色龙衣服，可涂装、方块伪装
   * </p>
   */
  public static final SRERole CHAMELEON = registerRole(
      new ChameleonRole(SRE.canyueId("chameleon"), 0x4CAF50, true, false, SRERole.MoodType.FAKE, -1, true))
      .setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(0).setOtherModeRole(true);
static {
  AllowOtherCameraType.EVENT.register((original, localplayer) -> {
    if (SREClient.gameComponent!=null&&SREClient.gameComponent.gameMode== SREGameModes.CHAMELEON_MODE){
      return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
    }
    return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
  });
}
  /**
   * 猎人：变色龙模式的击杀者阵营
   * <p>
   * - 同样穿全套变色龙衣服，额外携带霰弹枪
   * </p>
   */
  public static final SRERole CHAMELEON_HUNTER = registerRole(
      new ChameleonHunterRole(SRE.canyueId("chameleon_hunter"), TMMRoles.KILLER.color(), false, true,
          SRERole.MoodType.NONE, -1, true))
      .setCanPickUpRevolver(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(0).setOtherModeRole(true).setCanUseInstinct(false);

  public static SRERole registerRole(SRERole role) {
    return TMMRoles.registerRole(role);
  }

  public static SRERole registerRole(SRERole role, String... flags) {
    return TMMRoles.registerRole(role, flags);
  }

  public static void init() {

  }
}
