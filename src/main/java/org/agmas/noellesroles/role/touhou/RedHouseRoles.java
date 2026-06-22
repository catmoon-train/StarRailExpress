package org.agmas.noellesroles.role.touhou;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocent.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.THEventHandler;

import java.awt.*;
import java.util.List;

public class RedHouseRoles {
  public static final String NAMESPACE = "th_redhouse";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  public static final ResourceLocation FURANDORU_ID = id("furandoru");
  public static final ResourceLocation REMILIA_ID = id("remilia");
  public static final ResourceLocation BAKA_ID = id("baka");
  public static final ResourceLocation PACHURI_ID = id("pachuri");
  public static final ResourceLocation MAID_SAKUYA_ID = id("maid_sakuya");
  public static final ResourceLocation HOAN_MEIRIN_ID = id("hoan_meirin");

  // 杀手：蕾米莉亚
  public static SRERole REMILIA = TMMRoles.registerRole(
      new TouhouRole(REMILIA_ID, new Color(113, 98, 121).getRGB(),
          false, true, SRERole.MoodType.FAKE,
          Integer.MAX_VALUE, true) {
        @Override
        public InteractionResult rightClickEntity(Player player, Entity target) {
          if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player))
            return InteractionResult.PASS;
          if (target instanceof PlayerBodyEntity be) {
            PlayerBodyEntityComponent bdrc = PlayerBodyEntityComponent.KEY.get(be);
            bdrc.playerRole = THEventHandler.getRandomRole().identifier();
            bdrc.sync();
            be.setDeathReason(THEventHandler.getRandomDeathReason());
          }
          return InteractionResult.PASS;
        }

        @Override
        public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
          return SRE.id("textures/entity/custom_psycho/remilia.png");
        }
      })
      .setCanSeeCoin(true).setCanSeeBodyDeathReason(true).setCanSeeBodyRoleInfo(true).setCanSeeBodyKiller(true);
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
      })
      .setCanSeeCoin(true).setNeutrals(true).setCanUseInstinct(true).setCanIgnoreBlackout(true);
  // 好人：MAID_SAKUYA 十六夜咲夜
  public static SRERole MAID_SAKUYA = TMMRoles.registerRole(new TouhouRole(
      MAID_SAKUYA_ID, // 角色 ID
      new Color(164, 173, 193).getRGB(), // 蓝灰色
      true, // isInnocent = 非乘客阵营（杀手）
      false, // canUseKiller = 杀手能力
      SRERole.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime() * 2, // 2 倍冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setCanSeeTime(true).setDefaultMax(0);
  // 好人：大妖精baka
  public static SRERole BAKA = TMMRoles.registerRole(
      new TouhouRole(BAKA_ID, new Color(185, 240, 243).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true);
  // 好人：红美铃
  public static SRERole HOAN_MEIRIN = TMMRoles.registerRole(
      new TouhouRole(HOAN_MEIRIN_ID, new Color(243, 140, 132).getRGB(),
          true, false, SRERole.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setVigilanteTeam(true).setCanSeeCoin(true);
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
                  && gameWorldComponent.isRole(p, RedHouseRoles.FURANDORU);
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
      })
      .setCanSeeCoin(true);

  public static void init() {
    MountainRoles.init();
  }
}
