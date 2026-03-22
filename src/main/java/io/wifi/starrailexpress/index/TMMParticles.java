package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.ParticleTypeRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.particle.BlackSmokeParticle;
import io.wifi.starrailexpress.client.particle.FlashParticle;
import io.wifi.starrailexpress.client.particle.PoisonParticle;
import io.wifi.starrailexpress.client.particle.SnowflakeParticle;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

public interface TMMParticles {
    ParticleTypeRegistrar registrar = new ParticleTypeRegistrar(SRE.WATHE_MOD_ID);

    SimpleParticleType SNOWFLAKE = (SimpleParticleType) registrar.create("snowflake", FabricParticleTypes.simple(true));
    SimpleParticleType GUNSHOT = (SimpleParticleType) registrar.create("gunshot", FabricParticleTypes.simple(true));
    SimpleParticleType EXPLOSION = (SimpleParticleType) registrar.create("explosion", FabricParticleTypes.simple(true));
    SimpleParticleType BIG_EXPLOSION = (SimpleParticleType) registrar.create("big_explosion", FabricParticleTypes.simple(true));
    SimpleParticleType POISON = (SimpleParticleType) registrar.create("poison", FabricParticleTypes.simple(true));
    SimpleParticleType BLACK_SMOKE = (SimpleParticleType) registrar.create("black_smoke", FabricParticleTypes.simple(true));

    static void initialize() {
        registrar.registerEntries();
    }

    static void registerFactories() {
        ParticleFactoryRegistry.getInstance().register(SNOWFLAKE, SnowflakeParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(GUNSHOT, FlashParticle.GunshotFactory::new);
        ParticleFactoryRegistry.getInstance().register(EXPLOSION, FlashParticle.ExplosionFactory::new);
        ParticleFactoryRegistry.getInstance().register(BIG_EXPLOSION, FlashParticle.BigExplosionFactory::new);
        ParticleFactoryRegistry.getInstance().register(POISON, PoisonParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(BLACK_SMOKE, BlackSmokeParticle.Factory::new);
    }
}
