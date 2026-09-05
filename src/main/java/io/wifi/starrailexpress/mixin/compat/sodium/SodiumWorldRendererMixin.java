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

package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.content.entity.FirecrackerEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium 的实体剔除（Use Entity Culling）按区块 section 的可见性图裁剪实体，
 * 只豁免发光/显示名牌的实体。关灯事件会在同一 tick 修改全图灯方块，触发大范围
 * 光照更新与 section 重建，期间静止的玩法实体（轮椅、尸体、鞭炮等）
 * 所在的 section 可能被瞬时判定为不可见，导致这些实体"消失"；玩家因为有名牌
 * 不受影响。这里只对数量很少、关灯会闪一下的关键玩法实体跳过 section 剔除，
 * 便签/掉落物走正常剔除（数量大，且便签自身已有距离/视锥剔除）。
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void sre$keepGameplayEntitiesVisible(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PlayerBodyEntity
                || entity instanceof PuppeteerBodyEntity
                || entity instanceof WheelchairEntity
                || entity instanceof FirecrackerEntity) {
            cir.setReturnValue(true);
        }
    }
}
