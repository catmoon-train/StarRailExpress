package io.wifi.starrailexpress.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class BlackSmokeParticle extends TextureSheetParticle {
    BlackSmokeParticle(ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z);
        this.quadSize = 1f;
        this.setSize(0.25F, 0.25F);
        this.lifetime = 100;

        this.gravity = 3.0E-6F;
        this.yd = .5f;
        this.xd = 0;

        float col = world.random.nextIntBetweenInclusive(30, 60) / 255f;
        this.setColor(col, col, col);
    }

    @Override
    public void tick() {
        this.quadSize += 0.05f;

        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime && !(this.alpha <= 0.0F)) {
            this.yd *= .95f;
            this.xd = Mth.clamp(this.xd + .1f, 0, 1);
            this.move(this.xd, this.yd, this.zd);

            if (this.age >= this.lifetime - 60 && this.alpha > 0.01F) {
                this.alpha -= 0.015F;
            }
        } else {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientWorld, double d, double e, double f, double g, double h, double i) {
            BlackSmokeParticle BlackSmokeParticle = new BlackSmokeParticle(clientWorld, d, e, f, g, h, i);
            BlackSmokeParticle.setAlpha(0.95F);
            BlackSmokeParticle.pickSprite(this.spriteProvider);
            return BlackSmokeParticle;
        }
    }
}
