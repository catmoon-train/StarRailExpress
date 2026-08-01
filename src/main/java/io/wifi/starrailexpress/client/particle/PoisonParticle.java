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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;

public class PoisonParticle extends TextureSheetParticle {
    protected PoisonParticle(ClientLevel clientWorld,
                             double x, double y, double z, double vx, double vy, double vz) {
        super(clientWorld, x, y, z, vx, vy, vz);

        this.lifetime = 16;
        this.quadSize = 0.2f;
        this.setParticleSpeed(
                vx + (level.getRandom().nextIntBetweenInclusive(0, 100) / 4000f) * (level.getRandom().nextBoolean() ? -1 : 1),
                vy,
                vz + (level.getRandom().nextIntBetweenInclusive(0, 100) / 4000f) * (level.getRandom().nextBoolean() ? -1 : 1));

        this.rCol = 1f;
        this.gCol = 1f;
        this.bCol = 1f;
        this.alpha = 1f;

        this.friction = 0.96f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0f - ((float) this.age / this.lifetime);
    }

    public static class Factory<DefaultParticleType extends ParticleOptions> implements ParticleProvider<DefaultParticleType> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            PoisonParticle particle = new PoisonParticle(world, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
