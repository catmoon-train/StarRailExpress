package org.agmas.noellesroles.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;

/** 黑烟只从角色中心升起；使用原版 smoke 粒子，不再绘制球形阴影贴片。 */
public final class RouletteDevilSmokeRenderer {
    private static final Map<UUID, List<Particle>> PARTICLES = new HashMap<>();
    private RouletteDevilSmokeRenderer() {}

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) { clear(); return; }
        var visible = RouletteDevilClient.visibleDemons();
        for (UUID id : List.copyOf(PARTICLES.keySet())) {
            if (!visible.contains(id)) remove(id);
        }
        for (UUID id : visible) {
            var demon = client.level.getPlayerByUUID(id);
            if (!RouletteDevilClient.canSeeSmoke(id) || demon == null || !demon.isAlive() || demon.isSpectator()
                    || demon.distanceToSqr(client.player) > 64 * 64 || !client.player.hasLineOfSight(demon)
                    || demon == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
                remove(id); continue;
            }
            List<Particle> particles = PARTICLES.computeIfAbsent(id, ignored -> new ArrayList<>());
            particles.removeIf(p -> !p.isAlive());
            var random = client.level.random;
            for (int i = 0; i < 4; i++) {
                Particle particle = client.particleEngine.createParticle(ParticleTypes.SMOKE,
                        demon.getX() + random.nextGaussian() * .07,
                        demon.getY() + .8 + random.nextDouble() * .18,
                        demon.getZ() + random.nextGaussian() * .07,
                        random.nextGaussian() * .006, .012 + random.nextDouble() * .016,
                        random.nextGaussian() * .006);
                if (particle == null) continue;
                particle.setColor(.025F, .022F, .03F);
                particle.scale(2F);
                particle.setLifetime(12 + random.nextInt(9));
                particles.add(particle);
            }
        }
    }
    private static void remove(UUID id) {
        var particles = PARTICLES.remove(id);
        if (particles != null) particles.forEach(Particle::remove);
    }
    public static void clear() {
        PARTICLES.values().forEach(particles -> particles.forEach(Particle::remove));
        PARTICLES.clear();
    }
}
