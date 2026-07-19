package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.event.AllowNameRender;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
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
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 有一堆 events 在 OnRenderRoleName 里可以注册。请避免 mixin 和直接修改本类。
public class RoleNameRenderer {
    private static TrainRole targetRoleType = TrainRole.BYSTANDER;
    private static SRERole targetRole;
    private static Component roleText1;
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

    public static void renderHud(Font renderer, @NotNull LocalPlayer self, FakeGuiGraphics context,
            DeltaTracker tickCounter) {

        SREGameWorldComponent component = SREClient.gameComponent;
        if (component == null)
            return;

        if (component.isRunning()) {
            var result = OnRenderRoleName.RENDER_ALL.invoker().allowRender(self, context, tickCounter,
                    renderer);
            if (result.equals(TrueFalseResult.FALSE))
                return;
            if (result.equals(TrueFalseResult.PASS)) {
                if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(self)) {
                    if (self.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(self.getEyePosition())) < 3
                            && self.level().getBrightness(LightLayer.SKY,
                                    BlockPos.containing(self.getEyePosition())) < 10)
                        return;
                }
            }
        }

        Component nametag = Component.empty();
        final Component[] note = new Component[] { Component.empty(), Component.empty(), Component.empty(),
                Component.empty() };
        float range = getPlayerRange(self);
        if (component.isRunning()) {
            Optional<Float> result = OnRenderRoleName.RENDER_RANGE.invoker().getPlayerRange(self, range);
            range = result.orElse(range);
        }
        {
            OnRenderRoleName.RENDER_START.invoker().render(self, range, context, tickCounter, renderer);
        }
        {

            Player target = null;
            if (ProjectileUtil.getHitResultOnViewVector(self, entity -> entity instanceof Player,
                    range) instanceof EntityHitResult entityHitResult
                    && entityHitResult.getEntity() instanceof Player) {
                target = (Player) entityHitResult.getEntity();
                {
                    var result = OnRenderRoleName.RENDER_PLAYER.invoker().allowRender(self, target, context,
                            tickCounter, renderer);
                    if (result == TrueFalseResult.FALSE) {
                        targetRoleType = TrainRole.BYSTANDER;
                        targetRole = null;
                        nametag = Component.literal("");
                        return;
                    } else if (result == TrueFalseResult.PASS) {
                        if (!AllowNameRender.EVENT.invoker().allowRenderName(target)) {
                            targetRoleType = TrainRole.BYSTANDER;
                            targetRole = null;
                            nametag = Component.literal("");
                            return;
                        } else if (target.isInvisibleTo(self) && !GameUtils.isPlayerSpectatingOrCreative(self)) {
                            targetRoleType = TrainRole.BYSTANDER;
                            targetRole = null;
                            nametag = Component.literal("");
                            return;
                        }
                    }
                }
                {
                    var result = OnRenderRoleName.RENDER_PLAYER_NAME.invoker().allowRender(self, target, context,
                            tickCounter, renderer);
                    if (result.isFalse()) {
                        nametag = Component.empty();
                    } else if (result.isCustom()) {
                        nametag = result.getContent().orElse(Component.empty());
                    } else {
                        nametag = getDisplayName(target);
                    }
                }
                if (SREClient.modifierComponent != null) {
                    Component modifierText = Component.empty();
                    boolean shouldRender = false;
                    var result = OnRenderRoleName.RENDER_PLAYER_MODIFIER.invoker().allowRender(self, target, context,
                            tickCounter, renderer);
                    if (result.isFalse()) {
                        modifierText = Component.empty();
                        shouldRender = false;
                    } else if (result.isCustom()) {
                        modifierText = result.getContent().orElse(Component.empty());
                        shouldRender = true;
                    } else {
                        shouldRender = GameUtils.isPlayerSpectatingOrCreative(self);
                        MutableComponent temp = Component.literal("");
                        var modifiers = SREClient.modifierComponent.getModifiers(target);
                        for (SREModifier modifier : modifiers) {
                            temp = temp.append(Component.translatable(" [%s]", modifier.getName())
                                    .withColor(modifier.color));
                        }
                        modifierText = temp;
                    }
                    if (shouldRender) {
                        nametag = nametag.copy().append(modifierText);
                    }
                }
                if (component.canUseKillerFeatures(target)) {
                    targetRoleType = TrainRole.KILLER;
                } else if (component.isNeutralForKiller(target)) {
                    targetRoleType = TrainRole.KILLER;
                } else {
                    targetRoleType = TrainRole.BYSTANDER;
                }

                var role = component.getRole(target);
                if (role != null) {
                    targetRole = role;
                }
                context.pose().pushPose();
                context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f + 6, 0);
                context.pose().scale(0.6f, 0.6f, 1f);
                int nameWidth = renderer.width(nametag);
                context.drawString(renderer, nametag, -nameWidth / 2, 16,
                        Mth.color(1f, 1f, 1f) | ((int) (1 * 255) << 24));
                // 游戏未开始且不在大厅时，在名字下方提示该玩家是否参与本局
                if (!component.isRunning() && !SREClient.isInLobby()
                        && !ParticipationComponent.KEY.get(self.level()).isParticipating(target)) {
                    MutableComponent partTag = Component
                            .translatable("hud.sre.participation.not_participating")
                            .withStyle(ChatFormatting.GOLD);
                    int partWidth = renderer.width(partTag);
                    context.drawString(renderer, partTag, -partWidth / 2, 16 + renderer.lineHeight + 2,
                            Mth.color(1f, 0.69f, 0f) | (255 << 24));
                }
                {
                    TrainRole selfRoleType = TrainRole.BYSTANDER;
                    if (component.canUseKillerFeatures(self))
                        selfRoleType = TrainRole.KILLER;
                    if (component.isNeutralForKiller(self))
                        selfRoleType = TrainRole.KILLER;
                    if (targetRole != null) {
                        boolean allowRenderRole = true;
                        {
                            var result = OnRenderRoleName.RENDER_PLAYER_ROLE.invoker().allowRender(self, target,
                                    context,
                                    tickCounter, renderer);
                            if (result.isFalse()) {
                                roleText1 = null;
                                allowRenderRole = false;
                            } else if (result.isCustom()) {
                                roleText1 = result.getContent().orElse(Component.empty());
                            } else {
                                allowRenderRole = targetRoleType.equals(TrainRole.KILLER)
                                        && selfRoleType.equals(TrainRole.KILLER)
                                        && component.canSeeKillerTeammate(self);
                                roleText1 = RoleUtils.getRoleName(targetRole.identifier());
                            }
                        }
                        if (allowRenderRole) {
                            context.pose().translate(0, 20 + renderer.lineHeight, 0);
                            if (roleText1 != null) {
                                int roleWidth1 = renderer.width(roleText1);
                                context.drawString(renderer, roleText1, -roleWidth1 / 2, 0,
                                        Mth.color(1f, 0f, 0f) | ((int) (1 * 255) << 24));
                            }
                        }
                    }
                    {
                        Component cohortText = Component.translatable("game.tip.cohort");
                        boolean allowRenderCohort = true;
                        {
                            var result = OnRenderRoleName.RENDER_PLAYER_COHORT.invoker().allowRender(self, target,
                                    context,
                                    tickCounter, renderer);
                            if (result.isFalse()) {
                                allowRenderCohort = false;
                            } else if (result.isCustom()) {
                                cohortText = result.getContent().orElse(cohortText);
                            } else {
                                allowRenderCohort = selfRoleType == TrainRole.KILLER
                                        && targetRoleType == TrainRole.KILLER && component.canSeeKillerTeammate(self);
                            }
                        }
                        if (allowRenderCohort) {
                            context.pose().translate(0, 20 + renderer.lineHeight, 0);
                            int roleWidth = renderer.width(cohortText);
                            context.drawString(renderer, cohortText, -roleWidth / 2, 0,
                                    Mth.color(1f, 0f, 0f) | ((int) (255) << 24));
                        }
                    }
                    {
                        OnRenderRoleName.RENDER_PLAYER_EXTRA.invoker().renderExtra(self, target, context, tickCounter,
                                renderer);
                    }
                }
                context.pose().popPose();
            }
        }
        {

            if (ProjectileUtil.getHitResultOnViewVector(self, entity -> entity instanceof PuppeteerBodyEntity,
                    range) instanceof EntityHitResult entityHitResult
                    && entityHitResult.getEntity() instanceof PuppeteerBodyEntity pbe) {
                {
                    TrueFalseResult result = OnRenderRoleName.RENDER_PUPPETEER.invoker().allowRender(self, pbe,
                            context, tickCounter,
                            renderer);
                    if (result.isPass()) {
                        if (!GameUtils.isPlayerSpectatingOrCreative(self)) {
                            return;
                        }
                    } else if (result.isFalse()) {
                        return;
                    }
                }

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
        if (ProjectileUtil.getHitResultOnViewVector(self, entity -> entity instanceof NoteEntity,
                range) instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof NoteEntity notee) {
            {
                TrueFalseResult result = OnRenderRoleName.RENDER_NOTE.invoker().allowRender(self, notee,
                        context, tickCounter,
                        renderer);
                if (result.isFalse()) {
                    return;
                }
            }
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
        {
            OnRenderRoleName.RENDER_END.invoker().render(self, range, context, tickCounter, renderer);
        }
    }

    private static Component getDisplayName(Player target) {
        return target.getDisplayName();
    }

    public enum TrainRole {
        KILLER,
        BYSTANDER
    }
}