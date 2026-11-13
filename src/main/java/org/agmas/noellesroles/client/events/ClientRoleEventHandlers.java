package org.agmas.noellesroles.client.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.ItemClientActions;
import io.wifi.starrailexpress.client.PlateTrayClientEffects;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.HudMoodRenderer;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.event.ItemGameplayEvents;
import io.wifi.starrailexpress.event.client.ClientGameplayEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.CommandBlockProgressClient;
import org.agmas.noellesroles.client.IlliterateTextClientHandle;
import org.agmas.noellesroles.client.hud.InventoryIntroductionRenderer;
import org.agmas.noellesroles.client.scene.SceneFogClient;
import org.agmas.noellesroles.client.screen.FilterSelectionScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.game.command_block.CommandBlockCracks;
import org.agmas.noellesroles.game.modifier.fatskinny.FatSkinnyVisual;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.SaltedFishRoleData;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.agmas.noellesroles.role_data.special.BetterVigilanteRoleData;

/** 职业、修饰符、效果与活动的客户端监听器；仅由客户端入口统一注册一次。 */
@Environment(EnvType.CLIENT)
public final class ClientRoleEventHandlers {
    private static boolean registered;

    private ClientRoleEventHandlers() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ItemClientActions.initialize();
        PlateTrayClientEffects.register();
        InventoryIntroductionRenderer.register();
        ClientGameplayEvents.DISGUISE_STATE_COPIED.register(org.agmas.noellesroles.client.MustardPuppetAnimation::apply);

        // 和旧客户端 mixin 一样，也覆盖客户端进程内的集成服务器调用。
        GameUtilityEvents.ALIVE_OVERRIDE.register(player -> PelicanManager.isStashed(player)
                ? TrueFalseResult.TRUE : TrueFalseResult.PASS);
        ClientGameplayEvents.INSTINCT_OVERRIDE.register(ClientRoleEventHandlers::instinctOverride);
        ClientGameplayEvents.TIME_VISIBILITY.register((player, visible) -> visible
                || player != null && player.hasEffect(ModEffects.TIME_REVEAL));
        ClientGameplayEvents.STATUS_BAR_RENDER_HEAD.register((graphics, delta) -> {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.hasEffect(ModEffects.TIME_STOP)) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 30, 0);
            }
        });
        ClientGameplayEvents.STATUS_BAR_RENDER_RETURN.register((graphics, delta) -> {
            // RETURN 时重新查询，与原钩子一致；不是异常 finally 清理。
            Player player = Minecraft.getInstance().player;
            if (player != null && player.hasEffect(ModEffects.TIME_STOP)) graphics.pose().popPose();
        });
        // 原同优先级 HEAD 的配置顺序：故障机器人在小丑之前，首个处理者取消后续渲染。
        ClientGameplayEvents.KILLER_MOOD_RENDER.register((font, graphics, color, role) -> {
            Player player = Minecraft.getInstance().player;
            var game = SREGameWorldComponent.KEY.get(player.level());
            if (!game.isRole(player, ModRoles.GLITCH_ROBOT)) return false;
            renderSpecialMood(graphics, HudMoodRenderer.MOOD_HAPPY, ModRoles.GLITCH_ROBOT.color());
            return true;
        });
        ClientGameplayEvents.KILLER_MOOD_RENDER.register((font, graphics, color, role) -> {
            Player player = Minecraft.getInstance().player;
            var game = SREGameWorldComponent.KEY.get(player.level());
            if (!game.isRole(player, ModRoles.JESTER) && !game.isRole(player, ModRoles.MONOKUMA)) return false;
            renderSpecialMood(graphics, HudMoodRenderer.MOOD_JESTER, ModRoles.JESTER.color());
            return true;
        });
        ClientGameplayEvents.BODY_ROTATIONS.register((body, pose, animation, yaw, delta, scale) ->
                rotateSaltedFish(body, pose, yaw, delta));
        ClientGameplayEvents.BODY_SCALE.register((body, pose, amount) -> {
            float scale = FatSkinnyVisual.horizontalScale(body);
            if (scale != 1.0F) pose.scale(scale, 1.0F, scale);
        });
        // 旧秃鹫回调在 TAIL，不能提前隐藏已经绘制的身体。
        ClientGameplayEvents.BODY_RENDER_TAIL.register((body, yaw, delta, pose, buffers, light, alpha) ->
                PlayerBodyEntityComponent.KEY.get(body).vultured);
        ClientGameplayEvents.ALLOW_MAP_INTRO.register(id -> !CommandBlockProgressClient.isShowing());
        ClientGameplayEvents.ALLOW_ENTITY_FOCUS.register((id, duration, blackBars) ->
                !(CommandBlockProgressClient.isShowing() && AdvancedCameraDirector.isTrackActive()));
        ClientGameplayEvents.SCREEN_RENDER.register(ClientRoleEventHandlers::renderReadableScreen);

        ItemGameplayEvents.KNIFE_TARGET.register(ClientRoleEventHandlers::getKnifeTarget);
        ItemGameplayEvents.REVOLVER_TARGET.register(ClientRoleEventHandlers::getGunTarget);
        ItemGameplayEvents.STANDARD_REVOLVER_TARGET.register((user, result) ->
                CommandBlockCracks.prefer(user, result, 20.0F));
        ResourceLocation roleParticles = Noellesroles.id("plate_tray_role_particles");
        ItemGameplayEvents.PLATE_TRAY_CLIENT_TICK.addPhaseOrdering(roleParticles, Event.DEFAULT_PHASE);
        ItemGameplayEvents.PLATE_TRAY_CLIENT_TICK.register(roleParticles, (world, pos, state, tray) ->
                renderRoleParticles(world, pos, tray));
    }

    private static TrueFalseResult instinctOverride(Player player) {
        if (player == null) return TrueFalseResult.PASS;
        // 迷雾、鹈鹕、操控、禁本能效果均先于绝境自警的强制开启。
        if (SceneFogClient.isLocalPlayerInFog()) return TrueFalseResult.FALSE;
        if (PelicanManager.isStashed(player)) return TrueFalseResult.FALSE;
        if (isPlayerBeingControlled(player)) return TrueFalseResult.FALSE;
        if (player.hasEffect(ModEffects.EERIE_DOMAIN) || player.hasEffect(ModEffects.NO_INSTINCT)) {
            return TrueFalseResult.FALSE;
        }
        var game = SREGameWorldComponent.KEY.get(player.level());
        if (game.isRole(player, ModRoles.BETTER_VIGILANTE)) {
            var data = RoleData.getNullable(BetterVigilanteRoleData.class, player);
            if (data != null && data.lastStandActivated) return TrueFalseResult.TRUE;
        }
        return TrueFalseResult.PASS;
    }

    private static boolean isPlayerBeingControlled(Player player) {
        for (Player other : player.level().players()) {
            var game = SREGameWorldComponent.KEY.get(other.level());
            if (game.isRole(other, ModRoles.MANIPULATOR)) {
                var data = RoleData.getNullable(ManipulatorRoleData.class, other);
                if (data != null && data.isControlling && data.target != null
                        && data.target.equals(player.getUUID())) return true;
            }
        }
        return false;
    }

    private static void renderSpecialMood(FakeGuiGraphics graphics, ResourceLocation icon, int color) {
        // 保留 HEAD 替换：不钳制 moodRender，固定字高为 9，职业色 alpha 不做掩码。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 3.0F * HudMoodRenderer.moodOffset, 0.0F);
        graphics.blitSprite(icon, 5, 6, 14, 17);
        graphics.pose().popPose();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 10.0F * HudMoodRenderer.moodOffset, 0.0F);
        graphics.pose().translate(26.0F, (float) (8 + 9), 0.0F);
        graphics.pose().scale(HudMoodRenderer.MOOD_BAR_WIDTH * HudMoodRenderer.moodRender, 1.0F, 1.0F);
        graphics.fill(0, 0, 1, 1, color | (int) (HudMoodRenderer.moodAlpha * 255.0F) << 24);
        graphics.pose().popPose();
    }

    private static boolean rotateSaltedFish(PlayerBodyEntity body, PoseStack pose, float yaw, float delta) {
        if (!(body instanceof SaltedFishBodyEntity)) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || body.getPlayerUuid() == null) return false;
        Player owner = client.level.getPlayerByUUID(body.getPlayerUuid());
        var data = owner == null ? null : RoleData.getNullable(SaltedFishRoleData.class, owner);
        if (data == null || !data.isActive()) return false;
        pose.mulPose(Axis.YP.rotationDegrees(90.0f - yaw));
        pose.translate(1.0f, 0.2f + data.getRenderBounce(delta), 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(90.0f));
        pose.mulPose(Axis.XP.rotationDegrees(data.getRenderRoll(delta)));
        pose.mulPose(Axis.YP.rotationDegrees(90.0f));
        return true;
    }

    private static void renderReadableScreen(Screen screen, Runnable original) {
        if (!(screen instanceof RoleIntroduceScreen) && !(screen instanceof FilterSelectionScreen)) {
            original.run();
            return;
        }
        boolean previous = IlliterateTextClientHandle.scramble;
        IlliterateTextClientHandle.scramble = false;
        try {
            original.run();
        } finally {
            // 嵌套绘制父屏幕及异常退出均按进入前的状态恢复。
            IlliterateTextClientHandle.scramble = previous;
        }
    }

    private static HitResult getKnifeTarget(Player user) {
        // 原客户端 HEAD 完全替换：玩家不走 GameUtils 判活，允许傀儡本体与裂缝。
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> (entity instanceof Player player && player.isAlive() && !player.isSpectator())
                        || entity instanceof PuppeteerBodyEntity || CommandBlockCracks.isCrack(entity),
                4f);
    }

    private static HitResult getGunTarget(Player user) {
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user,
                entity -> io.wifi.starrailexpress.event.TargetingEvents.ALLOW_GUN_TARGET.invoker().allow(user, entity)
                        && (entity instanceof Player player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        || entity instanceof PuppeteerBodyEntity || entity instanceof PigeonEntity
                        || entity instanceof MorphlingKnifeDummyEntity || entity instanceof UndeadEntity),
                20f);
        if (!(result instanceof EntityHitResult)) {
            HitResult wire = ProjectileUtil.getHitResultOnViewVector(user,
                    entity -> entity instanceof TripwireTrapEntity, 20f);
            if (wire instanceof EntityHitResult) result = wire;
        }
        return CommandBlockCracks.prefer(user, result, 20.0F);
    }

    private static boolean renderRoleParticles(Level world, BlockPos pos, PlateTrayBlockEntity tray) {
        if (tray.getPoisoner() != null) {
            SRERole role = SREClient.gameComponent.getRole(Minecraft.getInstance().player);
            if (role == null) return false;
            boolean canSeePoison = role.identifier().getPath().equals(ModRoles.BARTENDER.identifier().getPath())
                    || role.identifier().getPath().equals(ModRoles.WAITER.identifier().getPath())
                    || role.identifier().getPath().equals(ModRoles.POISONER.identifier().getPath());
            if (!canSeePoison && SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
                if (world.players().stream().anyMatch(p -> GameUtils.isPlayerAliveAndSurvival(p)
                        && SREClient.gameComponent.isRole(p, ModRoles.POISONER))) canSeePoison = true;
            }
            if (canSeePoison) {
                world.addParticle(TMMParticles.POISON, true, (double) ((float) pos.getX() + 0.5F),
                        (double) pos.getY(), (double) ((float) pos.getZ() + 0.5F), 0.0D, (double) 0.15F, 0.0D);
                return true;
            }
        }
        if (tray.getArmorer() != null || tray.getWeakArmorer() != null || tray.getVirtualArmorer() != null) {
            SRERole role = SREClient.gameComponent.getRole(Minecraft.getInstance().player);
            if (role == null) return false;
            if (role.identifier().getPath().equals(ModRoles.BARTENDER.identifier().getPath())) {
                world.addParticle(ParticleTypes.EFFECT, true, (double) ((float) pos.getX() + 0.5F),
                        (double) pos.getY(), (double) ((float) pos.getZ() + 0.5F), 0.0D, (double) 0.15F, 0.0D);
                return true;
            }
        }
        return false;
    }
}
