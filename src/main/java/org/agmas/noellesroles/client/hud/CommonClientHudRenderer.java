package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.*;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import io.wifi.utils.client.betterrender.FakeHudRenderCallback;
import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemCooldowns.CooldownInstance;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.WayfarerHudRenderer;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.client.event.MutableComponentResult;
import org.agmas.noellesroles.client.event.OnMessageBelowMoneyRenderer;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.client.hud.roles.BroadcasterHud;
import org.agmas.noellesroles.client.hud.roles.CuckooHud;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.game.roles.Innocent.accountant.AccountantPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.alchemist.AlchemistPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.athlete.AthletePlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.attendant.AttendantHandler;
import org.agmas.noellesroles.game.roles.Innocent.clock_maker.ClockmakerPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.hoan_meirin.HoanMeirinPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.locksmith_inspiration.LocksmithInspirationComponent;
import org.agmas.noellesroles.game.roles.Innocent.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ninja.NinjaPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.commander.CommanderHudRender;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.shadow_falcon.ShadowFalconPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.utils.MessageDetail;

import java.awt.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BiConsumer;

public class CommonClientHudRenderer {
  static ArrayList<BiConsumer<FakeGuiGraphics, DeltaTracker>> roleRenderConsumers = null;
  static SRERole lastRenderRole = null;
  public static int effectStartY = 0;

  public static MessageDetail foldHelpDisplayTip = new MessageDetail(Component
      .translatable("noellesroles.hud.fold_help_display_tip", Component.keybind("key.noellesroles.show_help_display"))
      .withStyle(ChatFormatting.GRAY), false);
  public static MessageDetail creditText = new MessageDetail(Component
      .translatableWithFallback("noellesroles.hud.credit", "Modded Version Author: ")
      .append(Component.literal("残月列车团队"))
      .withStyle(ChatFormatting.AQUA), true);
  public static MessageDetail showHelpDisplayTip = new MessageDetail(Component
      .translatable("noellesroles.hud.show_help_display_tip", Component.keybind("key.noellesroles.show_help_display"))
      .withStyle(ChatFormatting.GRAY), true);

  public static void registerFather() {
    // Use FakeHudRenderCallback instead of Fabric's HudRenderCallback
    // This ensures rendering happens INSIDE the frame lifecycle
    // (beginFrame/endFrame)
    // which fixes the font rendering issue where text would randomly disappear
    FakeHudRenderCallback.EVENT.register((guiGraphics, trueDeltaTracker) -> {
      if (!OptimizedTextRenderer.INSTANCE.isTickDirty())
        return;
      DeltaTracker deltaTracker = DeltaTracker.ONE;
      final Minecraft client = Minecraft.getInstance();
      final Font font = client.font;
      // FakeGuiGraphics is already provided by the callback - no need to wrap again
      if (client.player == null)
        return;
      if (SREClient.gameComponent == null) {
        return;
      }
      final LocalPlayer player = client.player;
      {
        RoleNameRenderer.renderHud(font, player, guiGraphics, deltaTracker);
        LobbyPlayersRenderer.renderHud(font, player, guiGraphics);
      }
      {
        RoundTextRenderer.renderHud(font, player, guiGraphics, deltaTracker.getRealtimeDeltaTicks());
      }
      {
        TimeRenderer.renderHud(font, player, guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(true));
      }
      {
        if (Minecraft.getInstance().screen == null)
          HudStoreRenderer.renderHud(font, player, guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(true));
      }
      {
        HudMoodRenderer.renderHud(player, font, guiGraphics, deltaTracker);
      }
      {
        if (client.screen == null) {
          MutableComponentResult texts = OnMessageBelowMoneyRenderer.EVENT.invoker().onRenderer(
              client, guiGraphics,
              deltaTracker);
          java.util.List<MessageDetail> infoLines = texts.mutipleContent;
          int y = 20;
          int width = guiGraphics.guiWidth();
          int lineHeight = client.font.lineHeight + 4;
          if (NoellesrolesClient.isShowHelpDisplay) {
            if (SREClient.gameComponent != null) {
              if (SREClient.gameComponent.isRunning()) {
                infoLines.add(creditText);
                infoLines.add(foldHelpDisplayTip);
              }
            }
            for (var line : infoLines) {
              guiGraphics.drawString(client.font, line.mutableComponent(),
                  width - 10 - client.font.width(line.mutableComponent()), y,
                  java.awt.Color.WHITE.getRGB());
              y += lineHeight;
            }
          } else {
            if (SREClient.gameComponent != null) {
              if (SREClient.gameComponent.isRunning()) {
                infoLines.add(creditText);
                infoLines.add(showHelpDisplayTip);
              }
            }
            for (var line : infoLines) {
              if (!line.briefly())
                continue;
              guiGraphics.drawString(client.font, line.mutableComponent(),
                  width - 10 - client.font.width(line.mutableComponent()), y,
                  java.awt.Color.WHITE.getRGB());
              y += lineHeight;
            }
          }
          effectStartY = y;
        }
      }
      {
        if (SREClient.gameComponent.isRunning()) {
          if (client.player.hasEffect(ModEffects.SAFE_TIME)) {
            var effect = client.player.getEffect(ModEffects.SAFE_TIME);
            Component message = Component.translatable("hud.noellesroles.safe_time", effect.getDuration() / 20)
                .withStyle(ChatFormatting.GREEN);
            guiGraphics.drawCenteredString(client.font, message, guiGraphics.guiWidth() / 2, 40,
                java.awt.Color.WHITE.getRGB());
          }
        }
      }
      // if (SREClient.isPlayerSpectator())
      // return;
      {
        // 最后渲染在上层
        BroadcasterHud.renderBroadcast(guiGraphics, deltaTracker);
      }

      var consumer1 = CommonHudRenderCallback.EVENT.getConsumer();
      if (consumer1 != null && !consumer1.isEmpty()) {
        consumer1.forEach((c) -> {
          c.accept(guiGraphics, deltaTracker);
        });
      }
      
      SRERole role = SREClient.getCachedPlayerRole();
      if (role == null)
        return;
      if (role != lastRenderRole) {
        roleRenderConsumers = RoleHudRenderCallback.EVENT.getConsumer(role.identifier());
        lastRenderRole = role;
      }
      if (roleRenderConsumers != null) {
        try {
          roleRenderConsumers.forEach((c) -> c.accept(guiGraphics, deltaTracker));
        } catch (Exception e) {
          RoleHudRenderCallback.EVENT.removeConsumer(role.identifier());
          SRE.LOGGER.error("[ROLE HUD ERROR] Error while render role hud of {}. Removed it.",
              role.identifier(), e);
          client.player.displayClientMessage(
              Component.translatable("[CLIENT ERROR] Error while rendering Role Hud for %s.\n%s",
                  role.identifier().toString(), e.getMessage()).withStyle(ChatFormatting.RED),
              false);
          client.player.displayClientMessage(
              Component.translatable("[CLIENT ERROR] Error while rendering Role Hud for %s.\n%s",
                  role.identifier().toString(), e.getMessage()).withStyle(ChatFormatting.RED),
              true);
        }
      }
    });
  }

  public static void registerRenderersEvent() {
    registerFather();
    registerSons();
    OtherRolesRegister.registerSons();
  }

  public static void registerSons() {
    RoleHudRenderCallback.EVENT.register(ModRoles.STALKER_ID, (context, tickCounter) -> {
      var client = Minecraft.getInstance();
      // 获取跟踪者组件
      StalkerPlayerComponent stalkerComp = StalkerPlayerComponent.KEY.get(client.player);
      // 检查是否是跟踪者
      if (!stalkerComp.isActiveStalker())
        return;

      // 检查玩家是否存活
      if (!SREClient.isPlayerAliveAndInSurvival())
        return;

      // 渲染位置 - 左下角
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int x = 10;
      int y = screenHeight - 80;

      Font textRenderer = client.font;

      // 阶段显示
      Component phaseText = switch (stalkerComp.phase) {
        case 1 -> Component.translatable("hud.noellesroles.stalker.phase1").withStyle(ChatFormatting.DARK_PURPLE);
        case 2 -> Component.translatable("hud.noellesroles.stalker.phase2").withStyle(ChatFormatting.RED);
        case 3 -> Component.translatable("hud.noellesroles.stalker.phase3").withStyle(ChatFormatting.DARK_RED);
        default -> Component.empty();
      };
      context.drawString(textRenderer, phaseText, x, y, 0xFFFFFF);
      y += 12;

      // 能量条
      int maxEnergy = stalkerComp.phase == 1 ? stalkerComp.getPhase1EnergyRequired()
          : stalkerComp.getPhase2EnergyRequired();
      Component energyText = Component.translatable("hud.noellesroles.stalker.energy", stalkerComp.energy, maxEnergy);
      context.drawString(textRenderer, energyText, x, y, 0xAAAAAA);
      y += 12;

      // 一阶段：显示盾牌状态
      if (stalkerComp.phase == 1) {
        Component immunityText = stalkerComp.immunityUsed
            ? Component.translatable("hud.noellesroles.stalker.immunity_used").withStyle(ChatFormatting.GRAY)
            : Component.translatable("hud.noellesroles.stalker.immunity_available")
                .withStyle(ChatFormatting.GREEN);
        context.drawString(textRenderer, immunityText, x, y, 0xFFFFFF);
        y += 12;
      }

      // 二阶段及以上：击杀数
      if (stalkerComp.phase >= 2) {
        Component killsText = Component.translatable("hud.noellesroles.stalker.kills",
            stalkerComp.phase2Kills, stalkerComp.getPhase2KillsRequired());
        context.drawString(textRenderer, killsText, x, y, 0xFF6666);
        y += 12;
      }

      // 二阶段及以上：攻击冷却
      if (stalkerComp.phase >= 2
          && client.player.getCooldowns().isOnCooldown(client.player.getMainHandItem().getItem())) {
        var cooldowns = client.player.getCooldowns();
        CooldownInstance cooldown = cooldowns.cooldowns.get(client.player.getMainHandItem().getItem());
        if (cooldown != null) {
          String cooldownTime = String.format("%.1f", (cooldown.endTime - cooldowns.tickCount) / 20.);
          Component cooldownText = Component.translatable("hud.noellesroles.stalker.attack_cooldown", cooldownTime)
              .withStyle(ChatFormatting.RED);
          context.drawString(textRenderer, cooldownText, x, y, 0xFF0000);
          y += 12;
        }
      }

      // 三阶段：倒计时
      if (stalkerComp.phase == 3) {
        int seconds = stalkerComp.phase3Timer / 20;
        int minutes = seconds / 60;
        seconds %= 60;
        Component timerText = Component.translatable("hud.noellesroles.stalker.timer",
            String.format("%d:%02d", minutes, seconds));
        int color = stalkerComp.phase3Timer < 600 ? 0xFF0000 : 0xFFAA00; // 30秒以下变红
        context.drawString(textRenderer, timerText, x, y, color);
        y += 12;
      }

      // 窥视状态
      if (stalkerComp.isGazing) {
        Component gazingText = Component
            .translatable("hud.noellesroles.stalker.gazing", stalkerComp.gazingTargetCount)
            .withStyle(ChatFormatting.YELLOW);
        context.drawString(textRenderer, gazingText, x, y, 0xFFFFFF);
        y += 12;
      }
      if (stalkerComp.isDashOnCooldown()) {
        Component dashText = Component.translatable("hud.noellesroles.stalker.dash_cooldown",
            String.format("%.1f", stalkerComp.getDashCooldownSeconds()))
            .withStyle(ChatFormatting.YELLOW);
        context.drawString(textRenderer, dashText, x, y, 0xFFFFFF);
        y += 12;

      }

      // 蓄力进度（三阶段）
      if (stalkerComp.isCharging) {
        float chargeSeconds = stalkerComp.getChargeSeconds();
        float maxSeconds = StalkerPlayerComponent.MAX_CHARGE_TIME / 20.0f;
        Component chargeText = Component.translatable("hud.noellesroles.stalker.charging",
            String.format("%.1f", chargeSeconds), String.format("%.1f", maxSeconds));
        int chargeColor = chargeSeconds >= 1.0f ? 0x00FF00 : 0xFFFF00;
        context.drawString(textRenderer, chargeText, x, y, chargeColor);
      }

      // 突进状态
      if (stalkerComp.isDashing) {
        Component dashText = Component.translatable("hud.noellesroles.stalker.dashing")
            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        context.drawString(textRenderer, dashText, x, y, 0xFFFFFF);
      }
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.MA_CHEN_XU_ID, (context, tickCounter) -> {
      // 获取马晨絮组件
      var client = Minecraft.getInstance();
      MaChenXuPlayerComponent component = MaChenXuPlayerComponent.KEY.get(client.player);

      // 检查玩家是否存活
      if (!GameUtils.isPlayerAliveAndSurvival(client.player))
        return;

      // 渲染位置 - 左下角
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int x = 10;
      int y = screenHeight - 120;

      Font textRenderer = client.font;

      // 阶段显示
      Component phaseText = Component
          .translatable("hud.noellesroles.ma_chen_xu.phase",
              Component.translatable("hud.noellesroles.ma_chen_xu.phase" + component.stage))
          .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
      context.drawString(textRenderer, phaseText, x, y, 0xFFFFFFFF);
      y += 12;

      // 累计SAN掉落
      Component sanText = Component.translatable("hud.noellesroles.ma_chen_xu.total_san_loss",
          component.totalSanLoss).withStyle(ChatFormatting.RED);
      context.drawString(textRenderer, sanText, x, y, 0xFFFFFFFF);
      y += 12;

      // 进化进度
      int nextThreshold = switch (component.stage) {
        case 1 -> component.STAGE_2_THRESHOLD;
        case 2 -> component.STAGE_3_THRESHOLD;
        case 3 -> component.STAGE_4_THRESHOLD;
        default -> -1;
      };

      if (nextThreshold > 0) {
        Component progressText = Component.translatable("hud.noellesroles.ma_chen_xu.evolution_progress",
            component.totalSanLoss, nextThreshold).withStyle(ChatFormatting.YELLOW);
        context.drawString(textRenderer, progressText, x, y, 0xFFFFFFFF);
        y += 12;
      }
      if (client.player.hasEffect(ModEffects.SKILL_BANED)) {
        Component text = Component.translatable("message.tip.cant_use_skill")
            .withStyle(ChatFormatting.RED);
        context.drawString(textRenderer, text, x, y, 0xFFFFFFFF);
        y += 12;
      }

      // 里世界状态
      if (component.otherworldActive) {
        Component liShiJieText = Component.translatable("hud.noellesroles.ma_chen_xu.li_shi_jie_active",
            component.otherworldDuration / 20).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        context.drawString(textRenderer, liShiJieText, x, y, 0xFFFFFFFF);
        y += 12;
      }

      // 浊雨状态
      if (component.turbidRainActive) {
        Component turbidRainText = Component.translatable("message.noellesroles.ma_chen_xu.turbid_rain_activated")
            .withStyle(ChatFormatting.DARK_AQUA);
        context.drawString(textRenderer, turbidRainText, x, y, 0xFFFFFFFF);
        y += 12;
      }

      // 掠风状态（阶段4里世界专属）
      if (component.swiftWindActive) {
        Component swiftWindText = Component.translatable("hud.noellesroles.ma_chen_xu.skill.swift_wind")
            .append(Component.literal(": " + component.swiftWindDuration / 20 + "s"))
            .withStyle(ChatFormatting.AQUA);
        context.drawString(textRenderer, swiftWindText, x, y, 0xFFFFFFFF);
        y += 12;
      }

      if (component.ghostSkills.size() > 0) {
        if (component.nowSelectedSkill >= 0 && component.nowSelectedSkill < component.ghostSkills.size()) {
          Component mimicryText = Component
              .translatable("message.noellesroles.ma_chen_xu.now_sel_skill",
                  Component.translatable(
                      "hud.noellesroles.ma_chen_xu.skill." + component.ghostSkills.get(component.nowSelectedSkill))
                      .withStyle(ChatFormatting.AQUA))
              .withStyle(ChatFormatting.GOLD);
          context.drawString(textRenderer, mimicryText, x, y, 0xFFFFFFFF);
          y += 12;
        }
        {
          Component mimicryText = Component
              .translatable("message.noellesroles.ma_chen_xu.tip_for_skill",
                  NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
              .withStyle(ChatFormatting.GRAY);
          context.drawString(textRenderer, mimicryText, x, y, 0xFFFFFFFF);
          y += 12;
        }

        {
          Component mimicryText = component.getNowCooldownText();
          context.drawString(textRenderer, mimicryText, x, y, 0xFFFFFFFF);
          y += 12;
        }
        // 永久护盾状态
        if (component.permanentShield) {
          Component shieldText = Component.translatable("message.noellesroles.ma_chen_xu.shield")
              .withStyle(ChatFormatting.GOLD);
          context.drawString(textRenderer, shieldText, context.guiWidth() - textRenderer.width(shieldText) - 10,
              context.guiHeight() - 20, 0xFFFFFFFF);
        }
      }
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.GLITCH_ROBOT_ID, (context, tickCounter) -> {
      Minecraft client = Minecraft.getInstance();
      Component text = null;
      int color = 0xFFFFFFFF;
      if (!client.player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES))
        return;
      text = Component.translatable("info.glitch_robot.take_off_glasses.tip",
          Component.keybind("key.noellesroles.ability"));

      int screenWidth = client.getWindow().getGuiScaledWidth();
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int textWidth = client.font.width(text);

      // 右下角显示，留出一些边距
      int x = screenWidth - textWidth - 10;
      int y = screenHeight - 20;

      context.drawString(client.font, text, x, y, color);
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.NOISEMAKER_ID, (context, tickCounter) -> {
      Minecraft client = Minecraft.getInstance();
      Component text = null;
      int color = 0xFFFFFFFF;
      NoiseMakerPlayerComponent noisemakerComponent = NoiseMakerPlayerComponent.KEY.get(client.player);
      if (client.player.getActiveEffectsMap().containsKey(MobEffects.LUCK)) {
        MobEffectInstance eff = client.player.getEffect(MobEffects.LUCK);
        int seconds = eff.getDuration() / 20;
        text = Component.translatable("gui.noellesroles.noisemaker.during", seconds);
        color = 0x00fff7; // 青蓝色
      } else if (noisemakerComponent.cooldown > 0) {
        int seconds = (noisemakerComponent.cooldown) / 20;
        text = Component.translatable("gui.noellesroles.noisemaker.cooldown", seconds);
        color = 0xFF5555; // 红色
      } else {
        text = Component.translatable("gui.noellesroles.noisemaker.ready");
        color = 0x55FF55; // 绿色
      }

      int screenWidth = context.guiWidth();
      int screenHeight = context.guiHeight();
      int textWidth = client.font.width(text);

      // 右下角显示，留出一些边距
      int x = screenWidth - textWidth - 10;
      int y = screenHeight - 20;

      context.drawString(client.font, text, x, y, color);
    });

    RoleHudRenderCallback.EVENT.register(ModRoles.GHOST_ID, (context, tickCounter) -> {
      Minecraft client = Minecraft.getInstance();
      Component text = null;
      int color = 0xFFFFFFFF;
      if (client.player.hasEffect(ModEffects.SKILL_BANED))
        return;
      GhostPlayerComponent ghostComponent = GhostPlayerComponent.KEY.get(client.player);
      if (!ghostComponent.abilityUnlocked) {
        text = Component.translatable("gui.noellesroles.ghost.locked");
        color = 0xFFFF00; // 黄色
      } else if (ghostComponent.invisibilityTicks > 0) {
        int seconds = (ghostComponent.invisibilityTicks) / 20;
        text = Component.translatable("gui.noellesroles.ghost.during", seconds);
        color = 0x00fff7; // 青蓝色
      } else if (ghostComponent.cooldown > 0) {
        int seconds = (ghostComponent.cooldown) / 20;
        text = Component.translatable("gui.noellesroles.ghost.cooldown", seconds);
        color = 0xFF5555; // 红色
      } else {
        text = Component.translatable("gui.noellesroles.ghost.unlocked");
        color = 0x55FF55; // 绿色
      }
      int screenWidth = client.getWindow().getGuiScaledWidth();
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int textWidth = client.font.width(text);

      // 右下角显示，留出一些边距
      int x = screenWidth - textWidth - 10;
      int y = screenHeight - 20;

      context.drawString(client.font, text, x, y, color);
    });

    RoleHudRenderCallback.EVENT.register(ModRoles.CANDLE_BEARER_ID, (context, tickCounter) -> {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
        return;
      }
      CandleBearerPlayerComponent component = CandleBearerPlayerComponent.KEY.get(client.player);

      int screenWidth = client.getWindow().getGuiScaledWidth();
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int x = screenWidth - 10;
      int y = screenHeight - 20;
      Font font = client.font;

      Component progressText = Component.translatable("gui.noellesroles.candlebearer.progress",
          component.successfulCandles,
          component.requiredCandles).withStyle(ChatFormatting.GOLD);
      context.drawString(font, progressText, x - font.width(progressText), y - font.lineHeight * 4 - 10,
          Color.WHITE.getRGB());

      Component chargeText = Component.translatable("gui.noellesroles.candlebearer.charges",
          component.invisibilityCharges,
          CandleBearerPlayerComponent.MAX_INVISIBILITY_CHARGES).withStyle(ChatFormatting.YELLOW);
      context.drawString(font, chargeText, x - font.width(chargeText), y - font.lineHeight * 3 - 8,
          Color.WHITE.getRGB());

      Component livingCandleText;
      int livingCandleColor;
      if (component.livingCandleCooldownTicks > 0) {
        livingCandleText = Component.translatable("gui.noellesroles.candlebearer.living_cooldown",
            (component.livingCandleCooldownTicks + 19) / 20);
        livingCandleColor = 0xFFAA00;
      } else {
        livingCandleText = Component.translatable("gui.noellesroles.candlebearer.living_ready");
        livingCandleColor = 0x55FF55;
      }
      context.drawString(font, livingCandleText, x - font.width(livingCandleText), y - font.lineHeight * 2 - 6,
          livingCandleColor);

      Component stateText;
      int stateColor;
      if (component.invisibilityTicks > 0) {
        stateText = Component.translatable("gui.noellesroles.candlebearer.invisible",
            component.invisibilityTicks / 20);
        stateColor = 0x00fff7;
      } else {
        stateText = Component.translatable("gui.noellesroles.candlebearer.ready",
            NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
        stateColor = 0x55FF55;
      }
      context.drawString(font, stateText, x - font.width(stateText), y - font.lineHeight - 4, stateColor);
    });

    CommanderHudRender.register();
    WayfarerHudRenderer.registerRendererEvent();
    CuckooHud.register();
    RoleHudRenderCallback.EVENT.register(ModRoles.RECORDER.identifier(), (guiGraphics, deltaTracker) -> {
      // 记录员
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = RecorderPlayerComponent.KEY.get(client.player);
      // hud.noellesroles.recorder.process
      Component text = Component
          .translatable("hud.noellesroles.recorder.requirement",
              abpc.requiredCorrectCount)
          .withStyle(ChatFormatting.GOLD);
      Component text2 = Component
          .translatable("hud.noellesroles.recorder.process",
              abpc.getCorrectGuesses(), abpc.requiredCorrectCount)
          .withStyle(ChatFormatting.YELLOW);
      guiGraphics.drawString(font, text2, xOffset - font.width(text2), yOffset - font.lineHeight - 4,
          Color.WHITE.getRGB());
      guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight * 2 - 8,
          Color.WHITE.getRGB());
      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.CLOCKMAKER_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = ClockmakerPlayerComponent.KEY.get(client.player);
      Component text = Component
          .translatable("hud.noellesroles.clockmaker.use",
              NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
          .withStyle(ChatFormatting.GOLD);
      if (abpc.isUsingSkill) {
        text = Component.translatable("hud.noellesroles.clockmaker.already_using")
            .withStyle(ChatFormatting.DARK_AQUA);
      }
      // 按下技能键可花费125金币，减少游戏时间45秒并使世界时间加快2000tick。
      guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
          Color.WHITE.getRGB());
      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.CLEANER_ID, (guiGraphics, deltaTracker) -> {
      // 渲染清道夫的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = SREAbilityPlayerComponent.KEY.get(client.player);
      if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.cleaner.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        var text = Component
            .translatable("hud.cleaner.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      return;
    });
    // 忍者 HUD（模仿幽灵 Phantom）
    RoleHudRenderCallback.EVENT.register(ModRoles.NINJA_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client.player == null)
        return;

      NinjaPlayerComponent ninjaComp = NinjaPlayerComponent.KEY.get(client.player);
      if (ninjaComp == null)
        return;

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int x = screenWidth - 10;
      int y = screenHeight - 20;

      Component text = null;
      int color = 0xFFFFFFFF;

      // 技能激活中（格挡中）
      if (ninjaComp.isAbilityActive()) {
        int seconds = (int) Math.ceil(ninjaComp.getDurationSeconds());
        text = Component.translatable("hud.noellesroles.ninja.active", seconds);
        color = 0xFF5555;
      }
      // 技能冷却中
      else if (ninjaComp.isOnCooldown()) {
        int seconds = (int) Math.ceil(ninjaComp.getCooldownSeconds());
        text = Component.translatable("hud.noellesroles.ninja.cooldown", seconds);
        color = 0xFF5555; // 红色
      }
      // 技能可用
      else {
        text = Component.translatable("hud.noellesroles.ninja.ready");
        color = 0xFF5555;
      }

      // 计算文字宽度（右对齐）
      int textWidth = font.width(text);
      guiGraphics.drawString(font, text, x - textWidth, y, color);
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.JOJO_ID, (guiGraphics, deltaTracker) -> {
      // 渲染JOJO的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = SREAbilityPlayerComponent.KEY.get(client.player);
      if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.jojo.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        var THE_WORLD = Component.translatable("hud.noellesroles.jojo.the_world").withStyle(ChatFormatting.GOLD,
            ChatFormatting.BOLD);
        var text = Component
            .translatable("hud.jojo.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage(),
                THE_WORLD)
            .withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      return;
    });
    RoleHudRenderCallback.EVENT.register(RedHouseRoles.MAID_SAKUYA_ID, (guiGraphics, deltaTracker) -> {
      // 渲染SAKUYA的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = SREAbilityPlayerComponent.KEY.get(client.player);
      if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.maid_sakuya.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        var text = Component
            .translatable("hud.maid_sakuya.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      return;
    });

    RoleHudRenderCallback.EVENT.register(ModRoles.ATHLETE_ID, (guiGraphics, deltaTracker) -> {
      // 渲染运动员的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = AthletePlayerComponent.KEY.get(client.player);
      if (abpc.speedTicks > 0) {
        var text = Component
            .translatable("hud.noellesroles.athlete.active",
                abpc.speedTicks / 20)
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.noellesroles.athlete.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        var text = Component
            .translatable("hud.noellesroles.athlete.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      return;
    });

    RoleHudRenderCallback.EVENT.register(ModRoles.LOCKSMITH_ID, (guiGraphics, deltaTracker) -> {
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.player == null)
        return;
      if (!GameUtils.isPlayerAliveAndSurvival(client.player))
        return;

      LocksmithInspirationComponent component = ModComponents.LOCKSMITH_INSPIRATION.get(client.player);
      Component text = Component.translatable("hud.noellesroles.locksmith.inspiration",
          component.getInspirationPoints(), LocksmithInspirationComponent.MAX_POINTS)
          .withStyle(ChatFormatting.GOLD);

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      int x = screenWidth - client.font.width(text) - 10;
      int y = screenHeight - client.font.lineHeight - 10;
      guiGraphics.drawString(client.font, text, x, y, Color.WHITE.getRGB());
    });

    RoleHudRenderCallback.EVENT.register(ModRoles.WATCHER_ID, (guiGraphics, deltaTracker) -> {
      // 渲染watcher的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int xOffset = screenWidth - 10; // 右下角
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      var abpc = SREArmorPlayerComponent.KEY.get(client.player);
      var wtpc = WatcherPlayerComponent.KEY.get(client.player);
      {
        var text = Component
            .translatable("hud.bartender.has_armor",
                abpc.armor)
            .withStyle(ChatFormatting.GOLD);
        guiGraphics.drawString(font, text, 10, yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      {
        if (wtpc.getCooldown() > 0) {
          var text = Component
              .translatable("message.noellesroles.detective.on_cooldown", wtpc.getCooldown() / 20)
              .withStyle(ChatFormatting.AQUA);
          guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight * 2 - 8,
              Color.WHITE.getRGB());
        }
        {
          var text = Component
              .translatable("hud.noellesroles.watcher.stance_angry", wtpc.getCooldown() / 20)
              .withStyle(ChatFormatting.YELLOW);
          if (wtpc.isInCalmStance()) {
            text = Component
                .translatable("hud.noellesroles.watcher.stance_calm", wtpc.getCooldown() / 20)
                .withStyle(ChatFormatting.GREEN);
          }
          guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
              Color.WHITE.getRGB());
        }
      }
      return;
    });

    RoleHudRenderCallback.EVENT.register(RedHouseRoles.HOAN_MEIRIN_ID, (guiGraphics, deltaTracker) -> {
      // 渲染红美铃的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = HoanMeirinPlayerComponent.KEY.get(client.player);
      var shpc = SREArmorPlayerComponent.KEY.get(client.player);
      {
        var text = Component
            .translatable("hud.hoan_meirin.armor",
                shpc.armor)
            .withStyle(ChatFormatting.GOLD);
        guiGraphics.drawString(font, text, 10, yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      if (abpc.loneyTime > 5 * 20) {
        // 孤独值
        var text1 = Component
            .translatable("hud.hoan_meirin.lonely_value",
                Component.literal(String.format("%ds", (60 - abpc.loneyTime / 20)))
                    .withStyle(ChatFormatting.RED))
            .withStyle(ChatFormatting.YELLOW);
        guiGraphics.drawString(font, text1, xOffset - font.width(text1), yOffset - font.lineHeight * 3 - 12,
            Color.WHITE.getRGB());

        var text2 = Component
            .translatable("hud.hoan_meirin.lonely_tip")
            .withStyle(ChatFormatting.GOLD);
        guiGraphics.drawString(font, text2, xOffset - font.width(text2), yOffset - font.lineHeight * 2 - 8,
            Color.WHITE.getRGB());
      }
      if (client.player.hasEffect(MobEffects.LEVITATION)) {
        var text = Component
            .translatable("hud.hoan_meirin.ready_stop",
                NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.hoan_meirin.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        var text = Component
            .translatable("hud.hoan_meirin.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.GREEN);

        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.WIND_YAOSE_ID, (guiGraphics, deltaTracker) -> {
      // 渲染风精灵的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = SREAbilityPlayerComponent.KEY.get(client.player);
      if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.wind_yaose.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        var text = Component
            .translatable("hud.wind_yaose.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.EXAMPLER_ID, (guiGraphics, deltaTracker) -> {
      // 渲染小镇做题家的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = SREAbilityPlayerComponent.KEY.get(client.player);
      var psc = SREPlayerShopComponent.KEY.get(client.player);
      if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.exampler.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      } else {
        if (psc.balance < 100) {
          var text = Component
              .translatable("hud.exampler.money")
              .withStyle(ChatFormatting.YELLOW);
          guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
              Color.WHITE.getRGB());
        } else {
          var allneiJuanSkill = Component
              .translatable("hud.exampler.all_neijuan",
                  NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
              .withStyle(ChatFormatting.GOLD);
          guiGraphics.drawString(font, allneiJuanSkill, xOffset - font.width(allneiJuanSkill),
              yOffset - font.lineHeight * 3 - 12,
              Color.WHITE.getRGB());
          var text = Component
              .translatable("hud.exampler.ready")
              .withStyle(ChatFormatting.AQUA);
          guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
              Color.WHITE.getRGB());
        }

      }
      var chargeText = Component
          .translatable("hud.exampler.charges", abpc.charges)
          .withStyle(ChatFormatting.GOLD);
      guiGraphics.drawString(font, chargeText, xOffset - font.width(chargeText),
          yOffset - font.lineHeight * 2 - 8,
          Color.WHITE.getRGB());
      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.FORTUNETELLER_ID, (guiGraphics, deltaTracker) -> {
      // 渲染算命大师的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = SREAbilityPlayerComponent.KEY.get(client.player);
      var fpc = FortunetellerPlayerComponent.KEY.get(client.player);
      if (!fpc.protectedPlayers.isEmpty()) {
        int dy = yOffset - font.lineHeight * 2 - 12;
        for (var po : fpc.protectedPlayers) {
          var pl = client.level.getPlayerByUUID(po.player);
          if (pl == null)
            continue;
          var text = Component
              .translatable("hud.fortuneteller.protecting_line",
                  Component.literal(pl.getName().getString()).withStyle(ChatFormatting.GREEN),
                  Component.literal((po.time / 20) + "s").withStyle(ChatFormatting.YELLOW))
              .withStyle(ChatFormatting.GOLD);
          guiGraphics.drawString(font, text, xOffset - font.width(text), dy,
              Color.WHITE.getRGB());
          dy = dy - 2 - font.lineHeight;
        }
        var text = Component
            .translatable("hud.fortuneteller.protecting_above")
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset - font.lineHeight - 4,
            Color.WHITE.getRGB());
      }
      if (abpc.cooldown > 0) {
        var text = Component
            .translatable("hud.fortuneteller.cooldown", abpc.cooldown / 20)
            .withStyle(ChatFormatting.YELLOW);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
      } else {
        var text = Component
            .translatable("hud.fortuneteller.ready",
                NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.YELLOW);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
      }

      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.OLDMAN_ID, (guiGraphics, deltaTracker) -> {
      // 渲染老人的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      if (client.player.getVehicle() != null && client.player.getVehicle() instanceof WheelchairEntity) {
        var text = Component
            .translatable("hud.oldman.get_back", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.AQUA);
        guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
      }

      return;
    });
    RoleHudRenderCallback.EVENT.register(ModRoles.BARTENDER_ID, (guiGraphics, deltaTracker) -> {
      // 渲染酒保的提示
      var client = Minecraft.getInstance();

      var comc = SREArmorPlayerComponent.KEY.maybeGet(client.player).orElse(null);
      if (comc == null)
        return;
      if (comc.getArmor() <= 0)
        return;
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var text = Component.translatable("hud.bartender.has_armor", comc.getArmor())
          .withStyle(ChatFormatting.GOLD);
      guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
      return;

    });
    RoleHudRenderCallback.EVENT.register(ModRoles.ATTENDANT_ID, (guiGraphics, deltaTracker) -> {
      Minecraft client = Minecraft.getInstance();
      var comc = SREAbilityPlayerComponent.KEY.maybeGet(client.player).orElse(null);
      if (comc == null)
        return;
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var text = Component.literal("");
      if (comc.cooldown <= 0) {
        text.append(Component.translatable("hud.noellesroles.attendant.available",
            Component.keybind("key.noellesroles.ability"), AttendantHandler.area_distance)
            .withStyle(ChatFormatting.GOLD));
      } else {
        text.append(Component.translatable("hud.noellesroles.attendant.cooldown", (comc.cooldown / 20))
            .withStyle(ChatFormatting.RED));
      }

      guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset, Color.WHITE.getRGB());
      return;
    });

    // 小偷HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.THIEF_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘

      var thiefComponent = ThiefPlayerComponent.KEY.maybeGet(client.player).orElse(null);
      if (thiefComponent == null)
        return;

      // 显示当前模式
      Component progress = Component.literal("");
      var shopC = SREPlayerShopComponent.KEY.get(client.player);
      progress = Component.translatable("message.thief.honor_cost", shopC.balance, thiefComponent.honorCost)
          .withStyle(ChatFormatting.GOLD);
      Component modeText;
      if (thiefComponent.currentMode == ThiefPlayerComponent.MODE_STEAL_MONEY) {
        modeText = Component.translatable("hud.thief.mode.money").withStyle(ChatFormatting.GOLD);
      } else if (thiefComponent.currentMode == ThiefPlayerComponent.MODE_STEAL_ITEM) {
        modeText = Component.translatable("hud.thief.mode.item").withStyle(ChatFormatting.AQUA);
      } else {
        modeText = Component.translatable("hud.thief.mode.sell").withStyle(ChatFormatting.LIGHT_PURPLE);
      }

      // 显示冷却或就绪状态
      int dy = yOffset - font.lineHeight - 4;
      if (client.player.hasEffect(ModEffects.SAFE_TIME)) {
        var cdText = Component.translatable("hud.noellesroles.safe_time", thiefComponent.cooldown / 20)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, cdText, xOffset - font.width(cdText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight;
      } else if (thiefComponent.cooldown > 0) {
        var cdText = Component.translatable("hud.thief.cooldown", thiefComponent.cooldown / 20)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, cdText, xOffset - font.width(cdText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight;
      } else {
        var readyText = Component
            .translatable("hud.thief.ready", NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
            .withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, readyText, xOffset - font.width(readyText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight;
      }

      // 显示模式信息
      var modeInfo = Component.translatable("hud.thief.current_mode").withStyle(ChatFormatting.WHITE);
      guiGraphics.drawString(font, modeInfo, xOffset - font.width(modeInfo) - font.width(modeText), dy,
          Color.WHITE.getRGB());
      guiGraphics.drawString(font, modeText, xOffset - font.width(modeText), dy, Color.WHITE.getRGB());

      dy -= font.lineHeight + 8;

      guiGraphics.drawString(font, progress, xOffset - font.width(progress), dy, Color.WHITE.getRGB());
    });

    // 雇佣兵HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.MERCENARY_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client == null || client.player == null || SREClient.gameComponent == null) {
        return;
      }
      if (!SREClient.gameComponent.isRole(client.player, ModRoles.MERCENARY)) {
        return;
      }

      var mercenary = MercenaryPlayerComponent.KEY.maybeGet(client.player).orElse(null);
      if (mercenary == null) {
        return;
      }

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      Font font = client.font;
      int xOffset = screenWidth - 10;
      int yOffset = screenHeight - 10 - font.lineHeight;
      int dy = yOffset;

      int armor = SREArmorPlayerComponent.KEY.get(client.player).getArmor();
      var armorText = Component.translatable("hud.mercenary.shields", armor).withStyle(ChatFormatting.AQUA);
      guiGraphics.drawString(font, armorText, xOffset - font.width(armorText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      Component progress = Component.translatable(
          "hud.mercenary.progress",
          mercenary.contractKillCount,
          mercenary.requiredContractKills).withStyle(ChatFormatting.GOLD);
      guiGraphics.drawString(font, progress, xOffset - font.width(progress), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 6;

      if (mercenary.contractActive && mercenary.contractTargetUuid != null) {
        UUID targetUuid = mercenary.contractTargetUuid;
        var target = client.level == null ? null : client.level.getPlayerByUUID(targetUuid);
        var targetLabel = Component.translatable("hud.mercenary.target", mercenary.contractTargetName)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, targetLabel, xOffset - font.width(targetLabel), dy, Color.WHITE.getRGB());

        int iconSize = 18;
        int iconX = xOffset - font.width(targetLabel) - iconSize - 6;
        int iconY = dy - 1;
        if (target instanceof net.minecraft.client.player.AbstractClientPlayer clientTarget) {
          guiGraphics.drawPlayerFace(clientTarget.getSkin().texture(), iconX, iconY, iconSize);
        }
        dy -= font.lineHeight + 4;
      } else {
        Component idle = Component.translatable("hud.mercenary.idle").withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, idle, xOffset - font.width(idle), dy, Color.WHITE.getRGB());
      }
    });

    // 仇杀客HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.BLOOD_FEUDIST_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘

      BloodFeudistPlayerComponent bfComponent = ModComponents.BLOOD_FEUDIST.maybeGet(client.player).orElse(null);
      if (bfComponent == null)
        return;

      int dy = yOffset;

      // 显示误杀人数
      var killText = Component
          .translatable("hud.blood_feudist.accidental_kills", bfComponent.getAccidentalKillCount())
          .withStyle(ChatFormatting.RED);
      guiGraphics.drawString(font, killText, xOffset - font.width(killText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 2;

      // 显示速度状态
      if (bfComponent.hasSpeed1() || bfComponent.hasSpeed2()) {
        Component speedLabel = bfComponent.hasSpeed2() ? Component.translatable("hud.blood_feudist.speed2")
            : Component.translatable("hud.blood_feudist.speed1");
        Component speedStatus = bfComponent.isSpeedEnabled()
            ? Component.translatable("hud.blood_feudist.enabled").withStyle(ChatFormatting.GREEN)
            : Component.translatable("hud.blood_feudist.disabled").withStyle(ChatFormatting.GRAY);
        Component speedText = Component.literal("").append(speedLabel).append(speedStatus);
        guiGraphics.drawString(font, speedText, xOffset - font.width(speedText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight + 2;
      }

      // 显示急迫状态
      if (bfComponent.hasHaste2()) {
        Component hasteLabel = Component.translatable("hud.blood_feudist.haste2");
        Component hasteStatus = bfComponent.isHasteEnabled()
            ? Component.translatable("hud.blood_feudist.enabled").withStyle(ChatFormatting.GREEN)
            : Component.translatable("hud.blood_feudist.disabled").withStyle(ChatFormatting.GRAY);
        Component hasteText = Component.literal("").append(hasteLabel).append(hasteStatus);
        guiGraphics.drawString(font, hasteText, xOffset - font.width(hasteText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight + 2;
      }

      // 显示技能提示
      var readyText = Component
          .translatable("hud.blood_feudist.toggle_effects",
              NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
          .withStyle(ChatFormatting.YELLOW);
      guiGraphics.drawString(font, readyText, xOffset - font.width(readyText), dy, Color.WHITE.getRGB());
    });

    // 会计HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.ACCOUNTANT_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘

      var accountantComponent = AccountantPlayerComponent.KEY
          .maybeGet(client.player).orElse(null);
      if (accountantComponent == null)
        return;
      int dy = yOffset;

      // 显示当前模式
      Component modeText;
      if (accountantComponent
          .getCurrentMode() == AccountantPlayerComponent.MODE_INCOME) {
        modeText = Component.translatable("hud.accountant.mode.income").withStyle(ChatFormatting.GOLD);
      } else {
        modeText = Component.translatable("hud.accountant.mode.expense").withStyle(ChatFormatting.AQUA);
      }

      var modeInfo = Component.translatable("hud.accountant.current_mode").withStyle(ChatFormatting.WHITE);
      guiGraphics.drawString(font, modeInfo, xOffset - font.width(modeInfo) - font.width(modeText), dy,
          Color.WHITE.getRGB());
      guiGraphics.drawString(font, modeText, xOffset - font.width(modeText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示技能提示
      Component skillText = Component.translatable("hud.accountant.skill_cost", 175)
          .withStyle(ChatFormatting.GOLD);
      guiGraphics.drawString(font, skillText, xOffset - font.width(skillText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示被动收入倒计时
      int remainingSeconds = accountantComponent.getPassiveIncomeRemainingSeconds();
      Component incomeText = Component.translatable("hud.accountant.passive_income", remainingSeconds)
          .withStyle(ChatFormatting.YELLOW);
      guiGraphics.drawString(font, incomeText, xOffset - font.width(incomeText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示切换模式提示
      var toggleText = Component
          .translatable("hud.accountant.toggle_mode",
              NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
          .withStyle(ChatFormatting.GRAY);
      guiGraphics.drawString(font, toggleText, xOffset - font.width(toggleText), dy, Color.WHITE.getRGB());
    });

    // 药剂师HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.ALCHEMIST_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client == null)
        return;
      if (client.player == null)
        return;
      if (SREClient.gameComponent == null
          || !SREClient.gameComponent.isRole(client.player, ModRoles.ALCHEMIST)) {
        return;
      }

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘

      var alchemistComponent = AlchemistPlayerComponent.KEY
          .maybeGet(client.player).orElse(null);
      if (alchemistComponent == null)
        return;

      int dy = yOffset;

      // 显示当前选择的药剂
      int currentPotionIndex = alchemistComponent.getCurrentPotionIndex();
      Component potionName = Component.translatable("potion.noellesroles."
          + AlchemistPlayerComponent.getPotionKey(currentPotionIndex));
      Component potionLabel = Component.translatable("hud.alchemist.current_potion")
          .withStyle(ChatFormatting.WHITE);
      guiGraphics.drawString(font, potionLabel, xOffset - font.width(potionLabel) - font.width(potionName), dy,
          Color.WHITE.getRGB());
      guiGraphics.drawString(font, potionName, xOffset - font.width(potionName), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示调制花费
      int goldCost = AlchemistPlayerComponent.getPotionCost(currentPotionIndex);
      Component costText = Component.translatable("hud.alchemist.craft_cost", goldCost,
          AlchemistPlayerComponent.MATERIALS_TO_CRAFT)
          .withStyle(ChatFormatting.GOLD);
      guiGraphics.drawString(font, costText, xOffset - font.width(costText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示当前药剂的调制次数
      int craftCount = alchemistComponent.getCurrentPotionCraftCount();
      int maxCraftCount = AlchemistPlayerComponent.MAX_CRAFT_COUNT;
      Component countText = Component.translatable("hud.alchemist.craft_count", craftCount, maxCraftCount)
          .withStyle(ChatFormatting.LIGHT_PURPLE);
      guiGraphics.drawString(font, countText, xOffset - font.width(countText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示蹲下获取素材倒计时
      if (client.player.isShiftKeyDown()) {
        int remainingSeconds = alchemistComponent.getMaterialGatherRemainingSeconds();
        Component gatherText = Component.translatable("hud.alchemist.gather_countdown", remainingSeconds)
            .withStyle(ChatFormatting.YELLOW);
        guiGraphics.drawString(font, gatherText, xOffset - font.width(gatherText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight + 4;
      }

      // 显示切换药剂提示
      var toggleText = Component
          .translatable("hud.alchemist.switch_potion",
              NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
          .withStyle(ChatFormatting.GRAY);
      guiGraphics.drawString(font, toggleText, xOffset - font.width(toggleText), dy, Color.WHITE.getRGB());
    });

    // 潜水员HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.DIVER_ID, (context, tickCounter) -> {
      var client = Minecraft.getInstance();
      if (!GameUtils.isPlayerAliveAndSurvival(client.player))
        return;

      // 渲染位置 - 右下角
      int screenWidth = client.getWindow().getGuiScaledWidth();
      int screenHeight = client.getWindow().getGuiScaledHeight();
      int x = screenWidth - 10;
      int y = screenHeight - 30;

      Font font = client.font;

      // 显示装备脱除提示
      var abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage();
      var tipText = Component
          .translatable("hud.diver.remove_equipment_tip", abilityKey)
          .withStyle(ChatFormatting.AQUA);
      context.drawString(font, tipText, x - font.width(tipText), y, 0xFFFFFFFF);
    });

    // 苦力怕HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.CREEPER_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client == null || client.player == null || SREClient.gameComponent == null) {
        return;
      }
      if (!SREClient.gameComponent.isRole(client.player, ModRoles.CREEPER)) {
        return;
      }

      var creeperComponent = ModComponents.CREEPER.maybeGet(client.player).orElse(null);
      if (creeperComponent == null) {
        return;
      }

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘

      if (creeperComponent.ignited) {
        // 显示倒计时
        int secondsLeft = creeperComponent.igniteTimeLeft / 20;
        var countdownText = Component.translatable("hud.creeper.countdown", secondsLeft)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, countdownText, xOffset - font.width(countdownText), yOffset, Color.WHITE.getRGB());
      } else {
        // 显示技能提示
        var abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage();
        var igniteText = Component.translatable("hud.creeper.ignite", abilityKey)
            .withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, igniteText, xOffset - font.width(igniteText), yOffset, Color.WHITE.getRGB());
      }
    });

    // 影隼HUD
    RoleHudRenderCallback.EVENT.register(ModRoles.SHADOW_FALCON_ID, (guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client == null || client.player == null || SREClient.gameComponent == null) {
        return;
      }
      if (!SREClient.gameComponent.isRole(client.player, ModRoles.SHADOW_FALCON)) {
        return;
      }

      var shadowFalconComponent = ShadowFalconPlayerComponent.KEY.maybeGet(client.player).orElse(null);
      if (shadowFalconComponent == null) {
        return;
      }

      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      int dy = yOffset;

      // 显示护盾层数（酒保风格）
      int shieldLayers = shadowFalconComponent.temporaryShield;
      var shieldText = Component.translatable("hud.noellesroles.shadow_falcon.shield", shieldLayers)
          .withStyle(ChatFormatting.AQUA);
      guiGraphics.drawString(font, shieldText, xOffset - font.width(shieldText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示技能状态
      if (shadowFalconComponent.isPredationActive) {
        // 技能激活中
        int secondsLeft = shadowFalconComponent.skillTicks / 20;
        var activeText = Component.translatable("hud.noellesroles.shadow_falcon.active", secondsLeft)
            .withStyle(ChatFormatting.GOLD);
        guiGraphics.drawString(font, activeText, xOffset - font.width(activeText), dy, Color.WHITE.getRGB());
        dy -= font.lineHeight + 4;

        // 显示护盾状态提示
        if (shadowFalconComponent.temporaryShield > 0) {
          var shieldStatusText = Component.translatable("hud.noellesroles.shadow_falcon.shield_active")
              .withStyle(ChatFormatting.GREEN);
          guiGraphics.drawString(font, shieldStatusText, xOffset - font.width(shieldStatusText), dy, Color.WHITE.getRGB());
        }
      } else if (shadowFalconComponent.cooldown > 0) {
        // 冷却中
        int secondsLeft = shadowFalconComponent.cooldown / 20;
        var cooldownText = Component.translatable("hud.noellesroles.shadow_falcon.cooldown", secondsLeft)
            .withStyle(ChatFormatting.RED);
        guiGraphics.drawString(font, cooldownText, xOffset - font.width(cooldownText), dy, Color.WHITE.getRGB());
      } else {
        // 技能就绪
        var abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage();
        var readyText = Component.translatable("hud.noellesroles.shadow_falcon.ready", abilityKey)
            .withStyle(ChatFormatting.GREEN);
        guiGraphics.drawString(font, readyText, xOffset - font.width(readyText), dy, Color.WHITE.getRGB());
      }
    });
  }
}
