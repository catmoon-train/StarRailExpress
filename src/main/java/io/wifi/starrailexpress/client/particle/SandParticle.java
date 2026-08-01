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

package io.wifi.starrailexpress.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public class SandParticle extends TextureSheetParticle {
    private final float yRand;
    private final float zRand;

    private float angleX;
    private float angleY;
    private float angleZ;
    private float prevAngleX;
    private float prevAngleY;
    private float prevAngleZ;
    private final float angleRandX;
    private final float angleRandY;
    private final float angleRandZ;

    public SandParticle(ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);

        this.xd = velocityX;
        this.yd = velocityY;
        this.zd = velocityZ;

        this.zRand = world.random.nextFloat() * 3 - 1.5f;
        this.yRand = world.random.nextFloat() * 2 - 1;

        this.angleRandX = (world.random.nextFloat() * 2 - 1) * .15f;
        this.angleRandY = (world.random.nextFloat() * 2 - 1) * .15f;
        this.angleRandZ = (world.random.nextFloat() * 2 - 1) * .15f;

        // 沙尘粒子生命周期更短，下落更快
        this.lifetime = 30 + world.random.nextInt(20);
        this.quadSize = .08f + world.random.nextFloat() * .08f;
        this.alpha = 0f;

        // 沙尘颜色：沙黄色/棕色
        this.rCol = 0.76f + world.random.nextFloat() * 0.15f;
        this.gCol = 0.65f + world.random.nextFloat() * 0.15f;
        this.bCol = 0.45f + world.random.nextFloat() * 0.1f;

        this.setSprite(spriteProvider.get(world.random));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha += 0.02f;

        // 沙尘水平飘动幅度更大，模拟风沙效果
        float hSpeed = .35f;
        this.zd = Math.sin(this.zRand + this.age / 3f + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * hSpeed;
        // 沙尘下落速度比雪花快
        this.yd = -.2f + Math.sin(this.yRand + this.age / 2f + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * .15f;

        this.prevAngleX = angleX;
        this.prevAngleY = angleY;
        this.prevAngleZ = angleZ;

        this.angleX += angleRandX;
        this.angleY += angleRandY;
        this.angleZ += angleRandZ;

        if (this.onGround || this.xd == 0) {
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Quaternionf quaternionf = new Quaternionf();
        this.getFacingCameraMode().setRotation(quaternionf, camera, tickDelta);
        quaternionf.rotateXYZ(
                Mth.lerp(tickDelta, this.prevAngleX, this.angleX),
                Mth.lerp(tickDelta, this.prevAngleY, this.angleY),
                Mth.lerp(tickDelta, this.prevAngleZ, this.angleZ)
        );

        this.renderRotatedQuad(vertexConsumer, camera, quaternionf, tickDelta);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return new SandParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.spriteProvider);
        }
    }
}
