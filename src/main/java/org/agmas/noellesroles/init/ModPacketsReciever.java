package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.item.CocktailItem;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.AbilityHandler;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.block_entity.VendingMachinesBlockEntity;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.entity.ThrowingKnifeEntity;
import org.agmas.noellesroles.events.OnVendingMachinesBuyItems;
import org.agmas.noellesroles.item.ChefFoodItem;
import org.agmas.noellesroles.item.StalkerKnifeItem;
import org.agmas.noellesroles.packet.*;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.roles.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.roles.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class ModPacketsReciever {

  public static void registerPackets() {
    ServerPlayNetworking.registerGlobalReceiver(VendingMachinesBuyC2SPacket.TYPE, (payload, context) -> {
      context.server().execute(() -> {
        try {
          ServerPlayer player = context.player();
          ServerLevel serverLevel = player.serverLevel();
          BlockEntity blockEntity = serverLevel.getBlockEntity(payload.blockPos());
          if (blockEntity instanceof VendingMachinesBlockEntity vendingMachinesBlockEntity) {
            List<ShopEntry> shops = vendingMachinesBlockEntity.getShops();
            shops.stream().filter(a -> {
              if (BuiltInRegistries.ITEM.getKey(a.stack().getItem()).toString().equals(payload.item())) {
                return true;
              }
              return false;
            }).findFirst().ifPresent(entry -> {
              SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
              if (playerShopComponent.balance < entry.price()) {
                player.displayClientMessage(Component.translatable("noellesroles.not_enough_money")
                    .withStyle(ChatFormatting.RED), true);
                ServerPlayNetworking.send(player,
                    new VendingBuyMessageCallBackS2CPacket("not_enough_money"));
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
                    playerShopComponent.addToBalance(-entry.price());
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
    ServerPlayNetworking.registerGlobalReceiver(ProblemSetEventC2SPacket.ID, (payload, context) -> {
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
              Component.translatable("death_reason.noellesroles.success").withStyle(ChatFormatting.GREEN), true);
          // 没奖励，太抠门了。
        } else {
          if (gameWorldComponent.isRole(player, ModRoles.BAKA)) {
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
        if (gameWorldComponent.isRole(player, ModRoles.BAKA)) {
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
          var killer = player.level().players().stream().filter((p) -> {
            return gameWorldComponent.isRole(p, ModRoles.EXAMPLER);
          }).findFirst().orElse(null);
          if (killer != null) {
            var abpc = SREAbilityPlayerComponent.KEY.get(killer);
            abpc.charges++;
            // Noellesroles.LOGGER.info("Increase 1");
            if (abpc.charges >= 3) {
              if (RoleUtils.insertStackInFreeSlot(killer, ModItems.ExamplerPsychoItemStack.copy())) {
                killer.displayClientMessage(
                    Component.translatable("message.exampler.get_test_psycho").withStyle(ChatFormatting.GOLD),
                    true);
                abpc.charges -= 3;
              }
            }
            abpc.sync();
          }
          if (GameUtils.isPlayerAliveAndSurvival(player)) {
            var psc = SREPlayerShopComponent.KEY.get(player);
            if (psc.balance >= 100) {
              psc.addToBalance(-100);
              player.displayClientMessage(
                  Component.translatable("message.exampler.xiaozai", 100).withStyle(ChatFormatting.GREEN,
                      ChatFormatting.BOLD),
                  true);
            } else {
              GameUtils.killPlayer(player, true, killer, Noellesroles.id("fail_exam"));
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
      int foodT = TMMItemUtils.clearItem(player, (food) -> {
        if (food.getItem() instanceof CocktailItem)
          return false;
        if (food.has(ModDataComponentTypes.COOKED))
          return false;
        return food.has(DataComponents.FOOD);
      }, 1);
      int stuffT = TMMItemUtils.clearItem(player, ModItems.FOOD_STUFF, 2);
      if (!(foodT >= 1 && stuffT >= 2)) {
        player.displayClientMessage(Component.translatable("screen.noellesroles.chef.not_enough_food_stuff")
            .withStyle(ChatFormatting.RED), true);
        return;
      }
      var cooked_food = ModItems.COOKED_FOOD.getDefaultInstance();
      cooked_food.set(ModDataComponentTypes.COOKED, ModDataComponentTypes.cookedFood(payload.cookInfo()));
      ChefFoodItem.randomModel(cooked_food);
      RoleUtils.insertStackInFreeSlot(player, cooked_food);
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MORPH_PACKET, (payload, context) -> {
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

      if (gameWorldComponent.isRole(context.player(), ModRoles.VOODOO)) {
        abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
            NoellesRolesConfig.HANDLER.instance().voodooCooldown);
        abilityPlayerComponent.sync();
        VoodooPlayerComponent voodooPlayerComponent = (VoodooPlayerComponent) VoodooPlayerComponent.KEY
            .get(context.player());
        voodooPlayerComponent.setTarget(payload.player());

      }
      if (gameWorldComponent.isRole(context.player(), ModRoles.MORPHLING)) {
        MorphlingPlayerComponent morphlingPlayerComponent = (MorphlingPlayerComponent) MorphlingPlayerComponent.KEY
            .get(context.player());
        morphlingPlayerComponent.startMorph(payload.player());
      }
    });

    // 操纵师数据包处理
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MANIPULATOR_PACKET, (payload, context) -> {
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
        // 设置操纵师的冷却时间（根据配置）
        abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
            NoellesRolesConfig.HANDLER.instance().manipulatorCooldown);
        abilityPlayerComponent.sync();

        // 获取操纵师组件并设置目标
        ManipulatorPlayerComponent manipulatorPlayerComponent = (ManipulatorPlayerComponent) ManipulatorPlayerComponent.KEY
            .get(context.player());
        manipulatorPlayerComponent.setTarget(payload.player());
      }
    });

    ServerPlayNetworking.registerGlobalReceiver(TryThrowItemPacket.ID, (payload, context) -> {

      final var player = context.player();
      if (player.getMainHandItem().is(ModItems.THROWING_KNIFE)) {
        ItemCooldowns cooldowns1 = player.getCooldowns();
        Map<Item, ItemCooldowns.CooldownInstance> cooldowns = cooldowns1.cooldowns;
        if (GameUtils.isPlayerAliveAndSurvival( player)&&cooldowns1.isOnCooldown(ModItems.THROWING_KNIFE)
            && cooldowns.get(ModItems.THROWING_KNIFE).endTime - cooldowns1.tickCount <= 20)
          return;
        player.getMainHandItem().shrink(1);
        if (!cooldowns1.isOnCooldown(ModItems.THROWING_KNIFE)) {
          cooldowns1.addCooldown(ModItems.THROWING_KNIFE, 20);
        }
        ThrowingKnifeEntity entity = new ThrowingKnifeEntity(ModEntities.THROWING_KNIFE, player.level());
        entity.setPos(player.getEyePosition().add(0, -0.2, 0));
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.3f, 1.0f);
        entity.setOwner(player);
        player.level().addFreshEntity(entity);
        player.swing(InteractionHand.MAIN_HAND);
        ServerLevel serverLevel = player.serverLevel();
        serverLevel.players().forEach(p -> {
          serverLevel.playSound(p, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TRIDENT_THROW,
              SoundSource.PLAYERS, 1.0f, 1.0f);
        });

      }
      if (player.getMainHandItem().getItem() instanceof StalkerKnifeItem stalkerKnifeItem){
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player.getUUID(), ModRoles.STALKER)) {
          StalkerPlayerComponent stalkerPlayerComponent = StalkerPlayerComponent.KEY.get(player);
          if (stalkerPlayerComponent.phase ==3 && !stalkerPlayerComponent.isDashOnCooldown()){
            if (stalkerKnifeItem.tryDashAttack(player, player.getMainHandItem(), player.serverLevel())){
              stalkerPlayerComponent.dashCooldown = 60;
            }
          }
        }

      }
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.VULTURE_PACKET, (payload, context) -> {
      final var player = context.player();
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(player.level());
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
          BodyDeathReasonComponent bodyDeathReasonComponent = BodyDeathReasonComponent.KEY
              .get(playerBodyEntities.getFirst());
          if (!bodyDeathReasonComponent.vultured) {
            abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                NoellesRolesConfig.HANDLER.instance().vultureEatCooldown);
            VulturePlayerComponent vulturePlayerComponent = VulturePlayerComponent.KEY
                .get(player);
            vulturePlayerComponent.bodiesEaten++;
            vulturePlayerComponent.sync();
            player.playSound(SoundEvents.PLAYER_BURP, 1.0F, 0.5F);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            if (vulturePlayerComponent.bodiesEaten >= vulturePlayerComponent.bodiesRequired) {
              ArrayList<SRERole> shuffledKillerRoles = new ArrayList<>(Noellesroles.getEnableKillerRoles());
              shuffledKillerRoles.removeIf(role -> role.identifier().equals(ModRoles.EXECUTIONER_ID)
                  || role.identifier().equals(ModRoles.POISONER_ID)
                  || role.identifier().equals(ModRoles.WATER_GHOST_ID)
                  || role.identifier().equals(ModRoles.DIO_ID)
                  || Harpymodloader.VANNILA_ROLES.contains(role) || !role.canUseKiller()
                  || HarpyModLoaderConfig.HANDLER.instance().disabled
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
              // 继承变成杀手之前的40%金币
              playerShopComponent.setBalance((int) ((float) originalBalance * 0.4));

              // 播放全场音效
              player.level().playSound(null, player.blockPosition(),
                  SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED,
                  SoundSource.MASTER, 2.0F, 1.0F);

              RoleUtils.sendWelcomeAnnouncement(player);
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
      if (gameWorldComponent.isRole(context.player(), ModRoles.SWAPPER)) {
        SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY
            .get(context.player());
        if (!abilityPlayerComponent.canUseAbility())
          return;

        if (payload.player() != null && payload.player2() != null) {
          if (context.player().level().getPlayerByUUID(payload.player()) != null &&
              context.player().level().getPlayerByUUID(payload.player2()) != null) {

            SwapperPlayerComponent swapperComponent = ModComponents.SWAPPER.get(context.player());
            if (!swapperComponent.isSwapping) {
              swapperComponent.startSwap(payload.player(), payload.player2());
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
            ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY
                .get(context.player());
            if (executionerPlayerComponent.targetSelected)
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
    ServerPlayNetworking.registerGlobalReceiver(GamblerSelectRoleC2SPacket.ID, (payload, context) -> {
      context.server().execute(() -> {
        GamblerPlayerComponent component = GamblerPlayerComponent.KEY.get(context.player());
        component.selectRole(payload.roleId());
      });
    });
    ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.packet.BroadcasterC2SPacket.ID,
        (payload, context) -> {
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
          if (gameWorldComponent.isRole(context.player(), ModRoles.BROADCASTER)) {
            BroadcasterPlayerComponent comp = BroadcasterPlayerComponent.KEY.get(context.player());
            String message = payload.message();
            boolean onlySave = payload.onlySave();
            if (onlySave) {
              comp.setStoredStr(message);
              return;
            }
            if (playerShopComponent.balance < 100) {
              context.player().displayClientMessage(
                  Component.translatable("message.noellesroles.insufficient_funds"),
                  true);
              comp.setStoredStr(message);
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
            if (comp != null) {
              comp.setStoredStr("");
            }
            playerShopComponent.balance -= 100;
            playerShopComponent.sync();

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
      AbilityHandler.handler(payload, context);
    });
    ServerPlayNetworking.registerGlobalReceiver(AbilityWithTargetC2SPacket.ID, (payload, context) -> {
      AbilityHandler.handlerWithTarget(payload, context);
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.INSANE_KILLER_ABILITY_PACKET, (payload, context) -> {
      ServerPlayer player = (ServerPlayer) context.player();
      var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
      if (!gameWorldComponent.isSkillAvailable) {
        player.displayClientMessage(
            Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED), true);
        return;
      }
      InsaneKillerPlayerComponent component = InsaneKillerPlayerComponent.KEY.get(player);

      // 检查冷却
      if (component.cooldown > 0 && !component.isActive)
        return;

      component.toggleAbility();
      component.sync();
    });
    ServerPlayNetworking.registerGlobalReceiver(RecorderC2SPacket.TYPE, RecorderC2SPacket::handle);

    // 消防斧攻击包处理
    ServerPlayNetworking.registerGlobalReceiver(FireAxeStabPayload.ID, (payload, context) -> {
      ServerPlayer player = context.player();

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
      GameUtils.killPlayer(target, true, player, org.agmas.noellesroles.item.FireAxeItem.DEATH_REASON_FIRE_AXE);
      target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
      player.swing(InteractionHand.MAIN_HAND);

      // 回放记录
      if (SRE.REPLAY_MANAGER != null) {
        SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.FIRE_AXE));
      }
    });

    ServerPlayNetworking.registerGlobalReceiver(ModPackets.MONITOR_MARK_PACKET, (payload, context) -> {
      SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
          .get(context.player().level());
      if (gameWorldComponent.isRole(context.player(), ModRoles.MONITOR)) {
        MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY.get(context.player());

        // 检查冷却
        if (monitorComponent.canUseAbility()) {
          if (payload.target() != null) {
            Player targetPlayer = context.player().level().getPlayerByUUID(payload.target());
            if (targetPlayer != null && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
              // 标记目标
              monitorComponent.markTarget(payload.target());

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
                  String.format("%.1f", monitorComponent.getCooldownSeconds())),
              true);
        }
      }
    });
    ServerPlayNetworking.registerGlobalReceiver(ModPackets.WATER_GHOST_SKILL_PACKET,
        org.agmas.noellesroles.packet.WaterGhostUseSkillC2SPacket::handle);
  }

}
