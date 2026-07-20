package org.agmas.noellesroles.handler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.ShouldGiveKillerBalance;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.agmas.noellesroles.content.item.BowenBadgeItem;
import org.agmas.noellesroles.content.item.RopeItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.MountainRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.roles.THReimuRole;
import org.agmas.noellesroles.utils.RoleUtils;

public class TouhouHandlers {
  public static void register() {
    registerSkills();
    registerEvents();
  }

  public static void registerEvents() {
    // 魔理沙和灵梦不受到摔伤影响
    OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathreason, forceKill) -> {
      if (deathreason.equals(GameConstants.DeathReasons.FALL_DAMAGE)) {
        if (RoleUtils.isPlayerTheJob(victim, THMiscRoles.HAKUREI_REIMU)) {
          return TrueFalseResult.PASS;
        }
        if (RoleUtils.isPlayerTheJob(victim, THMiscRoles.KIRISAME_MARISA)) {
          return TrueFalseResult.PASS;
        }
      }
      return TrueFalseResult.PASS;
    });
    // 四季
    OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReasosn, forceKill) -> {
      if (killer == null)
        return TrueFalseResult.PASS;
      if (!RoleUtils.isPlayerTheJob(killer, THMiscRoles.SHIKIEIKI))
        return TrueFalseResult.PASS;
      if (killer.getMainHandItem().is(TMMItems.DERRINGER) || killer.getMainHandItem().is(TMMItems.REVOLVER)
          || killer.getMainHandItem().is(ModItems.BANDIT_REVOLVER)) {
        {
          var mainhandItem = victim.getMainHandItem();
          if (victim.distanceToSqr(killer) <= 6 * 6) {
            if (mainhandItem.getItem() instanceof TrainWeapon
                || mainhandItem.is(TMMItemTags.GUNS)) {
              return TrueFalseResult.TRUE;
            }
          }
        }

        var cca = SREAbilityPlayerComponent.KEY.get(killer);
        if (cca.duration <= 0 || cca.targetUUID == null) {
          return TrueFalseResult.FALSE;
        }
        if (victim.getUUID().equals(cca.targetUUID)) {
          return TrueFalseResult.TRUE;
        }
        return TrueFalseResult.FALSE;
      }
      return TrueFalseResult.PASS;
    });
    ShouldGiveKillerBalance.EVENT.register((victim, killer, deathReason) -> {
      if (RoleUtils.isPlayerTheJob(killer, THMiscRoles.KOMACHI))
        return TrueFalseResult.FALSE;
      return TrueFalseResult.PASS;
    });
    // 天子&小町
    OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
      var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
      // 小町
      // 你杀死的玩家将给予其所花费金额的100%与其当前金币的50%给你。一次获取上限为500。
      if (killer != null && gameWorldComponent.isRole(killer, THMiscRoles.KOMACHI)) {
        var vshop = SREPlayerShopComponent.KEY.get(player);
        int buyCosts = vshop.getTotalCostAndClear();
        int totaladd = buyCosts + vshop.balance / 2;
        if (totaladd > 500)
          totaladd = 500;
        SREPlayerShopComponent.KEY.get(killer).addToBalance(totaladd);
      }
      // 天子
      for (var p : player.level().players()) {
        if (p.getUUID() != player.getUUID() && (killer == null || p.getUUID() != killer.getUUID())) {
          if (gameWorldComponent.isRole(p, THMiscRoles.KOMACHI)) {
            // 每个玩家死后将给予其所花费金额的10%给你。一次获取上限为300。
            var vshop = SREPlayerShopComponent.KEY.get(player);
            int buyCosts = vshop.getTotalCostAndClear();

            int totaladd = (int) ((float) buyCosts * 0.1);
            if (totaladd > 300)
              totaladd = 300;
            SREPlayerShopComponent.KEY.get(p).addToBalance(totaladd);
          } else if (gameWorldComponent.isRole(p, THMiscRoles.TENSHI)) {
            if (p.getCooldowns().isOnCooldown(Items.BARRIER)) {
              continue;
            } else {
              p.getCooldowns().addCooldown(Items.BARRIER, 30 * 20);
              if (p instanceof ServerPlayer sp) {
                SRENetworkMessageUtils.sendCODSubtitleToPlayerTop(sp,
                    Component.translatable("message.tenshi.killer_killed.title")
                        .withStyle(ChatFormatting.RED),
                    Component.translatable("message.tenshi.killer_killed.subtitle", 30), 100);
              }
            }
          }
        }
      }

    });
  }

  public static void registerSkills() {
    RoleSkill.register(THMiscRoles.KIRISAME_MARISA,
        RoleSkill.skill(SRE.id("marisa_magic"), "skill.noellesroles.marisa_magic", context -> {
          Player player = context.player();
          for (var p : player.level().players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p)) {
              if (p.getUUID() != player.getUUID() && p.distanceToSqr(player) <= 6 * 6) {
                p.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, 1, true, false, true));
                p.setRemainingFireTicks(5 * 20);
              }
            }
          }
          return true;
        }).announceToSelf(true).cooldownSeconds(60).showOnHud(true).shifted(false).build());
    RoleSkill.register(THMiscRoles.HAKUREI_REIMU,
        RoleSkill.skill(SRE.id("reimu_flying"), "skill.noellesroles.reimu", context -> {
          final var player = context.player();
          final var level = player.serverLevel();
          var abilityCCA = SREAbilityPlayerComponent.KEY.get(player);
          if (!AreasWorldComponent.KEY.get(level).areasSettings.canJump) {
            player.displayClientMessage(
                Component.translatable("skill.noellesroles.reimu.rush").withStyle(ChatFormatting.RED), true);
            player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, 20, 0, true, false, false));
            BowenBadgeItem.fowardAndKnockbackPlayerNearby(player.level(), player, 3f);
            abilityCCA.setCooldown(THReimuRole.FLY_COOLDOWN);
            return false;
          }
          if (abilityCCA.duration > 0) {
            abilityCCA.duration = 0;
            THReimuRole.stopFlying(player);
            return true;
          }

          abilityCCA.duration = (THReimuRole.MAX_DURATION);
          abilityCCA.setCooldown(THReimuRole.FLY_COOLDOWN);
          THReimuRole.startFlying(player);
          return true;
        }).noAnnouncement().showOnHud(false).cooldownTicks(20 * 120).build());
    RoleSkill.register(RedHouseRoles.KOAKUMA,
        RoleSkill.skill(SRE.id("koakuma"), "skill.noellesroles.koakuma", context -> {
          var targetId = context.target();
          if (targetId == null)
            return false;
          final var player = context.player();
          final var level = player.serverLevel();
          final var target = level.getPlayerByUUID(targetId);
          if (target == null)
            return false;
          if (target.isSpectator() || target.isCreative())
            return false;
          var abilityCCA = SREAbilityPlayerComponent.KEY.get(player);
          abilityCCA.targetUUID = target.getUUID();
          // 不需要同步因为客户端不显示东西。
          return true;
        }).announceToSelf().showOnHud(true).cooldownTicks(20 * 120).build());
    RoleSkill.register(RedHouseRoles.DAIYOUSEI,
        RoleSkill.skill(SRE.id("daiyouse"), "skill.noellesroles.daiyouse", context -> {
          var targetId = context.target();
          if (targetId == null)
            return false;
          final var player = context.player();
          final var level = player.serverLevel();
          final var target = level.getPlayerByUUID(targetId);
          if (target == null)
            return false;
          if (target.isSpectator() || target.isCreative())
            return false;
          var targetRole = RoleUtils.getPlayerRole(target);
          if (targetRole == null)
            return false;
          {
            var taskcca = SREPlayerTaskComponent.KEY.get(target);
            var moodcca = SREPlayerMoodComponent.KEY.get(target);
            var minigameComponent = SREPlayerMinigameTaskComponent.KEY.get(target);
            if (!taskcca.tasks.isEmpty()) {
              taskcca.tasks.clear();
              taskcca.parallelTaskTypes.clear();
              taskcca.parallelTaskGenerated = false;
              taskcca.nextTaskTimer = 20;
              taskcca.currentTaskAge = 0;
              taskcca.sync();
            } else if (minigameComponent.pendingMinigameTasks > 0) {
              minigameComponent.pendingMinigameTasks = 0;
              minigameComponent.targetMinigameId = null;
              minigameComponent.sync();
            }
            moodcca.addMood(0.1f);
          }
          return true;
        }).announceToSelf().showOnHud(true).cooldownTicks(20 * 60).build());
    RoleSkill.register(THMiscRoles.SHIKIEIKI,
        RoleSkill.skill(SRE.id("shikieiki"), "skill.noellesroles.shikieiki.instinct", context -> {
          final int GAP = 15 * 20;
          final int TIME = 60 * 20;
          final int COOLDOWN_TIME = 45 * 20;
          final var player = context.player();
          final var cca = SREAbilityPlayerComponent.KEY.get(player);
          if (cca.hasCooldown()) {
            return false;
          }
          if (context.target() == null)
            return false;
          final var target = player.level().getPlayerByUUID(context.target());
          if (target == null)
            return false;
          cca.cooldown = COOLDOWN_TIME;
          var killInfo = GameUtils.getPlayerLastKillInfo(target);
          if (killInfo != null && player.level().getGameTime() - killInfo.time() <= GAP) {
            player.displayClientMessage(
                Component.translatable("message.shikieiki.skill.success").withStyle(ChatFormatting.GREEN), true);
            cca.targetUUID = target.getUUID();
            cca.duration = TIME;
            cca.sync();
            return true;
          }
          player.displayClientMessage(
              Component.translatable("message.shikieiki.skill.failed").withStyle(ChatFormatting.RED), true);
          cca.sync();
          return true;
        }).noCastCCA(true).announceToSelf(false).build());
    RoleSkill.register(THMiscRoles.KOMACHI_ID,
        RoleSkill.skill(SRE.id("komachi_rush"), "skill.noellesroles.komachi_rush", context -> {
          Player player = context.player();
          player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, 20, 0, true, false, false));
          BowenBadgeItem.fowardAndKnockbackPlayerNearby(player.level(), player, 2.5f);
          return true;
        }).announceToSelf(true).cooldownSeconds(60).showOnHud(true).shifted(false).build(),
        RoleSkill.skill(SRE.id("komachi_pull"), "skill.noellesroles.komachi_pull", context -> {
          Player player = context.player();
          var target = RopeItem.findTargetedPlayerInView(player.level(), player, 20);
          if (target == null) {
            return false;
          }
          // 身前2格
          RopeItem.pullPlayer(player, target, 1);
          return true;
        }).cooldownSeconds(90).announceToSelf(true).showOnHud(true).shifted(true).build());
    RoleSkill.register(MountainRoles.NITORI, RoleSkill.skill(SRE.id("nitori_exchange"),
        "skill.noellesroles.nitori_exchange",
        context -> {
          if (context.target() == null) {
            return false;
          }

          var target = context.player().level().getPlayerByUUID(context.target());
          if (target == null) {
            context.player().displayClientMessage(Component.translatable(
                "message.noellesroles.nitori_exchange.failed.no_target"), true);
            return false;
          }
          ItemStack it = context.player().getMainHandItem();
          if (it == null || it.isEmpty()) {
            context.player().displayClientMessage(Component.translatable(
                "message.noellesroles.nitori_exchange.failed.noitem"), true);
            return false;
          }
          var targetShop = SREPlayerShopComponent.KEY.get(target);
          var selfShop = SREPlayerShopComponent.KEY.get(context.player());
          if (targetShop.balance < 200) {

            context.player().displayClientMessage(Component.translatable(
                "message.noellesroles.nitori_exchange.failed.nomoney"), true);
            return false;
          }

          if (RoleUtils.insertStackInFreeSlot(target, it.copy())) {
            targetShop.addToBalance(-200);
            selfShop.addToBalance(200);
            context.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            context.player().displayClientMessage(Component.translatable(
                "message.noellesroles.nitori_exchange.success", it.getDisplayName()), true);
            return true;
          }
          return false;
        }).announceToSelf(false).cooldownSeconds(30).build());
  }
}
