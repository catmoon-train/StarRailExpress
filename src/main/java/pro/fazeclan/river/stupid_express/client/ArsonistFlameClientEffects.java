package pro.fazeclan.river.stupid_express.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
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

/** 公开阶段表现与特效注册；喷火使用受控火束，不再散布原版FLAME粒子。 */
public final class ArsonistFlameClientEffects {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private ArsonistFlameClientEffects() {}

    public static void register() {
        ArsonistMomentVisuals.register();
        ArsonistOverviewClient.register();
        ArsonistIntroClient.register();
        ArsonistFireJetClient.register();
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
        CustomParticleHandlers.register(StupidExpress.id("arsonist_flame"), (level, origin, duration, params) ->
                ArsonistFireJetClient.emit(level, origin, params));
        CustomParticleHandlers.register(StupidExpress.id("arsonist_kill"), (level, origin, duration, params) ->
                ArsonistFireJetClient.killBurst(level, origin));
        CustomParticleHandlers.register(StupidExpress.id("arsonist_shot"), (level, origin, duration, params) -> {
            if (params.length < 2) return;
            Vec3 look = Vec3.directionFromRotation(params[1], params[0]);
            Vec3 end = origin.add(look.scale(20));
            var clip = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            double range = clip.getType() == HitResult.Type.MISS ? 20 : clip.getLocation().distanceTo(origin);
            for (double d = 0.5; d < range; d += 0.7) {
                Vec3 point = origin.add(look.scale(d));
                level.addParticle(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z, 0, 0, 0);
            }
        });
    }
}
