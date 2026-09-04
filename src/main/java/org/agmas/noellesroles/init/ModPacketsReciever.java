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

package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.content.item.component.SREWritableBookContent;
import io.wifi.starrailexpress.content.item.component.SREWrittenBookContent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.packet.EditNewspaperPacket;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.block_entity.LotteryMachineBlockEntity;
import org.agmas.noellesroles.content.block_entity.VendingMachinesBlockEntity;
import org.agmas.noellesroles.content.entity.ThrowingKnifeEntity;
import org.agmas.noellesroles.content.item.ChefFoodItem;
import org.agmas.noellesroles.content.item.StalkerKnifeItem;
import org.agmas.noellesroles.content.item.ThrowingKnife;
import org.agmas.noellesroles.content.item.ZeroOneFiveShootPayload;
import org.agmas.noellesroles.events.OnVendingMachinesBuyItems;
import org.agmas.noellesroles.handler.TouhouHandlers;
import org.agmas.noellesroles.role_data.innocence.BroadcasterRoleData;
import org.agmas.noellesroles.role_data.innocence.MonitorRoleData;
import org.agmas.noellesroles.role_data.innocence.VoodooRoleData;
import org.agmas.noellesroles.role_data.killer.WarlockRoleData;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;
import org.agmas.noellesroles.role_data.killer.CreeperRoleData;
import org.agmas.noellesroles.role_data.innocence.BuilderRoleData;
import org.agmas.noellesroles.role_data.killer.EmbalmerRoleData;
import org.agmas.noellesroles.role_data.killer.ExecutionerRoleData;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.agmas.noellesroles.role_data.neutral.AmonRoleData;
import org.agmas.noellesroles.role_data.neutral.MonokumaRoleData;
import org.agmas.noellesroles.role_data.neutral.MorticianBodyMakerRoleData;
import org.agmas.noellesroles.role_data.neutral.PelicanRoleData;
import org.agmas.noellesroles.role_data.killer.MorphlingRoleData;
import org.agmas.noellesroles.role_data.killer.NinjaRoleData;
import org.agmas.noellesroles.role_data.killer.ShadowFalconRoleData;
import org.agmas.noellesroles.role_data.killer.SilencerRoleData;
import org.agmas.noellesroles.role_data.killer.SkincrawlerRoleData;
import org.agmas.noellesroles.role_data.killer.PartyRoleData;
import org.agmas.noellesroles.role_data.killer.StalkerRoleData;
import org.agmas.noellesroles.role_data.killer.SwapperRoleData;
import org.agmas.noellesroles.role_data.killer.YouluRoleData;
import org.agmas.noellesroles.role_data.neutral.VultureRoleData;
import org.agmas.noellesroles.role_data.innocence.PilotRoleData;
import org.agmas.noellesroles.role_data.killer.ImitatorRoleData;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.*;
import java.util.function.Predicate;

public class ModPacketsReciever {
  public static void registerPackets() {

    // 幽露：自由摄像机位置上报（服务端按最大距离校验后保存）
    ServerPlayNetworking.registerGlobalReceiver(YouluCamPosC2SPacket.ID, (payload, context) -> {
      context.server().execute(() -> {
        ServerPlayer player = context.player();
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(player, ModRoles.YOULU)) {
          return;
        }
        YouluRoleData rd = RoleData.getNullable(YouluRoleData.class, player);
        if (rd != null)
          rd.reportCamPos(player, payload.pos());
      });
    });

    // 幽露：ESC 取消自由摄像机（不生成球烟、不进冷却）
    ServerPlayNetworking.registerGlobalReceiver(YouluFreeCamCancelC2SPacket.ID, (payload, context) -> {
      context.server().execute(() -> {
        ServerPlayer player = context.player();
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(player, ModRoles.YOULU)) {
          return;
        }
        YouluRoleData rd = RoleData.getNullable(YouluRoleData.class, player);
        if (rd != null)
          rd.cancelFreeCam(player);
      });
    });

    ServerPlayNetworking.registerGlobalReceiver(VendingMachinesBuyC2SPacket.TYPE, (payload, context) -> {
      context.server().execute(() -> {
        try {
          ServerPlayer player = context.player();
          ServerLevel serverLevel = player.serverLevel();
          BlockEntity blockEntity = serverLevel.getBlockEntity(payload.blockPos());
          if (blockEntity instanceof VendingMachinesBlockEntity vendingMachinesBlockEntity) {
            List<ShopEntry> shops = vendingMachinesBlockEntity.getShops();
            Optional<ShopEntry> selectedEntry = Optional.empty();
            if (payload.slot() >= 0 && payload.slot() < shops.size()) {
              selectedEntry = Optional.of(shops.get(payload.slot()));
            }
            if (selectedEntry.isEmpty()) {
              selectedEntry = shops.stream()
                  .filter(a -> BuiltInRegistries.ITEM.getKey(a.stack().getItem()).toString().equals(payload.item()))
                  .findFirst();
            }
            selectedEntry.ifPresent(entry -> {
              if (!entry.hasEnoughCurrency(player)) {
                String messageKey = entry.currency() == ShopEntry.Currency.MINIGAME_TOKEN
                    ? "noellesroles.not_enough_minigame_token"
                    : "noellesroles.not_enough_money";
                player.displayClientMessage(Component.translatable(messageKey)
                    .withStyle(ChatFormatting.RED), true);
                ServerPlayNetworking.send(player,
                    new VendingBuyMessageCallBackS2CPacket(messageKey));
                player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                    0.9F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
                player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                    0.9F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
                SRE.REPLAY_MANAGER.recordStoreBuy(player.getUUID(),
                    BuiltInRegistries.ITEM.getKey(entry.stack().getItem()),
                    entry.stack().getCount(), entry.price());
                return;
              } else {
                if (OnVendingMachinesBuyItems.EVENT.invoker().allowBuy(player, entry)) {
                  if (entry.onBuy(player)) {
                    entry.spendCurrency(player);
                    player.displayClientMessage(Component.translatable("noellesroles.bought_item")
                        .withStyle(ChatFormatting.GREEN), true);
                    ServerPlayNetworking.send(player,
                        new VendingBuyMessageCallBackS2CPacket("noellesroles.bought_item"));
                    player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY),
                        SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                        0.9F + player.getRandom().nextFloat() * 0.2F,
                        player.getRandom().nextLong()));
                    SRE.REPLAY_MANAGER.recordStoreBuy(player.getUUID(),
                        BuiltInRegistries.ITEM.getKey(entry.stack().getItem()),
                        entry.stack().getCount(), entry.price());
                    org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData
                        .markMachinePurchased(player);

                  } else {
                    player.displayClientMessage(Component.translatable("noellesroles.cant_buy_item")
                        .withStyle(ChatFormatting.RED), true);
                    ServerPlayNetworking.send(player,
                        new VendingBuyMessageCallBackS2CPacket("noellesroles.cant_buy_item"));
                    player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL),
                        SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                        0.9F + player.getRandom().nextFloat() * 0.2F,
                        player.getRandom().nextLong()));

                  }
                } else {
                  player.displayClientMessage(Component.translatable("noellesroles.cant_buy_item_event")
                      .withStyle(ChatFormatting.RED), true);
                  ServerPlayNetworking.send(player,
                      new VendingBuyMessageCallBackS2CPacket("noellesroles.cant_buy_item_event"));
                  player.connection.send(new ClientboundSoundPacket(
                      BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL),
                      SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                      0.9F + player.getRandom().nextFloat() * 0.2F,
                      player.getRandom().nextLong()));
                }

              }

            });
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    });
    // 抽奖机：处理抽奖请求
    ServerPlayNetworking.registerGlobalReceiver(LotteryMachineDrawC2SPacket.TYPE, (payload, context) -> {
      ServerPlayer player = context.player();
      BlockPos pos = payload.blockPos();
      BlockEntity be = player.level().getBlockEntity(pos);
      if (!(be instanceof LotteryMachineBlockEntity lottery)) {
        return;
      }
      if (!lottery.hasPrizes()) {
        ServerPlayNetworking.send(player, new LotteryMachineResultS2CPacket(
            pos, false, "noellesroles.lottery.empty", ItemStack.EMPTY));
        return;
      }
      if (!org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData.allowMachinePurchase(player)) {
        ServerPlayNetworking.send(player, new LotteryMachineResultS2CPacket(
            pos, false, "message.noellesroles.lin_family.vending_cooldown", ItemStack.EMPTY));
        return;
      }
      if (!lottery.canAfford(player)) {
        ServerPlayNetworking.send(player, new LotteryMachineResultS2CPacket(
            pos, false, "noellesroles.not_enough_money", ItemStack.EMPTY));
        return;
      }
      lottery.spendDrawCost(player);
      Optional<ShopEntry> result = lottery.draw(player.getRandom());
      if (result.isEmpty()) {
        ServerPlayNetworking.send(player, new LotteryMachineResultS2CPacket(
            pos, false, "noellesroles.lottery.empty", ItemStack.EMPTY));
        return;
      }
      ShopEntry entry = result.get();
      ItemStack prize = entry.stack().copy();
      if (org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData.isLinFamily(player)) {
        org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData.givePurchasedItem(player, prize);
      } else if (!player.getInventory().add(prize)) {
        player.drop(prize, false);
      }
      org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData.markMachinePurchased(player);
      ServerPlayNetworking.send(player, new LotteryMachineResultS2CPacket(
          pos, true, "noellesroles.lottery.won", prize));
      player.connection.send(new ClientboundSoundPacket(
          BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP),
          SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F, 1.2F,
          player.getRandom().nextLong()));
    });
    ServerPlayNetworking.registerGlobalReceiver(RhythmGameResultC2SPacket.ID, (payload, context) -> {
      TouhouHandlers.handleMystiaResult(context.player(), payload.score());
    });
    ServerPlayNetworking.registerGlobalReceiver(ProblemSetEventC2SPacket.ID, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      ServerPlayer player = context.player();
      boolean isForced = payload.forced();
      var mainHandItem = player.getMainHandItem();
      var offHandItem = player.getOffhandItem();
      if (mainHandItem.is(FunnyItems.PROBLEM_SET)) {
        mainHandItem.shrink(1);
      } else {
        if (offHandItem.is(FunnyItems.PROBLEM_SET)) {
          offHandItem.shrink(1);
        }
      }
      var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

      if (payload.success()) {
        var psc = SREPlayerShopComponent.KEY.get(player);
        if (isForced) {
          player.displayClientMessage(
              Component.translatable("mathproblem.noellesroles.success").withStyle(ChatFormatting.GREEN), true);
          // 没奖励，太抠门了。
        } else {
          if (gameWorldComponent.isRole(player, THRedHouseRoles.BAKA)) {
            player.displayClientMessage(
                Component.translatable("message.baka.problem_set.success").withStyle(ChatFormatting.GREEN), true);
            psc.addToBalance(200);
          } else {
            player.displayClientMessage(
                Component.translatable("message.baka.not_baka.problem_set.success").withStyle(ChatFormatting.GREEN),
                true);
            psc.addToBalance(100);
          }
        }
      } else {
        if (gameWorldComponent.isRole(player, THRedHouseRoles.BAKA)) {
          player.displayClientMessage(
              Component.translatable("message.baka.problem_set.failed").withStyle(ChatFormatting.YELLOW), true);
          var pmc = SREPlayerMoodComponent.KEY.get(player);
          pmc.setMood(pmc.getMood() * 0.3f);
          return;
        }
        if (!gameWorldComponent.isRunning())
          return;
        if (isForced) {
          player.displayClientMessage(
              Component.translatable("message.exampler.problem_set.failed").withStyle(ChatFormatting.YELLOW),
              true);
          // 如果是小镇做题家给的则杀死玩家
          // 获取所有小镇做题家，给所有小镇做题家同时增加能量
          List<ServerPlayer> allExamplers = player.level().players().stream()
              .filter(p -> p instanceof ServerPlayer && gameWorldComponent.isRole(p, ModRoles.EXAMPLER))
              .map(p -> (ServerPlayer) p)
              .toList();
          ServerPlayer firstKiller = allExamplers.isEmpty() ? null : allExamplers.getFirst();
          for (ServerPlayer killer : allExamplers) {
            var abpc = SREAbilityPlayerComponent.KEY.get(killer);
            abpc.status++;
            // Noellesroles.LOGGER.info("Increase 1");
            if (abpc.status >= 3) {
              if (RoleUtils.insertStackInFreeSlot(killer, ModItems.ExamplerPsychoItemStack.copy())) {
                killer.displayClientMessage(
                    Component.translatable("message.exampler.get_test_psycho").withStyle(ChatFormatting.GOLD),
                    true);
                abpc.status -= 3;
              }
            }
            abpc.sync();
          }
          if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            var psc = SREPlayerShopComponent.KEY.get(player);
            if (psc.balance >= 100) {
              psc.addToBalance(-100);
              player.displayClientMessage(
                  Component.translatable("message.exampler.xiaozai", 100).withStyle(ChatFormatting.GREEN,
                      ChatFormatting.BOLD),
                  true);
            } else {
              GameUtils.killPlayer(player, true, firstKiller, Noellesroles.id("fail_exam"));
            }
          }
        } else {
          player.displayClientMessage(
              Component.translatable("message.baka.not_baka.problem_set.failed").withStyle(ChatFormatting.YELLOW),
              true);
          // 如果是baka给的则杀死玩家
          if (GameUtils.isPlayerAliveAndSurvival(player)) {
            GameUtils.killPlayer(player, true, null, Noellesroles.id("baka"));
          }
        }
        // player.displayClientMessage(Component.literal("Failed"), true);
      }
    });
    ServerPlayNetworking.registerGlobalReceiver(ChefCookC2SPacket.ID, (payload, context) -> {
      final var player = context.player();
      if (SREItemUtils.countItem(player, ModPacketsReciever::isChefCookableFood) < 1
          || SREItemUtils.countItem(player, ModItems.FOOD_STUFF) < 2) {
        player.displayClientMessage(Component.translatable("screen.noellesroles.chef.not_enough_food_stuff")
            .withStyle(ChatFormatting.RED), true);
        return;
      }
      shrinkMatchingItems(player, ModPacketsReciever::isChefCookableFood, 1);
      shrinkMatchingItems(player, foodStuff -> foodStuff.is(ModItems.FOOD_STUFF), 2);
      var cooked_food = ModItems.COOKED_FOOD.getDefaultInstance();
      cooked_food.set(ModDataComponentTypes.COOKED, ModDataComponentTypes.cookedFood(payload.cookInfo()));
      ChefFoodItem.randomModel(cooked_food);
      RoleUtils.insertStackInFreeSlot(player, cooked_food);
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MORPH_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
          .get(context.player());

      if (payload.player() == null)
        return;
      if (context.player().level().getPlayerByUUID(payload.player()) == null)
        return;

      if (gameWorldComponent.isRole(context.player(), ModRoles.VOODOO)
          || gameWorldComponent.isRole(context.player(), BounsRoles.LENGXIAO)) {
        // 巫毒/冷霄使用共享技能冷却。
        if (abilityPlayerComponent.cooldown > 0)
          return;
        abilityPlayerComponent.cooldown = 15 * 20;
        abilityPlayerComponent.sync();
        RoleData.getOptional(VoodooRoleData.class, context.player())
            .ifPresent(d -> d.setTarget(payload.player()));

        // 回放记录：巫毒师/冷笑绑定玩家
        SRE.REPLAY_MANAGER.recordCustomEvent(
            Component.translatable("replay.event.voodoo.bind",
                GameReplayUtils.getReplayPlayerDisplayText(context.player(), true),
                GameReplayUtils.getReplayPlayerDisplayText(context.player().level().getPlayerByUUID(payload.player()),
                    true)));

      }
      if (gameWorldComponent.isRole(context.player(), ModRoles.MORPHLING)) {
        MorphlingRoleData morphlingPlayerComponent = RoleData.getNullable(MorphlingRoleData.class, context.player());
        // 变形使用自身独立冷却（morphTicks，负值表示冷却中），不受举刀假人共享技能冷却影响。
        if (morphlingPlayerComponent == null || morphlingPlayerComponent.getMorphTicks() != 0)
          return;
        morphlingPlayerComponent.startMorph(payload.player());
      }
    });
    // newspaperHandler
    ServerPlayNetworking.registerGlobalReceiver(EditNewspaperPacket.ID, (payload, context) -> {
      var player = context.player();
      var mainHandItem = player.getMainHandItem();
      if (mainHandItem.is(ModItems.NEWSPAPER)) {
        var titOpt = payload.title();
        if (titOpt.isPresent()) {
          var list = new ArrayList<Filterable<Component>>();
          for (var p : payload.pages()) {
            list.add(Filterable.passThrough(Component.literal(p)));
          }
          String title = titOpt.get();
          if (title.length() >= SREWrittenBookContent.TITLE_MAX_LENGTH) {
            title = title.substring(0, SREWrittenBookContent.TITLE_MAX_LENGTH);
          }
          String shortTitle = title;
          if (shortTitle.length() >= 10) {
            shortTitle = shortTitle.substring(0, 8) + "...";
          }
          mainHandItem.set(SREDataComponentTypes.WRITTEN_BOOK_CONTENT,
              new SREWrittenBookContent(Filterable.passThrough(title), player.getScoreboardName(), list, true));
          mainHandItem.set(DataComponents.ITEM_NAME,
              Component.translatable("item.noellesroles.newspaper.name",
                  Component.translatable("item.noellesroles.newspaper.title.warp", shortTitle)
                      .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));

          if (mainHandItem.has(SREDataComponentTypes.WRITABLE_BOOK_CONTENT)) {
            mainHandItem.remove(SREDataComponentTypes.WRITABLE_BOOK_CONTENT);
          }
        } else {
          var list = new ArrayList<Filterable<String>>();
          for (var p : payload.pages()) {
            list.add(Filterable.passThrough(p));
          }
          mainHandItem.set(DataComponents.ITEM_NAME,
              Component.translatable("item.noellesroles.newspaper.draft",
                  Component.translatable("item.noellesroles.newspaper.draft.warp", player.getName()).withStyle(
                      ChatFormatting.ITALIC, ChatFormatting.GRAY)));
          mainHandItem.set(SREDataComponentTypes.WRITABLE_BOOK_CONTENT, new SREWritableBookContent(list));
        }
      }
    });
    // 静语者技能数据包处理
    ServerPlayNetworking.registerGlobalReceiver(SilencerC2SPacket.ID, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))
        return;
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      if (payload.targetPlayer() == null)
        return;
      if (context.player().level().getPlayerByUUID(payload.targetPlayer()) == null)
        return;
      if (gameWorldComponent.isRole(context.player(), ModRoles.SILENCER)) {
        RoleData.getOptional(SilencerRoleData.class, context.player())
            .ifPresent(silencer -> silencer.startSkill(payload.targetPlayer()));
      }
    });

    // 静语者帮助数据包处理（其他玩家右键静语者目标）
    ServerPlayNetworking.registerGlobalReceiver(SilencerHelpC2SPacket.ID, (payload, context) -> {
      ServerPlayer helper = context.player();
      ServerPlayer targetPlayer = context.player().level().getServer().getPlayerList()
          .getPlayer(payload.targetPlayer());
      if (targetPlayer == null)
        return;
      if (!GameUtils.isPlayerAliveAndSurvival(helper))
        return;
      if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
        return;
      // Find the silencer who has targetPlayer as their target
      targetPlayer.level().players().forEach(p -> {
        if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp)) {
          SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
          if (gw.isRole(sp, ModRoles.SILENCER)) {
            RoleData.getOptional(SilencerRoleData.class, sp).ifPresent(sc -> {
              if (sc.phase == 2 && targetPlayer.getUUID().equals(sc.targetUUID)) {
                sc.helpTarget();
                // 提示帮助者
                helper.displayClientMessage(
                    Component.translatable("message.noellesroles.silencer.help_success"),
                    true);
              }
            });
          }
        }
      });
    });

    ServerPlayNetworking.registerGlobalReceiver(NinjaAbilityC2SPacket.ID, (payload, context) -> {
      if (RoleSkill.blockForSpectator(context.player()))
        return;
      if (RoleData.getOptional(NinjaRoleData.class, context.player())
          .map(NinjaRoleData::useAbility).orElse(false)) {
        ConfigWorldComponent.onPlayerUsedSkill(context.player());
      }
    });
    // 巫师“盔甲护身”：在背包选择玩家后赋予护盾
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.WizardShieldC2SPacket.ID,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(context.player().level());
          if (payload.player() == null)
            return;
          if (!gameWorldComponent.isRole(context.player(), ModRoles.WIZARD))
            return;
          var wizard = RoleData.getNullable(WizardRoleData.class, context.player());
          if (wizard == null || wizard.selectedSpell != WizardRoleData.Spell.ARMOR)
            return;
          var target = context.player().level().getPlayerByUUID(payload.player());
          if (target instanceof ServerPlayer stp) {
            wizard.grantShieldTo((ServerPlayer) context.player(), stp);
          }
        });

    // Wizard数据包处理
    ServerPlayNetworking.registerGlobalReceiver(WizardSwitchSpellC2SPacket.ID, (payload, context) -> {
      ServerPlayer player = context.player();
      if (player.hasEffect(ModEffects.SAFE_TIME))
        return;
      if (RoleSkill.blockForSpectator(player, false))
        return;
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
      if (!gameWorldComponent.isSkillAvailable)
        return;
      if (!gameWorldComponent.isRole(player, ModRoles.WIZARD))
        return;
      RoleData.getOptional(WizardRoleData.class, player).ifPresent(WizardRoleData::cycleSpell);
    });

    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MANIPULATOR_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
          .get(context.player());

      if (payload.player() == null)
        return;
      if (abilityPlayerComponent.cooldown > 0)
        return;
      if (context.player().level().getPlayerByUUID(payload.player()) == null)
        return;

      if (gameWorldComponent.isRole(context.player(), ModRoles.MANIPULATOR)) {
        // 获取操纵师组件并设置目标；校验（标记/距离/事件）与冷却由 setTarget 内部处理
        ManipulatorRoleData manipulatorPlayerComponent = RoleData
            .getNullable(ManipulatorRoleData.class, context.player());
        if (manipulatorPlayerComponent != null) {
          manipulatorPlayerComponent.setTarget(payload.player());
        }
      }
    });

    // 咒术师·领域展开：背包点选一名已被诅咒且存活的目标，对其展开领域（校验/冷却由组件内部处理）
    ServerPlayNetworking.registerGlobalReceiver(
        org.agmas.noellesroles.packet.WarlockDomainC2SPacket.ID, (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;
          if (payload.target() == null)
            return;
          SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
              .get(context.player().level());
          if (gameWorldComponent.isRole(context.player(), ModRoles.WARLOCK)) {
            RoleData.getOptional(WarlockRoleData.class, context.player())
                .ifPresent(d -> d.tryOpenDomainOn(payload.target()));
          }
        });

    // 阿蒙背包点选玩家包：附身到点选的成熟宿主身上（进入附身）。校验由 setPossessTarget 内部处理。
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.AMON_SELECT_TARGET_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      if (payload.player() == null)
        return;
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      if (gameWorldComponent.isRole(context.player(), ModRoles.AMON)) {
        AmonRoleData amonData = RoleData.getNullable(AmonRoleData.class, context.player());
        if (amonData != null) {
          amonData.setPossessTarget(payload.player());
        }
      }
    });

    // 操纵师附身移动输入包：驱动被操控目标移动，或请求结束操控
    ServerPlayNetworking.registerGlobalReceiver(
        org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket.ID, (payload, context) -> {
          ManipulatorRoleData manipulatorPlayerComponent = RoleData
              .getNullable(ManipulatorRoleData.class, context.player());
          if (manipulatorPlayerComponent == null || !manipulatorPlayerComponent.isControlling
              || manipulatorPlayerComponent.target == null)
            return;
          if (payload.stop()) {
            manipulatorPlayerComponent.stopControl(false);
            return;
          }
          var targetPlayer = context.player().level().getPlayerByUUID(manipulatorPlayerComponent.target);
          if (targetPlayer == null)
            return;
          var inControlCCA = org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA.KEY.get(targetPlayer);
          // 校验：发送者确实是该目标的操控者
          if (!context.player().getUUID().equals(inControlCCA.controller))
            return;
          inControlCCA.applyControlInput(payload.movementBits(), payload.yaw(), payload.pitch());
        });

    // 操纵师附身期间：以目标身份释放目标自身技能（冷却记在目标身上）
    ServerPlayNetworking.registerGlobalReceiver(
        org.agmas.noellesroles.packet.ManipulatorAbilityC2SPacket.ID, (payload, context) -> {
          ManipulatorRoleData manipulatorPlayerComponent = RoleData
              .getNullable(ManipulatorRoleData.class, context.player());
          if (manipulatorPlayerComponent == null || !manipulatorPlayerComponent.isControlling
              || manipulatorPlayerComponent.target == null)
            return;
          var targetPlayer = context.player().level().getPlayerByUUID(manipulatorPlayerComponent.target);
          if (!(targetPlayer instanceof net.minecraft.server.level.ServerPlayer targetServerPlayer))
            return;
          var inControlCCA = org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA.KEY.get(targetPlayer);
          if (!context.player().getUUID().equals(inControlCCA.controller))
            return;
          RoleSkill.beginUseIgnoreSkillBannedEffects(targetServerPlayer);
        });

    ServerPlayNetworking.registerGlobalReceiver(TryThrowItemPacket.ID, (payload, context) -> {
      final var player = context.player();
      if (player.isSpectator())
        return;
      if (player.hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      ItemStack mainHandItem = player.getMainHandItem();
      if (mainHandItem.getItem() instanceof ThrowingKnife tk) {
        ItemCooldowns cooldowns1 = player.getCooldowns();
        Map<Item, ItemCooldowns.CooldownInstance> cooldowns = cooldowns1.cooldowns;
        if (GameUtils.isPlayerAliveAndSurvival(player) && cooldowns1.isOnCooldown(tk)
            && cooldowns.get(tk).endTime - cooldowns1.tickCount <= 20)
          return;
        if (!player.isCreative())
          mainHandItem.shrink(1);
        if (!cooldowns1.isOnCooldown(tk)) {
          cooldowns1.addCooldown(tk, 20);
        }
        ThrowingKnifeEntity entity = new ThrowingKnifeEntity(ModEntities.THROWING_KNIFE, player, player.level(),
            tk.getDefaultInstance());

        entity.setPos(player.getEyePosition().add(0, 0, 0));
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.3f, 1.0f);
        entity.setOwner(player);
        player.level().addFreshEntity(entity);
        player.swing(InteractionHand.MAIN_HAND);
        ServerLevel serverLevel = player.serverLevel();
        if (mainHandItem.is(ModItems.THROWING_KNIFE)) {
          serverLevel.players().forEach(p -> {
            serverLevel.playSound(p, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 1.0f, 1.0f);
          });
        }
      }
      if (player.getMainHandItem().getItem() instanceof StalkerKnifeItem) {
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player.getUUID(), ModRoles.STALKER)) {
          StalkerRoleData stalkerPlayerComponent = RoleData.getNullable(StalkerRoleData.class, player);
          if (stalkerPlayerComponent != null && stalkerPlayerComponent.tryStartNormalDash()) {
            ConfigWorldComponent.onPlayerUsedSkill(player);
          }
        }
      }
      // 阴阳剑Q键突进
      if (player.getMainHandItem()
          .getItem() instanceof org.agmas.noellesroles.game.roles.neutral.monokuma.YinYangSwordItem) {
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player.getUUID(), ModRoles.MONOKUMA)) {
          var comp = RoleData.getNullable(MonokumaRoleData.class, player);
          if (comp != null && comp.phase == 2) {
            org.agmas.noellesroles.game.roles.neutral.monokuma.YinYangSwordItem.performDashAttack(player);
          }
        }
      }
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.VULTURE_PACKET, (payload, context) -> {
      final var player = context.player();
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(player.level());
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      if (!gameWorldComponent.isSkillAvailable) {
        player.displayClientMessage(
            Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
        return;
      }
      SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
          .get(player);

      if (gameWorldComponent.isRole(player, ModRoles.VULTURE)
          && GameUtils.isPlayerAliveAndSurvival(player)) {
        if (abilityPlayerComponent.cooldown > 0)
          return;
        abilityPlayerComponent.sync();
        List<PlayerBodyEntity> playerBodyEntities = player.level().getEntities(
            EntityTypeTest.forExactClass(PlayerBodyEntity.class), player.getBoundingBox().inflate(10),
            (playerBodyEntity -> {
              return playerBodyEntity.getUUID().equals(payload.playerBody());
            }));
        if (!playerBodyEntities.isEmpty()) {
          PlayerBodyEntityComponent bodyDeathReasonComponent = PlayerBodyEntityComponent.KEY
              .get(playerBodyEntities.getFirst());
          if (!bodyDeathReasonComponent.vultured) {
            abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                NoellesRolesConfig.HANDLER.instance().vultureEatCooldown);
            VultureRoleData vulturePlayerComponent = RoleData.getNullable(VultureRoleData.class, player);
            if (vulturePlayerComponent != null) {
              vulturePlayerComponent.bodiesEaten++;
              vulturePlayerComponent.sync();
              player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
              if (vulturePlayerComponent.bodiesEaten >= vulturePlayerComponent.bodiesRequired) {
                ArrayList<SRERole> shuffledKillerRoles = new ArrayList<>(Noellesroles.getEnableKillerRoles());
                shuffledKillerRoles.removeIf(role -> role.identifier().equals(ModRoles.EXECUTIONER_ID)
                    || role.identifier().equals(ModRoles.POISONER_ID)
                    || role.identifier().equals(ModRoles.WATER_GHOST_ID)
                    || role.identifier().equals(ModRoles.DIO_ID)
                    || Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller()
                    || HarpyModLoaderConfig.HANDLER.instance().getDisabled()
                        .contains(role.identifier().getPath()));
                if (shuffledKillerRoles.isEmpty())
                  shuffledKillerRoles.add(TMMRoles.KILLER);
                Collections.shuffle(shuffledKillerRoles);

                SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                    .get(player);
                // 保存变成杀手之前的金币数量
                int originalBalance = playerShopComponent.balance;
                final var first = shuffledKillerRoles.getFirst();
                // gameWorldComponent.addRole(player, first);
                // ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player,
                // first);
                RoleUtils.changeRole(player, first);
                // 继承变成杀手之前的40%金币 + 100 金币
                playerShopComponent.setBalance((int) ((float) originalBalance * 0.4));
                playerShopComponent.addToBalance(100);

                // 播放全场音效
                player.level().playSound(null, player.blockPosition(),
                    SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED,
                    SoundSource.MASTER, 2.0F, 1.0F);

                RoleUtils.sendWelcomeAnnouncement(player);
              }
            }

            bodyDeathReasonComponent.vultured = true;
            bodyDeathReasonComponent.sync();
          }
        }

      }
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.SWAP_PACKET, (payload, context) -> {
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      if (gameWorldComponent.isRole(context.player(), ModRoles.SWAPPER)) {
        SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY
            .get(context.player());
        if (!abilityPlayerComponent.canUseAbility())
          return;

        if (payload.player() != null && payload.player2() != null) {
          if (context.player().level().getPlayerByUUID(payload.player()) != null &&
              context.player().level().getPlayerByUUID(payload.player2()) != null) {

            var swapperData = RoleData.getOptional(SwapperRoleData.class, context.player());
            if (swapperData.isPresent() && !swapperData.get().isSwapping) {
              swapperData.get().startSwap(payload.player(), payload.player2());
            }
          }
        }
      }
    });

    ServerPlayNetworking.registerGlobalReceiver(ModPackets.EXECUTIONER_SELECT_TARGET_PACKET,
        (payload, context) -> {
          // 检查是否启用了手动选择目标功能
          if (!NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return; // 如果未启用，则忽略该数据包
          }
          SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
              .get(context.player().level());
          if (gameWorldComponent.isRole(context.player(), ModRoles.EXECUTIONER)) {
            ExecutionerRoleData executionerPlayerComponent = RoleData.getNullable(ExecutionerRoleData.class,
                context.player());
            if (executionerPlayerComponent == null || executionerPlayerComponent.targetSelected)
              return;

            if (payload.target() != null) {
              Player targetPlayer = context.player().level().getPlayerByUUID(payload.target());
              if (targetPlayer != null && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                if (gameWorldComponent.getRole(targetPlayer).isInnocent()) {
                  executionerPlayerComponent.setTarget(payload.target());
                } else {
                  context.player().displayClientMessage(
                      Component.translatable("message.error.executioner.invalid_target"), true);
                }
              } else {
                context.player().displayClientMessage(
                    Component.translatable("message.error.executioner.target_not_found"), true);
              }
            }
          }
        });
    ServerPlayNetworking.registerGlobalReceiver(GamblerSelectRoleC2SPacket.ID,
        new GamblerSelectRoleC2SPacket.Receiver());

    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.BroadcasterC2SPacket.ID,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY
              .get(context.player());
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(context.player().level());
          SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(context.player());
          if (!GameUtils.isPlayerAliveAndSurvival(context.player())) {
            context.player().displayClientMessage(
                Component.translatable("message.noellesroles.fuck_death_send"),
                true);
            return;
          }

          // 模仿者使用广播员能力
          if (gameWorldComponent.isRole(context.player(), ModRoles.IMITATOR)) {
            var imitComp = RoleData.getNullable(ImitatorRoleData.class, context.player());
            if (imitComp != null && !payload.onlySave()) {
              imitComp.useMessageAbility(context.player(), payload.message());
            }
            return;
          }

          if (gameWorldComponent.isRole(context.player(), ModRoles.BROADCASTER)) {
            var comp = RoleData.getOptional(BroadcasterRoleData.class, context.player());
            final String originalMessage = payload.message();
            String message = originalMessage;
            boolean onlySave = payload.onlySave();
            if (onlySave) {
              comp.ifPresent(d -> d.setStoredStr(originalMessage));
              return;
            }
            if (playerShopComponent.balance < 50) {
              context.player().displayClientMessage(
                  Component.translatable("message.noellesroles.insufficient_funds"),
                  true);
              comp.ifPresent(d -> d.setStoredStr(originalMessage));
              if (context.player() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) context.player();
                player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                    0.9F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
              }
              return;
            }
            if (message.length() > 256) {
              message = message.substring(0, 256);
            }
            comp.ifPresent(d -> d.setStoredStr(""));
            playerShopComponent.balance -= 50;
            playerShopComponent.sync();

            // 记录广播员发送的消息
            Noellesroles.LOGGER.info("[Broadcaster] {} sent broadcast: {}", context.player().getName().getString(),
                message);

            for (ServerPlayer player : Objects.requireNonNull(context.player().getServer())
                .getPlayerList().getPlayers()) {
              org.agmas.noellesroles.packet.BroadcastMessageS2CPacket packet = new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
                  Component.translatable("message.noellesroles.broadcaster.general",
                      Component.literal(message).withStyle(ChatFormatting.WHITE))
                      .withStyle(ChatFormatting.GREEN));
              net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
            }
            abilityPlayerComponent.cooldown = 0;
            abilityPlayerComponent.sync();
          }
        });

    ServerPlayNetworking.registerGlobalReceiver(ModPackets.ABILITY_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      RoleSkill.beginUseShifted(context.player());
    });
    ServerPlayNetworking.registerGlobalReceiver(UnifiedSkillInputC2SPacket.ID, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME)) {
        return;
      }
      RoleSkill.beginUse(context.player(), payload.target(), payload.slot(), payload.phase(), payload.forceShifted());
    });
    ServerPlayNetworking.registerGlobalReceiver(UnifiedSkillSelectC2SPacket.ID,
        (payload, context) -> RoleSkill.selectSkill(context.player(), payload.slot()));
    ServerPlayNetworking.registerGlobalReceiver(AbilityWithTargetC2SPacket.ID, (payload, context) -> {
      RoleSkill.beginUseShiftedWithTarget(context.player(), payload.target());
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.INSANE_KILLER_ABILITY_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      if (RoleSkill.blockForSpectator(context.player()))
        return;
      ServerPlayer player = (ServerPlayer) context.player();
      var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
      if (!gameWorldComponent.isSkillAvailable) {
        player.displayClientMessage(
            Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
        return;
      }
      RoleSkill.beforeUse(player, ModRoles.INSANE_KILLER);
      InsaneKillerRoleData component = RoleData.getNullable(InsaneKillerRoleData.class, player);

      // 检查冷却
      if (component != null) {
        if (component.cooldown > 0 && !component.isActive)
          return;

        component.toggleAbility();
        component.sync();
      }
      RoleSkill.afterUse(player, ModRoles.INSANE_KILLER);
    });
    ServerPlayNetworking.registerGlobalReceiver(RecorderC2SPacket.TYPE, RecorderC2SPacket::handle);
    ServerPlayNetworking.registerGlobalReceiver(MercenaryContractSignC2SPacket.TYPE,
        MercenaryContractSignC2SPacket::handle);

    // 消防斧攻击包处理
    ServerPlayNetworking.registerGlobalReceiver(FireAxeStabPayload.ID, (payload, context) -> {
      ServerPlayer player = context.player();
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      // 验证目标是否存在且在范围内
      if (!(player.serverLevel().getEntity(payload.target()) instanceof ServerPlayer target))
        return;
      if (target.distanceTo(player) > 3.0)
        return;

      // 检查目标是否存活
      if (!GameUtils.isPlayerAliveAndSurvival(target)) {
        player.displayClientMessage(
            Component.translatable("item.noellesroles.fire_axe.target_dead")
                .withStyle(ChatFormatting.RED),
            true);
        return;
      }

      // 获取玩家手中的消防斧
      var stack = player.getMainHandItem();
      if (!stack.is(ModItems.FIRE_AXE)) {
        return;
      }

      // 检查耐久是否满
      if (stack.getDamageValue() > 0) {
        player.displayClientMessage(
            Component.translatable("item.noellesroles.fire_axe.not_full_durability")
                .withStyle(ChatFormatting.RED),
            true);
        return;
      }

      // 检查冷却
      if (player.getCooldowns().isOnCooldown(ModItems.FIRE_AXE)) {
        player.displayClientMessage(
            Component.translatable("item.noellesroles.fire_axe.on_cooldown")
                .withStyle(ChatFormatting.RED),
            true);
        return;
      }

      // 消耗耐久
      if (!player.isCreative()) {
        stack.hurtAndBreak(3, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
      }

      // 添加冷却
      if (!player.isCreative()) {
        player.getCooldowns().addCooldown(ModItems.FIRE_AXE, 60 * 20); // 60秒冷却
      }

      // 执行击杀
      GameUtils.killPlayer(target, true, player, org.agmas.noellesroles.content.item.FireAxeItem.DEATH_REASON_FIRE_AXE);
      target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
      player.swing(InteractionHand.MAIN_HAND);

      // 回放记录
      if (SRE.REPLAY_MANAGER != null) {
        SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.FIRE_AXE));
      }
    });

    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MONITOR_MARK_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
        return;
      if (context.player().hasEffect(ModEffects.SKILL_BANED) || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
        return;
      }
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      if (gameWorldComponent.isRole(context.player(), ModRoles.MONITOR)) {
        var monitorData = RoleData.getOptional(MonitorRoleData.class, context.player());

        // 检查冷却
        if (monitorData.isPresent() && monitorData.get().canUseAbility()) {
          if (payload.target() != null) {
            Player targetPlayer = context.player().level().getPlayerByUUID(payload.target());
            if (targetPlayer != null && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
              // 标记目标
              monitorData.get().markTarget(payload.target());

              // 发送成功消息
              context.player().displayClientMessage(
                  Component
                      .translatable("message.noellesroles.monitor.marked",
                          targetPlayer.getName().getString())
                      .withStyle(ChatFormatting.AQUA),
                  true);
            } else {
              context.player().displayClientMessage(
                  Component.translatable("message.noellesroles.monitor.target_not_found"), true);
            }
          }
        } else {
          // 冷却中
          context.player().displayClientMessage(
              Component.translatable("message.noellesroles.monitor.cooldown",
                  String.format("%.1f", monitorData.map(MonitorRoleData::getCooldownSeconds).orElse(0f))),
              true);
        }
      }
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.WATER_GHOST_SKILL_PACKET,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
            return;
          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          org.agmas.noellesroles.packet.WaterGhostUseSkillC2SPacket.handle(payload, context);
        });

    // 苦力怕技能包处理
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.RicesRoleRhapsody.CREEPER_ABILITY_PACKET,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
            return;
          if (RoleSkill.blockForSpectator(context.player()))
            return;
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
              .get(player.level());

          if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
          }

          if (gameWorldComponent.isRole(player, BounsRoles.CREEPER)) {
            if (RoleData.getOptional(CreeperRoleData.class, player)
                .map(CreeperRoleData::ignite).orElse(false)) {
              ConfigWorldComponent.onPlayerUsedSkill(player);
            }
          }
        });

    // 交换者 G 键瞬移交换技能：与正前方目标交换位置
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.SwapperFrontSwapC2SPacket.ID,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;
          if (RoleSkill.blockForSpectator(context.player()))
            return;
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
              .get(player.level());
          if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
          }
          if (gameWorldComponent.isRole(player, ModRoles.SWAPPER)) {
            RoleData.getOptional(SwapperRoleData.class, player).ifPresent(swapper -> swapper.frontSwap(player));
          }
        });

    // 影隼技能包处理
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.RicesRoleRhapsody.SHADOW_FALCON_ABILITY_PACKET,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;
          if (RoleSkill.blockForSpectator(context.player()))
            return;
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

          if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
          }

          if (gameWorldComponent.isRole(player, ModRoles.SHADOW_FALCON)) {
            var shadowFalconData = RoleData.getOptional(ShadowFalconRoleData.class, player);
            // 蹲下优先脱下喷气背包和鞘翅，无条件优先执行
            if (player.isShiftKeyDown()) {
              shadowFalconData.ifPresent(ShadowFalconRoleData::removeJetpack);
              return;
            }
            // 使用技能
            if (shadowFalconData.map(ShadowFalconRoleData::useAbility).orElse(false)) {
              ConfigWorldComponent.onPlayerUsedSkill(player);
            }
          }
        });

    // 飞行员/影隼脱下喷气背包包处理
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.RicesRoleRhapsody.PILOT_REMOVE_JETPACK_PACKET,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

          if (gameWorldComponent.isRole(player, ModRoles.PILOT)) {
            RoleData.getOptional(PilotRoleData.class, player).ifPresent(PilotRoleData::removeJetpack);
          } else if (gameWorldComponent.isRole(player, ModRoles.SHADOW_FALCON)) {
            RoleData.getOptional(ShadowFalconRoleData.class, player).ifPresent(ShadowFalconRoleData::removeJetpack);
          }
        });

    // 派对狂技能包处理
    ServerPlayNetworking.registerGlobalReceiver(PartyKillerC2SPacket.TYPE,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;

          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

          if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
          }

          if (gameWorldComponent.isRole(player, ModRoles.PARTY_KILLER)) {
            if (payload.targetPlayer() == null) {
              player.displayClientMessage(
                  Component.translatable("message.noellesroles.party.no_target"), true);
              return;
            }
            Player target = player.level().getPlayerByUUID(payload.targetPlayer());
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
              player.displayClientMessage(
                  Component.translatable("message.noellesroles.party.target_not_found"), true);
              return;
            }

            // 检查冷却
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
            if (!ability.canUseAbility()) {
              player.displayClientMessage(Component.literal("技能冷却中"), true);
              return;
            }

            // 设置冷却 35秒
            ability.setCooldown(35 * 20);
            ability.sync();

            // 获取基于开局玩家数计算的阈值（已在游戏开始时初始化）
            PartyRoleData pc = RoleData.getNullable(PartyRoleData.class, player);
            if (pc == null)
              return;
            int threshold = pc.getThreshold();

            // 为目标设置氦气变声（4分钟 = 240秒 = 4800 ticks）
            HeliumBuzzPlayerComponent buzz = HeliumBuzzPlayerComponent.KEY.get(target);
            buzz.apply(4 * 60 * 20, 1); // 4分钟，强度1

            // 记录到组件
            pc.addAffectedTarget(target.getUUID());
            pc.schedulePartySound(6 * 20); // 6秒后从当前位置播放
            pc.sync();

            // 回放记录：派对狂对玩家使用氦气变声
            SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.party.helium_voice",
                    GameReplayUtils.getReplayPlayerDisplayText(player, true),
                    GameReplayUtils.getReplayPlayerDisplayText(target, true)));

            // 检查是否达到触发阈值
            if (pc.getCount() >= threshold) {
              PartyRoleData.triggerPartyTime((ServerLevel) player.level(), player);
              pc.clearCount(); // 只清零自己的计数
            }
          }
        });

    // ==================== 鹈鹕技能网络包处理 ====================
    ServerPlayNetworking.registerGlobalReceiver(PelicanEatC2SPacket.ID,
        (payload, context) -> {
          ServerPlayer player = context.player();
          if (player.isSpectator())
            return;

          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
          if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
          }
          if (!gameWorldComponent.isRole(player, ModRoles.PELICAN))
            return;
          PelicanRoleData comp = RoleData.getNullable(PelicanRoleData.class, player);
          if (comp == null) {
            return;
          }
          // 蹲下释放，否则对鼠标准星目标吞噬
          if (player.isShiftKeyDown()) {
            comp.releaseLast();
          } else {
            // 寻找2.15格内有视线的最近存活玩家
            ServerPlayer target = null;
            double closest = 2.15D * 2.15D;
            for (ServerPlayer p : player.serverLevel()
                .getPlayers(p -> p != player && GameUtils.isPlayerAliveAndSurvival(p))) {
              double dist = player.distanceToSqr(p);
              if (dist < closest && player.hasLineOfSight(p)) {
                closest = dist;
                target = p;
              }
            }
            if (target != null) {
              comp.tryEat(target);
            } else {
              player.displayClientMessage(
                  Component.translatable("message.noellesroles.pelican.no_target").withStyle(ChatFormatting.RED), true);
            }
          }
        });

    // ==================== 愚者网络包处理 ====================

    // V键祷告/加入会议
    ServerPlayNetworking.registerGlobalReceiver(
        org.agmas.noellesroles.game.roles.innocence.fool.FoolPrayerC2SPacket.ID,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))// 安全时间
            return;
          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
              .get(player.level());

          if (!gameWorldComponent.isSkillAvailable)
            return;

          org.agmas.noellesroles.game.roles.innocence.fool.PrayerHandler.startPrayer(player);
        });

    // 退出塔罗会
    ServerPlayNetworking.registerGlobalReceiver(
        org.agmas.noellesroles.game.roles.innocence.fool.FoolLeaveMeetingC2SPacket.ID,
        (payload, context) -> {
          ServerPlayer player = context.player();
          org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager.memberLeaveMeeting(player);
        });

    // 塔罗会投票
    ServerPlayNetworking.registerGlobalReceiver(
        org.agmas.noellesroles.game.roles.innocence.fool.FoolTarotVoteC2SPacket.ID,
        (payload, context) -> {
          ServerPlayer player = context.player();
          org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager.submitVote(player, payload.votedFor());
        });

    // 短管霰弹枪装备音效包处理
    ServerPlayNetworking.registerGlobalReceiver(ShortShotgunEquipPayload.ID, (payload, context) -> {
      ServerPlayer player = context.player();
      if (player.level().isClientSide)
        return;
      // 播放上膛音效，让附近所有玩家都能听到
      player.level().playSound(null, player.blockPosition(), NRSounds.SHOTGUNU_COCK, SoundSource.PLAYERS, 1.0F, 1.0F);
    });

    // 零一五枪射击包处理
    ServerPlayNetworking.registerGlobalReceiver(ZeroOneFiveShootPayload.ID, new ZeroOneFiveShootPayload.Receiver());

    // 建筑师技能包处理
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.RicesRoleRhapsody.BUILDER_ABILITY_PACKET,
        (payload, context) -> {
          if (context.player().hasEffect(ModEffects.SAFE_TIME))
            return;
          if (RoleSkill.blockForSpectator(context.player()))
            return;
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

          if (!gameWorldComponent.isSkillAvailable) {
            player.displayClientMessage(
                Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
            return;
          }

          if (gameWorldComponent.isRole(player, ModRoles.BUILDER)) {
            BuilderRoleData builderComponent = RoleData.getNullable(BuilderRoleData.class, player);
            if (builderComponent == null)
              return;

            // 蹲下按技能键切换模式（不受冷却影响）
            if (payload.shiftDown()) {
              builderComponent.switchMode();
              return;
            }

            // 根据当前模式使用技能
            boolean skillUsed;
            if (builderComponent.isBuildMode()) {
              skillUsed = builderComponent.useBuildAbility();
            } else {
              skillUsed = builderComponent.useDemolishAbility();
            }
            if (skillUsed) {
              ConfigWorldComponent.onPlayerUsedSkill(player);
            }
          }
        });

    // 葬仪模式切换包处理
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MORTICIAN_TOGGLE_MODE_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))
        return;
      ServerPlayer player = context.player();
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

      if (!gameWorldComponent.isSkillAvailable) {
        return;
      }

      if (gameWorldComponent.isRole(player, ModRoles.MORTICIAN_BODYMAKER)) {
        MorticianBodyMakerRoleData morticianComponent = RoleData.getNullable(MorticianBodyMakerRoleData.class, player);
        if (morticianComponent != null) {
          morticianComponent.toggleMode();
        }
      }
    });

    // 模仿者切换槽位包处理
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.IMITATOR_SWITCH_SLOT_PACKET, (payload, context) -> {
      if (context.player().hasEffect(ModEffects.SAFE_TIME))
        return;
      ServerPlayer player = context.player();
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

      if (!gameWorldComponent.isSkillAvailable) {
        return;
      }

      if (gameWorldComponent.isRole(player, ModRoles.IMITATOR)) {
        var imitatorComponent = RoleData.getNullable(ImitatorRoleData.class, player);
        if (imitatorComponent != null) {
          imitatorComponent.switchSlot();
        }
      }
    });

    // 葬仪造尸包处理
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MORTICIAN_CREATE_BODY_PACKET, (payload, context) -> {
      ServerPlayer player = context.player();

      if (context.player().hasEffect(ModEffects.SKILL_BANED) || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
        return;
      }
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

      if (gameWorldComponent.isRole(player, ModRoles.MORTICIAN_BODYMAKER)) {
        MorticianBodyMakerRoleData morticianComponent = RoleData.getNullable(MorticianBodyMakerRoleData.class, player);
        if (morticianComponent == null)
          return;

        // 安全时间内直接进入造尸冷却（必须在isSkillAvailable判断之前）
        if (context.player().hasEffect(ModEffects.SAFE_TIME)) {
          morticianComponent.bodyCreationCooldown = MorticianBodyMakerRoleData.BODY_CREATION_COOLDOWN;
          morticianComponent.sync();
          return;
        }

        if (!gameWorldComponent.isSkillAvailable) {
          return;
        }

        // 检查造尸冷却
        if (!morticianComponent.canCreateBody()) {
          player.displayClientMessage(
              Component
                  .translatable("message.noellesroles.mortician_bodymaker.cooldown",
                      (morticianComponent.bodyCreationCooldown + 19) / 20)
                  .withStyle(ChatFormatting.RED),
              true);
          return;
        }

        // 找到目标玩家
        Player targetPlayer = player.level().getPlayerByUUID(payload.targetUuid());
        if (targetPlayer == null || !(targetPlayer instanceof ServerPlayer)) {
          player.displayClientMessage(
              Component.translatable("message.noellesroles.mortician_bodymaker.create_body.target_not_found")
                  .withStyle(ChatFormatting.RED),
              true);
          return;
        }

        // 创建尸体（冷却在createBody内部设置）
        morticianComponent.createBody((ServerPlayer) targetPlayer, payload.deathReason());
      }
    });

    // ==================== 嬉命人网络包 ====================
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.EmbalmerC2SPacket.ID,
        (payload, context) -> {
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
          if (!gameWorld.isSkillAvailable)
            return;
          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          if (player.hasEffect(ModEffects.SAFE_TIME))
            return;
          if (!gameWorld.isRole(player, ModRoles.EMBALMER))
            return;
          if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
          var comp = RoleData.getNullable(EmbalmerRoleData.class, player);
          if (comp == null)
            return;
          if (comp.masqueradeCooldown > 0) {
            player.displayClientMessage(
                Component.translatable("message.noellesroles.embalmer.cooldown", (comp.masqueradeCooldown + 19) / 20)
                    .withStyle(ChatFormatting.RED),
                true);
            return;
          }
          // Trigger masquerade - 排除拥有 jeb 修饰符的玩家
          java.util.List<ServerPlayer> players = player.serverLevel().getPlayers(
              p2 -> !p2.isSpectator() && !WorldModifierComponent.KEY.get(p2.level()).isModifier(p2, SEModifiers.JEB_));
          if (players.size() < 2) {
            player.displayClientMessage(Component.literal("Need at least 2 players."), true);
            return;
          }
          java.util.Map<UUID, UUID> swaps = new java.util.LinkedHashMap<>();
          java.util.Map<UUID, Float> pitches = new java.util.HashMap<>();
          java.util.List<UUID> uuids = new java.util.ArrayList<>();
          for (var p : players)
            uuids.add(p.getUUID());
          java.util.Collections.shuffle(uuids, new java.util.Random());
          for (int i = 0; i < uuids.size(); i++) {
            UUID from = players.get(i).getUUID();
            UUID to = uuids.get(i);
            if (from.equals(to))
              to = uuids.get((i + 1) % uuids.size());
            swaps.put(from, to);
            pitches.put(from, 0.7F + (new java.util.Random().nextFloat() * 0.6F));
          }
          comp.skinSwaps = swaps;
          comp.voicePitches = pitches;
          comp.masqueradeActive = true;
          comp.masqueradeTicksLeft = EmbalmerRoleData.MASQUERADE_DURATION;
          comp.masqueradeCooldown = EmbalmerRoleData.MASQUERADE_COOLDOWN;
          comp.sync();
          // 全场播放音效（遍历所有玩家，绕过距离衰减）
          for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
            p.playNotifySound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.MASTER, 1.0F, 1.0F);
          }
          // 广播皮肤交换数据给所有玩家
          EmbalmerSkinSwapS2CPacket swapPacket = new EmbalmerSkinSwapS2CPacket(swaps, pitches,
              EmbalmerRoleData.MASQUERADE_DURATION);
          for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
            ServerPlayNetworking.send(p, swapPacket);
          }
          player.displayClientMessage(
              Component.translatable("message.noellesroles.embalmer.activated").withStyle(ChatFormatting.DARK_PURPLE),
              true);
        });

    // ==================== 窃皮者网络包 ====================
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.SkincrawlerC2SPacket.ID,
        (payload, context) -> {
          ServerPlayer player = context.player();
          SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
          if (!gameWorld.isSkillAvailable)
            return;
          if (player.hasEffect(ModEffects.SAFE_TIME))
            return;
          if (context.player().hasEffect(ModEffects.SKILL_BANED)
              || context.player().hasEffect(ModEffects.SKILL_FREEZED)) {
            return;
          }
          if (!gameWorld.isRole(player, ModRoles.SKINCRAWLER))
            return;
          if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
          var comp = RoleData.getOptional(SkincrawlerRoleData.class, player);
          if (comp.isEmpty())
            return;
          if (comp.get().stealCooldown > 0) {
            player.displayClientMessage(
                Component
                    .translatable("message.noellesroles.skincrawler.cooldown", (comp.get().stealCooldown + 19) / 20)
                    .withStyle(ChatFormatting.RED),
                true);
            return;
          }
          io.wifi.starrailexpress.content.entity.PlayerBodyEntity body = null;
          for (io.wifi.starrailexpress.content.entity.PlayerBodyEntity b : player.serverLevel().getEntitiesOfClass(
              io.wifi.starrailexpress.content.entity.PlayerBodyEntity.class, player.getBoundingBox().inflate(3.0D))) {
            if (b.getPlayerUuid() != null && !b.getPlayerUuid().equals(player.getUUID())) {
              body = b;
              break;
            }
          }
          if (body != null) {
            UUID prev = comp.get().stolenSkin != null ? comp.get().stolenSkin : player.getUUID();
            comp.get().stolenSkin = body.getPlayerUuid();
            body.getComponent().setSkinUuid(prev);
            comp.get().stealCooldown = SkincrawlerRoleData.STEAL_COOLDOWN;
            // 广播皮肤给所有玩家
            for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
              ServerPlayNetworking.send(p,
                  new org.agmas.noellesroles.packet.SkincrawlerSkinS2CPacket(player.getUUID(), comp.get().stolenSkin));
            }
            comp.get().sync();
            // 回放记录：窃皮者改变自身皮肤
            Player skincrawlerTarget = player.serverLevel().getPlayerByUUID(comp.get().stolenSkin);
            SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.skincrawler.change_skin",
                    GameReplayUtils.getReplayPlayerDisplayText(player, true),
                    skincrawlerTarget != null ? GameReplayUtils.getReplayPlayerDisplayText(skincrawlerTarget, true)
                        : Component.literal("<???>")));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f,
                1.0f);
            player.displayClientMessage(
                Component.translatable("message.noellesroles.skincrawler.stolen").withStyle(ChatFormatting.GOLD), true);
          } else {
            player.displayClientMessage(
                Component.translatable("message.noellesroles.skincrawler.no_body").withStyle(ChatFormatting.RED), true);
          }
        });
  }

  private static boolean isChefCookableFood(ItemStack food) {
    if (food.is(ModItems.FOOD_STUFF))
      return false;
    if (food.getItem() instanceof CocktailItem)
      return false;
    if (food.has(ModDataComponentTypes.COOKED))
      return false;
    return food.has(DataComponents.FOOD);
  }

  private static int shrinkMatchingItems(Player player, Predicate<ItemStack> predicate, int count) {
    int remaining = count;
    for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
      ItemStack stack = player.getInventory().getItem(i);
      if (stack.isEmpty() || !predicate.test(stack))
        continue;
      int toShrink = Math.min(remaining, stack.getCount());
      stack.shrink(toShrink);
      remaining -= toShrink;
      if (stack.isEmpty()) {
        player.getInventory().setItem(i, ItemStack.EMPTY);
      }
    }
    player.containerMenu.broadcastChanges();
    player.inventoryMenu.slotsChanged(player.getInventory());
    return count - remaining;
  }
}
