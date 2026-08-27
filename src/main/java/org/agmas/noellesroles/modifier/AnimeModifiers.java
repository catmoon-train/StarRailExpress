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

package org.agmas.noellesroles.modifier;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.exmo.sre.subtitle.SubtitleS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.EggModifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.content.effects.TimeStopEffect;

import java.awt.*;
import java.util.ArrayList;

public class AnimeModifiers {
    public static final String NAMESPACE = "anime";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final ResourceLocation RE0_486_ID = id("re0_486");
    public static SREModifier RE0_486 = HMLModifiers
            .register(new EggModifier(RE0_486_ID, new Color(243, 207, 180).getRGB(), null, null, false, false),
                    "anime")
            .setHidden(true)
            .setDefaultEnableChance(200)
            .setDefaultEnableNeededPlayerCount(12);

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
            }
            SRENetworkMessageUtils
                    .sendCODSubtitleToAll(
                            Component.translatable("message.anime.re0_486.trigger.title")
                                    .withStyle(ChatFormatting.GOLD),
                            Component.translatable("message.anime.re0_486.trigger.subtitle"), 100, RE0_486.color(),
                            false, SubtitleS2CPayload.POS_CENTER);
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component
                            .translatable("message.anime.re0_486.trigger.text",
                                    GameReplayUtils.getReplayPlayerDisplayText(player, true))
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            // 时停，让其像时间回溯
            TimeStopEffect.tryTriggerStart(player, 20 * 2,
                    Component.translatable("message.anime.re0_486.trigger.time_stop"));
        });
    }
    static {
        RE0_486.setAddedVersion("4.3");
    }
}
