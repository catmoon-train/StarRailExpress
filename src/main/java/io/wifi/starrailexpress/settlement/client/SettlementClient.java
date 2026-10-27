package io.wifi.starrailexpress.settlement.client;

import com.google.gson.Gson;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.settlement.SettlementPayload;
import io.wifi.starrailexpress.settlement.SettlementRequest;
import io.wifi.starrailexpress.settlement.SettlementSnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** 舞台客户端唯一入口。所有状态只在客户端线程变更；语音线程只读取 active。 */
public final class SettlementClient {
    private enum Phase { OFF, LOADING, READY, PLAYING, FINISHING }
    private static final Gson GSON = new Gson();
    private static volatile boolean active;
    private static Phase phase = Phase.OFF;
    private static UUID session;
    private static SettlementSnapshot snapshot;
    private static String[] fragments;
    private static int received, stable, age, playTick, holdTicks, eventIndex = -1;
    private static boolean teleported, holding, parsing, seenIdle;
    private static String dimension;
    private static Entity oldCamera;
    private static CameraType oldCameraType;
    private static boolean oldHideGui;
    private static Vec3 oldPosition, smoothPosition;
    private static float oldYaw, oldPitch, smoothYaw, smoothPitch;
    private static long lastCameraFrame;
    private static Component title = Component.empty(), eventText = Component.empty();
    private static final Set<UUID> retired = new HashSet<>();
    static final List<ActorState> actors = new ArrayList<>();
    static final Map<UUID, ActorState> byId = new LinkedHashMap<>();
    private static SettlementPhysics physics = new SettlementPhysics();
    private static boolean registered;

    private SettlementClient() { }
    public static boolean isActive() { return active; }
    public static boolean isPlaying() { return phase == Phase.PLAYING; }
    public static boolean isBlack() { return active && phase != Phase.PLAYING; }
    public static SettlementSnapshot snapshot() { return snapshot; }
    public static int tick() { return playTick; }
    public static int eventIndex() { return eventIndex; }
    public static int holdTicks() { return holdTicks; }
    public static Component title() { return title; }
    public static Component eventText() { return eventText; }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SettlementPayload.ID,
                (payload, context) -> context.client().execute(() -> receive(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(SettlementClient::clientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clear(); retired.clear();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(SettlementStageRenderer::render);
        SettlementPostEffect.register();
    }

    private static void receive(SettlementPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.action() == SettlementPayload.PREPARE) {
            if (retired.contains(payload.session()) || Objects.equals(session, payload.session())) return;
            clear();
            session = payload.session();
            oldCamera = mc.getCameraEntity();
            oldCameraType = mc.options.getCameraType(); oldHideGui = mc.options.hideGui;
            Camera camera = mc.gameRenderer.getMainCamera();
            oldPosition = camera.getPosition(); oldYaw = camera.getYRot(); oldPitch = camera.getXRot();
            dimension = mc.level == null ? null : mc.level.dimension().location().toString();
            seenIdle = mc.level != null && !SREGameWorldComponent.KEY.get(mc.level).isRunning();
            phase = Phase.LOADING; active = true;
            mc.options.hideGui = true;
            mc.getSoundManager().stop();
            // PREPARE 只确认黑屏，绝不能把上一场的 FINISHED 带进新会话。
            send(SettlementRequest.BLACK);
            return;
        }
        if (!active || !Objects.equals(session, payload.session())) return;
        switch (payload.action()) {
            case SettlementPayload.SNAPSHOT -> fragment(payload);
            case SettlementPayload.TELEPORTED -> teleported = true;
            case SettlementPayload.PLAY -> {
                if (phase != Phase.READY || snapshot == null) return;
                phase = Phase.PLAYING; playTick = 0; eventIndex = -1;
                lastCameraFrame = 0;
                SettlementAudio.cue("INTRO");
                // 演出一开始就放本局胜利/失败对应的结算音乐，PREPARE 已把上一份现场音停掉。
                SettlementAudio.playSettlementMusic(localWin());
            }
            case SettlementPayload.STOP -> clear();
            default -> { }
        }
    }

    private static void fragment(SettlementPayload payload) {
        if (phase != Phase.LOADING || parsing || snapshot != null) return;
        if (payload.total() < 1 || payload.total() > 512 || payload.index() < 0
                || payload.index() >= payload.total() || payload.data().length() > 24000) { clear(); return; }
        if (fragments == null) fragments = new String[payload.total()];
        if (fragments.length != payload.total()) { clear(); return; }
        if (fragments[payload.index()] != null) {
            if (!fragments[payload.index()].equals(payload.data())) clear();
            return;
        }
        fragments[payload.index()] = payload.data(); received++;
        if (received != fragments.length) return;
        String[] complete = fragments; fragments = null; parsing = true;
        UUID expectedSession = session;
        // 最大约 12 MB 的 JSON 在后台拼接和解析，tick 不做等待或一次性大解析。
        CompletableFuture.supplyAsync(() -> decode(complete), Util.backgroundExecutor())
                .whenComplete((decoded, error) -> Minecraft.getInstance().execute(() -> {
            if (!active || !Objects.equals(session, expectedSession)) return;
            parsing = false;
            if (error != null || decoded == null) {
                SRE.LOGGER.warn("Rejected settlement snapshot", error); clear(); return;
            }
            snapshot = decoded.snapshot();
            title = parse(snapshot.titleJson());
            arrange(snapshot);
            for (var actor : actors) actor.initialRole = decoded.initialRoles().getOrDefault(actor.fact.id(), actor.role);
        }));
    }

    private record Decoded(SettlementSnapshot snapshot, Map<UUID, String> initialRoles) { }

    private static Decoded decode(String[] complete) {
        SettlementSnapshot value = GSON.fromJson(String.join("", complete), SettlementSnapshot.class);
        if (!valid(value)) return null;
        Map<UUID, String> initial = new LinkedHashMap<>();
        for (var event : value.events()) {
            if (event.source() != null && event.sourceRole() != null && !event.sourceRole().isBlank())
                initial.putIfAbsent(event.source(), event.sourceRole());
            if (event.target() != null && event.targetRole() != null && !event.targetRole().isBlank())
                initial.putIfAbsent(event.target(), event.targetRole());
        }
        return new Decoded(value, initial);
    }

    private static boolean valid(SettlementSnapshot value) {
        if (value == null || value.dimension() == null || value.actors() == null || value.events() == null
                || !Double.isFinite(value.x()) || !Double.isFinite(value.y()) || !Double.isFinite(value.z())
                || value.events().size() > 100000 || value.actors().size() > 512) return false;
        Set<UUID> ids = new HashSet<>();
        for (var actor : value.actors()) if (actor == null || actor.id() == null || actor.name() == null
                || actor.role() == null || !ids.add(actor.id())) return false;
        for (var event : value.events()) if (event == null || event.type() == null) return false;
        return true;
    }

    private static void arrange(SettlementSnapshot value) {
        List<SettlementSnapshot.Actor> winners = value.actors().stream().filter(SettlementSnapshot.Actor::winner)
                .sorted((a, b) -> Boolean.compare(Objects.equals(b.id(), value.mvp()), Objects.equals(a.id(), value.mvp())))
                .toList();
        int winner = 0, loser = 0;
        int losers = value.actors().size() - winners.size();
        for (var actor : winners) {
            double slot = winner == 0 ? 0 : ((winner + 1) / 2) * (winner % 2 == 1 ? -1 : 1);
            double spacing = Math.min(1.8, 25.0 / Math.max(1, winners.size()));
            addActor(actor, slot * spacing, 0, winner++);
        }
        for (var actor : value.actors()) if (!actor.winner()) {
            double x = ((loser % 13) - (Math.min(losers, 13) - 1) * .5) * 1.7;
            addActor(actor, x, 5 + Math.min(7, loser / 13) * 1.05, loser++);
        }
    }

    private static void addActor(SettlementSnapshot.Actor actor, double x, double z, int order) {
        ActorState state = new ActorState(actor, x, z, order);
        actors.add(state); byId.put(actor.id(), state); physics.bodies.add(state.body);
    }

    private static void clientTick(Minecraft mc) {
        if (!active) return;
        age++;
        if (mc.level == null || mc.player == null) { if (age > 100) clear(); return; }
        String currentDimension = mc.level.dimension().location().toString();
        // 本功能只在原维度远处演出；任何维度切换（含重生）均撤销本地独占状态。
        if (dimension != null && !dimension.equals(currentDimension)) { clear(); return; }
        boolean running = SREGameWorldComponent.KEY.get(mc.level).isRunning();
        if (!running) seenIdle = true;
        if ((seenIdle && running) || MapVotingComponent.KEY.get(mc.level).isVotingActive()
                || age > (snapshot == null ? 1200 : snapshot.durationTicks() + 1200)) {
            clear(); return;
        }
        mc.options.hideGui = true;
        if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        if (phase == Phase.LOADING && snapshot != null && teleported) {
            if (stageReady(mc)) stable++; else stable = 0;
            if (stable >= 8) { phase = Phase.READY; send(SettlementRequest.READY); }
        }
        if (phase != Phase.PLAYING) return;
        playTick++;
        for (ActorState actor : actors) {
            if (!actor.fact.winner() && playTick >= 150 + Math.min(40, actor.order * 2) && !actor.body.active) {
                // 后排坐姿在短暂升空过渡后真正释放三质点胶囊，而非插值到预排尸堆。
                actor.body.release(Math.max(-.8,Math.min(.8,actor.x * .12)), 10.5, 5.8 + (actor.order % 3) * .35);
            }
            actor.blackness += ((actor.dead ? 1 : 0) - actor.blackness) * .15f;
        }
        physics.tick();
        if (playTick == SettlementSnapshot.INTRO_TICKS) {
            for (ActorState actor : actors) { actor.dead = false; actor.blackness = 0; }
            for (ActorState actor : actors) actor.role = actor.initialRole;
        }
        int next = (playTick - SettlementSnapshot.INTRO_TICKS) / snapshot.eventTicks();
        if (playTick >= SettlementSnapshot.INTRO_TICKS && next >= 0 && next < snapshot.events().size()
                && next != eventIndex) {
            eventIndex = next; showEvent(snapshot.events().get(next));
        }
        if (playTick == snapshot.durationTicks() - 80) {
            for (ActorState actor : actors) { actor.dead = actor.fact.dead(); actor.role = actor.fact.role(); }
        }
        boolean down = playTick >= SettlementSnapshot.SKIP_AFTER_TICKS && mc.isWindowActive()
                && (GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS);
        if (down) {
            if (!holding || holdTicks % 5 == 0) send(SettlementRequest.HOLD);
            holdTicks++; holding = true;
        } else {
            if (holding) send(SettlementRequest.RELEASE);
            holdTicks = 0; holding = false;
        }
        if (playTick >= snapshot.durationTicks()) {
            if (holding) send(SettlementRequest.RELEASE);
            holding = false; holdTicks = 0;
            send(SettlementRequest.FINISHED); phase = Phase.FINISHING;
        }
    }

    private static boolean stageReady(Minecraft mc) {
        if (!snapshot.dimension().equals(mc.level.dimension().location().toString())) return false;
        if (mc.player.distanceToSqr(snapshot.x() + .5, snapshot.y() + 1, snapshot.z() - 10.5) >= 9) return false;
        int minX = Mth.floor(snapshot.x() - 16) >> 4, maxX = Mth.floor(snapshot.x() + 16) >> 4;
        int minZ = Mth.floor(snapshot.z() - 16) >> 4, maxZ = Mth.floor(snapshot.z() + 16) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (mc.level.getChunkSource().getChunk(x, z,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) return false;
        return true;
    }

    /** 本地玩家本局胜负：优先用舞台快照里的本人条目，未参与的旁观者回落到回合结束组件。 */
    private static boolean localWin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ActorState self = byId.get(mc.player.getUUID());
        if (self != null) return self.fact.winner();
        if (mc.level == null) return false;
        try {
            return io.wifi.starrailexpress.cca.SREGameRoundEndComponent.KEY.get(mc.level).didWin(mc.player.getUUID());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void showEvent(SettlementSnapshot.Event event) {
        eventText = parse(event.textJson());
        ActorState source = byId.get(event.source()), target = byId.get(event.target());
        if (source != null) source.actionAt = playTick;
        if (target != null) target.actionAt = playTick;
        if ("PLAYER_KILL".equals(event.type()) && target != null) target.dead = true;
        if (("PLAYER_REVIVAL".equals(event.type()) || "REVIVE".equals(event.type()))) {
            ActorState revived = target != null ? target : source;
            if (revived != null) revived.dead = false;
        }
        // 两个字段分别属于 source/target 的事件时职业，不将 targetRole 误当作 source 的新职业。
        if (source != null && event.sourceRole() != null && !event.sourceRole().isBlank()) source.role = event.sourceRole();
        if (target != null && event.targetRole() != null && !event.targetRole().isBlank()) target.role = event.targetRole();
        SettlementAudio.cue(event.type());
    }

    public static Component parse(String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        try {
            var level = Minecraft.getInstance().level;
            if (level == null) return Component.empty();
            Component component = Component.Serializer.fromJson(json, level.registryAccess());
            return component == null ? Component.empty() : component;
        } catch (RuntimeException ignored) {
            return Component.translatable("settlement.starrailexpress.unreadable");
        }
    }

    private static void send(int action) {
        if (session != null && ClientPlayNetworking.canSend(SettlementRequest.ID))
            ClientPlayNetworking.send(new SettlementRequest(session, action));
    }

    public static void clear() {
        if (session != null) {
            if (holding) send(SettlementRequest.RELEASE);
            if (retired.size() > 32) retired.clear();
            retired.add(session);
        }
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            mc.getSoundManager().stop();
            mc.options.hideGui = oldHideGui;
            if (oldCameraType != null) mc.options.setCameraType(oldCameraType);
            mc.setCameraEntity(oldCamera != null && oldCamera.level() == mc.level && !oldCamera.isRemoved()
                    ? oldCamera : mc.player);
            if (oldPosition != null) {
                mc.gameRenderer.getMainCamera().setPosition(oldPosition);
                mc.gameRenderer.getMainCamera().setRotation(oldYaw, oldPitch);
            }
        }
        phase = Phase.OFF; active = false; session = null; snapshot = null;
        fragments = null; received = stable = age = playTick = holdTicks = 0;
        teleported = holding = parsing = false; eventIndex = -1;
        oldCamera = null; oldPosition = smoothPosition = null; oldCameraType = null;
        title = eventText = Component.empty(); actors.clear(); byId.clear();
        physics = new SettlementPhysics(); lastCameraFrame = 0;
        SettlementAudio.clear(); SettlementPostEffect.close();
    }

    /** 每帧平滑一次，交由独立高优先级 Camera.setup 注入调用。 */
    public static void applyCamera(Camera camera, float partialTick) {
        if (!active) return;
        if (snapshot == null) {
            if (oldPosition != null) { camera.setPosition(oldPosition); camera.setRotation(oldYaw, oldPitch); }
            return;
        }
        double t = playTick + (isPlaying() ? partialTick : 0);
        double focus = smooth((t - 210) / 55) * (1 - smooth((t - 300) / 20));
        ActorState mvp = byId.get(snapshot.mvp());
        double targetX = mvp == null ? 0 : mvp.x;
        double targetY = 1.45, targetZ = 0;
        if (t >= SettlementSnapshot.INTRO_TICKS && eventIndex >= 0) {
            var event = snapshot.events().get(eventIndex);
            ActorState source = byId.get(event.source()), target = byId.get(event.target());
            ActorState selected = source != null ? source : target;
            if (selected != null) {
                targetX = selected.body.active ? selected.body.nodes[1].x : selected.x;
                targetY = selected.body.active ? selected.body.nodes[1].y + .7 : 1.4;
                targetZ = selected.body.active ? selected.body.nodes[1].z : selected.z;
                focus = .3;
            }
        }
        // 镜头必须留在黑壳内部（正面墙 z=-15）；窄屏通过 FOV 扩展适配，不穿墙拉远。
        double wideDistance = 12;
        Vec3 desired = new Vec3(snapshot.x() + .5 + targetX * focus,
                snapshot.y() + 1 + Mth.lerp(focus, 4.2, targetY + 1),
                snapshot.z() + .5 - Mth.lerp(focus, wideDistance, 4.8));
        Vec3 look = new Vec3(snapshot.x() + .5 + targetX * focus,
                snapshot.y() + 1 + targetY, snapshot.z() + .5 + targetZ);
        long now = System.nanoTime();
        double dt = lastCameraFrame == 0 ? 1 : Math.min(.1, (now - lastCameraFrame) / 1e9);
        lastCameraFrame = now;
        double alpha = 1 - Math.exp(-dt * 5);
        smoothPosition = smoothPosition == null ? desired : smoothPosition.lerp(desired, alpha);
        Vec3 delta = look.subtract(smoothPosition);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        if (dt == 1) { smoothYaw = yaw; smoothPitch = pitch; }
        else { smoothYaw += Mth.wrapDegrees(yaw - smoothYaw) * (float) alpha;
            smoothPitch += (pitch - smoothPitch) * (float) alpha; }
        camera.setPosition(smoothPosition); camera.setRotation(smoothYaw, smoothPitch);
    }

    /** 同帧重申最终姿态，不再次积分平滑时钟。 */
    public static void reassertCamera(Camera camera) {
        if (!active) return;
        if (smoothPosition == null) {
            if (oldPosition != null) { camera.setPosition(oldPosition); camera.setRotation(oldYaw, oldPitch); }
        } else { camera.setPosition(smoothPosition); camera.setRotation(smoothYaw, smoothPitch); }
    }

    static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t);
    }

    static final class ActorState {
        final SettlementSnapshot.Actor fact;
        final double x, z;
        final int order;
        final SettlementPhysics.Body body;
        String role;
        String initialRole;
        boolean dead;
        float blackness;
        int actionAt = -1000;
        ActorState(SettlementSnapshot.Actor fact, double x, double z, int order) {
            this.fact = fact; this.x = x; this.z = z; this.order = order;
            initialRole = role = fact.role(); dead = fact.dead();
            body = new SettlementPhysics.Body(x, 1, z, order);
        }
    }
}
