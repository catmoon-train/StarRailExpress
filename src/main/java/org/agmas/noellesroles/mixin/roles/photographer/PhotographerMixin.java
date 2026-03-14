package org.agmas.noellesroles.mixin.roles.photographer;

import io.wifi.starrailexpress.game.GameUtils;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.component.AdmirerPlayerComponent.GAZE_ANGLE;
import static org.agmas.noellesroles.component.AdmirerPlayerComponent.GAZE_DISTANCE;

@Mixin(CameraItem.class)
public class PhotographerMixin {
    @Inject(
            method = "takePhoto",
            at = @At("HEAD")
    )
    public void noe$take(CameraHolder holder, ServerPlayer executingPlayer, ItemStack stack, CallbackInfo ci) {
        final var holderEntity = holder.asHolderEntity();
        if (holderEntity instanceof ServerPlayer serverPlayer){
            serverPlayer.serverLevel().players().forEach(
                    serverPlayer1 -> {
                        if (serverPlayer1!=serverPlayer){
                            if (GameUtils.isPlayerAliveAndSurvival(serverPlayer1)){
                                if (isBoundTargetVisible(serverPlayer1, serverPlayer)){
                                    serverPlayer1.sendSystemMessage(
                                            Component.translatable("message.noellesroles.photographer.blindness"),
                                            true);
                                }
                                if (isBoundTargetVisible(serverPlayer1, serverPlayer)){
                                    if (serverPlayer1.hasEffect(MobEffects.INVISIBILITY)){
                                        serverPlayer1.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 *6, 0, true, false, true));

                                    }
                                    serverPlayer1.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 20 *3, 0, true, false, true));
                                }
                            }
                        }
                    }
            );
        }
    }

    private boolean isBoundTargetVisible(Player boundTarget , Player player) {

        if (boundTarget == null)
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(boundTarget))
            return false;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 targetPos = boundTarget.getEyePosition();

        double distance = eyePos.distanceTo(targetPos);
        if (distance > 12)
            return false;

        // 视野角度检查（70度扇形，半角35度）
        Vec3 toTarget = targetPos.subtract(eyePos).normalize();
        double dot = lookDir.dot(toTarget);
        if (dot < Math.cos(Math.toRadians(70)))
            return false;

        // 射线检测
        Level world = player.level();
        ClipContext context = new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player);
        BlockHitResult hit = world.clip(context);
        return hit.getType() == HitResult.Type.MISS ;
    }

}
