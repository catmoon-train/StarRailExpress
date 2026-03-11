package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.event.AllowNameRender;
import io.wifi.starrailexpress.event.OnKillerCohortDisplay;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
import org.jetbrains.annotations.NotNull;

public class RoleNameRenderer {
    private static TrainRole targetRole = TrainRole.BYSTANDER;
    private static Role targetRole2;
    private static MutableComponent roleText1;
    private static float nametagAlpha = 0f;
    private static float noteAlpha = 0f;
    private static Component nametag = Component.empty();
    private static final Component[] note = new Component[] { Component.empty(), Component.empty(), Component.empty(),
            Component.empty() };

    public static float getPlayerRange(Player player) {
        if (player.getMainHandItem().is(Items.SPYGLASS)) {
            if (player.isUsingItem())
                if (player.getUseItem().is(Items.SPYGLASS)) {
                    return 16f;
                }
        }
        if (GameFunctions.isPlayerSpectatingOrCreative(player)) {
            return 8f;
        }
        return 2f;
    }

    @SuppressWarnings("unused")
    public static void renderHud(Font renderer, @NotNull LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter) {
        GameWorldComponent component = GameWorldComponent.KEY.get(player.level());
        if (player.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())) < 3
                && player.level().getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition())) < 10)
            return;
        float range = getPlayerRange(player);
        range = range * (GameFunctions.isPlayerSpectatingOrCreative(player) ? 1f : 1f);
        Player target = null;
        if (ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof Player player1,
                range) instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof Player) {
            target = (Player) entityHitResult.getEntity();
            if (!AllowNameRender.EVENT.invoker().allowRenderName(target)) {
                targetRole = TrainRole.BYSTANDER;
                targetRole2 = null;
                nametagAlpha = 0;
                nametag = Component.literal("");
                return;
            }
            nametagAlpha = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 4, nametagAlpha, 1f);
            nametag = target.getDisplayName();
            if (component.canUseKillerFeatures(target)) {
                targetRole = TrainRole.KILLER;
            } else if (component.isNeutralForKiller(target)) {
                targetRole = TrainRole.KILLER;
            } else {
                targetRole = TrainRole.BYSTANDER;
            }
            boolean shouldObfuscate = PlayerPsychoComponent.KEY.get(target).getPsychoTicks() > 0;
            nametag = shouldObfuscate ? Component.literal("urscrewed" + "X".repeat(player.getRandom().nextInt(8)))
                    .withStyle(style -> style.applyFormats(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_RED))
                    : nametag;
            if (SREClient.gameComponent != null) {
                var role = SREClient.gameComponent.getRole(target);
                if (role != null) {
                    targetRole2 = role;
                }
            }

        } else {
            nametagAlpha = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 4, nametagAlpha, 0f);
        }
        if (nametagAlpha > 0.05f) {
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 6, 0);
            context.pose().scale(0.6f, 0.6f, 1f);
            int nameWidth = renderer.width(nametag);
            context.drawString(renderer, nametag, -nameWidth / 2, 16,
                    Mth.color(1f, 1f, 1f) | ((int) (nametagAlpha * 255) << 24));
            if (component.isRunning()) {
                TrainRole playerRole = TrainRole.BYSTANDER;
                if (component.canUseKillerFeatures(player))
                    playerRole = TrainRole.KILLER;
                if (component.isNeutralForKiller(player))
                    playerRole = TrainRole.KILLER;
                if (targetRole2 != null) {
                    if (component.isKillerTeamRole(targetRole2) && playerRole.equals(TrainRole.KILLER)) {
                        if (component.canSeeKillerTeammate(player)) {
                            context.pose().translate(0, 20 + renderer.lineHeight, 0);
                            if (target != null) {
                                roleText1 = Component
                                        .translatable("announcement.role." + targetRole2.identifier().getPath());
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
                                    Mth.color(1f, 0f, 0f) | ((int) (nametagAlpha * 255) << 24));
                        }
                    }
                }
                if (playerRole == TrainRole.KILLER && targetRole == TrainRole.KILLER) {
                    context.pose().translate(0, 20 + renderer.lineHeight, 0);
                    if (component.canSeeKillerTeammate(player)) {
                        MutableComponent roleText = Component.translatable("game.tip.cohort");
                        int roleWidth = renderer.width(roleText);
                        context.drawString(renderer, roleText, -roleWidth / 2, 0,
                                Mth.color(1f, 0f, 0f) | ((int) (nametagAlpha * 255) << 24));
                    }

                }
            }
            context.pose().popPose();
        }
        if (ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof NoteEntity,
                range) instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof NoteEntity note) {
            noteAlpha = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 4, noteAlpha, 1f);
            nametagAlpha = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true), nametagAlpha, 0f);
            RoleNameRenderer.note[0] = Component.literal(note.getLines()[0]);
            RoleNameRenderer.note[1] = Component.literal(note.getLines()[1]);
            RoleNameRenderer.note[2] = Component.literal(note.getLines()[2]);
            RoleNameRenderer.note[3] = Component.literal(note.getLines()[3]);
        } else {
            noteAlpha = Mth.lerp(tickCounter.getGameTimeDeltaPartialTick(true) / 4, noteAlpha, 0f);
        }
        if (noteAlpha > 0.05f) {
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 6, 0);
            context.pose().scale(0.6f, 0.6f, 1f);
            for (int i = 0; i < note.length; i++) {
                Component line = note[i];
                int lineWidth = renderer.width(line);
                context.drawString(renderer, line, -lineWidth / 2, 16 + (i * (renderer.lineHeight + 2)),
                        Mth.color(1f, 1f, 1f) | ((int) (noteAlpha * 255) << 24));
            }
            context.pose().popPose();
        }
    }

    private enum TrainRole {
        KILLER,
        BYSTANDER
    }
}