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

package org.agmas.noellesroles.mixin.client.roles.undead_lord;

import io.wifi.starrailexpress.content.item.RevolverItem;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity;
import org.agmas.noellesroles.content.entity.PigeonEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让左轮（及委托其目标检测的暗器）的准星可以锁定亡灵之主的亡灵实体（客户端目标检测）。
 * 复刻原版谓词并追加 {@link UndeadEntity}，命中后由 {@code UndeadGunPayloadMixin} 在服务端结算击杀。
 */
@Mixin(RevolverItem.class)
public class UndeadGunTargetMixin {

    @Inject(method = "getGunTarget", at = @At("HEAD"), cancellable = true)
    private static void allowUndeadTarget(Player user, CallbackInfoReturnable<HitResult> cir) {
        // 优先判定玩家等常规目标；绊线只作兜底，避免挡在玩家前面的绊线抢走子弹
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                        || entity instanceof PuppeteerBodyEntity
                        || entity instanceof PigeonEntity
                        || entity instanceof MorphlingKnifeDummyEntity
                        || entity instanceof UndeadEntity,
                20f);
        if (!(result instanceof net.minecraft.world.phys.EntityHitResult)) {
            // 没打中任何常规目标：再判定设陷者绊线（服务端由 TrapperTrapGunPayloadMixin 结算击落）
            HitResult wireResult = ProjectileUtil.getHitResultOnViewVector(user,
                    entity -> entity instanceof TripwireTrapEntity, 20f);
            if (wireResult instanceof net.minecraft.world.phys.EntityHitResult) {
                result = wireResult;
            }
        }
        cir.setReturnValue(result);
    }
}
