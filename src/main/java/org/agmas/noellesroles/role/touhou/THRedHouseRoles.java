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

import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.game.roles.innocence.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.roles.*;
import org.agmas.noellesroles.role_data.neutral.RemiliaBloodServantRoleData;
import org.agmas.noellesroles.role_data.vigilante.HoanMeirinRoleData;

import java.awt.*;
import java.util.List;

public class THRedHouseRoles {
  public static final String NAMESPACE = "th_redhouse";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  public static final ResourceLocation KOAKUMA_ID = id("koakuma");
  public static final ResourceLocation DAIYOUSEI_ID = id("daiyousei");
  public static final ResourceLocation FURANDORU_ID = id("furandoru");
  public static final ResourceLocation REMILIA_ID = id("remilia");
  public static final ResourceLocation BAKA_ID = id("baka");
  public static final ResourceLocation PACHURI_ID = id("pachuri");
  public static final ResourceLocation MAID_SAKUYA_ID = id("maid_sakuya");
  public static final ResourceLocation HOAN_MEIRIN_ID = id("hoan_meirin");
  // 小恶魔
  public static SRERole KOAKUMA = TMMRoles.registerRole(
      new TouhouRole(KOAKUMA_ID, new Color(175, 94, 83).getRGB(),
          false, false, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true),
      "th_redhouse").setNeutralForKiller(true).addTwoWayOpposingRole(ModRoles.PRANKSTER)
      .setCanUseInstinct(true);
  // 大妖精
  public static SRERole DAIYOUSEI = TMMRoles.registerRole(
      new TouhouRole(DAIYOUSEI_ID, new Color(171, 216, 167).getRGB(),
          true, false, SRERole.MoodType.FAKE,
          TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false) {
        @Override
        public InteractionResult onDropItem(Player player, ItemStack item) {
          if (item.is(ModItems.SHILIJIA))
            return InteractionResult.SUCCESS;
          if (item.is(ModItems.CALMING_TEA))
            return InteractionResult.SUCCESS;
          if (item.is(ModItems.WREATH))
            return InteractionResult.SUCCESS;
          return InteractionResult.PASS;

        }
      }, "th_redhouse").setCanAcrossFog(true).setMoodColor((t) -> new Color(t.getColor()));
  // 杀手：蕾米莉亚
  public static SRERole REMILIA = TMMRoles.registerRole(
      new THRemiliaRole(REMILIA_ID, new Color(113, 98, 121).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true),
      "th_redhouse")
      .setCanSeeCoin(true)
      .setCanSeeBodyDeathReason(true)
      .setCanSeeBodyRoleInfo(true)
      .setCanSeeBodyKiller(true)
      .setDefaultEnableChance(3000);
  // 蕾米莉亚的仆从
  // remilia_blood_servant
  public static ResourceLocation REMILIA_BLOOD_SERVANT_ID = id("remilia_blood_servant");
  public static SRERole REMILIA_BLOOD_SERVANT = TMMRoles.registerRole(
      new THRemiliaServantRole(REMILIA_BLOOD_SERVANT_ID, new Color(113, 98, 121).getRGB(),
          false, false, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true))
      .setCanBeRandomedByOtherRoles(false)
      .setNeutralForKiller(true)
      .setDefaultMax(0)
      .setCanSetSpawnInfoInConfig(false)
      .setCanUseInstinctAndNightVision(true)
      .setCanSeeTeammateKillerRole(false)
      .setInstinctType(InstinctType.NONE, InstinctType.custom(TMMRoles.CIVILIAN.color()))
      .setCanIgnoreBlackout(true)
      .setDarknessImmune(true)
      .setRoleData(RemiliaBloodServantRoleData::new);
  // 独立中立：芙兰朵露
  public static SRERole FURANDORU = TMMRoles.registerRole(
      new TouhouRole(FURANDORU_ID, new Color(177, 153, 130).getRGB(),
          false, false, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true) {
        @Override
        public void serverTick(ServerPlayer player) {
          if (player.isSpectator())
            return;
          // 复用cca
          GhostPlayerComponent.KEY.get(player).checkFuranLastStand(SREGameWorldComponent.KEY.get(player.level()));
        }
      }, "th_redhouse").setHiddenForRoleRotation(true)
      .setCanSeeCoin(true).setNeutrals(true).setCanUseInstinctAndNightVision(true).setCanIgnoreBlackout(true);
  // 好人：MAID_SAKUYA 十六夜咲夜
  public static SRERole MAID_SAKUYA = TMMRoles.registerRole(new TouhouRole(
      MAID_SAKUYA_ID, // 角色 ID
      new Color(164, 173, 193).getRGB(), // 蓝灰色
      true, // isInnocent = 非乘客阵营（杀手）
      false, // canUseKiller = 杀手能力
      SRERole.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime() * 2, // 2 倍冲刺时间
      false // 不隐藏计分板
  ), "th_redhouse").setCanSeeCoin(true).setCanSeeTime(true).setDefaultMax(1).setDefaultEnableChance(2000);
  // 好人：大妖精baka
  public static SRERole BAKA = TMMRoles.registerRole(
      new TouhouRole(BAKA_ID, new Color(185, 240, 243).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false),
      "th_redhouse")
      .setCanSeeCoin(true);
  // 好人：红美铃
  public static SRERole HOAN_MEIRIN = TMMRoles.registerRole(
      new TouhouRole(HOAN_MEIRIN_ID, new Color(243, 140, 132).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime() * 3, false),
      "th_redhouse")
      .setCanAutoAddMoney(true)
      .setVigilanteTeam(true).setSpecialVigilante(true).setCanSeeCoin(true)
      .setRoleData(HoanMeirinRoleData::new)
      .setSpecialMapRole(SRERole.SpecialMapRoleMap.CAN_JUMP);
  // 好人：帕秋莉 Patchouli Knowledge
  public static SRERole PACHURI = TMMRoles.registerRole(
      new TouhouRole(PACHURI_ID, new Color(184, 144, 182).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public void serverTick(ServerPlayer player) {
          if (player.isSpectator())
            return;
          if (player.hasEffect(ModEffects.SKILL_BANED))
            return;
          if (player.level().getGameTime() % 30 == 0) {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            List<ServerPlayer> target_furans = player.serverLevel().getPlayers((p) -> {
              return GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p) && p.distanceToSqr(player) <= 25
                  && gameWorldComponent.isRole(p, THRedHouseRoles.FURANDORU);
            });
            for (ServerPlayer p : target_furans) {
              p.addEffect(new MobEffectInstance(
                  MobEffects.MOVEMENT_SLOWDOWN,
                  40, // 持续时间 60s（tick）
                  2, // 等级（0 = 速度 I）
                  true, // ambient（环境效果，如信标）
                  false, // showParticles（显示粒子）
                  true // showIcon（显示图标）
              ));
              p.addEffect(new MobEffectInstance(
                  MobEffects.INVISIBILITY,
                  40, // 持续时间 60s（tick）
                  1, // 等级（0 = 速度 I）
                  true, // ambient（环境效果，如信标）
                  false, // showParticles（显示粒子）
                  true // showIcon（显示图标）
              ));
              p.addEffect(new MobEffectInstance(
                  ModEffects.USED_BANED,
                  40, // 持续时间 60s（tick）
                  1, // 等级（0 = 速度 I）
                  true, // ambient（环境效果，如信标）
                  false, // showParticles（显示粒子）
                  true // showIcon（显示图标）
              ));
            }
          }
        }
      }, "th_redhouse")
      .setHiddenForRoleRotation(true).setCanSeeCoin(true);

  public static void init() {
  }
}
