package pro.fazeclan.river.stupid_express.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.StupidExpress;

/** 灭火器的白色喷雾：沿视线前方5格圆锥散布云粒子，墙体可以遮挡。 */
public final class ExtinguisherSprayClientEffects {
    private ExtinguisherSprayClientEffects() {}

    public static void register() {
        CustomParticleHandlers.register(StupidExpress.id("extinguisher_spray"), (level, origin, duration, params) -> {
            if (params.length < 3) return;
            Vec3 look = Vec3.directionFromRotation(params[1], params[0]);
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(look).normalize();
            double radius = Math.min(5, Math.max(0, params[2]));
            for (int i = 0; i < 24; i++) {
                double forward = 0.5 + level.random.nextDouble() * 0.5;
                double sideways = Math.sqrt(Math.max(0, 1 - forward * forward));
                double angle = level.random.nextDouble() * Math.PI * 2;
                Vec3 direction = look.scale(forward).add(right.scale(Math.cos(angle) * sideways))
                        .add(up.scale(Math.sin(angle) * sideways)).normalize();
                Vec3 point = origin.add(direction.scale(0.3 + level.random.nextDouble() * Math.max(0, radius - 0.3)));
                var clip = level.clip(new ClipContext(origin, point, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, net.minecraft.client.Minecraft.getInstance().player));
                if (clip.getType() != HitResult.Type.MISS) point = clip.getLocation().subtract(direction.scale(0.05));
                Vec3 velocity = direction.scale(0.05);
                level.addParticle(ParticleTypes.CLOUD, point.x, point.y, point.z,
                        velocity.x, velocity.y, velocity.z);
            }
        });
    }
}
