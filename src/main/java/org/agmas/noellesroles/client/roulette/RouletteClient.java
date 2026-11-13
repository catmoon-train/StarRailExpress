package org.agmas.noellesroles.client.roulette;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.game.minigame.roulette.*;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.*;

/** 只缓存公开快照；私密提示不进入桌面渲染器，所有结果仍由服务端裁定。 */
public final class RouletteClient {
    private static final int MAX_TABLES = 32;
    private static final Map<BlockPos, State> TABLES = new LinkedHashMap<>();
    private static ClientLevel world;
    private static boolean registered;
    private static PrivateInfo info;

    private RouletteClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        BlockEntityRenderers.register(ModBlocks.DEVIL_ROULETTE_TABLE_ENTITY, RouletteTableRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(RouletteNetworking.Public.ID, (p, ctx) ->
                ctx.client().execute(() -> receive(p)));
        ClientPlayNetworking.registerGlobalReceiver(RouletteNetworking.Private.ID, (p, ctx) ->
                ctx.client().execute(() -> receivePrivate(p)));
        ClientTickEvents.END_CLIENT_TICK.register(RouletteClient::tick);
        HudRenderCallback.EVENT.register(RouletteHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private static void checkWorld() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            clear();
            world = current;
        }
    }

    private static boolean nearby(BlockPos pos) {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.hasChunkAt(pos)
                && mc.player.distanceToSqr(Vec3.atCenterOf(pos)) <= 32 * 32;
    }

    private static void receive(RouletteNetworking.Public packet) {
        checkWorld();
        if (!nearby(packet.pos())) return;
        if (world.getBlockEntity(packet.pos()) instanceof DevilRouletteTableEntity table) {
            State old = TABLES.get(packet.pos());
            if (old != null && old.view.session().equals(packet.view().session())
                    && packet.view().sequence() < old.view.sequence()) return;
            table.acceptView(packet.view());
            observe(table);
        }
    }

    public static State observe(DevilRouletteTableEntity table) {
        checkWorld();
        if (!nearby(table.getBlockPos()) || table.getLevel() != world) return null;
        BlockPos pos = table.getBlockPos().immutable();
        RouletteView view = table.getView();
        State state = TABLES.get(pos);
        long now = world.getGameTime();
        if (state == null || !state.view.session().equals(view.session())) {
            if (state != null) forget(pos);
            if (TABLES.size() >= MAX_TABLES) forget(TABLES.keySet().iterator().next());
            state = new State(pos, view, now);
            TABLES.put(pos, state);
        } else if (view.sequence() > state.view.sequence()) {
            state.update(view, now);
        }
        return state;
    }

    private static void receivePrivate(RouletteNetworking.Private packet) {
        checkWorld();
        if (!nearby(packet.pos()) || !(world.getBlockEntity(packet.pos()) instanceof DevilRouletteTableEntity table)) return;
        State state = observe(table);
        if (state == null || !state.view.session().equals(packet.session())) return;
        var mc = Minecraft.getInstance();
        if (packet.kind() == RouletteNetworking.CLEAR) {
            if (info != null && info.matches(packet.pos(), packet.session())) info = null;
            if (mc.screen instanceof RouletteScreen screen && screen.matches(packet.pos(), packet.session())) screen.onClose();
        } else if (packet.kind() == RouletteNetworking.HELP) {
            mc.setScreen(RouletteScreen.help(packet.pos(), packet.session()));
        } else if (state.view.isParticipant(mc.player.getUUID())) {
            CompoundTag data = packet.data();
            if (packet.kind() == RouletteNetworking.INFO) {
                String key = data.getString("Key");
                if (!key.isBlank()) info = new PrivateInfo(packet.pos(), packet.session(), key,
                        Math.max(0, data.getInt("Index")), data.contains("Shell") ? data.getInt("Shell") : -1,
                        data.getInt("Index") > 0 ? Long.MAX_VALUE : world.getGameTime() + 160);
            } else if (packet.kind() == RouletteNetworking.SELECT && state.view.phase() == RoulettePhase.READY
                    && state.view.operator().equals(mc.player.getUUID()) && data.hasUUID("Token")) {
                mc.setScreen(RouletteScreen.selection(packet.pos(), packet.session(), data));
            }
        }
    }

    private static void tick(Minecraft mc) {
        checkWorld();
        if (world == null || mc.player == null) return;
        long now = world.getGameTime();
        for (BlockPos pos : List.copyOf(TABLES.keySet())) {
            if (!nearby(pos) || !(world.getBlockEntity(pos) instanceof DevilRouletteTableEntity table) || table.isRemoved()) {
                forget(pos);
                continue;
            }
            State state = observe(table);
            if (state != null) RouletteEffects.tick(state, table, now);
        }
        if (info != null && (now >= info.expires || !TABLES.containsKey(info.pos))) info = null;
        if (mc.screen instanceof RouletteScreen screen && !screen.valid()) screen.onClose();
        State participant = participant();
        RouletteEffects.heartbeat(participant, now);
    }

    public static State get(BlockPos pos) { return TABLES.get(pos); }

    public static State participant() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        State nearest = null;
        double distance = Double.MAX_VALUE;
        for (State state : TABLES.values()) {
            double d = mc.player.distanceToSqr(Vec3.atCenterOf(state.pos));
            if (state.view.isParticipant(mc.player.getUUID()) && d < distance) {
                distance = d;
                nearest = state;
            }
        }
        return nearest;
    }

    static PrivateInfo privateInfo(State state) {
        return info != null && info.matches(state.pos, state.view.session()) ? info : null;
    }

    private static void forget(BlockPos pos) {
        TABLES.remove(pos);
        if (info != null && info.pos.equals(pos)) info = null;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof RouletteScreen screen && screen.pos().equals(pos)) screen.onClose();
        RouletteEffects.clearShake(pos);
    }

    private static void clear() {
        TABLES.clear();
        info = null;
        RouletteEffects.clear();
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof RouletteScreen) mc.setScreen(null);
        world = null;
    }

    static record PrivateInfo(BlockPos pos, UUID session, String key, int index, int shell, long expires) {
        boolean matches(BlockPos p, UUID id) { return pos.equals(p) && session.equals(id); }
    }

    public static final class State {
        public final BlockPos pos;
        public RouletteView view;
        public long receivedAt;
        public float lastAge;
        public boolean impactDone;
        public boolean resultSeen;
        public float handoffFrom;
        public boolean shotSaw;
        public UUID releasedCuffs = RouletteView.NONE;
        public final long[][] arrivals = new long[2][8];

        State(BlockPos pos, RouletteView view, long now) {
            this.pos = pos;
            this.view = view;
            receivedAt = now;
            lastAge = view.age(now, 0);
            // 新观察者从当前时刻开始，不追播早于快照的声音或命中。
            impactDone = view.phase().impact() >= 0 && lastAge >= view.phase().impact();
            handoffFrom = view.phase() == RoulettePhase.HANDOFF ? actorYaw(view) : operatorYaw(view);
            shotSaw = view.saw() || view.phase() == RoulettePhase.SHOOT && view.delta() == -2;
            if (view.message().contains("cuffs_skipped")) releasedCuffs = view.actor().equals(view.front()) ? view.back() : view.front();
            for (long[] row : arrivals) Arrays.fill(row, Long.MIN_VALUE / 2);
        }

        void update(RouletteView next, long now) {
            boolean phaseChanged = next.phase() != view.phase() || next.startedAt() != view.startedAt();
            resultSeen = !phaseChanged && next.phase().impact() >= 0;
            if (phaseChanged) {
                handoffFrom = next.phase() == RoulettePhase.HANDOFF ? actorYaw(view) : operatorYaw(view);
                if (next.phase() == RoulettePhase.SHOOT) shotSaw = next.saw();
                if (next.phase() != RoulettePhase.HANDOFF) releasedCuffs = RouletteView.NONE;
                float age = next.age(now, 0);
                lastAge = age <= 2 ? -.01F : age;
                impactDone = next.phase().impact() >= 0 && age >= next.phase().impact();
            }
            // 包括背包腾空后延迟领取的补给，不局限于装填补给阶段。
            arrivals(view.frontItems(), next.frontItems(), 0, now);
            arrivals(view.backItems(), next.backItems(), 1, now);
            if (!view.cuffed().equals(RouletteView.NONE) && next.cuffed().equals(RouletteView.NONE)
                    && next.message().contains("skip")) releasedCuffs = view.cuffed();
            view = next;
            receivedAt = now;
        }

        private void arrivals(List<Integer> old, List<Integer> next, int side, long now) {
            Map<Integer, Integer> counts = new HashMap<>();
            old.forEach(kind -> counts.merge(kind, 1, Integer::sum));
            int stagger = 0;
            for (int i = 0; i < next.size(); i++) {
                int kind = next.get(i), count = counts.getOrDefault(kind, 0);
                if (count > 0) counts.put(kind, count - 1);
                else arrivals[side][i] = now + stagger++;
            }
        }

        public int seconds() {
            var level = Minecraft.getInstance().level;
            return Math.max(0, view.seconds() - (level == null ? 0 : (int) ((level.getGameTime() - receivedAt) / 20)));
        }

        public static float operatorYaw(RouletteView view) { return view.operator().equals(view.front()) ? 180 : 0; }
        public static float actorYaw(RouletteView view) { return view.actor().equals(view.front()) ? 180 : 0; }
    }
}
