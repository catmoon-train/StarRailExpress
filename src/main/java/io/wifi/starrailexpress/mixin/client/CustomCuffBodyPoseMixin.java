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

package io.wifi.starrailexpress.mixin.client;

import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「自定义手铐」被拷住时的整体身体姿势（坐下 / 游泳）。
 *
 * <p>
 * 只作用于客户端：被铐住的人自己与旁观者的画面都由各自客户端根据同步过来的手铐配置渲染，
 * 服务端的实体 Pose 保持原样（不影响判定），与
 * {@code org.agmas.noellesroles.mixin.effects.FearSitPoseMixin}（恐惧坐姿）、
 * {@code ...mixin.roles.tomato_head.SwimPoseEffectMixin}（游泳姿势）同一套做法。
 */
@Mixin(Player.class)
public class CustomCuffBodyPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void sre$customCuffBodyPose(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        CustomItemData data = CustomItemRuntime.getCuffData(player);
        if (data == null || data.kind() != CustomItemData.Kind.CUFF) {
            return;
        }
        switch (data.cuffPose()) {
            case SIT -> {
                player.setPose(Pose.SITTING);
                ci.cancel();
            }
            case SWIM -> {
                player.setPose(Pose.SWIMMING);
                ci.cancel();
            }
            default -> {
                // 其余姿势只改四肢，不动整体 Pose
            }
        }
    }
}
