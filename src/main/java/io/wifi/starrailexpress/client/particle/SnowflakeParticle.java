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

public class SnowflakeParticle extends TextureSheetParticle {
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

    public SnowflakeParticle(ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);

        this.xd = velocityX;
        this.yd = velocityY;
        this.zd = velocityZ;

        this.zRand = world.random.nextFloat() * 2 - 1;
        this.yRand = world.random.nextFloat() * 2 - 1;

        this.angleRandX = (world.random.nextFloat() * 2 - 1) * .1f;
        this.angleRandY = (world.random.nextFloat() * 2 - 1) * .1f;
        this.angleRandZ = (world.random.nextFloat() * 2 - 1) * .1f;

        this.lifetime = 40 + world.random.nextInt(20);
        this.quadSize = .1f + world.random.nextFloat() * .1f;
        this.alpha = 0f;

        this.setSprite(spriteProvider.get(world.random));
    }

    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public void tick() {
        super.tick();
        this.alpha += 0.01f;

        float v = .2f;
        this.zd = Math.sin(this.zRand + this.age / 2f + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * v;
        this.yd = -.1f + Math.sin(this.yRand + this.age / 2f + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * v;

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
            return new SnowflakeParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.spriteProvider);
        }
    }
}
