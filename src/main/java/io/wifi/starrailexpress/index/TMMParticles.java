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

package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.ParticleTypeRegistrar;
import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.particle.*;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

public interface TMMParticles {
    ParticleTypeRegistrar registrar = new ParticleTypeRegistrar(SRE.WATHE_MOD_ID);
    ParticleTypeRegistrar tmmRegistrar = new ParticleTypeRegistrar(StarRailExpressID.TMM_MOD_ID);

    SimpleParticleType SNOWFLAKE = (SimpleParticleType) registrar.create("snowflake", FabricParticleTypes.simple(true));
    SimpleParticleType SAND = (SimpleParticleType) tmmRegistrar.create("sand", FabricParticleTypes.simple(true));
    SimpleParticleType GUNSHOT = (SimpleParticleType) registrar.create("gunshot", FabricParticleTypes.simple(true));
    SimpleParticleType EXPLOSION = (SimpleParticleType) registrar.create("explosion", FabricParticleTypes.simple(true));
    SimpleParticleType BIG_EXPLOSION = (SimpleParticleType) registrar.create("big_explosion", FabricParticleTypes.simple(true));
    SimpleParticleType POISON = (SimpleParticleType) registrar.create("poison", FabricParticleTypes.simple(true));
    SimpleParticleType BLACK_SMOKE = (SimpleParticleType) registrar.create("black_smoke", FabricParticleTypes.simple(true));

    static void initialize() {
        registrar.registerEntries();
        tmmRegistrar.registerEntries();
    }

    static void registerFactories() {
        ParticleFactoryRegistry.getInstance().register(SNOWFLAKE, SnowflakeParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(SAND, SandParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(GUNSHOT, FlashParticle.GunshotFactory::new);
        ParticleFactoryRegistry.getInstance().register(EXPLOSION, FlashParticle.ExplosionFactory::new);
        ParticleFactoryRegistry.getInstance().register(BIG_EXPLOSION, FlashParticle.BigExplosionFactory::new);
        ParticleFactoryRegistry.getInstance().register(POISON, PoisonParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(BLACK_SMOKE, BlackSmokeParticle.Factory::new);
    }
}
