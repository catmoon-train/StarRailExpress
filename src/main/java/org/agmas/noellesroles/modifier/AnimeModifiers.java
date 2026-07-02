package org.agmas.noellesroles.modifier;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.EggModifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.content.effects.TimeStopEffect;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;

import java.awt.Color;
import java.util.ArrayList;

import net.exmo.sre.subtitle.SubtitleS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class AnimeModifiers {
    public static final String NAMESPACE = "anime";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final ResourceLocation RE0_486_ID = id("re0_486");
    public static SREModifier RE0_486 = HMLModifiers
            .register(new EggModifier(RE0_486_ID, new Color(243, 207, 180).getRGB(), null, null, false, false),
                    "anime")
            .setHidden(true);

    public static void init() {
        registerEvents();
    }

    public static void registerEvents() {
        // 486 似了，全体活人回溯开局位置。
        OnPlayerDeath.EVENT.register((np, deathReason) -> {
            if (!(np instanceof ServerPlayer player))
                return;
            var wmc = WorldModifierComponent.getInstance(player);
            if (!wmc.isModifier(player, RE0_486))
                return;
            var level = player.level();
            final var players = new ArrayList<>(level.players());
            players.removeIf((p) -> !GameUtils.isPlayerAliveAndSurvival(p));

            // 复活486，删除修饰符
            wmc.removeModifier(player, RE0_486);
            GameUtils.revivePlayerToItsRoom(player);
            // 触发回溯
            for (var p : players) {
                GameUtils.teleportBackToRoom(p);
                SRENetworkMessageUtils
                        .sendCODSubtitleToAll(
                                Component.translatable("message.anime.re0_486.trigger.title")
                                        .withStyle(ChatFormatting.GOLD),
                                Component.translatable("message.anime.re0_486.trigger.subtitle"), 100, RE0_486.color(),
                                false, SubtitleS2CPayload.POS_CENTER);
                SRE.REPLAY_MANAGER.recordCustomEvent(
                        Component.translatable("message.anime.re0_486.trigger.title").withStyle(ChatFormatting.YELLOW),
                        false);
            }
            // 时停，让其像时间回溯
            TimeStopEffect.tryTriggerStart(player, 20 * 2,
                    Component.translatable("message.anime.re0_486.trigger.text"));
        });
    }
}
