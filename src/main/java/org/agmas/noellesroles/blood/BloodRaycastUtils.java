package org.agmas.noellesroles.blood;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 血液效果射线投射工具类
 * 用于处理玩家与目标边界框之间的射线检测和反射计算
 * 仅在客户端环境下运行
 */
@Environment(EnvType.CLIENT)
public class BloodRaycastUtils {
    /**
     * 工具类构造函数，无需实例化
     */
    public BloodRaycastUtils() {
    }

    /**
     * 从玩家视线方向投射射线到目标边界框
     * 
     * @param player 执行射线投射的玩家（可为null）
     * @param targetPos 目标位置，将围绕此位置创建边界框
     * @return 射线检测结果，包含命中点、反射方向和表面法线；如果没有命中则返回null
     */
    public static @Nullable RaycastResult raycastToPlayerBox(Player player, Vec3 targetPos) {
        // 创建一个以目标位置为中心的边界框，模拟玩家尺寸（宽0.6米，高1.8米）
        AABB targetBox = new AABB(targetPos.x - 0.3, targetPos.y, targetPos.z - 0.3, targetPos.x + 0.3,
                targetPos.y + 1.8, targetPos.z + 0.3);
        
        RaycastResult result;
        Vec3 eyePos;
        
        if (player != null) {
            // 情况1：存在玩家时，从玩家眼睛位置沿视线方向投射射线
            eyePos = player.getEyePosition();
            // 获取玩家的视线方向向量
            Vec3 lookVec = player.getViewVector(1.0F);
            // 计算射线终点（沿视线方向延伸100个单位）
            Vec3 endPos = eyePos.add(lookVec.scale(100.0));
            // 尝试进行射线与边界框的碰撞检测
            result = raycastBox(eyePos, endPos, targetBox);
            if (result != null) {
                return result; // 如果命中，直接返回结果
            }

            // 情况2：如果第一次未命中，尝试从眼睛位置指向边界框中心
            Vec3 boxCenter = targetBox.getCenter();
            result = raycastBox(eyePos, boxCenter, targetBox);
        } else {
            // 情况3：没有玩家时，使用边界框中心作为起点和终点（退化情况）
            eyePos = targetBox.getCenter();
            result = raycastBox(eyePos, eyePos, targetBox);
        }

        return result;
    }

    /**
     * 执行射线与边界框的碰撞检测，并计算反射方向
     * 
     * @param start 射线起点
     * @param end 射线终点
     * @param box 待检测的边界框
     * @return 射线检测结果，如果未命中则返回null
     */
    private static @Nullable RaycastResult raycastBox(Vec3 start, Vec3 end, AABB box) {
        // 使用边界框的clip方法计算射线与框的交点
        Vec3 hit = (Vec3) box.clip(start, end).orElse(null);
        if (hit == null) {
            return null; // 未命中边界框
        } else {
            // 计算命中点处的表面法线
            Vec3 surfaceNormal = calculateBoxNormal(hit, box);
            // 计算入射方向（从起点指向终点，并归一化）
            Vec3 incomingDir = end.subtract(start).normalize();
            // 计算入射方向与法线的点积
            double dotProduct = incomingDir.dot(surfaceNormal);
            // 根据反射公式计算反射方向：R = I - 2*(I·N)*N
            Vec3 bounceDir = incomingDir.subtract(surfaceNormal.scale(2.0 * dotProduct));
            // 返回包含命中点、反射方向和表面法线的结果对象
            return new RaycastResult(hit, bounceDir, surfaceNormal);
        }
    }

    /**
     * 计算边界框上某点的表面法线
     * 通过判断命中点接近哪个面来确定法线方向
     * 
     * @param hitPos 命中点的位置
     * @param box 边界框
     * @return 命中点所在表面的法线向量
     */
    private static Vec3 calculateBoxNormal(Vec3 hitPos, AABB box) {
        double epsilon = 1.0E-4; // 容差值，用于判断点是否在表面上
        
        // 检查命中点接近哪个面，返回相应的法线
        if (Math.abs(hitPos.x - box.minX) < epsilon) {
            return new Vec3(-1.0, 0.0, 0.0); // 左面（X负方向）
        } else if (Math.abs(hitPos.x - box.maxX) < epsilon) {
            return new Vec3(1.0, 0.0, 0.0); // 右面（X正方向）
        } else if (Math.abs(hitPos.y - box.minY) < epsilon) {
            return new Vec3(0.0, -1.0, 0.0); // 底面（Y负方向）
        } else if (Math.abs(hitPos.y - box.maxY) < epsilon) {
            return new Vec3(0.0, 1.0, 0.0); // 顶面（Y正方向）
        } else if (Math.abs(hitPos.z - box.minZ) < epsilon) {
            return new Vec3(0.0, 0.0, -1.0); // 后面（Z负方向）
        } else {
            // 默认返回顶面法线（如果未能匹配其他面）
            return Math.abs(hitPos.z - box.maxZ) < epsilon ? new Vec3(0.0, 0.0, 1.0) : new Vec3(0.0, 1.0, 0.0);
        }
    }

    /**
     * 射线检测结果类
     * 用于存储射线与边界框碰撞的相关信息
     */
    @Environment(EnvType.CLIENT)
    public static class RaycastResult {
        /** 射线命中点的位置 */
        public final Vec3 hitPosition;
        /** 反射方向向量 */
        public final Vec3 normal;
        /** 命中点所在表面的法线向量 */
        public final Vec3 surface;

        /**
         * 构造函数
         * 
         * @param hitPosition 命中点位置
         * @param normal 反射方向
         * @param surface 表面法线
         */
        public RaycastResult(Vec3 hitPosition, Vec3 normal, Vec3 surface) {
            this.hitPosition = hitPosition;
            this.normal = normal;
            this.surface = surface;
        }
    }
}