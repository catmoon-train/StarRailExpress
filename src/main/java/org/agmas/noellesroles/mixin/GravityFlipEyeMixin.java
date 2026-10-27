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

package org.agmas.noellesroles.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 重力颠倒的眼位层。
 *
 * <p>倒立时玩家模型整体翻转：脚底朝上（撞到天花板），头部垂在碰撞箱<b>底部</b>。
 * 若沿用原版眼位（{@code getY() + 1.62}，碰撞箱顶部），相机就停在倒立模型的脚底，
 * 第一人称看到的便是脚底位置。因此把眼高改成从碰撞箱顶部量：
 * <pre>
 *     眼位 = getY() + 碰撞箱高 - 眼高   （站立玩家即 getY() + 0.18）
 * </pre>
 * 相机（{@code Camera#tick} 平滑读取 {@code getEyeHeight()}）、射线起点
 * （{@code getEyePosition()}）、声音/投射物发射点都随之下沉到头部位置。
 *
 * <p>注意：{@code getEyeHeight} 有 {@code ()} 与 {@code (Pose)} 两个重载，
 * 必须带描述符 {@code ()F} 指定无参版本——相机读的是无参版，
 * 而 {@code (Pose)} 版只服务睡觉/游泳等姿态计算，不能误伤。
 */
@Mixin(Entity.class)
public abstract class GravityFlipEyeMixin {

    /** 眼高：碰撞箱高 - 原眼高（站立玩家 1.8 - 1.62 = 0.18）。 */
    @Inject(method = "getEyeHeight()F", at = @At("RETURN"), cancellable = true)
    private void noellesroles$upsideDownEyeHeight(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (GravityFlipEffect.isFlipped(self)) {
            EntityDimensions dimensions = self.getDimensions(self.getPose());
            cir.setReturnValue(dimensions.height() - cir.getReturnValue());
        }
    }

    /** 眼位 Y：getY() + 碰撞箱高 - 原眼高。 */
    @Inject(method = "getEyeY()D", at = @At("RETURN"), cancellable = true)
    private void noellesroles$upsideDownEyeY(CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        if (GravityFlipEffect.isFlipped(self)) {
            EntityDimensions dimensions = self.getDimensions(self.getPose());
            cir.setReturnValue(self.getY() + dimensions.height() - dimensions.eyeHeight());
        }
    }
}
