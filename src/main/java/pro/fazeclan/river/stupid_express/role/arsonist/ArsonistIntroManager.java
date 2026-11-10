package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import net.exmo.sre.camera.AdvancedCameraNode;
import net.exmo.sre.camera.AdvancedCameraPayload;
import net.exmo.sre.camera.AdvancedCameraSequence;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 六秒演出使用独立服务端tick推进，保存并恢复原暂停/效果状态，不占用80秒战斗时间。 */
public final class ArsonistIntroManager {
    private static final List<Holder<MobEffect>> LOCKS = List.of(
            ModEffects.MOVE_BANED, ModEffects.USED_BANED, ModEffects.SKILL_BANED, ModEffects.INVINCIBLE);
    private static final Map<ServerLevel, Session> SESSIONS = new ConcurrentHashMap<>();
    private record Viewer(Vec3 position, float yaw, float pitch, Map<Holder<MobEffect>, MobEffectInstance> effects, int capturedAt) {}
    private static final class Session {
        final ServerPlayer owner;
        final Vec3 position;
        final float yaw;
        final AABB area;
        final int startedAt;
        final boolean wasFrozen;
        final Map<UUID, Viewer> viewers = new HashMap<>();
        Session(ServerPlayer owner) {
            this.owner = owner;
            position = owner.position();
            yaw = owner.getYRot();
            area = AreasWorldComponent.KEY.get(owner.serverLevel()).getPlayArea();
            startedAt = owner.server.getTickCount();
            wasFrozen = SREGameTimeComponent.KEY.get(owner.level()).timeFrozen;
        }
    }

    private ArsonistIntroManager() {}

    public static boolean isActive(ServerLevel level) { return SESSIONS.containsKey(level); }
    public static int remaining(ServerPlayer owner) {
        Session s = SESSIONS.get(owner.serverLevel());
        return s != null && s.owner == owner ? Math.max(0, ArsonistRules.INTRO_TICKS - (owner.server.getTickCount() - s.startedAt)) : 0;
    }

    public static void register() {
        ArsonistOverviewChunks.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var entry : new ArrayList<>(SESSIONS.entrySet())) {
                Session s = entry.getValue();
                var data = ArsonistRoleData.of(s.owner);
                if (s.owner.hasDisconnected() || s.owner.serverLevel() != entry.getKey() || data == null || !data.arsonMoment
                        || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(s.owner)
                        || SREGameWorldComponent.KEY.get(entry.getKey()).getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
                    finish(entry.getKey(), false);
                    if (data != null && data.arsonMoment) data.reset();
                    continue;
                }
                for (var view : s.viewers.entrySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(view.getKey());
                    if (p == null || p.serverLevel() != entry.getKey() || p.isSpectator()) continue;
                    p.setDeltaMovement(Vec3.ZERO);
                    if (p.position().distanceToSqr(view.getValue().position()) > 0.0025D) {
                        Vec3 pos = view.getValue().position();
                        p.teleportTo(entry.getKey(), pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
                    }
                }
                if (server.getTickCount() - s.startedAt >= ArsonistRules.INTRO_TICKS) finish(entry.getKey(), true);
            }
        });
        // 战斗死亡（包括强杀）暂停，管理员泛型强杀与离线清理保留。
        GameUtilityEvents.BEFORE_KILL.register((victim, body, killer, reason, force) ->
                victim == null || !(victim.level() instanceof ServerLevel level) || !isActive(level)
                        || reason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.DISCONNECT)
                        || killer == null && force && reason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.GENERIC));
    }

    public static void begin(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        Session s = new Session(owner);
        SESSIONS.put(level, s);
        SREGameTimeComponent.KEY.get(level).setTimeFrozen(true);
        List<ServerPlayer> viewers = new ArrayList<>(level.players());
        for (ServerPlayer p : viewers) rememberAndLock(s, p);
        ArsonistOverviewChunks.prepare(level, viewers, s.area);
        for (ServerPlayer p : viewers) send(s, p, 0);
    }

    private static void rememberAndLock(Session s, ServerPlayer p) {
        Map<Holder<MobEffect>, MobEffectInstance> saved = new HashMap<>();
        for (var effect : LOCKS) {
            var previous = p.getEffect(effect);
            if (previous != null) saved.put(effect, new MobEffectInstance(previous));
            if (!p.isSpectator()) p.addEffect(new MobEffectInstance(effect, ArsonistRules.INTRO_TICKS + 10, 0, false, false, false));
        }
        p.stopUsingItem();
        p.setDeltaMovement(Vec3.ZERO);
        p.closeContainer();
        s.viewers.put(p.getUUID(), new Viewer(p.position(), p.getYRot(), p.getXRot(), saved, p.server.getTickCount()));
    }

    public static void join(ServerPlayer p) {
        Session s = SESSIONS.get(p.serverLevel());
        if (s == null) return;
        int elapsed = p.server.getTickCount() - s.startedAt;
        if (elapsed >= ArsonistRules.INTRO_TICKS) return;
        rememberAndLock(s, p);
        ArsonistOverviewChunks.prepare(p.serverLevel(), List.of(p), s.area);
        send(s, p, elapsed);
    }

    private static void send(Session s, ServerPlayer p, int elapsed) {
        ServerPlayNetworking.send(p, new ArsonistPackets.IntroS2C(s.owner.getUUID(), s.position, s.yaw, s.area, elapsed, true));
        ServerPlayNetworking.send(p, AdvancedCameraPayload.play(track(s, p, elapsed).toJson()));
    }

    private static AdvancedCameraSequence track(Session s, ServerPlayer p, int elapsed) {
        Vec3 forward = Vec3.directionFromRotation(0, s.yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 focus = s.position.add(forward.scale(1.2)).add(0, 0.5, 0);
        Vec3 close = s.position.add(right.scale(2.4)).add(forward.scale(-2.2)).add(0, 1.7, 0);
        double framing = Math.hypot(s.area.getXsize(), s.area.getZsize()) / (2 * Math.tan(Math.toRadians(52.5))) * 1.15;
        Vec3 center = new Vec3((s.area.minX + s.area.maxX) / 2, s.position.y + 1, (s.area.minZ + s.area.maxZ) / 2);
        Vec3 overview = center.add(0, Math.max(28, Math.max(s.area.maxY - center.y + 8, framing)), 0.01);
        List<AdvancedCameraNode> nodes = new ArrayList<>();
        if (elapsed < 24) {
            nodes.add(new AdvancedCameraNode(0, 0, p.getEyePosition(), null, p.getYRot(), p.getXRot(), 0));
            nodes.add(new AdvancedCameraNode(Math.min(8, 24 - elapsed), Math.max(0, 16 - elapsed), close, focus, null, null, 58));
        } else nodes.add(new AdvancedCameraNode(0, 0, overview, center, null, null, 105));
        if (elapsed < 108) nodes.add(new AdvancedCameraNode(Math.max(0, 64 - Math.max(24, elapsed)),
                108 - Math.max(64, elapsed), overview, center, null, null, 105));
        Viewer original = s.viewers.get(p.getUUID());
        Vec3 eye = original.position().add(0, p.getEyeHeight(), 0);
        nodes.add(new AdvancedCameraNode(Math.min(12, ArsonistRules.INTRO_TICKS - elapsed), 0, eye, null,
                original.yaw(), original.pitch(), 0));
        // 黑边由本次客户端演出绘制，避免通用导演结束时额外10tick黑屏吞掉战斗开场。
        return new AdvancedCameraSequence(nodes, false, true, false);
    }

    public static void cancel(ServerPlayer owner) {
        for (var entry : new ArrayList<>(SESSIONS.entrySet())) {
            if (entry.getValue().owner == owner) finish(entry.getKey(), false);
        }
    }

    private static void finish(ServerLevel level, boolean completed) {
        Session s = SESSIONS.remove(level);
        if (s == null) return;
        int elapsed = s.owner.server.getTickCount() - s.startedAt;
        SREGameTimeComponent.KEY.get(level).setTimeFrozen(s.wasFrozen);
        for (var entry : s.viewers.entrySet()) {
            ServerPlayer p = s.owner.server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || p.hasDisconnected()) continue;
            for (var effect : LOCKS) {
                var current = p.getEffect(effect);
                if (current != null && current.getAmplifier() == 0 && current.getDuration() <= ArsonistRules.INTRO_TICKS + 10) p.removeEffect(effect);
                var previous = entry.getValue().effects().get(effect);
                if (previous != null) {
                    int left = ArsonistRules.remainingEffectTicks(previous.getDuration(),
                            s.owner.server.getTickCount() - entry.getValue().capturedAt());
                    if (left == -1 || left > 0) p.addEffect(new MobEffectInstance(effect, left, previous.getAmplifier(),
                            previous.isAmbient(), previous.isVisible(), previous.showIcon()));
                }
            }
            ServerPlayNetworking.send(p, AdvancedCameraPayload.clearPayload());
            if (p.serverLevel() == level && !p.isSpectator()) {
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), entry.getValue().yaw(), entry.getValue().pitch());
            }
            ServerPlayNetworking.send(p, new ArsonistPackets.IntroS2C(s.owner.getUUID(), s.position, s.yaw, s.area, ArsonistRules.INTRO_TICKS, false));
            ArsonistOverviewChunks.restore(p);
        }
        if (completed) {
            var data = ArsonistRoleData.of(s.owner);
            if (data != null && data.arsonMoment) data.finishIntro();
        }
    }
}
