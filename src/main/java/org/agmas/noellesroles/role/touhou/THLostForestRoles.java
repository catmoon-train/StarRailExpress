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
import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.util.Color;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.utils.RoleUtils;
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
      .addRelatedModifier(SEModifiers.LOVERS)
      .setNeutrals(true)
      .setNeutralForKiller(true)
      .setToggledOnInstinctType(InstinctType.KILLER_INSTINCT)
      .setCanBeRandomedByOtherRoles(false)
      .setCanUseInstinctAndNightVision(true)
      .setHiddenForRoleRotation(true)
      .setRoleData(MagicianRoleData::new);

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
      .setCanUseInstinct(true)
      .addRelatedModifier(SEModifiers.LOVERS)
      .setInstinctType(InstinctType.DEFAULT, InstinctType.KILLER_INSTINCT)
      .setCanBeRandomedByOtherRoles(false)
      .setHiddenForRoleRotation(true);

  public static void init() {
  }

  /**
   * 不死伴侣（蓬莱山辉夜 / 藤原妹红）真正死亡时记录回放事件。
   * 若伴侣也真正死亡，则记录 "A 与 B 真正死亡"；否则记录 "A 真正死亡"。
   */
  public static void recordImmortalPairRealDeath(ServerPlayer victim, ResourceLocation partnerRoleId) {
    ServerPlayer partner = null;
    for (var p : victim.serverLevel().players()) {
      if (p instanceof ServerPlayer sp) {
        SRERole role = RoleUtils.getPlayerRole(sp);
        if (role != null && role.identifier().equals(partnerRoleId)) {
          partner = sp;
          break;
        }
      }
    }
    boolean partnerTrulyDead = partner != null
        && partner.isSpectator()
        && !ModComponents.DEFIBRILLATOR.get(partner).isReviving();
    if (partnerTrulyDead) {
      Component first;
      Component second;
      if (partnerRoleId.equals(MOKOU_ID)) {
        first = GameReplayUtils.getReplayPlayerDisplayText(victim, true);
        second = GameReplayUtils.getReplayPlayerDisplayText(partner, true);
      } else {
        first = GameReplayUtils.getReplayPlayerDisplayText(partner, true);
        second = GameReplayUtils.getReplayPlayerDisplayText(victim, true);
      }
      SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable(
          "replay.event.touhou.immortal_pair.real_dead", first, second));
    } else {
      SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable(
          "replay.event.touhou.immortal_pair.real_dead_single",
          GameReplayUtils.getReplayPlayerDisplayText(victim, true)));
    }
  }
    static {
        KAGUYA.setAddedVersion("4.3");
        MOKOU.setAddedVersion("4.3");
    }
}
