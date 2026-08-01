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

package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.entity.CameraHolder;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
                                    serverPlayer1.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 20 *3, 0, true, false, true));
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
