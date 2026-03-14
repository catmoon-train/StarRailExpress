package org.agmas.noellesroles.mixin.roles.patroller;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PatrollerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.component.AdmirerPlayerComponent.GAZE_ANGLE;
import static org.agmas.noellesroles.component.AdmirerPlayerComponent.GAZE_DISTANCE;

import org.agmas.harpymodloader.component.WorldModifierComponent;

@Mixin(GameUtils.class)
public class PatrollerKillMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"))
    private static void onKillPlayer(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason,
            CallbackInfo ci) {
        if (victim == null || victim.level().isClientSide())
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(victim.level());
        if (gameWorld == null || !gameWorld.isRunning())
            return;

        // 遍历所有玩家，查找附近的巡警
        for (Player player : victim.level().players()) {
            // 排除受害者自己（虽然巡警死了也不能触发能力，但以防万一）
            if (player.equals(victim))
                continue;
            // 检查是否是巡警
            if (!gameWorld.isRole(player, ModRoles.PATROLLER))
                continue;
            
            // 检查是否存活
            if (modifierComponent != null && modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
                var splc = SplitPersonalityComponent.KEY.get(player);
                if (splc != null) {
                    if (splc.isDeath())
                        continue;
                }
            } else {
                if (!GameUtils.isPlayerAliveAndSurvival(player))
                    continue;
            }

            // 检查距离（50格内）
            if (player.distanceToSqr(victim) > 50 * 50 || !isBoundTargetVisible(victim, player))
                continue;

            if (player.hasLineOfSight(victim)) {
                PatrollerPlayerComponent patrollerComponent = ModComponents.PATROLLER.get(player);
                patrollerComponent.onNearbyDeath();
            }
        }
    }

    @Unique
    private static boolean isBoundTargetVisible(Player boundTarget, Player player) {

        if (boundTarget == null)
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(boundTarget))
            return false;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 targetPos = boundTarget.getEyePosition();

        double distance = eyePos.distanceTo(targetPos);
        if (distance > GAZE_DISTANCE)
            return false;

        // 视野角度检查（90度扇形，半角45度）
        Vec3 toTarget = targetPos.subtract(eyePos).normalize();
        double dot = lookDir.dot(toTarget);
        if (dot < Math.cos(Math.toRadians(GAZE_ANGLE)))
            return false;

        // 射线检测
        Level world = player.level();
        ClipContext context = new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = world.clip(context);
        return hit.getType() == HitResult.Type.MISS ||
                hit.getLocation().distanceTo(targetPos) < 1.0;
    }

}