package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.event.AllowNameRender;
import io.wifi.starrailexpress.event.OnKillerCohortDisplay;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.EntityHitResult;

import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RoleNameRenderer {
    private static TrainRole targetRole = TrainRole.BYSTANDER;
    private static SRERole targetRole2;
    private static MutableComponent roleText1;
    // private static float nametagAlpha = 0f;
    // private static float noteAlpha = 0f;
    public static Map<UUID, String> displayTags = new HashMap<>();

    public static float getPlayerRange(Player player) {
        if (player.getMainHandItem().is(Items.SPYGLASS)) {
            if (player.isUsingItem())
                if (player.getUseItem().is(Items.SPYGLASS)) {
                    return 16f;
                }
        }
        if (GameUtils.isPlayerSpectatingOrCreative(player)) {
            return 8f;
        }
        return 2f;
    }

    @SuppressWarnings("unused")
    public static void renderHud(Font renderer, @NotNull LocalPlayer player, FakeGuiGraphics context,
            DeltaTracker tickCounter) {
        Component nametag = Component.empty();
        final Component[] note = new Component[] { Component.empty(), Component.empty(), Component.empty(),
                Component.empty() };
        float range = getPlayerRange(player);
        range = range * (GameUtils.isPlayerSpectatingOrCreative(player) ? 1f : 1f);
        {
            SREGameWorldComponent component = SREGameWorldComponent.KEY.get(player.level());
            if (player.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())) < 3
                    && player.level().getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition())) < 10)
                return;
            Player target = null;
            if (ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof Player player1,
                    range) instanceof EntityHitResult entityHitResult
                    && entityHitResult.getEntity() instanceof Player) {
                target = (Player) entityHitResult.getEntity();
                if (!AllowNameRender.EVENT.invoker().allowRenderName(target)) {
                    targetRole = TrainRole.BYSTANDER;
                    targetRole2 = null;
                    nametag = Component.literal("");
                    return;
                }
                nametag = target.getDisplayName();
                if (SREPlayerMoodComponent.KEY.get(player).getMood() <= 0.4) {
                    nametag = Component.empty();
                }
                if (component.canUseKillerFeatures(target)) {
                    targetRole = TrainRole.KILLER;
                } else if (component.isNeutralForKiller(target)) {
                    targetRole = TrainRole.KILLER;
                } else {
                    targetRole = TrainRole.BYSTANDER;
                }
                boolean shouldObfuscate = SREPlayerPsychoComponent.KEY.get(target).getPsychoTicks() > 0;
                nametag = shouldObfuscate
                        ? Component.literal("urscrewed" + "X".repeat(player.getRandom().nextInt(8)))
                                .withStyle(style -> style.applyFormats(ChatFormatting.OBFUSCATED,
                                        ChatFormatting.DARK_RED))
                        : nametag;
                if (SREClient.gameComponent != null) {
                    var role = SREClient.gameComponent.getRole(target);
                    if (role != null) {
                        targetRole2 = role;
                    }
                }
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 6, 0);
                context.pose().scale(0.6f, 0.6f, 1f);
                int nameWidth = renderer.width(nametag);
                context.drawString(renderer, nametag, -nameWidth / 2, 16,
                        Mth.color(1f, 1f, 1f) | ((int) (1 * 255) << 24));
                if (component.isRunning()) {
                    TrainRole playerRole = TrainRole.BYSTANDER;
                    if (component.canUseKillerFeatures(player))
                        playerRole = TrainRole.KILLER;
                    if (component.isNeutralForKiller(player))
                        playerRole = TrainRole.KILLER;
                    if (targetRole2 != null) {
                        // 迷失杀手：杀手本能中不显示迷失杀手和杀手同伙信息
                        if (component.isRole(target, ModRoles.LOST_KILLER)) {
                            // 不做任何显示
                        } else if (component.isKillerTeamRole(targetRole2) && playerRole.equals(TrainRole.KILLER)) {
                            if (component.canSeeKillerTeammate(player)) {
                                context.pose().translate(0, 20 + renderer.lineHeight, 0);
                                if (target != null) {
                                    roleText1 = Component
                                            .translatable(
                                                    "announcement.star.role." + targetRole2.identifier().getPath());
                                    MutableComponent roleText2 = OnKillerCohortDisplay.EVENT.invoker()
                                            .onCohortRender(target);
                                    if (roleText2 != null) {
                                        roleText1 = roleText2;
                                    }
                                }
                                if (roleText1 == null)
                                    return;
                                int roleWidth1 = renderer.width(roleText1);
                                context.drawString(renderer, roleText1, -roleWidth1 / 2, 0,
                                        Mth.color(1f, 0f, 0f) | ((int) (1 * 255) << 24));
                            }
                        }
                    }
                    if (targetRole2 == ModRoles.MEATBALL){
                        // 显示肉汁提示
                        context.pose().translate(0, 20 + renderer.lineHeight, 0);
                        MutableComponent meatballTip = Component.translatable("game.tip.meatball_role");
                        int meatballTipWidth = renderer.width(meatballTip);
                        context.drawString(renderer, meatballTip, -meatballTipWidth / 2, 0,
                                Mth.color(1f, 0.5f, 0f) | ((int) (1 * 255) << 24));
                        
                        // 检查附近是否有其他玩家，如果有则显示无法攻击的提示
                        boolean nearbyPlayers = false;
                        for (Player nearbyPlayer : player.level().players()) {
                            if (nearbyPlayer != null && nearbyPlayer != target && nearbyPlayer.distanceTo(target) <= 4.0D) {
                                nearbyPlayers = true;
                                break;
                            }
                        }
                        
                        if (nearbyPlayers) {
                            // 无法在人群中攻击的提示
                            context.pose().translate(0, 20 + renderer.lineHeight, 0);
                            MutableComponent crowdTip = Component.translatable("game.tip.meatball_cannot_attack_in_crowd");
                            int crowdTipWidth = renderer.width(crowdTip);
                            context.drawString(renderer, crowdTip, -crowdTipWidth / 2, 0,
                                    Mth.color(1f, 0f, 0f) | ((int) (1 * 255) << 24));
                        }
                    }
                    // 迷失杀手：不显示杀手同伙标签
                    if (playerRole == TrainRole.KILLER && targetRole == TrainRole.KILLER && !component.isRole(target, ModRoles.LOST_KILLER)) {
                        context.pose().translate(0, 20 + renderer.lineHeight, 0);
                        if (component.canSeeKillerTeammate(player)) {
                            MutableComponent roleText = Component.translatable("game.tip.cohort");
                            int roleWidth = renderer.width(roleText);
                            context.drawString(renderer, roleText, -roleWidth / 2, 0,
                                    Mth.color(1f, 0f, 0f) | ((int) (255) << 24));
                        }

                    }
                }
                context.pose().popPose();
            }
        }
        var deathPenalty = ModComponents.DEATH_PENALTY.get(player);
        boolean hasPenalty = false;
        hasPenalty = deathPenalty.hasPenalty();
        if (!hasPenalty && (player.isSpectator() || player.isCreative())) {
            if (ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof PuppeteerBodyEntity,
                    range) instanceof EntityHitResult entityHitResult
                    && entityHitResult.getEntity() instanceof PuppeteerBodyEntity pbe) {
                UUID uid = pbe.getOwnerUuid().orElse(null);
                String name2 = SREClientUtils.getPlayerNameByUid(uid);
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 6, 0);
                context.pose().scale(0.6f, 0.6f, 1f);
                int nameWidth2 = renderer.width(name2);
                Component tipC = Component.translatable("entity.noellesroles.puppeteer_body")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(renderer, name2, -nameWidth2 / 2, 16,
                        Mth.color(1f, 1f, 1f) | ((int) (1 * 255) << 24));
                context.drawString(renderer, tipC, -renderer.width(tipC) / 2, 4,
                        Mth.color(1f, 1f, 1f) | ((int) (1 * 255) << 24));
                context.pose().popPose();
            }
        }
        if (ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof PuppeteerBodyEntity,
                range) instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof PuppeteerBodyEntity pbe) {

        }
        if (ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof NoteEntity,
                range) instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof NoteEntity notee) {
            note[0] = Component.literal(notee.getLines()[0]);
            note[1] = Component.literal(notee.getLines()[1]);
            note[2] = Component.literal(notee.getLines()[2]);
            note[3] = Component.literal(notee.getLines()[3]);

            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 6, 0);
            context.pose().scale(0.6f, 0.6f, 1f);
            for (int i = 0; i < note.length; i++) {
                Component line = note[i];
                int lineWidth = renderer.width(line);
                context.drawString(renderer, line, -lineWidth / 2, 16 + (i * (renderer.lineHeight + 2)),
                        Mth.color(1f, 1f, 1f) | ((int) (1 * 255) << 24));
            }
            context.pose().popPose();

        }

    }

    private enum TrainRole {
        KILLER,
        BYSTANDER
    }
}