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

package org.agmas.noellesroles.mixin.client.roles.puppeteer;

import io.wifi.starrailexpress.content.item.KnifeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让刀可以攻击傀儡本体实体（客户端目标检测）
 */
@Mixin(KnifeItem.class)
public class PuppeteerBodyKnifeTargetMixin {
    
    @Inject(method = "getKnifeTarget", at = @At("HEAD"), cancellable = true)
    private static void allowPuppeteerBodyTarget(Player user, CallbackInfoReturnable<HitResult> cir) {
        // 扩展目标检测，包含 PuppeteerBodyEntity
        HitResult result = ProjectileUtil.getHitResultOnViewVector(user,
            entity -> (entity instanceof Player player && player.isAlive() && !player.isSpectator())
                    || entity instanceof PuppeteerBodyEntity,
            4f);
        cir.setReturnValue(result);
    }
}