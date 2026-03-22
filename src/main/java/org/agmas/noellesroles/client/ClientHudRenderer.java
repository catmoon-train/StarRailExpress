package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.AttendantHandler;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.commander.CommanderHudRender;
import org.agmas.noellesroles.roles.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.roles.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;

import java.awt.*;

public class ClientHudRenderer {
  public static void registerFather() {
    HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client == null)
        return;
      if (client.player == null)
        return;
      if (SREClient.gameComponent == null) {
        return;
      }
      if (!SREClient.gameComponent.isRunning())
        return;
      if (!client.player.hasEffect(ModEffects.NO_COLLIDE))
        return;
      var effect = client.player.getEffect(ModEffects.NO_COLLIDE);
      Component message = Component.translatable("hud.noellesroles.safe_time", effect.getDuration() / 20)
          .withStyle(ChatFormatting.GREEN);
      guiGraphics.drawCenteredString(client.font, message, guiGraphics.guiWidth() / 2, 40,
          java.awt.Color.WHITE.getRGB());
    });
    HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
      var client = Minecraft.getInstance();
      if (client == null)
        return;
      if (client.player == null)
        return;
      if (SREClient.gameComponent == null) {
        return;
      }
      SRERole role = SREClient.gameComponent.getRole(client.player);
      if (role == null)
        return;
      var consumer = RoleHudRenderCallback.EVENT.getConsumer(role.identifier());
      if (consumer != null) {
        try {
          consumer.forEach((c) -> c.accept(guiGraphics, deltaTracker));
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
  }

  public static void registerSons() {
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
      int screenWidth = client.getWindow().getGuiScaledWidth();
      int screenHeight = client.getWindow().getGuiScaledHeight();
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
      GhostPlayerComponent ghostComponent = GhostPlayerComponent.KEY.get(client.player);
      if (!ghostComponent.abilityUnlocked) {
        // 计算解锁剩余时间
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(client.level);
        if (gameTime != null) {
          long remainingTicks = gameTime.getTime();
          if (remainingTicks > GhostPlayerComponent.UNLOCK_REMAINING_TICKS) {
            // 剩余时间 > 3分钟，显示还需要多少时间到解锁
            long ticksUntilUnlock = remainingTicks - GhostPlayerComponent.UNLOCK_REMAINING_TICKS;
            int secondsUntilUnlock = ((int) ticksUntilUnlock + 19) / 20;
            text = Component.translatable("gui.noellesroles.ghost.not_unlocked", secondsUntilUnlock);
            color = 0xFFFF00; // 黄色
          } else {
            // 已到解锁时间但还没解锁，显示准备解锁
            text = Component.translatable("gui.noellesroles.ghost.unlocking");
            color = 0xFFAA00; // 橙色
          }
        } else {
          // 如果无法获取游戏时间，显示等待状态
          text = Component.translatable("gui.noellesroles.ghost.waiting");
          color = 0xFFAA00; // 橙色
        }
      } else if (ghostComponent.invisibilityTicks > 0) {
        int seconds = (ghostComponent.invisibilityTicks) / 20;
        text = Component.translatable("gui.noellesroles.ghost.during", seconds);
        color = 0x00fff7; // 青蓝色
      } else if (ghostComponent.cooldown > 0) {
        int seconds = (ghostComponent.cooldown) / 20;
        text = Component.translatable("gui.noellesroles.ghost.cooldown", seconds);
        color = 0xFF5555; // 红色
      } else {
        text = Component.translatable("gui.noellesroles.ghost.ready");
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

    CommanderHudRender.register();
    WayfarerHudRenderer.registerRendererEvent();
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
    RoleHudRenderCallback.EVENT.register(ModRoles.MAID_SAKUYA_ID, (guiGraphics, deltaTracker) -> {
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
    RoleHudRenderCallback.EVENT.register(ModRoles.HOAN_MEIRIN_ID, (guiGraphics, deltaTracker) -> {
      // 渲染红美铃的提示
      var client = Minecraft.getInstance();
      int screenWidth = guiGraphics.guiWidth();
      int screenHeight = guiGraphics.guiHeight();
      var font = client.font;
      int yOffset = screenHeight - 10 - font.lineHeight; // 右下角
      int xOffset = screenWidth - 10; // 距离右边缘
      var abpc = HoanMeirinPlayerComponent.KEY.get(client.player);
      {
        var text = Component
            .translatable("hud.hoan_meirin.armor",
                abpc.armor)
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
                  Component.literal(pl.getDisplayName().getString()).withStyle(ChatFormatting.GREEN),
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
      if (thiefComponent.cooldown > 0) {
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

      var accountantComponent = org.agmas.noellesroles.component.AccountantPlayerComponent.KEY
          .maybeGet(client.player).orElse(null);
      if (accountantComponent == null)
        return;
      int dy = yOffset;

      // 显示当前模式
      Component modeText;
      if (accountantComponent
          .getCurrentMode() == org.agmas.noellesroles.component.AccountantPlayerComponent.MODE_INCOME) {
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

      var alchemistComponent = org.agmas.noellesroles.component.AlchemistPlayerComponent.KEY
          .maybeGet(client.player).orElse(null);
      if (alchemistComponent == null)
        return;

      int dy = yOffset;

      // 显示当前选择的药剂
      int currentPotionIndex = alchemistComponent.getCurrentPotionIndex();
      Component potionName = Component.translatable("potion.noellesroles."
          + org.agmas.noellesroles.component.AlchemistPlayerComponent.getPotionKey(currentPotionIndex));
      Component potionLabel = Component.translatable("hud.alchemist.current_potion")
          .withStyle(ChatFormatting.WHITE);
      guiGraphics.drawString(font, potionLabel, xOffset - font.width(potionLabel) - font.width(potionName), dy,
          Color.WHITE.getRGB());
      guiGraphics.drawString(font, potionName, xOffset - font.width(potionName), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示调制花费
      int goldCost = org.agmas.noellesroles.component.AlchemistPlayerComponent.getPotionCost(currentPotionIndex);
      Component costText = Component.translatable("hud.alchemist.craft_cost", goldCost,
          org.agmas.noellesroles.component.AlchemistPlayerComponent.MATERIALS_TO_CRAFT)
          .withStyle(ChatFormatting.GOLD);
      guiGraphics.drawString(font, costText, xOffset - font.width(costText), dy, Color.WHITE.getRGB());
      dy -= font.lineHeight + 4;

      // 显示当前药剂的调制次数
      int craftCount = alchemistComponent.getCurrentPotionCraftCount();
      int maxCraftCount = org.agmas.noellesroles.component.AlchemistPlayerComponent.MAX_CRAFT_COUNT;
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
  }
}
