package io.wifi.starrailexpress.client.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnRoundStartWelcomeTimmer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RoundTextRenderer {
    private static final Map<String, Optional<GameProfile>> failCache = new HashMap<>();
    private static final int WELCOME_DURATION = 200 + GameConstants.FADE_TIME * 2;
    private static final int END_DURATION = 200;
    private static RoleAnnouncementTexts.RoleAnnouncementText role = RoleAnnouncementTexts.CIVILIAN;
    public static int welcomeTime = 0;
    public static int killers = 0;
    public static int targets = 0;
    public static int endTime = 0;

    public static Map<UUID, SRERole> lastRole = new HashMap<>();
    
    // 缓存变量减少重复计算
    private static final Map<Component, Integer> textWidthCache = new HashMap<>();
    private static Component cachedWelcomeText = null;
    private static Component cachedPremiseText = null;
    private static Component cachedGoalText = null;
    private static Component cachedCanJumpTip = null;
    private static int cachedWelcomeWidth = 0;
    private static int cachedPremiseWidth = 0;
    private static int cachedGoalWidth = 0;
    private static int cachedCanJumpWidth = 0;
    private static int lastKillers = -1;
    private static int lastTargets = -1;
    private static boolean lastCanJump = false;

    @SuppressWarnings("IntegerDivisionInFloatingPointContext")
    public static void renderHud(Font renderer, LocalPlayer player, @NotNull FakeGuiGraphics context,
            float partialTicks) {
        // Skip rendering entirely if tick hasn't changed - cached text will be replayed
        if (!OptimizedTextRenderer.INSTANCE.isTickDirty()) {
            return;
        }

        boolean isLooseEnds = SREGameWorldComponent.KEY.get(player.level()).getGameMode() == SREGameModes.LOOSE_ENDS;

        if (welcomeTime > 0) {
            if (welcomeTime <= WELCOME_DURATION - GameConstants.FADE_TIME + 15) {
                MapDetailsRenderer.renderHud(renderer, player, context, partialTicks);
            }
            
            // 缓存文本和宽度
            if (lastKillers != killers || lastTargets != targets || cachedWelcomeText == null) {
                cachedWelcomeText = isLooseEnds ? Component.translatable("announcement.star.loose_ends.welcome") : role.welcomeText;
                cachedPremiseText = isLooseEnds ? Component.translatable("announcement.star.loose_ends.premise") : role.premiseText.apply(killers);
                cachedGoalText = isLooseEnds ? Component.translatable("announcement.star.loose_ends.goal") : role.goalText.apply(targets);
                cachedWelcomeWidth = renderer.width(cachedWelcomeText);
                cachedPremiseWidth = renderer.width(cachedPremiseText);
                cachedGoalWidth = renderer.width(cachedGoalText);
                lastKillers = killers;
                lastTargets = targets;
            }
            
            boolean canJump = SREClient.gameComponent.isJumpAvailable();
            if (lastCanJump != canJump || cachedCanJumpTip == null) {
                cachedCanJumpTip = canJump
                        ? Component.translatable("announcement.star.tip.can_jump").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("announcement.star.tip.cant_jump").withStyle(ChatFormatting.YELLOW);
                cachedCanJumpWidth = renderer.width(cachedCanJumpTip);
                lastCanJump = canJump;
            }
            
            int color = isLooseEnds ? 0x9F0000 : 0xFFFFFF;
            float centerX = context.guiWidth() / 2f;
            float centerY = context.guiHeight() / 2f + 3.5f;
            
            context.pose().pushPose();
            context.pose().translate(centerX, centerY, 0);
            
            if (welcomeTime <= 180) {
                context.pose().pushPose();
                context.pose().scale(2.6f, 2.6f, 1f);
                context.drawString(renderer, cachedWelcomeText, -cachedWelcomeWidth / 2, -12, color);
                context.pose().popPose();
            }
            
            if (welcomeTime <= 120) {
                context.pose().pushPose();
                context.pose().scale(1.2f, 1.2f, 1f);
                context.drawString(renderer, cachedPremiseText, -cachedPremiseWidth / 2, 0, color);
                context.pose().popPose();
            }
            
            if (welcomeTime <= 60) {
                context.drawString(renderer, cachedGoalText, -cachedGoalWidth / 2, 14, color);
            }
            
            if (welcomeTime <= 120) {
                context.drawString(renderer, cachedCanJumpTip, -cachedCanJumpWidth / 2, 28, color);
            }
            
            context.pose().popPose();
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (endTime > 0 && endTime < END_DURATION - (GameConstants.FADE_TIME * 2) && !game.isRunning()) {
            SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(player.level());
            if (roundEnd.getWinStatus() == GameUtils.WinStatus.NONE)
                return;
            Player winner = null;
            if (game.getLooseEndWinner() != null)
                winner = player.level().getPlayerByUUID(game.getLooseEndWinner());
            Component endText = role.getEndText(roundEnd.getWinStatus(),
                    winner == null ? roundEnd.getCustomWinners() : winner.getName(), roundEnd);
            if (endText == null)
                return;
                
            // 预计算宽度
            int endTextWidth = renderer.width(endText);
            MutableComponent winMessage = getWinMessage(roundEnd, winner);
            int winMessageWidth = renderer.width(winMessage);
            
            float centerX = context.guiWidth() / 2f;
            float centerY = context.guiHeight() / 2f - 40;
            
            context.pose().pushPose();
            context.pose().translate(centerX, centerY, 0);
            
            context.pose().pushPose();
            context.pose().scale(2.6f, 2.6f, 1f);
            context.drawString(renderer, endText, -endTextWidth / 2, -12, 0xFFFFFF);
            context.pose().popPose();
            
            context.pose().pushPose();
            context.pose().scale(1.2f, 1.2f, 1f);
            context.drawString(renderer, winMessage, -winMessageWidth / 2, -4, 0xFFFFFF);
            context.pose().popPose();
            if (isLooseEnds) {
                Component titleText = RoleAnnouncementTexts.LOOSE_END.titleText;
                int titleWidth = getOrCacheWidth(renderer, titleText);
                context.drawString(renderer, titleText, -titleWidth / 2, 14, 0xFFFFFF);

                int looseEnds = 0;
                for (SREGameRoundEndComponent.RoundEndData entry : roundEnd.players) {
                    float xPos = ((looseEnds % 6) - 3.5f) * 24f; // 24f = 12 * 2
                    float yPos = 14 + (looseEnds / 6) * 24f; // 24f = 12 * 2
                    looseEnds++;
                    
                    PlayerInfo playerEntry = SREClient.PLAYER_ENTRIES_CACHE.get(entry.player().getId());
                    if (playerEntry != null && playerEntry.getSkin().texture() != null) {
                        ResourceLocation texture = playerEntry.getSkin().texture();
                        float offColour = entry.wasDead() ? 0.4f : 1f;

                        context.pose().pushPose();
                        context.pose().scale(2f, 2f, 1f);
                        context.pose().translate(xPos, yPos, 0);
                        
                        RenderSystem.enableBlend();
                        context.pose().pushPose();
                        context.pose().translate(8, 0, 0);
                        context.innerBlit(texture, 0, 8, 0, 8, 0, 8 / 64f, 16 / 64f, 8 / 64f, 16 / 64f, 1f,
                                offColour, offColour, 1f);
                        context.pose().translate(-0.5, -0.5, 0);
                        context.pose().scale(1.125f, 1.125f, 1f);
                        context.innerBlit(texture, 0, 8, 0, 8, 0, 40 / 64f, 48 / 64f, 8 / 64f, 16 / 64f, 1f,
                                offColour, offColour, 1f);
                        context.pose().popPose();
                        
                        if (entry.wasDead()) {
                            context.pose().translate(13, 0, 0);
                            context.pose().scale(2f, 1f, 1f);
                            context.drawString(renderer, "x", -renderer.width("x") / 2, 0, 0xE10000, false);
                            context.drawString(renderer, "x", -renderer.width("x") / 2, 1, 0x550000, false);
                        }
                        
                        context.pose().popPose();
                    }
                }
                context.pose().popPose();
            } else {
                int vigilanteTotal = 1;
                int loose_endsTotal = 1;

                for (SREGameRoundEndComponent.RoundEndData entry : roundEnd.players) {
                    final var role1 = lastRole.get(entry.player().getId());
                    if (role1 != null)
                        if (role1.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                            loose_endsTotal++;
                        } else if (role1.isVigilanteTeam()) {
                            vigilanteTotal += 1;
                        }
                }

                // 预缓存所有组件和宽度
                Component neutralTitle = Component.translatable("announcement.star.title.neutral");
                Component looseEndRole = Component.translatable("announcement.star.role.loose_end");
                Component civilianTitle = RoleAnnouncementTexts.CIVILIAN.titleText;
                Component vigilanteTitle = RoleAnnouncementTexts.VIGILANTE.titleText;
                Component killerTitle = RoleAnnouncementTexts.KILLER.titleText;
                
                int neutralWidth = getOrCacheWidth(renderer, neutralTitle);
                int looseEndWidth = getOrCacheWidth(renderer, looseEndRole);
                int civilianWidth = getOrCacheWidth(renderer, civilianTitle);
                int vigilanteWidth = getOrCacheWidth(renderer, vigilanteTitle);
                int killerWidth = getOrCacheWidth(renderer, killerTitle);

                int neutralY = (loose_endsTotal > 1) ? (14 + 16 + 32 * ((loose_endsTotal) / 2)) : 14;
                
                context.drawString(renderer, neutralTitle, -neutralWidth / 2 - 90, neutralY, Color.YELLOW.getRGB());

                if (loose_endsTotal > 1) {
                    context.drawString(renderer, looseEndRole, -looseEndWidth / 2 - 90, 14, new Color(160, 0, 0).getRGB());
                }
                
                context.drawString(renderer, civilianTitle, -civilianWidth / 2, 14, 0xFFFFFF);
                context.drawString(renderer, vigilanteTitle, -vigilanteWidth / 2 + 90, 14, 0xFFFFFF);
                context.drawString(renderer, killerTitle, -killerWidth / 2 + 90, 14 + 16 + 32 * ((vigilanteTotal) / 2), 0xFFFFFF);

                int civilians = 0;
                int neutrals = 0;
                int vigilantes = 0;
                int killers = 0;
                int loose_ends = 0;

                for (SREGameRoundEndComponent.RoundEndData entry : roundEnd.players) {
                    if (entry.player == null)
                        continue;

                    final var role1 = lastRole.get(entry.player().getId());
                    final SRERole role2 = role1;
                    
                    // 预计算位置
                    float translateX = 0;
                    float translateY = 0;
                    float extraTranslateY = 0;

                    if (role1 == null || role1 != null && role1.isInnocent() && !role1.canUseKiller()
                            && ((role2 != null && !role2.isNeutrals() && !role2.isVigilanteTeam()))) {
                        translateX = -36 + (civilians % 5) * 12;
                        translateY = 14 + (civilians / 5) * 16;
                        civilians++;
                    } else {
                        if (role2 != null) {
                            if (role2.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                                translateX = -63 + (loose_ends % 2) * 12;
                                translateY = 14 + (loose_ends / 2) * 16;
                                loose_ends++;
                            } else if (role2.isNeutrals()) {
                                if (loose_endsTotal > 1) {
                                    extraTranslateY = 8 + ((loose_endsTotal) / 2) * 16;
                                }
                                translateX = -63 + (neutrals % 2) * 12;
                                translateY = 14 + (neutrals / 2) * 16;
                                neutrals++;
                            } else if (role2.isInnocent() || role2.isVigilanteTeam()) {
                                translateX = 27 + (vigilantes % 2) * 12;
                                translateY = 14 + (vigilantes / 2) * 16;
                                vigilantes++;
                            } else if (role2.canUseKiller()) {
                                extraTranslateY = 8 + ((vigilanteTotal) / 2) * 16;
                                translateX = 27 + (killers % 2) * 12;
                                translateY = 14 + (killers / 2) * 16;
                                killers++;
                            } else {
                                translateX = -36 + (civilians % 5) * 12;
                                translateY = 14 + (civilians / 5) * 16;
                                civilians++;
                            }
                        }
                    }
                    
                    context.pose().pushPose();
                    context.pose().scale(2f, 2f, 1f);
                    if (extraTranslateY != 0) {
                        context.pose().translate(0, extraTranslateY, 0);
                    }
                    context.pose().translate(translateX, translateY, 0);
                    
                    // 渲染角色名
                    if (role1 != null) {
                        context.pose().pushPose();
                        context.pose().scale(0.32f, 0.32f, 1f);
                        context.pose().translate(38, 36, 200);
                        var text = Component.translatable("announcement.star.role." + role1.getIdentifier().getPath());
                        int textWidth = getOrCacheWidth(renderer, text);
                        context.drawString(renderer, text, -textWidth / 2, 0, role1.getColor());
                        context.pose().popPose();
                    } else {
                        context.pose().pushPose();
                        context.pose().scale(0.32f, 0.32f, 1f);
                        context.pose().translate(38, 36, 200);
                        var text = Component.translatable("announcement.star.role.unknown");
                        int textWidth = getOrCacheWidth(renderer, text);
                        context.drawString(renderer, text, -textWidth / 2, 0, 0xffffff);
                        context.pose().popPose();
                    }
                    PlayerInfo playerListEntry = SREClient.PLAYER_ENTRIES_CACHE.get(entry.player().getId());
                    if (playerListEntry != null) {
                        GameProfile playerProfile = playerListEntry.getProfile();
                        ResourceLocation texture = playerListEntry.getSkin().texture();

                        if (texture != null) {
                            float offColour = entry.wasDead() ? 0.4f : 1f;
                            RenderSystem.enableBlend();
                            context.pose().pushPose();
                            context.pose().translate(8, 0, 0);
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
                            context.drawString(renderer, Component.literal("👑").withStyle(ChatFormatting.GOLD), 0, 0, 0);
                            context.pose().popPose();
                        }
                        
                        if (playerProfile != null) {
                            String p_name = playerProfile.getName();
                            if (p_name.length() >= 10) {
                                p_name = p_name.substring(0, 9) + "...";
                            }
                            var nameText = Component.literal(p_name);
                            int nameWidth = getOrCacheWidth(renderer, nameText);
                            
                            context.pose().pushPose();
                            context.pose().scale(0.2f, 0.2f, 1f);
                            context.pose().translate(60, 44, 200);
                            context.drawString(renderer, nameText, -nameWidth / 2, 0, 0xffffff);
                            context.pose().popPose();
                        }

                        if (entry.wasDead()) {
                            context.pose().translate(13, 0, 0);
                            context.pose().scale(2f, 1f, 1f);
                            int xWidth = renderer.width("x");
                            context.drawString(renderer, "x", -xWidth / 2, 0, 0xE10000, false);
                            context.drawString(renderer, "x", -xWidth / 2, 1, 0x550000, false);
                        }
                    }
                    context.pose().popPose();
                }
                context.pose().popPose();
            }
        }

    }
    
    // 宽度缓存辅助方法
    private static int getOrCacheWidth(Font renderer, Component text) {
        return textWidthCache.computeIfAbsent(text, t -> renderer.width(t));
    }
    
    // 清理缓存方法（可选，在需要时调用）
    public static void clearCache() {
        textWidthCache.clear();
        cachedWelcomeText = null;
        cachedPremiseText = null;
        cachedGoalText = null;
        cachedCanJumpTip = null;
    }

    private static MutableComponent getWinMessage(SREGameRoundEndComponent roundEnd, Player winner) {
        if (roundEnd.getWinStatus().equals(WinStatus.CUSTOM)) {
            if (winner != null) {
                return Component.translatable("game.win.star." + roundEnd.CustomWinnerID,
                        winner.getDisplayName());
            } else {
                Component winners = roundEnd.getCustomWinners();
                return Component.translatable("game.win.star." + roundEnd.CustomWinnerID, winners);
            }
        } else if (roundEnd.getWinStatus().equals(WinStatus.CUSTOM_COMPONENT)) {
            if (roundEnd.CustomWinnerSubtitle != null)
                return Component.literal("").append(roundEnd.CustomWinnerSubtitle);
        }
        if (winner != null) {
            return Component.translatable("game.win.star." + roundEnd.getWinStatus().name().toLowerCase().toLowerCase(),
                    winner.getDisplayName());
        }
        return Component.translatable("game.win.star." + roundEnd.getWinStatus().name().toLowerCase().toLowerCase());

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
                                SREGameRoundEndComponent.KEY.get(player.level()).didWin(player.getUUID())
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
        RoundTextRenderer.cachedWelcomeText = null;
        RoundTextRenderer.cachedCanJumpTip = null;
        RoundTextRenderer.cachedGoalText = null;
        RoundTextRenderer.cachedPremiseText = null;
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