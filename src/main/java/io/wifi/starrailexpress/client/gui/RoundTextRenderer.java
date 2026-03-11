package io.wifi.starrailexpress.client.gui;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.GameRoundEndComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnRoundStartWelcomeTimmer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.game.GameFunctions.WinStatus;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public class RoundTextRenderer {
    private static final Map<String, Optional<GameProfile>> failCache = new HashMap<>();
    private static final int WELCOME_DURATION = 200 + GameConstants.FADE_TIME * 2;
    private static final int END_DURATION = 200;
    private static RoleAnnouncementTexts.RoleAnnouncementText role = RoleAnnouncementTexts.CIVILIAN;
    public static int welcomeTime = 0;
    public static int killers = 0;
    public static int targets = 0;
    public static int endTime = 0;

    public static Map<UUID, Role> lastRole = new HashMap<>();

    @SuppressWarnings("IntegerDivisionInFloatingPointContext")
    public static void renderHud(Font renderer, LocalPlayer player, @NotNull GuiGraphics context, float partialTicks) {
        boolean isLooseEnds = GameWorldComponent.KEY.get(player.level()).getGameMode() == SREGameModes.LOOSE_ENDS;

        if (welcomeTime > 0) {
            if (welcomeTime <= WELCOME_DURATION - GameConstants.FADE_TIME + 15) {
                MapDetailsRenderer.renderHud(renderer, player, context, partialTicks);
            }
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 3.5, 0);
            context.pose().pushPose();
            context.pose().scale(2.6f, 2.6f, 1f);
            int color = isLooseEnds ? 0x9F0000 : 0xFFFFFF;
            if (welcomeTime <= 180) {
                Component welcomeText = isLooseEnds ? Component.translatable("announcement.loose_ends.welcome")
                        : role.welcomeText;
                context.drawString(renderer, welcomeText, -renderer.width(welcomeText) / 2, -12, color);
            }
            context.pose().popPose();
            context.pose().pushPose();
            context.pose().scale(1.2f, 1.2f, 1f);
            if (welcomeTime <= 120) {
                Component premiseText = isLooseEnds ? Component.translatable("announcement.loose_ends.premise")
                        : role.premiseText.apply(killers);
                context.drawString(renderer, premiseText, -renderer.width(premiseText) / 2, 0, color);
            }
            context.pose().popPose();
            context.pose().pushPose();
            context.pose().scale(1f, 1f, 1f);
            if (welcomeTime <= 60) {
                Component goalText = isLooseEnds ? Component.translatable("announcement.loose_ends.goal")
                        : role.goalText.apply(targets);
                context.drawString(renderer, goalText, -renderer.width(goalText) / 2, 14, color);
            }
            if (welcomeTime <= 120) {
                boolean canJump = SREClient.gameComponent.isJumpAvailable();
                MutableComponent canJumpTip = canJump
                        ? Component.translatable("announcement.tip.can_jump").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("announcement.tip.cant_jump").withStyle(ChatFormatting.YELLOW);
                context.drawString(renderer, canJumpTip, -renderer.width(canJumpTip) / 2, 28, color);
            }
            context.pose().popPose();
            context.pose().popPose();
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.level());
        if (endTime > 0 && endTime < END_DURATION - (GameConstants.FADE_TIME * 2) && !game.isRunning()) {
            GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.get(player.level());
            if (roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE)
                return;
            Player winner = null;
            if (game.getLooseEndWinner() != null)
                winner = player.level().getPlayerByUUID(game.getLooseEndWinner());
            Component endText = role.getEndText(roundEnd.getWinStatus(),
                    winner == null ? roundEnd.getCustomWinners() : winner.getDisplayName(), roundEnd);
            if (endText == null)
                return;
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f - 40, 0);
            context.pose().pushPose();
            context.pose().scale(2.6f, 2.6f, 1f);
            context.drawString(renderer, endText, -renderer.width(endText) / 2, -12, 0xFFFFFF);
            context.pose().popPose();
            context.pose().pushPose();
            context.pose().scale(1.2f, 1.2f, 1f);
            MutableComponent winMessage = getWinMessage(roundEnd, winner);
            context.drawString(renderer, winMessage, -renderer.width(winMessage) / 2, -4, 0xFFFFFF);
            context.pose().popPose();
            if (isLooseEnds) {
                context.drawString(renderer, RoleAnnouncementTexts.LOOSE_END.titleText,
                        -renderer.width(RoleAnnouncementTexts.LOOSE_END.titleText) / 2, 14, 0xFFFFFF);
                int looseEnds = 0;
                for (GameRoundEndComponent.RoundEndData entry : roundEnd.players) {
                    context.pose().pushPose();
                    context.pose().scale(2f, 2f, 1f);
                    context.pose().translate(((looseEnds % 6) - 3.5) * 12, 14 + (looseEnds / 6) * 12, 0);
                    looseEnds++;
                    PlayerInfo playerEntry = SREClient.PLAYER_ENTRIES_CACHE.get(entry.player().getId());
                    if (playerEntry != null && playerEntry.getSkin().texture() != null) {
                        ResourceLocation texture = playerEntry.getSkin().texture();

                        RenderSystem.enableBlend();
                        context.pose().pushPose();
                        context.pose().translate(8, 0, 0);
                        float offColour = entry.wasDead() ? 0.4f : 1f;
                        context.innerBlit(texture, 0, 8, 0, 8, 0, 8 / 64f, 16 / 64f, 8 / 64f, 16 / 64f, 1f, offColour,
                                offColour, 1f);
                        context.pose().translate(-0.5, -0.5, 0);
                        context.pose().scale(1.125f, 1.125f, 1f);
                        context.innerBlit(texture, 0, 8, 0, 8, 0, 40 / 64f, 48 / 64f, 8 / 64f, 16 / 64f, 1f, offColour,
                                offColour, 1f);
                        context.pose().popPose();
                    }
                    if (entry.wasDead()) {
                        context.pose().translate(13, 0, 0);
                        context.pose().scale(2f, 1f, 1f);
                        context.drawString(renderer, "x", -renderer.width("x") / 2, 0, 0xE10000, false);
                        context.drawString(renderer, "x", -renderer.width("x") / 2, 1, 0x550000, false);
                    }
                    context.pose().popPose();
                }
                context.pose().popPose();
            } else {
                int vigilanteTotal = 1;
                int loose_endsTotal = 1;

                for (GameRoundEndComponent.RoundEndData entry : roundEnd.players) {
                    final var role1 = lastRole.get(entry.player().getId());
                    if (role1 != null)
                        if (role1.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                            loose_endsTotal++;
                        } else if (role1.isVigilanteTeam()) {
                            vigilanteTotal += 1;
                        }

                }

                context.drawString(renderer, Component.translatable("announcement.title.neutral"),
                        -renderer.width(
                                Component.translatable("announcement.title.neutral"))
                                / 2 - 90,
                        (loose_endsTotal > 1) ? (14 + 16 + 32 * ((loose_endsTotal) / 2)) : 14, Color.YELLOW.getRGB());
                if (loose_endsTotal > 1) {
                    context.drawString(renderer, Component.translatable("announcement.role.loose_end"),
                            -renderer.width(
                                    Component.translatable("announcement.role.loose_end"))
                                    / 2 - 90,
                            14, new Color(160, 0, 0).getRGB());
                }
                context.drawString(renderer, RoleAnnouncementTexts.CIVILIAN.titleText,
                        -renderer.width(RoleAnnouncementTexts.CIVILIAN.titleText) / 2, 14, 0xFFFFFF);
                context.drawString(renderer, RoleAnnouncementTexts.VIGILANTE.titleText,
                        -renderer.width(RoleAnnouncementTexts.VIGILANTE.titleText) / 2 + 90, 14, 0xFFFFFF);
                context.drawString(renderer, RoleAnnouncementTexts.KILLER.titleText,
                        -renderer.width(RoleAnnouncementTexts.KILLER.titleText) / 2 + 90,
                        14 + 16 + 32 * ((vigilanteTotal) / 2), 0xFFFFFF);
                int civilians = 0;
                int neutrals = 0;
                int vigilantes = 0;
                int killers = 0;
                int loose_ends = 0;

                for (GameRoundEndComponent.RoundEndData entry : roundEnd.players) {
                    context.pose().pushPose();
                    context.pose().scale(2f, 2f, 1f);

                    if (entry.role() == null)
                        continue;

                    final var role1 = lastRole.get(entry.player().getId());
                    final Role role2 = role1;

                    if (role1 == null || role1 != null && role1.isInnocent() && !role1.canUseKiller()
                            && ((role2 != null && !role2.isNeutrals() && !role2.isVigilanteTeam()))) {
                        context.pose().translate(-36 + (civilians % 5) * 12, 14 + (civilians / 5) * 16, 0);
                        civilians++;
                    } else {
                        if (role2 != null) {
                            if (role2.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                                context.pose().translate(-63 + (loose_ends % 2) * 12, 14 + (loose_ends / 2) * 16, 0);
                                loose_ends++;
                            } else if (role2.isNeutrals()) {
                                if (loose_endsTotal > 1) {
                                    context.pose().translate(0, 8 + ((loose_endsTotal) / 2) * 16, 0);
                                }
                                context.pose().translate(-63 + (neutrals % 2) * 12, 14 + (neutrals / 2) * 16, 0);
                                neutrals++;
                            } else if (role2.isInnocent() || role2.isVigilanteTeam()) {
                                context.pose().translate(27 + (vigilantes % 2) * 12, 14 + (vigilantes / 2) * 16, 0);
                                vigilantes++;
                            } else if (role2.canUseKiller()) {
                                context.pose().translate(0, 8 + ((vigilanteTotal) / 2) * 16, 0);
                                context.pose().translate(27 + (killers % 2) * 12, 14 + (killers / 2) * 16, 0);
                                killers++;
                            } else {
                                context.pose().translate(-36 + (civilians % 5) * 12, 14 + (civilians / 5) * 16, 0);
                                civilians++;
                            }

                        }
                    }
                    if (role1 != null) {
                        context.pose().pushPose();
                        context.pose().scale(0.32f, 0.32f, 1f);
                        context.pose().translate(38, 36, 200);
                        var text = Component.translatable("announcement.role." + role1.getIdentifier().getPath());
                        context.drawString(renderer,
                                text, -(renderer.width(text) / 2), 0,
                                role1.getColor());
                        context.pose().popPose();
                    } else {
                        context.pose().pushPose();
                        context.pose().scale(0.32f, 0.32f, 1f);
                        context.pose().translate(38, 36, 200);
                        var text = Component.translatable("announcement.role.unknown");
                        context.drawString(renderer,
                                text, -(renderer.width(text) / 2), 0,
                                0xffffff);
                        context.pose().popPose();
                    }
                    PlayerInfo playerListEntry = SREClient.PLAYER_ENTRIES_CACHE.get(entry.player().getId());
                    if (playerListEntry != null) {
                        GameProfile playerProfile = playerListEntry.getProfile();
                        ResourceLocation texture = playerListEntry.getSkin().texture();

                        if (texture != null) {
                            RenderSystem.enableBlend();
                            context.pose().pushPose();
                            context.pose().translate(8, 0, 0);
                            float offColour = entry.wasDead() ? 0.4f : 1f;
                            context.innerBlit(texture, 0, 8, 0, 8, 0, 8 / 64f, 16 / 64f, 8 / 64f, 16 / 64f, 1f,
                                    offColour, offColour, 1f);
                            context.pose().translate(-0.5, -0.5, 0);
                            context.pose().scale(1.125f, 1.125f, 1f);
                            context.innerBlit(texture, 0, 8, 0, 8, 0, 40 / 64f, 48 / 64f, 8 / 64f, 16 / 64f, 1f,
                                    offColour, offColour, 1f);
                            context.pose().popPose();

                        }
                        if (entry.hasWin) {

                            context.pose().pushPose();
                            context.pose().translate(14, -2, 0);
                            context.pose().scale(0.5f, 0.5f, 1f);
                            context.drawString(renderer,
                                    Component.literal("👑").withStyle(ChatFormatting.GOLD), 0, 0, 0);
                            context.pose().popPose();
                        }
                        if (playerProfile != null) {
                            context.pose().pushPose();
                            context.pose().scale(0.2f, 0.2f, 1f);
                            context.pose().translate(60, 44, 200);
                            String p_name = playerProfile.getName();
                            if (p_name.length() >= 10) {
                                p_name = p_name.substring(0, 9) + "...";
                            }
                            var text = Component.literal(p_name);
                            context.drawString(renderer,
                                    text, -(renderer.width(text) / 2), 0,
                                    0xffffff);
                            context.pose().popPose();
                        }

                        if (entry.wasDead()) {
                            context.pose().translate(13, 0, 0);
                            context.pose().scale(2f, 1f, 1f);
                            context.drawString(renderer, "x", -renderer.width("x") / 2, 0, 0xE10000, false);
                            context.drawString(renderer, "x", -renderer.width("x") / 2, 1, 0x550000, false);
                        }
                    }
                    context.pose().popPose();
                }
                context.pose().popPose();
            }
        }

    }

    private static MutableComponent getWinMessage(GameRoundEndComponent roundEnd, Player winner) {
        if (roundEnd.getWinStatus().equals(WinStatus.CUSTOM)) {
            if (winner != null) {
                return Component.translatable("game.win." + roundEnd.CustomWinnerID,
                        winner.getDisplayName());
            } else {
                Component winners = roundEnd.getCustomWinners();
                return Component.translatable("game.win." + roundEnd.CustomWinnerID, winners);
            }
        } else if (roundEnd.getWinStatus().equals(WinStatus.CUSTOM_COMPONENT)) {
            if (roundEnd.CustomWinnerSubtitle != null)
                return Component.literal("").append(roundEnd.CustomWinnerSubtitle);
        }
        if (winner != null) {
            return Component.translatable("game.win." + roundEnd.getWinStatus().name().toLowerCase().toLowerCase(),
                    winner.getDisplayName());
        }
        return Component.translatable("game.win." + roundEnd.getWinStatus().name().toLowerCase().toLowerCase());

    }

    public static void tick() {
        if (Minecraft.getInstance().level != null) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (welcomeTime > 0) {
                switch (welcomeTime) {
                    case 200 -> {
                        if (player != null)
                            player.level().playSeededSound(player, player.getX(), player.getY(), player.getZ(),
                                    TMMSounds.UI_RISER, SoundSource.MASTER, 10f, 1f, player.getRandom().nextLong());
                    }
                    case 180 -> {
                        if (player != null)
                            player.level().playSeededSound(player, player.getX(), player.getY(), player.getZ(),
                                    TMMSounds.UI_PIANO, SoundSource.MASTER, 10f, 1.25f, player.getRandom().nextLong());
                    }
                    case 120 -> {
                        if (player != null)
                            player.level().playSeededSound(player, player.getX(), player.getY(), player.getZ(),
                                    TMMSounds.UI_PIANO, SoundSource.MASTER, 10f, 1.5f, player.getRandom().nextLong());
                    }
                    case 60 -> {
                        if (player != null)
                            player.level().playSeededSound(player, player.getX(), player.getY(), player.getZ(),
                                    TMMSounds.UI_PIANO, SoundSource.MASTER, 10f, 1.75f, player.getRandom().nextLong());
                    }
                    case 1 -> {
                        if (player != null)
                            player.level().playSeededSound(player, player.getX(), player.getY(), player.getZ(),
                                    TMMSounds.UI_PIANO_STINGER, SoundSource.MASTER, 10f, 1f,
                                    player.getRandom().nextLong());
                    }
                }
                OnRoundStartWelcomeTimmer.EVENT.invoker().onWelcome(player, welcomeTime);
                welcomeTime--;
            }
            if (endTime > 0) {
                if (endTime == END_DURATION - (GameConstants.FADE_TIME * 2)) {
                    if (player != null)
                        player.level().playSeededSound(player, player.getX(), player.getY(), player.getZ(),
                                GameRoundEndComponent.KEY.get(player.level()).didWin(player.getUUID())
                                        ? TMMSounds.UI_PIANO_WIN
                                        : TMMSounds.UI_PIANO_LOSE,
                                SoundSource.MASTER, 10f, 1f, player.getRandom().nextLong());
                }
                endTime--;
            }
            Options options = Minecraft.getInstance().options;
            if (options != null && options.keyPlayerList.isDown())
                endTime = Math.max(2, endTime);
        }
    }

    public static void startWelcome(RoleAnnouncementTexts.RoleAnnouncementText role, int killers, int targets) {
        RoundTextRenderer.role = role;
        welcomeTime = WELCOME_DURATION;
        RoundTextRenderer.killers = killers;
        RoundTextRenderer.targets = targets;
    }

    public static void startEnd() {
        welcomeTime = 0;
        endTime = END_DURATION;
    }

    public static GameProfile getGameProfile(String disguise) {
        Optional<GameProfile> optional = SkullBlockEntity.fetchGameProfile(disguise).getNow(failCache(disguise));
        return optional.orElse(failCache(disguise).get());
    }

    public static PlayerSkin getSkinTextures(String disguise) {
        try {
            return Minecraft.getInstance().getSkinManager().getOrLoad(getGameProfile(disguise)).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Optional<GameProfile> failCache(String name) {
        return failCache.computeIfAbsent(name, (d) -> Optional.of(new GameProfile(UUID.randomUUID(), name)));
    }

}