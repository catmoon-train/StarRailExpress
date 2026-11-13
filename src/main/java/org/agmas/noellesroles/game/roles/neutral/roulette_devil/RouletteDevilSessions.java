package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.RouletteDevilC2SPacket;
import org.agmas.noellesroles.packet.RouletteDevilStateS2CPacket;
import org.agmas.noellesroles.role_data.neutral.RouletteDevilRoleData;

/** 单局持有两人的锁、背包保险和房间；所有终止路径共用幂等关闭操作。 */
public final class RouletteDevilSessions {
    private static final Map<UUID, Session> PLAYERS = new HashMap<>();
    private static final Set<UUID> BYPASS = new HashSet<>();
    private static final Set<UUID> DEATHS = new HashSet<>();
    private static final Map<UUID, RouletteDevilStateS2CPacket> LAST = new HashMap<>();
    private static final class Session {
        final UUID demon, opponent;
        final RouletteDevilRoleData data;
        final RouletteDevilRoom room;
        final long enterAt;
        final List<SeatEntity> seats = new ArrayList<>();
        DevilRouletteTableEntity table;
        boolean entered, closed;
        Session(ServerPlayer demon, ServerPlayer opponent, RouletteDevilRoom room, RouletteDevilRoleData data) {
            this.demon = demon.getUUID(); this.opponent = opponent.getUUID(); this.room = room; this.data = data;
            enterAt = room.level.getGameTime() + RouletteDevilRules.ENTRY_TICKS;
        }
    }
    private RouletteDevilSessions() {}
    public static boolean inDuel(UUID id) { return PLAYERS.containsKey(id); }
    public static boolean locked(UUID id) { return inDuel(id) && !BYPASS.contains(id); }
    public static boolean settlingDeath(UUID id) { return DEATHS.contains(id); }
    public static boolean positionAllowed(ServerPlayer p, double x, double y, double z) {
        Session s = PLAYERS.get(p.getUUID());
        if (s == null) return true;
        if (!s.entered) return p.distanceToSqr(x, y, z) <= .01;
        BlockPos seat = s.room.seat(s.demon.equals(p.getUUID()));
        return RouletteDevilRules.withinSeat(x - seat.getX() - .5, y - seat.getY(), z - seat.getZ() - .5);
    }
    public static boolean opponents(UUID a, UUID b) {
        Session s = PLAYERS.get(a); return s != null && (s.demon.equals(b) || s.opponent.equals(b));
    }
    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(RouletteDevilC2SPacket.ID, (packet, context) -> context.server().execute(() -> {
            if (packet.action() != 1) return; // 种子只能通过统一技能系统，不能绕过冷却。
            ServerPlayer target = context.server().getPlayerList().getPlayer(packet.target());
            challenge(context.player(), target);
        }));
    }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RouletteDevilSessions::tick);
        OnPlayerDeathWithKiller.EVENT.register((p, killer, reason) -> {
            if (p instanceof ServerPlayer sp) cancel(sp.getUUID(), sp.getServer());
        });
        OnGameEnd.EVENT.register((level, component) -> {
            for (Session s : new HashSet<>(PLAYERS.values())) close(s, level.getServer());
            LAST.clear(); syncAll(level.getServer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            cancel(handler.player.getUUID(), server); LAST.remove(handler.player.getUUID());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!inDuel(handler.player.getUUID())) RouletteDevilRecovery.get(server.overworld()).restore(handler.player);
            LAST.remove(handler.player.getUUID()); syncAll(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session s : new HashSet<>(PLAYERS.values())) close(s, server);
            LAST.clear(); BYPASS.clear(); DEATHS.clear();
        });
    }
    public static boolean challenge(ServerPlayer demon, ServerPlayer opponent) {
        RouletteDevilRoleData data = RouletteDevilRole.data(demon);
        if (data == null || !RouletteDevilRole.isDevil(demon) || !GameUtils.isGameStarted
                || !GameUtils.isPlayerAliveAndSurvival(demon) || opponent == null || opponent == demon
                || opponent.level() != demon.level() || !GameUtils.isPlayerAliveAndSurvival(opponent)) return false;
        if (RouletteDevilRole.refugeeActive(demon) || data.refugee.pending()) {
            RouletteDevilRole.tell(demon, "refugee_suspended"); return false;
        }
        if (inDuel(demon.getUUID()) || inDuel(opponent.getUUID()) || ModEffects.isInAnyDomain(demon)
                || ModEffects.isInAnyDomain(opponent) || ModEffects.hasDomainSanctuary(opponent)
                || demon.hasEffect(ModEffects.SKILL_BANED)) { RouletteDevilRole.tell(demon, "busy"); return false; }
        if (!RouletteDevilRules.eligible(RouletteDevilRole.lowSan(opponent), data.mature(opponent.getUUID(), demon.level().getGameTime()))) {
            RouletteDevilRole.tell(demon, "ineligible"); return false;
        }
        RouletteDevilRoom room = RouletteDevilRoom.create(demon.serverLevel());
        if (room == null) { RouletteDevilRole.tell(demon, "room_failed"); return false; }
        Session s = new Session(demon, opponent, room, data);
        PLAYERS.put(s.demon, s); PLAYERS.put(s.opponent, s);
        try {
            for (ServerPlayer p : List.of(demon, opponent)) {
                p.closeContainer();
                RouletteDevilRecovery.get(p.serverLevel()).capture(p);
                p.getInventory().clearContent(); p.getInventory().selected = 0; p.inventoryMenu.broadcastChanges();
                BYPASS.add(p.getUUID());
                try { p.stopSleeping(); p.stopRiding(); } finally { BYPASS.remove(p.getUUID()); }
                p.addEffect(new MobEffectInstance(ModEffects.DOMAIN_MARK, -1, 7, false, false, false));
                p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, -1, 0, false, false, false));
                p.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 20, 0, false, false, false));
                p.setInvulnerable(false); p.setDeltaMovement(0, 0, 0);
                RouletteDevilRole.title(p, "entry_title", "entry_subtitle");
            }
            data.manifestUntil = 0; data.sync(); RouletteDevilRole.removeVoices(demon);
            syncAll(demon.getServer()); return true;
        } catch (RuntimeException error) {
            close(s, demon.getServer()); Noellesroles.LOGGER.error("轮盘恶魔入场失败，已返还玩家", error); return false;
        }
    }
    private static void tick(MinecraftServer server) {
        for (Session s : new HashSet<>(PLAYERS.values())) {
            if (s.closed) continue;
            ServerPlayer demon = server.getPlayerList().getPlayer(s.demon), other = server.getPlayerList().getPlayer(s.opponent);
            if (!GameUtils.isGameStarted || demon == null || other == null || RouletteDevilRole.data(demon) != s.data
                    || !demon.isAlive() || !other.isAlive() || !GameUtils.isPlayerAliveAndSurvival(demon) || !GameUtils.isPlayerAliveAndSurvival(other)) {
                close(s, server); continue;
            }
            if (!s.entered && s.room.level.getGameTime() >= s.enterAt) {
                try {
                    seat(s, demon, true); seat(s, other, false);
                    if (!(s.room.level.getBlockEntity(s.room.table()) instanceof DevilRouletteTableEntity table)) throw new IllegalStateException("Missing roulette table");
                    s.table = table;
                    if (!table.startDemonMatch(demon, other, (winner, loser) -> settle(s, server, winner, loser))) throw new IllegalStateException("Cannot start roulette");
                    s.entered = true;
                } catch (RuntimeException error) {
                    close(s, server); Noellesroles.LOGGER.error("轮盘恶魔房间启动失败，已返还玩家", error); continue;
                }
            }
            if (s.entered && (s.table.isRemoved() || !s.table.isGameActive() || s.seats.stream().anyMatch(Entity::isRemoved))) close(s, server);
        }
        if (server.overworld().getGameTime() % 5 == 0) syncAll(server);
    }
    private static void seat(Session s, ServerPlayer p, boolean front) {
        BlockPos pos = s.room.seat(front);
        BYPASS.add(p.getUUID());
        try {
            p.stopRiding();
            p.teleportTo(s.room.level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, Set.of(), front ? 0 : 180, 0);
            SeatEntity seat = TMMEntities.SEAT.create(s.room.level);
            if (seat == null) throw new IllegalStateException("Missing seat entity");
            var block = (MountableBlock) s.room.level.getBlockState(pos).getBlock();
            var point = block.getSitPos(s.room.level, s.room.level.getBlockState(pos), pos).add(pos.getX(), pos.getY(), pos.getZ());
            seat.setSeatPos(pos); seat.moveTo(point.x, point.y, point.z, 0, 0);
            s.room.level.addFreshEntity(seat); s.seats.add(seat);
            if (!p.startRiding(seat, true)) throw new IllegalStateException("Cannot mount roulette seat");
            p.fallDistance = 0; p.setDeltaMovement(0, 0, 0);
        } finally { BYPASS.remove(p.getUUID()); }
    }
    private static void settle(Session s, MinecraftServer server, UUID winner, UUID loser) {
        if (s.closed) return;
        ServerPlayer demon = server.getPlayerList().getPlayer(s.demon);
        boolean demonWon = s.demon.equals(winner);
        boolean spared = !demonWon && RouletteDevilRules.spareDefeat(s.data.startingPlayers, s.data.losses);
        if (demonWon) s.data.wins++; else if (spared) s.data.losses++;
        // 先关闭、返程、恢复库存，再杀输家。任何死亡/胜利回调都不可能再次结算本局。
        close(s, server);
        if (demon != null) {
            s.data.sync();
            if (spared) demon.displayClientMessage(Component.translatable("message.noellesroles.roulette_devil.spared", s.data.lives()), false);
            else if (demonWon) demon.displayClientMessage(Component.translatable("message.noellesroles.roulette_devil.progress", s.data.wins, s.data.required()), false);
        }
        ServerPlayer victim = server.getPlayerList().getPlayer(loser);
        ServerPlayer victor = server.getPlayerList().getPlayer(winner);
        if (!spared && victim != null && GameUtils.isPlayerAliveAndSurvival(victim)) {
            DEATHS.add(loser);
            try { victim.setInvulnerable(false); GameUtils.killPlayer(victim, true, victor, RouletteDevilRole.DEATH, true); }
            finally { DEATHS.remove(loser); }
        }
        if (demonWon && demon != null) RouletteDevilRole.declareVictory(demon);
        syncAll(server);
    }
    public static void cancel(UUID player, MinecraftServer server) { Session s = PLAYERS.get(player); if (s != null) close(s, server); }
    /** 必须在难民保存玩家快照之前返程，避免将黑室位置和临时背包写入回溯节点。 */
    public static void cancelAllForRefugee(MinecraftServer server) {
        for (Session s : new HashSet<>(PLAYERS.values())) {
            close(s, server);
            for (UUID id : List.of(s.demon, s.opponent)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) RouletteDevilRole.tell(p, "refugee_interrupted");
            }
        }
    }
    private static void close(Session s, MinecraftServer server) {
        if (s.closed) return;
        s.closed = true; PLAYERS.remove(s.demon, s); PLAYERS.remove(s.opponent, s);
        if (s.table != null) s.table.reset();
        for (UUID id : List.of(s.demon, s.opponent)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue; // 保险记录保留到该玩家下次登录。
            RouletteDevilRecovery.get(server.overworld()).restore(p);
            MountableBlock.lastPos.remove(id);
            p.setInvulnerable(RouletteDevilRole.isDevil(p) && GameUtils.isPlayerAliveAndSurvival(p) && GameUtils.isGameStarted);
        }
        for (SeatEntity seat : s.seats) seat.discard();
        for (ItemEntity item : s.room.level.getEntitiesOfClass(ItemEntity.class, new AABB(s.room.floor).inflate(8))) item.discard();
        s.room.clear(); syncAll(server);
    }
    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<ServerPlayer> demons = GameUtils.isGameStarted ? players.stream().filter(RouletteDevilRole::isDevil)
                .filter(GameUtils::isPlayerAliveAndSurvival).toList() : List.of();
        for (ServerPlayer viewer : players) {
            var d = RouletteDevilRole.data(viewer);
            boolean own = d != null && GameUtils.isGameStarted;
            long now = viewer.level().getGameTime();
            Session s = PLAYERS.get(viewer.getUUID());
            var packet = new RouletteDevilStateS2CPacket(demons.stream().map(Entity::getUUID).toList(),
                    demons.stream().filter(p -> p.level() == viewer.level() && RouletteDevilRole.canSee(viewer, p)).map(Entity::getUUID).toList(),
                    own ? players.stream().filter(p -> p.level() == viewer.level() && GameUtils.isPlayerAliveAndSurvival(p) && RouletteDevilRole.lowSan(p)).map(Entity::getUUID).toList() : List.of(),
                    own ? d.seeds.keySet().stream().sorted().toList() : List.of(),
                    own ? d.seeds.keySet().stream().filter(id -> d.mature(id, now)).sorted().toList() : List.of(),
                    own ? d.wins : 0, own ? d.required() : 0, own ? d.lives() : 0, own ? d.manifestUntil : 0,
                    s == null ? null : s.demon.equals(viewer.getUUID()) ? s.opponent : s.demon, s == null ? 0 : s.enterAt);
            if (!packet.equals(LAST.put(viewer.getUUID(), packet))) ServerPlayNetworking.send(viewer, packet);
        }
    }
}
