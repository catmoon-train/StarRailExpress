package pro.fazeclan.river.stupid_express.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
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
        ArsonistMomentVisuals.register();
        ClientPlayNetworking.registerGlobalReceiver(ArsonistPackets.MomentS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    if (packet.active()) ACTIVE.add(packet.owner()); else ACTIVE.remove(packet.owner());
                    ArsonistMomentVisuals.accept(packet);
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ACTIVE.clear();
            ArsonistMomentVisuals.clear();
        });
        OnGettingPlayerSkin.EVENT.register((player, original) -> ACTIVE.contains(player.getUUID())
                ? OnGettingPlayerSkin.PlayerSkinResult.playerSkin(SERoles.ARSONIST.getPsychoSkin(player,
                        original.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM), original.model())
                : OnGettingPlayerSkin.PlayerSkinResult.SKIP);
        // 喷火范围可视化：服务端只发一条「yaw、pitch、射程、锥半角」的自定义特效包，
        // 形状全部在客户端本地 addParticle 生成（客户端粒子，不占网络包、不经过 ServerLevel.sendParticles）。
        CustomParticleHandlers.register(StupidExpress.id("arsonist_flame"), (level, origin, duration, params) -> {
            if (params.length < 3) return;
            Vec3 look = Vec3.directionFromRotation(params[1], params[0]).normalize();
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1e-6D) right = new Vec3(1, 0, 0);
            right = right.normalize();
            Vec3 up = right.cross(look).normalize();
            // 参数与服务端命中判定共用同一组数值，画出来的边界就是实际能烧到的边界。
            double radius = Math.min(5.0D, Math.max(0.5D, params[2]));
            double halfAngleDeg = params.length >= 4
                    ? Math.max(5.0D, Math.min(80.0D, params[3])) : 35.0D;
            double tanHalf = Math.tan(Math.toRadians(halfAngleDeg));
            Vec3 drift = look.scale(0.04D);

            // 1) 锥口外圈：最远处的圆形截面，直观标出 5 格射程与锥口宽度。
            Vec3 mouth = origin.add(look.scale(radius));
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2.0D * i / 12;
                Vec3 point = mouth.add(right.scale(Math.cos(a) * radius * tanHalf))
                        .add(up.scale(Math.sin(a) * radius * tanHalf));
                addFlame(level, origin, point, ParticleTypes.SOUL_FIRE_FLAME, drift);
            }

            // 2) 四条母线：从枪口连到锥口，标出锥形张开的两条边界（含上下俯仰方向）。
            for (int edge = 0; edge < 4; edge++) {
                double cosA = Math.cos(edge * Math.PI / 2.0D);
                double sinA = Math.sin(edge * Math.PI / 2.0D);
                for (double d = 1.0D; d <= radius; d += 1.0D) {
                    Vec3 point = origin.add(look.scale(d))
                            .add(right.scale(cosA * d * tanHalf))
                            .add(up.scale(sinA * d * tanHalf));
                    addFlame(level, origin, point, ParticleTypes.SMALL_FLAME, drift);
                }
            }

            // 3) 锥内火焰与少量烟：保持"正在喷火"的体积感，采样点始终落在锥形内。
            for (int i = 0; i < 12; i++) {
                double d = 0.4D + Math.cbrt(level.random.nextDouble()) * (radius - 0.4D);
                double spread = d * tanHalf * 0.7D;
                Vec3 point = origin.add(look.scale(d))
                        .add(right.scale((level.random.nextDouble() * 2 - 1) * spread))
                        .add(up.scale((level.random.nextDouble() * 2 - 1) * spread));
                addFlame(level, origin, point,
                        i % 4 == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, drift);
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

    /**
     * 客户端本地生成一颗粒子：被墙挡住时把粒子收回到命中点，火焰不会穿墙。
     * 全部走客户端世界的 addParticle，不经过服务端粒子包。
     */
    private static void addFlame(ClientLevel level, Vec3 origin, Vec3 point,
                                 ParticleOptions particle, Vec3 drift) {
        var clip = level.clip(new ClipContext(origin, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        if (clip.getType() != HitResult.Type.MISS) {
            if (clip.getLocation().distanceToSqr(origin) < 0.04D) return;
            point = clip.getLocation();
        }
        level.addParticle(particle, point.x, point.y, point.z, drift.x, drift.y, drift.z);
    }
}
