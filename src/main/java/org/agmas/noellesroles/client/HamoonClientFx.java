package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.joml.Vector3f;

/** 波纹之力：一圈金色波纹，由服务端只发一个自定义粒子包。 */
public final class HamoonClientFx {
    public static final ResourceLocation ID = Noellesroles.id("hamoon");
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.84F, 0.25F), 1.4F);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(1.0F, 0.97F, 0.75F), 1.1F);

    private HamoonClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(ID, HamoonClientFx::play);
    }

    private static void play(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        float radius = params.length > 0 ? params[0] : 5.0F;
        int rings = 3;
        int count = 28;
        for (int ring = 1; ring <= rings; ring++) {
            double r = radius * ring / rings;
            double y = origin.y + 0.05D * ring;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count + ring * 0.35D;
                double x = origin.x + Math.cos(angle) * r;
                double z = origin.z + Math.sin(angle) * r;
                level.addParticle(ring == rings ? GOLD : PALE, x, y, z, 0.0D, 0.04D, 0.0D);
                if (i % 4 == 0) {
                    level.addParticle(ParticleTypes.END_ROD, x, y + 0.35D, z, 0.0D, 0.06D, 0.0D);
                }
            }
        }
        level.addParticle(ParticleTypes.FLASH, origin.x, origin.y + 0.6D, origin.z, 0.0D, 0.0D, 0.0D);
    }
}
