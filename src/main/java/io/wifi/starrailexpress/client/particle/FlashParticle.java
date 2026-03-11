package io.wifi.starrailexpress.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

public class FlashParticle extends TextureSheetParticle {
    protected FlashParticle(ClientLevel world, double x, double y, double z,
                            double vx, double vy, double vz,
                            float scale) {
        super(world, x, y, z, vx, vy, vz);

        this.lifetime = 3;
        this.quadSize = scale;
        this.setParticleSpeed(vx, vy, vz);

        this.rCol = 1f;
        this.gCol = 1f;
        this.bCol = 1f;
        this.alpha = 1f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float tint) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0f - ((float) this.age / this.lifetime);
    }

    public static class GunshotFactory extends Factory<SimpleParticleType> {
        public GunshotFactory(SpriteSet sprites) {
            super(sprites);
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType parameters, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return super.createParticle(parameters, world, x, y, z, velocityX, velocityY, velocityZ, 0.2f);
        }
    }

    public static class ExplosionFactory extends Factory<SimpleParticleType> {
        public ExplosionFactory(SpriteSet sprites) {
            super(sprites);
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType parameters, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return super.createParticle(parameters, world, x, y, z, velocityX, velocityY, velocityZ, 0.4f);
        }
    }

    public static class BigExplosionFactory extends Factory<SimpleParticleType> {
        public BigExplosionFactory(SpriteSet sprites) {
            super(sprites);
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType parameters, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return super.createParticle(parameters, world, x, y, z, velocityX, velocityY, velocityZ, .8f);
        }
    }

    public static abstract class Factory<DefaultParticleType extends ParticleOptions> implements ParticleProvider<DefaultParticleType> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(DefaultParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double vx, double vy, double vz,
                                       float scale) {
            FlashParticle particle = new FlashParticle(world, x, y, z, vx, vy, vz, scale);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
