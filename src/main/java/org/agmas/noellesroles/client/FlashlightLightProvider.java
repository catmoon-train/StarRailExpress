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

package org.agmas.noellesroles.client;

import com.mojang.math.Axis;

import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Range;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;

public class FlashlightLightProvider implements DynamicLightBehavior {

    // ===== 可调参数 =====
    private static final float RADIUS = 5.0F; // 锥体底部半径（方块单位）
    private static final float DEPTH = 10.0F; // 锥体深度（长度）
    private static final float DISTANCE_DELTA = 0.5F; // 锥体起点前移偏移（让光源从眼睛前方开始）

    // ===== 实体与缓存 =====
    private final Entity entity;
    private Matrix3d rotationMatrix; // 世界 → 实体空间
    private Matrix3d inverseRotationMatrix; // 实体空间 → 世界

    // 状态变化跟踪
    private double prevX, prevY, prevZ;
    private float prevYaw, prevPitch;

    public FlashlightLightProvider(Entity entity) {
        this.entity = entity;
        this.computeMatrices();
    }

    // ============================================
    // 核心方法：计算某个方块位置的光照强度 (0~15)
    // ============================================
    @Override
    public @Range(from = 0L, to = 15L) double lightAtPos(BlockPos pos, double falloffRatio) {
        // 1. 取方块中心点
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        Vector3d worldPos = new Vector3d(x, y, z);

        // 2. 转换到实体空间
        Vector3d local = this.worldToEntitySpace(worldPos);

        // 3. 计算到锥体中心轴 (Y轴) 的距离
        double distAxis = Math.sqrt(local.x * local.x + local.z * local.z);

        // 4. 计算锥体符号距离函数 (SDF) > 0 表示在锥体内部
        // 锥体顶点位于 local.y = DEPTH/2 - DISTANCE_DELTA 处
        double sdf = Math.min(
                RADIUS * (0.5 - local.y / DEPTH) - distAxis, // 锥体侧面
                DEPTH * 0.5 - Math.abs(local.y) // 限制前后范围
        );

        // 5. 如果在锥体外，直接返回 0
        if (sdf < 0) {
            return 0;
        }

        // 6. 计算亮度：距离顶点越近越亮（距离衰减指数 1.5）
        double distance = DEPTH / 2 - local.y - DISTANCE_DELTA;
        double intensity = DEPTH / Math.pow(distance, 1.5);
        double light = intensity * 15.0;

        // 7. 用 smoothstep 对边缘进行柔和过渡
        double factor = Mth.smoothstep(sdf);
        return Math.clamp(factor * light, 0.0, 15.0);
    }

    // ============================================
    // 计算光照影响范围（用于性能优化）
    // ============================================
    @Override
    public BoundingBox getBoundingBox() {
        // 在实体空间中构建一个包含整个锥体的包围盒，然后转换到世界空间
        double[] horizontal = { -RADIUS, RADIUS };
        double[] vertical = { -Math.ceil(DEPTH / 2), Math.floor(DEPTH / 2) };
        var vectors = new ArrayList<Vector3d>();

        for (double hx : horizontal) {
            for (double vy : vertical) {
                for (double hz : horizontal) {
                    vectors.add(this.entityToWorldSpace(new Vector3d(hx, vy, hz)));
                }
            }
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (var v : vectors) {
            if (v.x < minX)
                minX = v.x;
            if (v.y < minY)
                minY = v.y;
            if (v.z < minZ)
                minZ = v.z;
            if (v.x > maxX)
                maxX = v.x;
            if (v.y > maxY)
                maxY = v.y;
            if (v.z > maxZ)
                maxZ = v.z;
        }

        return new BoundingBox(
                Mth.floor(minX), Mth.floor(minY), Mth.floor(minZ),
                Mth.ceil(maxX), Mth.ceil(maxY), Mth.ceil(maxZ));
    }

    // ============================================
    // 检测实体状态是否变化（触发矩阵更新）
    // ============================================
    @Override
    public boolean hasChanged() {
        if (Math.abs(entity.getX() - prevX) >= 0.1 ||
                Math.abs(entity.getY() - prevY) >= 0.1 ||
                Math.abs(entity.getZ() - prevZ) >= 0.1 ||
                Math.abs(entity.getYRot() - prevYaw) >= 0.1 ||
                Math.abs(entity.getXRot() - prevPitch) >= 0.1) {

            this.prevX = entity.getX();
            this.prevY = entity.getY();
            this.prevZ = entity.getZ();
            this.prevYaw = entity.getYRot();
            this.prevPitch = entity.getXRot();

            this.computeMatrices();
            return true;
        }
        return false;
    }

    // ============================================
    // 矩阵计算（将世界坐标转换到“视线为 -Y”的实体空间）
    // ============================================
    private void computeMatrices() {
        // 参考 Illuminated 的实现，此旋转序列经过实践验证
        var matrix = new Matrix3d();
        matrix.rotate(Axis.ZP.rotationDegrees(entity.getXRot())); // 俯仰
        matrix.rotate(Axis.ZN.rotation(Mth.HALF_PI)); // 调整轴
        matrix.rotate(Axis.YP.rotationDegrees(entity.getYRot())); // 偏航
        matrix.rotate(Axis.YP.rotation(Mth.HALF_PI)); // 最终调整
        this.rotationMatrix = matrix;
        this.inverseRotationMatrix = matrix.invert(new Matrix3d());
    }

    // ---- 坐标转换辅助 ----
    private Vector3d worldToEntitySpace(Vector3d in) {
        in.sub(entity.getX(), entity.getEyeY(), entity.getZ());
        in.mul(this.rotationMatrix);
        in.y += DEPTH / 2 - DISTANCE_DELTA; // 平移到锥体顶点
        return in;
    }

    private Vector3d entityToWorldSpace(Vector3d in) {
        in.y -= DEPTH / 2 - DISTANCE_DELTA;
        in.mul(this.inverseRotationMatrix);
        in.add(entity.getX(), entity.getEyeY(), entity.getZ());
        return in;
    }
}