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

package org.agmas.noellesroles.mixin.roles.chino;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.agmas.noellesroles.role.anime.chino.ChinoHeadRideManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 卡布奇诺咖啡师抱着人时，绝不能因为一次传送把对方一起拖到世界另一端。
 *
 * <p>镜像双生之子的 {@code TwinChildrenTeleportMixin}：领域（咒术师角斗场、游记放逐、
 * 塔罗会）会把其中一人瞬移到两万格外，随后 {@code positionRider} / 每 tick 的摆位逻辑
 * 会把另一个人跟着拖过去，领域崩解时人就留在虚空里或被列车碾死。
 *
 * <p>这里在 {@code teleportTo} 之前松手：只要落点离对方超过
 * {@link ChinoHeadRideManager#MAX_RIDE_DISTANCE} 格，就先把乘骑状态解除再让传送生效。
 * 两个入口都要拦：7 参变体（技能 / 领域 / 命令传送，3 参变体也会走到这里）与
 * 5 参的跨维度变体。{@code onTeleportStarting} 是幂等的，第二个入口命中时已是空表，直接返回。
 */
@Mixin(ServerPlayer.class)
public abstract class ChinoHeadRideTeleportMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void noellesroles$releaseHeadRideOnFarTeleport(ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        ChinoHeadRideManager.onTeleportStarting((ServerPlayer) (Object) this, x, y, z);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"))
    private void noellesroles$releaseHeadRideOnFarDimensionTeleport(ServerLevel level, double x, double y, double z,
            float yRot, float xRot, CallbackInfo ci) {
        ChinoHeadRideManager.onTeleportStarting((ServerPlayer) (Object) this, x, y, z);
    }
}
