package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;

public class MonokumaRole extends CustomWinnerRole {
  public MonokumaRole() {
    super(ModRoles.MONOKUMA_ID, new Color(128, 128, 128).getRGB(), false, false, MoodType.FAKE, Integer.MAX_VALUE,
        true);
  }

  @Override
  public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
    long timeSlot = System.currentTimeMillis() / 3000;
    if (timeSlot % 2 == 0) {
      return Noellesroles.id("textures/entity/custom_psycho/black_psycho.png");
    } else {
      return Noellesroles.id("textures/entity/custom_psycho/white_psycho.png");
    }
  }

  @Override
  public boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent comp) {
    // 黑白已经在 onHitTriggered 中给了阴阳剑，不需要再给球棒
    return true;
  }

  @Override
  public Item getPsychoItem() {
    return org.agmas.noellesroles.init.ModItems.YINYANG_SWORD;
  }

  @Override
  public java.util.function.Predicate<Item> cantPickupItem(Player player) {
    // 黑白熊形态无法捡起任何物品
    var comp = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY.maybeGet(player)
        .orElse(null);
    if (comp != null && comp.phase == 3) {
      return item -> true; // 禁止捡起所有物品
    }
    return super.cantPickupItem(player);
  }

  @Override

  /**
   * 玩家是否获胜。在获胜统计时被调用。
   * 
   * @param player
   * @return
   */
  public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
    // 黑白熊：依附最近玩家的阵营获胜
    ServerLevel serverLevel = player.serverLevel();
    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverLevel);
    if (!winStatus.equals(WinStatus.NONE)) {
      MonokumaPlayerComponent comp = MonokumaPlayerComponent.KEY.get(player);
      if (comp.phase != 3) {
        // 未变身 → 失败
        return false;
      }

      // 寻找6格内最近的存活玩家
      ServerPlayer nearestPlayer = null;
      double nearestDist = Double.MAX_VALUE;

      for (ServerPlayer p : serverLevel.players()) {
        if (p.getUUID().equals(player.getUUID()))
          continue;
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))
          continue;
        double q = p.distanceToSqr(player);
        if (q < nearestDist) {
          nearestDist = q;
          nearestPlayer = p;
        }
      }

      if (nearestPlayer == null) {
        // 6格内无存活玩家 → 失败
        return false;
      }

      SRERole nearestRole = gameComponent.getRole(nearestPlayer);
      if (nearestRole == null)
        return false;

      if (nearestRole.isInnocent() && winStatus.isInnocentWin()) {
        return true;
      } else if (nearestRole.isKiller() && winStatus.isKillerWin()) {
        // 最近的是杀手 → 黑白熊与坏人一起获胜
        return true;
      }
      return false;
      // 最近的是中立 → 黑白熊失败
      // monokumaResult != null 表示黑白熊成功依附，不修改整体胜负
      // monokumaResult == null 表示黑白熊失败，同样不修改
    }
    return original;
  }

  // @Override
  // public java.util.List<net.minecraft.world.item.ItemStack> getDefaultItems() {
  // return java.util.List.of(
  // new net.minecraft.world.item.ItemStack(TMMItems.REVOLVER)
  // );
  // }

  // @Override
  // public ResourceLocation getDisplayRole(Player player) {
  // // 对所有人（包括自己）显示为义警
  // var comp =
  // org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY.maybeGet(player).orElse(null);
  // if (comp != null && comp.phase <= 2) {
  // return TMMRoles.VIGILANTE.identifier();
  // }
  // return super.getDisplayRole(player);
  // }
}