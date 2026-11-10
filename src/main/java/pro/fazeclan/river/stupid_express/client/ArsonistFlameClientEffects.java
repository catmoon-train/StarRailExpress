package pro.fazeclan.river.stupid_express.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistPackets;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 阶段皮肤只公开UUID和开关；喷火形状本地生成，不公开浇油名单。 */
public final class ArsonistFlameClientEffects {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private ArsonistFlameClientEffects() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ArsonistPackets.MomentS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    if (packet.active()) ACTIVE.add(packet.owner()); else ACTIVE.remove(packet.owner());
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
        OnGettingPlayerSkin.EVENT.register((player, original) -> ACTIVE.contains(player.getUUID())
                ? OnGettingPlayerSkin.PlayerSkinResult.playerSkin(SERoles.ARSONIST.getPsychoSkin(player,
                        original.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM), original.model())
                : OnGettingPlayerSkin.PlayerSkinResult.SKIP);
        CustomParticleHandlers.register(StupidExpress.id("arsonist_flame"), (level, origin, duration, params) -> {
            if (params.length < 3) return;
            Vec3 look = Vec3.directionFromRotation(params[1], params[0]);
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(look).normalize();
            double radius = Math.min(5, Math.max(0, params[2]));
            for (int i = 0; i < 24; i++) {
                double forward = level.random.nextDouble();
                double sideways = Math.sqrt(1 - forward * forward);
                double angle = level.random.nextDouble() * Math.PI * 2;
                Vec3 direction = look.scale(forward).add(right.scale(Math.cos(angle) * sideways))
                        .add(up.scale(Math.sin(angle) * sideways));
                Vec3 point = origin.add(direction.scale(0.4 + level.random.nextDouble() * Math.max(0, radius - 0.4)));
                var clip = level.clip(new ClipContext(origin, point, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, net.minecraft.client.Minecraft.getInstance().player));
                if (clip.getType() != HitResult.Type.MISS) point = clip.getLocation().subtract(direction.scale(0.05));
                Vec3 velocity = direction.scale(0.06);
                level.addParticle(i % 5 == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME,
                        point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
            }
        });
        CustomParticleHandlers.register(StupidExpress.id("arsonist_shot"), (level, origin, duration, params) -> {
            if (params.length < 2) return;
            Vec3 look = Vec3.directionFromRotation(params[1], params[0]);
            Vec3 end = origin.add(look.scale(20));
            var clip = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, net.minecraft.client.Minecraft.getInstance().player));
            double range = clip.getType() == HitResult.Type.MISS ? 20 : clip.getLocation().distanceTo(origin);
            for (double d = 0.5; d < range; d += 0.7) {
                Vec3 point = origin.add(look.scale(d));
                level.addParticle(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z, 0, 0, 0);
            }
        });
    }
}
