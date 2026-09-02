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

package org.agmas.noellesroles.handler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.event.*;
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
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

import java.util.Random;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.content.item.BowenBadgeItem;
import org.agmas.noellesroles.content.item.RopeItem;
import org.agmas.noellesroles.handler.utils.THYukariPortalManager;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.*;
import org.agmas.noellesroles.role.touhou.roles.*;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.OpenScreenManager;
import org.agmas.noellesroles.utils.RoleUtils;

public class TouhouHandlers {
  public static final Random RANDOM = new Random();

  public static void register() {
    registerSkills();
    registerEvents();
    registerInitEvents();
  }

  public static void registerInitEvents() {
    THYukariPortalManager.registerEvents();
    ModdedRoleRemoved.EVENT.register((player, role) -> {
      if (!(player instanceof ServerPlayer sp)) {
        return;
      }
      if (RoleUtils.compareRole(role, THMiscRoles.IBUKI_SUIKA)) {
        THSuikaRole.restore(sp);
      } else if (RoleUtils.compareRole(role, THMiscRoles.HAKUREI_REIMU)) {
        THReimuRole.stopFlying(sp);
      }
    });
    OnGameTrueStarted.EVENT.register((serverLevel) -> {
      final var modifierCca = WorldModifierComponent.KEY.get(serverLevel);
      final var gameCca = SREGameWorldComponent.getInstance(serverLevel);
      for (final var player : serverLevel.players()) {
        final var role = RoleUtils.getPlayerRole(player);
        // 强制绑定不死组辉夜和妹红为恋人
        {
          if (RoleUtils.compareRole(role, THLostForestRoles.KAGUYA)) {
            if (modifierCca.isModifier(player, SEModifiers.LOVERS)
                && LoversComponent.KEY.get(player).getLoverAsPlayer() != null) {
              return;
            }
            Player mokou = null;
            for (final Player p : player.level().players()) {
              if (!modifierCca.isModifier(p.getUUID(), SEModifiers.LOVERS)
                  && gameCca.isRole(p.getUUID(), THLostForestRoles.MOKOU)) {
                mokou = p;
              }
            }
            if (mokou != null) {
              modifierCca.addModifier(player.getUUID(), SEModifiers.LOVERS, false);
              modifierCca.addModifier(mokou.getUUID(), SEModifiers.LOVERS, false);
              LoversComponent.KEY.get(player).setLoverAndSync(mokou.getUUID());
              LoversComponent.KEY.get(mokou).setLoverAndSync(player.getUUID());
              modifierCca.sync();
            } else {
              SRE.LOGGER.warn("Cannot find mokou for kaguya {}", player.getScoreboardName());
            }
          }
        }
      }
    });
  }

  public static void registerEvents() {
    // 大小姐仆从不能杀蕾米莉亚
    THIbarakiKasenRole.registerEvents();
    DoremyRoleData.registerEvents();
    AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathreason) -> {
      if (killer == null)
        return true;
      if (RoleUtils.isPlayerTheJob(killer, THRedHouseRoles.REMILIA_BLOOD_SERVANT)) {
        if (RoleUtils.isPlayerTheJob(victim, THRedHouseRoles.REMILIA))
          return false;
      }
      return true;
    });
    // 蕾米莉亚杀人转换职业
    OnPlayerDeathWithBody.EVENT.register((victim, killer, deathReason, body) -> {
      if (killer == null)
        return;
      if (RoleUtils.isPlayerTheJob(victim, ModRoles.DOOMED_SINNER)) {
        return;
      }
      if (RoleUtils.isPlayerTheJob(killer, THRedHouseRoles.REMILIA)) {
        final var cdcca = SREAbilityPlayerComponent.KEY.get(killer);
        if (cdcca.hasCooldown()) {
          return;
        }
        MCItemsUtils.clearItem(victim, (item) -> !item.is(TMMItems.LETTER) && !item.is(TMMItems.KEY));
        victim.displayClientMessage(
            Component.translatable("hud.noellesroles.remilia.victim", killer.getName()).withStyle(ChatFormatting.GOLD),
            true);
        if (victim instanceof ServerPlayer svictim) {
          SRENetworkMessageUtils.sendBroadcast(svictim, Component
              .translatable("hud.noellesroles.remilia.victim", killer.getName()).withStyle(ChatFormatting.GOLD));
        }
        killer.displayClientMessage(Component.translatable("hud.noellesroles.remilia.success", victim.getName(),
            RoleUtils.getPlayerRoleName(victim, true)).withStyle(ChatFormatting.GREEN), true);
        RoleUtils.changeRole(victim, THRedHouseRoles.REMILIA_BLOOD_SERVANT);

        DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(victim);
        component.triggerDeath(30 * 20, null, victim.position());

        cdcca.setCooldown(THRemiliaRole.COOLDOWN_TICKS);
      }
    });
    // 魔理沙和灵梦不受到摔伤影响
    OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathreason, forceKill) -> {
      if (deathreason.equals(GameConstants.DeathReasons.FALL_DAMAGE)) {
        if (RoleUtils.isPlayerTheJob(victim, THMiscRoles.HAKUREI_REIMU)) {
          return TrueFalseResult.FALSE;
        }
        if (RoleUtils.isPlayerTheJob(victim, THMagicForestRoles.KIRISAME_MARISA)) {
          return TrueFalseResult.FALSE;
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
          var offhandItem = victim.getOffhandItem();
          if (victim.distanceToSqr(killer) <= 7 * 7) {
            if (mainhandItem.getItem() instanceof TrainWeapon
                || mainhandItem.is(TMMItemTags.GUNS)
                || mainhandItem.is(TMMItemTags.BOWS)
                || offhandItem.getItem() instanceof TrainWeapon
                || offhandItem.is(TMMItemTags.GUNS)
                || offhandItem.is(TMMItemTags.BOWS)) {
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

    // 四季
    AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReasosn) -> {
      if (killer == null)
        return true;
      if (!RoleUtils.isPlayerTheJob(victim, THMiscRoles.SHIKIEIKI))
        return true;
      if (SREAbilityPlayerComponent.KEY.get(victim).targetUUID == null)
        return true;
      if (SREAbilityPlayerComponent.KEY.get(victim).targetUUID == killer.getUUID()
          && SREAbilityPlayerComponent.KEY.get(victim).hasDuration()) {
        return false;
      }
      return true;
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

  public static void handleMystiaResult(ServerPlayer player, final int score) {
    player.displayClientMessage(
        Component.translatable("skill.noellesroles.mystia.score", score).withStyle(ChatFormatting.AQUA),
        true);
    if (!RoleUtils.isPlayerTheJob(player, THMiscRoles.MYSTIA)) {
      return;
    }
    if (score >= 85) {
      SREPlayerMoodComponent.KEY.get(player).addMood(0.4f);
      {
        SREPlayerShopComponent.KEY.get(player).addToBalance(25);
        SREPlayerTaskComponent taskcca = SREPlayerTaskComponent.KEY.get(player);
        if (!taskcca.tasks.isEmpty()) {
          taskcca.tasks.clear();
          taskcca.parallelTaskTypes.clear();
          taskcca.parallelTaskGenerated = false;
          taskcca.nextTaskTimer = 20;
          taskcca.currentTaskAge = 0;
          taskcca.sync();
        }

        player.displayClientMessage(
            Component.translatable("skill.noellesroles.mystia.san", score).withStyle(ChatFormatting.AQUA),
            true);
      }
      if (score >= 95) {
        int choice = RANDOM.nextInt(1, 4);
        /*
         * ├─ 1. 获得一份烤八目海鳗，可丢出，食用可以获得夜视。
         * ├─ 2. 使自身半径5格内持续失明和失去透视，持续10s。
         * └─ 3. 获得速度2，持续30s。
         */
        if (choice == 1) {

          player.displayClientMessage(
              Component.translatable("skill.noellesroles.mystia.haiman", score).withStyle(ChatFormatting.AQUA),
              true);
          RoleUtils.insertOrDropItem(player, FunnyItems.COOKED_HAIMAN.getDefaultInstance());
        } else if (choice == 2) {

          player.displayClientMessage(
              Component.translatable("skill.noellesroles.mystia.self.blindness", score).withStyle(ChatFormatting.AQUA),
              true);
          for (ServerPlayer p : player.serverLevel().players()) {
            if (p.isSpectator() || p.isCreative())
              continue;
            if (p.equals(player))
              continue;
            p.addEffect(ModEffects.of(ModEffects.NO_INSTINCT, 10 * 20, 1, false, false, true));
            p.addEffect(ModEffects.of(MobEffects.BLINDNESS, 10 * 20, 1, false, false, true));
            p.addEffect(ModEffects.of(MobEffects.DARKNESS, 10 * 20, 1, false, false, true));
            p.displayClientMessage(
                Component.translatable("skill.noellesroles.mystia.blindness").withStyle(ChatFormatting.RED), true);
          }
        } else if (choice == 3) {
          player.displayClientMessage(
              Component.translatable("skill.noellesroles.mystia.self.speed", score).withStyle(ChatFormatting.AQUA),
              true);
          player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 30 * 20, 1, false, true, true));
        }
      }
    }
  }

  public static void registerSkills() {
    THHatanokokoroRole.registerSkills();
    THYukariRole.registerSkills();
    THKotiyaSanaeRole.registerEvents();
    THYuyukoRole.registerEvents();
    RoleSkill.register(THMiscRoles.MYSTIA, RoleSkill.skill(SRE.id("mystia"), "skill.noellesroles.mystia", (ctx) -> {
      OpenScreenManager.openScreen(ctx.player(), OpenScreenManager.RHYTHM_GAME_SCREEN_ROLE);
      return true;
    }).showOnHud(true).cooldownSeconds(90).announceToSelf().build());
    RoleSkill.register(THMiscRoles.KIJIN_SEIJA,
        RoleSkill.skill(SRE.id("kijin_seija_upside_down"), "skill.noellesroles.seija.upside_down", (ctx) -> {
          final int DISTANCE = 8;
          final int DURATION = 15 * 20;
          final var player = ctx.player();
          for (final var p : player.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
              continue;
            if (p.distanceToSqr(player) > DISTANCE * DISTANCE) {
              continue;
            }
            if (p.getUUID().equals(player.getUUID()))
              continue;
            p.addEffect(ModEffects.of(ModEffects.UPSIDE_DOWN, DURATION, 0, true, false, true));
            p.addEffect(ModEffects.of(ModEffects.MOVE_UPSIDE_DOWN, DURATION, 0, true, false, true));
            p.addEffect(ModEffects.of(ModEffects.MOUSE_UPSIDE_DOWN, DURATION, 0, true, false, true));
          }
          return true;
        }).cooldownSeconds(90).announceToSelf().recordReplay().showOnHud(true).build());
    RoleSkill.register(THMiscRoles.KONPAKU_YOUMU,
        RoleSkill.skill(SRE.id("konpaku_youmu"), "skill.noellesroles.konpaku_youmu.ghost", (ctx) -> {
          THKonpakuYoumuRole.enterGhost(ctx.player());
          return true;
        }).cooldownSeconds(60).noAnnouncement().recordReplay().showOnHud(true).build(),
        RoleSkill.skill(SRE.id("konpaku_youmu/leave"), "skill.noellesroles.konpaku_youmu.ghost.leave", (ctx) -> {
          THKonpakuYoumuRole.exitGhost(ctx.player());
          return true;
        }).cooldownSeconds(2).shifted(true).noAnnouncement().showOnHud(true).build());
    RoleSkill.register(THMiscRoles.MAMIZOU,
        RoleSkill.skill(SRE.id("mamizou_select"), "skill.noellesroles.mamizou_select", THMamizouRole::handleSelect)
            .noAnnouncement()
            .showOnHud(true).cooldownSeconds(60).build());
    RoleSkill.register(THMiscRoles.REIUJI_UTSUHO,
        RoleSkill.skill(SRE.id("utsuho"), "skill.noellesroles.utsuho", THUtsuhoRole::skillHandler)
            .announceToSelf().showOnHud(true).cooldownSeconds(120).build());
    RoleSkill.register(THMiscRoles.IBUKI_SUIKA,
        RoleSkill.skill(SRE.id("suika_big"), "skill.noellesroles.suika.big", THSuikaRole::handleSkillBig)
            .announceToSelf()
            .showOnHud(true).cooldownSeconds(60).build(),
        RoleSkill.skill(SRE.id("suika_small"), "skill.noellesroles.suika.small", THSuikaRole::handleSkillSmall)
            .announceToSelf().cooldownSeconds(60).showOnHud(true).shifted(true).build());
    RoleSkill.register(THMagicForestRoles.KIRISAME_MARISA,
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
            return true;
          }
          if (abilityCCA.duration > 0) {
            abilityCCA.duration = 0;
            THReimuRole.stopFlying(player);
            return true;
          }

          abilityCCA.duration = (THReimuRole.MAX_DURATION);
          THReimuRole.startFlying(player);
          return true;
        }).noAnnouncement().showOnHud(false).cooldownTicks(THReimuRole.FLY_COOLDOWN).build());
    RoleSkill.register(THRedHouseRoles.KOAKUMA,
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
    RoleSkill.register(THRedHouseRoles.DAIYOUSEI,
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
            moodcca.addMood(0.4f);
          }
          return true;
        }).recordReplay().withTarget().announceToSelf().showOnHud(true).cooldownTicks(20 * 60).build());
    RoleSkill.register(THMiscRoles.SHIKIEIKI,
        RoleSkill.skill(SRE.id("shikieiki"), "skill.noellesroles.shikieiki.instinct", context -> {
          final int GAP = 45 * 20;
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
        }).announceToSelf(true).cooldownSeconds(60).showOnHud(true).build(),
        RoleSkill.skill(SRE.id("komachi_pull"), "skill.noellesroles.komachi_pull", context -> {
          Player player = context.player();
          var target = RopeItem.findTargetedPlayerInView(player.level(), player, 12);
          if (target == null) {
            return false;
          }
          // 身前2格
          RopeItem.pullPlayer(player, target, 1);
          return true;
        }).cooldownSeconds(90).announceToSelf(true).showOnHud(true).build());
    RoleSkill.register(THMountainRoles.NITORI, RoleSkill.skill(SRE.id("nitori_exchange"),
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
        }).withTarget().announceToSelf(false).cooldownSeconds(30).build());
  }
}
