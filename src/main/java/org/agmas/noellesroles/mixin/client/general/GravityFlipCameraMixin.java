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

package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 重力颠倒的视角层：把世界投影矩阵绕 Z 轴旋转 180°，
 * 整个视野随之一同倒转——天在下、地在上，左右相对观察者互换。
 *
 * <p>只旋转「世界」投影（{@code renderLevel} 里算给地形/实体/粒子的那个，
 * 即其中第一次 {@code getProjectionMatrix} 调用的结果）：
 * 第一人称的手部渲染会在 {@code renderItemInHand} 里另取一次投影矩阵，
 * 因此右手留在原地（右下角），不会跟着滚到左上角。
 *
 * <p>投影矩阵每帧新建，原地旋转不会跨帧累积；HUD / GUI 走各自投影，不受牵连。
 * 相机下沉（眼位改到碰撞箱顶部量）见 {@code GravityFlipEyeMixin}。
 */
@Mixin(GameRenderer.class)
public abstract class GravityFlipCameraMixin {

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;getProjectionMatrix(D)Lorg/joml/Matrix4f;",
                    ordinal = 0))
    private Matrix4f noellesroles$rollWorldProjection(GameRenderer instance, double fov,
            Operation<Matrix4f> original) {
        Matrix4f matrix = original.call(instance, fov);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && GravityFlipEffect.isFlipped(client.player)) {
            matrix.rotateZ((float) Math.PI);
        }
        return matrix;
    }
}
