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

package io.wifi.starrailexpress.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.wifi.starrailexpress.disguise.EntityDisguise;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 实体伪装期间把玩家的眼高压到目标实体的眼高。
 * <p>
 * 碰撞箱（width / height）保持不变——地图是按人的尺寸做的；只改眼高，于是相机、准星射线、
 * 枪械命中判定这些读 {@code getEyeY()} 的地方一起下移，画面和命中点不会错开。
 * <p>
 * 查询走 {@link EntityDisguise#applyEyeHeight}：一次哈希查找，不创建实体、不枚举实体类型。
 * 服务端与客户端都生效（服务端命中判定与客户端相机一致）。
 */
@Mixin(Player.class)
public abstract class DisguisedPlayerDimensionsMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions sre$applyDisguisedEyeHeight(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        EntityDimensions disguised = EntityDisguise.applyEyeHeight(self, dimensions);
        return disguised == null ? dimensions : disguised;
    }
}
