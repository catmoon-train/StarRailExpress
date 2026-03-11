package org.agmas.noellesroles.blood.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 血液粒子效果类
 * 处理血液粒子的物理模拟、渲染和状态变化
 * 支持三种状态：空中飞溅、地面血渍、墙面血迹
 */
@Environment(EnvType.CLIENT)
public class BloodParticle extends TextureSheetParticle {
    /** 重力加速度，影响血液粒子下落速度 */
    public static final float GRAVITY = 0.04F;
    
    /** 粒子状态：
     * 0 = 空中飞溅（自由落体）
     * 1 = 地面血渍
     * 2 = 墙面血迹
     */
    private int state = 0;
    
    /** 墙面方向（仅当state=2时有效）：
     * 1 = 西墙（-X方向）
     * 2 = 东墙（+X方向）
     * 3 = 北墙（-Z方向）
     * 4 = 南墙（+Z方向）
     */
    private int wallDirection = 0;
    
    /** 撞击缩放因子，根据撞击速度调整血迹大小 */
    private float impactScale = 1.5F;
    
    /** 存储所有活动的血液粒子的弱引用集合，用于统一管理 */
    private static final Set<BloodParticle> ALL_PARTICLES = 
        Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 血液粒子构造函数
     * 
     * @param world 客户端世界
     * @param x 初始X坐标
     * @param y 初始Y坐标
     * @param z 初始Z坐标
     * @param vx 初始X方向速度
     * @param vy 初始Y方向速度
     * @param vz 初始Z方向速度
     * @param scale 粒子初始大小
     */
    protected BloodParticle(ClientLevel world, double x, double y, double z, double vx, double vy, double vz,
            float scale) {
        super(world, x, y, z, vx, vy, vz);
        this.lifetime = 24000; // 粒子存活时间（约20分钟）
        this.quadSize = scale; // 粒子大小
        this.setParticleSpeed(vx, vy, vz); // 设置粒子速度
        
        // 设置粒子颜色（深红色）
        this.rCol = 0.5F;
        this.gCol = 0.0F;
        this.bCol = 0.0F;
        this.alpha = 1.0F; // 完全不透明
        
        this.state = 0; // 初始状态为空中飞溅
        
        // 将粒子添加到全局集合中
        ALL_PARTICLES.add(this);
    }

    /**
     * 清除所有血液粒子
     * 遍历所有活动粒子并移除它们，然后清空集合
     */
    public static void clearParticles() {
        var var_114514 = ALL_PARTICLES.iterator();

        while (var_114514.hasNext()) {
            BloodParticle particle = (BloodParticle) var_114514.next();
            if (particle.isAlive()) {
                particle.remove(); // 移除存活的粒子
            }
        }

        ALL_PARTICLES.clear(); // 清空集合
    }

    /**
     * 获取粒子渲染类型
     * 使用半透明的粒子渲染类型，支持透明度混合
     */
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * 获取粒子光照颜色
     * 返回最大光照值，确保粒子在任何光照条件下都可见
     */
    protected int getLightColor(float tint) {
        return 15728880; // 最大光照值（15 << 20 | 15 << 4）
    }

    /**
     * 渲染粒子
     * 根据粒子状态使用不同的渲染方式：
     * - 状态0：使用父类的标准渲染
     * - 状态1/2：自定义渲染，调整粒子朝向以贴合表面
     */
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        if (this.state == 0) {
            // 空中飞溅状态使用标准渲染
            super.render(vertexConsumer, camera, tickDelta);
        } else {
            // 地面或墙面血迹使用自定义渲染
            Vec3 cameraPos = camera.getPosition();
            
            // 计算粒子在渲染帧中的位置（插值平滑移动）
            float x = (float) (Mth.lerp((double) tickDelta, this.xo, this.x) - cameraPos.x());
            float y = (float) (Mth.lerp((double) tickDelta, this.yo, this.y) - cameraPos.y());
            float z = (float) (Mth.lerp((double) tickDelta, this.zo, this.z) - cameraPos.z());
            
            Quaternionf quaternion = new Quaternionf();
            
            // 地面血迹：旋转90度使其平贴地面
            if (this.state == 1) {
                quaternion.rotationX((float) Math.toRadians(90.0));
                y += 0.001F; // 轻微偏移防止Z-fighting
            } 
            // 墙面血迹：根据墙面方向旋转
            else if (this.state == 2) {
                switch (this.wallDirection) {
                    case 1: // 西墙
                        quaternion.rotationY((float) Math.toRadians(180.0));
                        z -= 0.0635F; // 偏移避免与墙面重叠
                        break;
                    case 2: // 东墙
                        quaternion.rotationY((float) Math.toRadians(0.0));
                        z += 0.0635F;
                        break;
                    case 3: // 北墙
                        quaternion.rotationY((float) Math.toRadians(-90.0));
                        x -= 0.0635F;
                        break;
                    case 4: // 南墙
                        quaternion.rotationY((float) Math.toRadians(90.0));
                        x += 0.0635F;
                }
            }

            // 定义粒子四个顶点的初始位置（标准正方形）
            Vector3f[] vertices = new Vector3f[] { 
                new Vector3f(-1.0F, -1.0F, 0.0F), 
                new Vector3f(-1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F), 
                new Vector3f(1.0F, -1.0F, 0.0F) 
            };
            
            // 计算缩放后的粒子大小
            float scale = this.getQuadSize(tickDelta) * this.impactScale;

            // 对每个顶点应用旋转、缩放和位移
            for (int i = 0; i < 4; ++i) {
                Vector3f vertex = vertices[i];
                vertex.rotate(quaternion);      // 应用旋转
                vertex.mul(scale);              // 应用缩放
                vertex.add(x, y, z);            // 应用位移
            }

            // 获取纹理坐标
            float minU = this.getU0();
            float maxU = this.getU1();
            float minV = this.getV0();
            float maxV = this.getV1();
            
            int light = this.getLightColor(tickDelta);
            
            // 构建四个顶点，逆时针顺序形成四边形
            vertexConsumer.addVertex(vertices[0].x(), vertices[0].y(), vertices[0].z())
                .setUv(maxU, maxV)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
            vertexConsumer.addVertex(vertices[1].x(), vertices[1].y(), vertices[1].z())
                .setUv(maxU, minV)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
            vertexConsumer.addVertex(vertices[2].x(), vertices[2].y(), vertices[2].z())
                .setUv(minU, minV)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
            vertexConsumer.addVertex(vertices[3].x(), vertices[3].y(), vertices[3].z())
                .setUv(minU, maxV)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
        }
    }

    /**
     * 粒子更新逻辑
     * 每tick调用，处理物理模拟和状态转换
     */
    public void tick() {
        // 保存上一帧的速度用于撞击计算
        double prevVelX = this.xd;
        double prevVelY = this.yd;
        double prevVelZ = this.zd;
        
        // 空中飞溅状态：应用重力
        if (this.state == 0) {
            this.yd -= 0.03999999910593033; // 重力加速度
        }
        
        // 地面或墙面状态：停止移动
        if (this.state == 1 || this.state == 2) {
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
        }
        
        // 调用父类的tick方法（更新位置、年龄等）
        super.tick();
        
        // 检查状态转换
        if (this.state == 0) {
            // 检查是否撞击地面
            if (this.onGround) {
                this.state = 1; // 转换为地面血迹
                double impactVelocity = Math.sqrt(prevVelX * prevVelX + prevVelY * prevVelY + prevVelZ * prevVelZ);
                // 根据撞击速度调整血迹大小
                this.impactScale = 1.0F + (float) Math.min(impactVelocity * 1.5, 2.0);
                // 播放撞击音效
                this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                        SoundSource.AMBIENT, (float) impactVelocity * 2.0F, 2.0F, true);
            } 
            // 检查是否撞击墙面（通过速度变化检测）
            else if (this.hasPhysics) {
                boolean xStopped = Math.abs(prevVelX) > 0.01 && Math.abs(this.xd) < 0.001;
                boolean zStopped = Math.abs(prevVelZ) > 0.01 && Math.abs(this.zd) < 0.001;
                
                if (xStopped || zStopped) {
                    this.state = 2; // 转换为墙面血迹
                    double impactVelocity = Math.sqrt(prevVelX * prevVelX + prevVelY * prevVelY + prevVelZ * prevVelZ);
                    // 根据撞击速度调整血迹大小
                    this.impactScale = 1.0F + (float) Math.min(impactVelocity * 1.0, 2.0);
                    
                    // 确定墙面方向
                    if (xStopped && !zStopped) {
                        // X方向停止，确定是东墙还是西墙
                        this.wallDirection = prevVelX > 0.0 ? 4 : 3;
                    } else if (zStopped && !xStopped) {
                        // Z方向停止，确定是北墙还是南墙
                        this.wallDirection = prevVelZ > 0.0 ? 2 : 1;
                    } else if (xStopped && zStopped) {
                        // 两个方向都停止，选择速度更大的方向
                        if (Math.abs(prevVelX) > Math.abs(prevVelZ)) {
                            this.wallDirection = prevVelX > 0.0 ? 4 : 3;
                        } else {
                            this.wallDirection = prevVelZ > 0.0 ? 2 : 1;
                        }
                    }
                }
            }
        }
        
        // 随时间逐渐变暗（模拟血液干涸）
        this.rCol = 0.5F - (float) this.age / (float) (this.lifetime * 4);
    }

    /**
     * 血液粒子工厂类
     * 用于创建BloodParticle实例
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        /** 粒子精灵集，包含血液粒子的纹理动画帧 */
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * 创建血液粒子实例
         * 
         * @param type 粒子类型（忽略）
         * @param world 客户端世界
         * @param x X坐标
         * @param y Y坐标
         * @param z Z坐标
         * @param vx X方向速度
         * @param vy Y方向速度
         * @param vz Z方向速度
         * @return 新创建的血液粒子
         */
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z,
                double vx, double vy, double vz) {
            BloodParticle particle = new BloodParticle(world, x, y, z, vx, vy, vz, 0.5F);
            particle.pickSprite(this.sprites); // 为粒子选择随机纹理
            return particle;
        }
    }
}